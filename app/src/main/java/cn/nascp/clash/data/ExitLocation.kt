package cn.nascp.clash.data

import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal val exitLocationServices=listOf(
    "https://ipwho.is/?lang=zh-CN",
    "https://ip.api.skk.moe/cf-geoip",
    "https://api.ipapi.is/",
    "https://api.ip.sb/geoip",
    "https://get.geojs.io/v1/ip/geo.json",
    "https://ipapi.co/json")

data class ExitLocation(val ip:String,val country:String="",val countryCode:String="",val region:String="",val city:String="",
                        val timezone:String="",val utcOffset:String="",val elapsedMillis:Long=0,val updatedAt:Long=System.currentTimeMillis(),
                        val organization:String="",val asn:Long=0,val asnOrganization:String="",val source:String="")

/** Read only source-address metadata. Never infer that a selected group carried the request. */
internal fun parseExitLocation(text:String,elapsedMillis:Long,source:String=""):ExitLocation {
    val root=JSONObject(text)
    require(!root.optBoolean("error") && (!root.has("success") || root.optBoolean("success")))
    fun clean(value:String)=value.filter{!it.isISOControl()}.trim().take(128)
    fun field(obj:JSONObject,key:String)=(obj.opt(key) as? String).orEmpty()
    val ip=(root.opt("ip") as? String).orEmpty().trim()
    require(ip.length<=45)
    // Validate literals without resolving hostnames supplied by an untrusted response.
    val valid=if(ip.contains(':')) ip.matches(Regex("[0-9a-fA-F:]+")) && runCatching{InetAddress.getByName(ip).hostAddress!=null}.getOrDefault(false)
        else ip.split('.').let{it.size==4 && it.all{part->part.isNotEmpty() && part.all(Char::isDigit) && (part.toIntOrNull()?.let{it in 0..255}==true)}}
    require(valid)
    val nested=root.optJSONObject("location")
    val country=clean(nested?.let{field(it,"country")} ?: field(root,"country_name").ifBlank{field(root,"country")})
    var code=clean(nested?.let{field(it,"country_code")} ?: field(root,"country_code"))
    if(code.isBlank() && field(root,"country_name").isNotBlank())code=clean(field(root,"country"))
    if(code.isBlank() && country.length==2)code=country
    val zone=nested?.opt("timezone") ?: root.opt("timezone")
    val timezone=clean(when(zone){is String->zone;is JSONObject->field(zone,"id");else->""})
    val offset=clean(if(zone is JSONObject)field(zone,"utc") else field(root,"utc_offset"))
    // Providers use distinct schemas: ipapi.is nests ASN, ipwho.is nests connection,
    // ipapi.co prefixes its ASN with AS, while GeoJS and SKK use flat fields.
    val connection=root.optJSONObject("connection")
    val asnObject=root.optJSONObject("asn")
    val organization=clean(listOf(
        asnObject?.let{field(it,"org")}.orEmpty(),root.optJSONObject("company")?.let{field(it,"name")}.orEmpty(),
        connection?.let{field(it,"org").ifBlank{field(it,"isp")}}.orEmpty(),
        field(root,"organization"),field(root,"isp"),field(root,"org"),field(root,"asOrg"),field(root,"organization_name")
    ).firstOrNull{it.isNotBlank()}.orEmpty())
    val asnRaw=asnObject?.opt("asn") ?: connection?.opt("asn") ?: root.opt("asn")
    val asn=asnRaw?.toString()?.removePrefix("AS")?.toLongOrNull()?.takeIf{it in 1..4294967295L} ?: 0
    val asnOrganization=clean(listOf(asnObject?.let{field(it,"org")}.orEmpty(),
        connection?.let{field(it,"isp")}.orEmpty(),field(root,"asn_organization"),field(root,"org"),
        field(root,"asOrg"),field(root,"organization_name")).firstOrNull{it.isNotBlank()}.orEmpty())
    return ExitLocation(ip,country,code.uppercase(Locale.ROOT).takeIf{it.matches(Regex("[A-Z]{2}"))}.orEmpty(),
        clean(nested?.let{field(it,"state")} ?: field(root,"region")),
        clean(nested?.let{field(it,"city")} ?: field(root,"city")),timezone,offset,elapsedMillis,
        organization=organization,asn=asn,asnOrganization=asnOrganization,source=source)
}

/** Uses Android's ordinary sockets, which participate in the app's VPN routing.
 * It must stay outside the synchronized JNI bridge so probing cannot block Stop or stats. */
internal suspend fun queryExitLocation(endpoints:List<String> = exitLocationServices):ExitLocation = withTimeout(33_000) {
    for(endpoint in endpoints) {
        currentCoroutineContext().ensureActive()
        try {
            return@withTimeout withTimeout(5_000) service@{
                // Up to two retries share the service's five-second budget.
                for(attempt in 0..2) {
                    try { return@service fetchExitLocation(endpoint) }
                    catch(error:IOException) {
                        if(attempt==2)throw error
                        delay(250L shl attempt)
                    }
                }
                error("Unreachable")
            }
        } catch(timeout:TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){ /* A fixed UI error is emitted after all services fail. */ }
    }
    error("出口信息更新失败，请重试")
}

private suspend fun fetchExitLocation(endpoint:String):ExitLocation = suspendCancellableCoroutine { continuation ->
    val active=AtomicReference<HttpURLConnection?>()
    continuation.invokeOnCancellation { active.getAndSet(null)?.disconnect() }
    Dispatchers.IO.dispatch(continuation.context) {
        try {
            val url=URL(endpoint)
            require(url.protocol=="https" && url.userInfo==null)
            val connection=url.openConnection() as HttpURLConnection
            active.set(connection)
            if(!continuation.isActive){active.getAndSet(null)?.disconnect();return@dispatch}
            connection.connectTimeout=5000;connection.readTimeout=5000;connection.instanceFollowRedirects=false;connection.useCaches=false
            // Never reuse a connection established through the previously selected node.
            connection.setRequestProperty("Connection","close")
            connection.setRequestProperty("Cache-Control","no-cache")
            connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("User-Agent","Clash-Android/${cn.nascp.clash.BuildConfig.VERSION_NAME}")
            val start=System.nanoTime()
            require(connection.responseCode in 200..299)
            val text=connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {
                    if(!continuation.isActive)throw CancellationException()
                    val count=input.read(buffer);if(count<0)break
                    require(output.size()+count<=128*1024)
                    output.write(buffer,0,count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            val result=parseExitLocation(text,(System.nanoTime()-start)/1_000_000,url.host)
            continuation.resumeWith(Result.success(result))
        } catch(error:Exception) {
            if(continuation.isActive)continuation.resumeWith(Result.failure(error))
        } finally { active.getAndSet(null)?.disconnect() }
    }
}

internal data class ExitRoutingKey(val phase:VpnPhase,val profile:String,val mode:String,val runtimeRevision:Long,val groups:List<Pair<String,String>>)
internal fun AppState.exitRoutingKey()=ExitRoutingKey(phase,activeId,mode,runtimeRevision,
    proxies.keys().asSequence().mapNotNull { name -> proxies.optJSONObject(name)?.takeIf{it.has("all")}?.let{name to it.optString("now")} }.sortedBy{it.first}.toList())
