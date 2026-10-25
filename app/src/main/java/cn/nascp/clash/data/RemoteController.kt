package cn.nascp.clash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteInstance(val id:String,val name:String,val address:String,val token:String)
fun ClashRepository.remotes():List<RemoteInstance> {
    val data=JSONArray(draft("remote-instances").ifBlank{"[]"})
    return (0 until data.length()).map { val o=data.getJSONObject(it);RemoteInstance(o.getString("id"),o.getString("name"),o.getString("address"),o.getString("token")) }
}
fun ClashRepository.saveRemotes(values:List<RemoteInstance>){saveDraft("remote-instances",JSONArray().also{a->values.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("address",it.address).put("token",it.token))}}.toString())}
internal fun remoteBase(instance:RemoteInstance):URL {
    val base=URL(instance.address.trim().trimEnd('/'))
    require(base.protocol=="https" && base.host.isNotBlank() && base.userInfo==null && base.query==null && base.ref==null){"请使用不含凭据或查询参数的 HTTPS 地址"}
    return base
}
suspend fun remoteApi(instance:RemoteInstance,path:String,method:String="GET",data:JSONObject?=null):JSONObject=withContext(Dispatchers.IO){
    val base=remoteBase(instance)
    require(path.startsWith("/") && !path.startsWith("//")){"无效的控制器路径"}
    val conn=URL(base.toString().trimEnd('/')+path).openConnection() as HttpURLConnection
    conn.requestMethod=method;conn.instanceFollowRedirects=false;conn.connectTimeout=8000;conn.readTimeout=10000
    conn.setRequestProperty("Authorization","Bearer ${instance.token}")
    try{if(data!=null){conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use{it.write(data.toString().toByteArray())}}
        check(conn.responseCode in 200..299){"远程请求 HTTP ${conn.responseCode}；该端点可能不支持此能力"}
        val body=conn.inputStream.use { input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;check(out.size()+n<=8*1024*1024){"远程响应过大"};out.write(buffer,0,n)};out.toString("UTF-8") }
        if(body.isBlank())JSONObject()else JSONObject(body)
    }finally{conn.disconnect()}
}

fun remoteLogs(instance:RemoteInstance):kotlinx.coroutines.flow.Flow<String> = remoteLogs(instance,
    okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(8,java.util.concurrent.TimeUnit.SECONDS).readTimeout(0,java.util.concurrent.TimeUnit.SECONDS).build())
internal fun remoteLogs(instance:RemoteInstance,client:okhttp3.OkHttpClient):kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.callbackFlow {
    val url=remoteBase(instance).toString().trimEnd('/').replaceFirst("https://","wss://")+"/logs?level=info"
    val socket=client.newWebSocket(okhttp3.Request.Builder().url(url).header("Authorization","Bearer ${instance.token}").build(),object:okhttp3.WebSocketListener(){
        override fun onMessage(webSocket:okhttp3.WebSocket,text:String){
            val message=runCatching{JSONObject(text).optString("payload")}.getOrDefault("无效日志事件")
            trySend(message.replace(Regex("https?://\\S+"),"[URL]").replace(Regex("(?i)(password|token|secret|uuid|authorization)[=: ]+[^ ,;]+"),"$1=[redacted]").take(1500))
        }
        override fun onFailure(webSocket:okhttp3.WebSocket,t:Throwable,response:okhttp3.Response?){trySend("日志连接失败；请检查服务器日志端点和认证");close()}
        override fun onClosed(webSocket:okhttp3.WebSocket,code:Int,reason:String){close()}
    })
    awaitClose {socket.cancel();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
}
