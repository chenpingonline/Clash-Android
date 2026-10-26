package cn.nascp.clash.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import cn.nascp.clash.MainViewModel
import cn.nascp.clash.data.*
import cn.nascp.clash.core.NativeCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Shared heading rhythm for grouped pages, aligned with the contents of each card. */
@Composable internal fun SectionHeading(title:String) {
    UiText(title,Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,top=8.dp,bottom=2.dp)
        .semantics { heading() },style=MaterialTheme.typography.bodyMedium,
        fontWeight=androidx.compose.ui.text.font.FontWeight.Medium,
        color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.85f))
}

@Composable internal fun NavigationGroup(title:String?,routes:List<String>,go:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(title != null)SectionHeading(title)
        @Composable fun Rows() {
            routes.forEachIndexed { index,id ->
                if(index>0 && LocalPlain.current)Spacer(Modifier.height(1.dp))
                Entry(screens.getValue(id).title,icon=settingsScreenIcon(id)){go(id)}
            }
        }
        if(LocalPlain.current)PlainGroup{Rows()} else Panel{Rows()}
    }
}

@Composable internal fun SettingsHome(go:(String)->Unit) {
    // Keep rows outside the dock touch area, but soften the viewport's cut edge.
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.padding(bottom=LocalDockClearance.current)) {
    CompositionLocalProvider(LocalDockClearance provides 0.dp) {
    Page(Modifier.drawWithContent {
        drawContent()
        val fadeHeight = 24.dp.toPx().coerceAtMost(size.height)
        val top = size.height - fadeHeight
        drawRect(Brush.verticalGradient(listOf(background.copy(alpha=0f),background),
            startY=top,endY=size.height),topLeft=Offset(0f,top),size=Size(size.width,fadeHeight))
    }) {
        NavigationGroup(null,listOf("appearance","vpn"),go)
        NavigationGroup(null,listOf("automation","system"),go)
        NavigationGroup(null,listOf("backup","core"),go)
        NavigationGroup(null,listOf("global-enhancements","remote"),go)
        NavigationGroup(null,listOf("about"),go)
    }
    }
    }
}

@Composable internal fun ProfileNavigation(s:AppState,vm:MainViewModel,go:(String)->Unit) {
    NavigationGroup("配置内容",listOf("profile-info","node-edit","group-edit","profile-rules"),go)
    NavigationGroup("网络与解析",listOf("network","dns","geo"),go)
    NavigationGroup("配置增强",listOf("override","script"),go)
    NavigationGroup("高级与历史",listOf("yaml","history"),go)
    val context=androidx.compose.ui.platform.LocalContext.current
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/yaml")){uri->
        if(uri!=null)vm.run{
            val yaml=s.selected?.yaml?:error("请先选择配置")
            withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(yaml.toByteArray())}?:error("无法导出")}
            vm.repo.message("已导出原始配置")
        }
    }
    SubpageButton(outlined=true,modifier=Modifier.fillMaxWidth(),onClick={export.launch("clash-config.yaml")}){UiText("导出当前 YAML")}
}

@Composable internal fun ProfileRulesPage(s:AppState,go:(String)->Unit) {
    Page {
        ProfileScope(s)
        if(s.selected!=null)NavigationGroup("配置规则",listOf("rule-edit","rule-import","rule-set"),go)
    }
}

@Composable internal fun ProfileScope(s:AppState) {
    if(s.selected==null)Info("请先选择配置") else {
        UiText("${tr("仅作用于配置")}：${s.selected!!.name}",style=MaterialTheme.typography.bodyMedium)
        Info("修改保留为该配置的增强，保存后下次启动 VPN 生效。")
        if(s.phase !in listOf(VpnPhase.STOPPED,VpnPhase.FAILED))Info("请先停止 VPN 再保存修改。")
    }
}

@Composable internal fun GlobalEnhancementsPage(go:(String)->Unit) {
    Page {Info("作用于全部配置，保存后下次启动 VPN 生效。");NavigationGroup("全局增强",listOf("global-override","global-script"),go)}
}

@Composable internal fun GeoStatusPage(vm:MainViewModel,s:AppState) {
    var status by remember{mutableStateOf(JSONArray())}
    suspend fun refresh(){status=withContext(Dispatchers.IO){NativeCore.invoke("geo-status") as JSONArray}}
    LaunchedEffect(Unit){refresh()}
    Page {
        Info("本机共享数据库状态；来源和更新计划在对应配置中设置。")
        if(status.length()==0)Info("暂无 GEO 数据")
        for(i in 0 until status.length()) {
            val item=status.getJSONObject(i)
            Panel {UiText(item.optString("name"));UiText(if(item.optBoolean("exists"))bytes(item.optLong("bytes"))+" · "+java.util.Date(item.optLong("updated")) else tr("尚未下载"))}
        }
        MainButton("刷新状态"){vm.run{refresh()}}
        MainButton("更新 GEO 数据",s.phase==VpnPhase.RUNNING){vm.run{withContext(Dispatchers.IO){NativeCore.invoke("geo-update")};refresh();vm.repo.message("GEO 更新请求完成，请核对文件时间")}}
        if(s.phase!=VpnPhase.RUNNING)Info("启动 VPN 后可更新 GEO 数据。")
    }
}

internal val profileScopedRoutes=setOf("profile","profile-info","node-edit","group-edit","profile-rules","network","dns","dns-servers","fakeip","fallback","hosts","geo","override","script","yaml","history","rule-edit","rule-import","rule-set","rule-provider")
