package cn.nascp.clash.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ConnectionApplication(val uid:Int?,val label:String="",val packages:List<String> = emptyList(),val icon:Bitmap?=null) {
    val shared get()=packages.size>1
    val searchText get()=listOf(label,packages.joinToString(" "),uid?.toString().orEmpty()).joinToString(" ")
}

internal class ConnectionApplicationResolver(context:Context) {
    private val manager=context.applicationContext.packageManager
    private val iconSize=(40*context.resources.displayMetrics.density).toInt().coerceIn(32,192)
    private data class Cached(val app:ConnectionApplication,val time:Long)
    private val cache=LruCache<Int,Cached>(128)
    fun resolve(uid:Int):ConnectionApplication {
        val now=android.os.SystemClock.elapsedRealtime()
        cache.get(uid)?.takeIf{now-it.time<15_000}?.let{return it.app}
        val packages=runCatching{manager.getPackagesForUid(uid)?.distinct()?.sorted().orEmpty()}.getOrDefault(emptyList())
        val info=packages.singleOrNull()?.let{runCatching{manager.getApplicationInfo(it,0)}.getOrNull()}
        val label=info?.let{runCatching{manager.getApplicationLabel(it).toString().filterNot(Char::isISOControl).take(128)}.getOrNull()}.orEmpty()
        val icon=info?.let{runCatching{manager.getApplicationIcon(it).toBitmap(iconSize,iconSize)}.getOrNull()}
        return ConnectionApplication(uid,label,packages,icon).also{cache.put(uid,Cached(it,now))}
    }
}

internal fun connectionUid(connection:JSONObject):Int? = connection.optJSONObject("metadata")?.optInt("uid",-1)?.takeIf{it>0}

@Composable internal fun rememberConnectionApplications(connections:JSONArray):Map<Int,ConnectionApplication> {
    val context=LocalContext.current.applicationContext
    val resolver=remember(context){ConnectionApplicationResolver(context)}
    val apps by produceState(emptyMap<Int,ConnectionApplication>(),connections,resolver) {
        value=withContext(Dispatchers.IO){
            (0 until connections.length()).mapNotNull{connectionUid(connections.getJSONObject(it))}.distinct().associateWith(resolver::resolve)
        }
    }
    return apps
}

@Composable private fun ApplicationIcon(app:ConnectionApplication?) {
    if(app?.icon!=null && !app.shared)Image(app.icon.asImageBitmap(),contentDescription=null,
        modifier=Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).testTag("connection-app-icon:${app.packages.singleOrNull().orEmpty()}"))
    else Icon(Icons.Outlined.Apps,contentDescription=null,modifier=Modifier.size(28.dp).testTag("connection-app-icon:unknown"),tint=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable internal fun connectionAppTitle(app:ConnectionApplication?):String=when {
    app==null || app.packages.isEmpty()->tr("未知应用")
    app.shared->"${tr("共享 UID")} ${app.uid}"
    else->app.label.ifBlank{app.packages.single()}
}

@Composable internal fun ActivityConnectionRow(title:String,subtitle:String,app:ConnectionApplication?,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=64.dp).padding(horizontal=18.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        ApplicationIcon(app)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            UiText(connectionAppTitle(app),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            UiText(title,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            UiText(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun ConnectionApplicationDetails(connection:JSONObject) {
    val connections=remember(connection){JSONArray().put(connection)}
    val apps=rememberConnectionApplications(connections)
    val app=connectionUid(connection)?.let(apps::get)
    Panel {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            ApplicationIcon(app)
            Column(Modifier.weight(1f)) {
                UiText("应用",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                UiText(connectionAppTitle(app),style=MaterialTheme.typography.titleMedium)
            }
        }
        app?.uid?.let{UiText("UID: $it")}
        app?.packages?.forEach{UiText(it,style=MaterialTheme.typography.bodySmall)}
        if(app?.shared==true)Info("多个应用共用此 UID，无法唯一确定发起应用。")
    }
}
