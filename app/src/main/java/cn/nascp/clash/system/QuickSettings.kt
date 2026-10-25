package cn.nascp.clash.system

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import cn.nascp.clash.R

/** Android 13+ supplies the system confirmation; older versions use the edit panel. */
fun requestVpnTile(context: Context, result: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < 33) {
        result("请下拉控制中心，点击编辑，将 Clash VPN 添加到快捷设置")
        return
    }
    val manager = context.getSystemService(StatusBarManager::class.java)
    if (manager == null) {
        result("系统暂不支持直接添加，请在控制中心的编辑界面添加 Clash VPN")
        return
    }
    try {
        manager.requestAddTileService(
            ComponentName(context, VpnTileService::class.java),
            "Clash VPN", Icon.createWithResource(context, R.drawable.ic_qs_clash),
            context.mainExecutor
        ) { code ->
            result(when (code) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Clash VPN 已添加到快捷设置"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Clash VPN 已在快捷设置中，可下拉控制中心使用"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "未添加快捷开关，可稍后重试或在控制中心手动添加"
                else -> "系统未能添加，请在控制中心的编辑界面添加 Clash VPN"
            })
        }
    } catch (_: SecurityException) {
        result("系统未能添加，请在控制中心的编辑界面添加 Clash VPN")
    } catch (_: IllegalArgumentException) {
        result("系统未能添加，请在控制中心的编辑界面添加 Clash VPN")
    }
}
