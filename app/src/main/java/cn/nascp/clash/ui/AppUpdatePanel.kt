package cn.nascp.clash.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.nascp.clash.update.AppUpdateViewModel

@Composable internal fun AppUpdatePanel() {
    val context = LocalContext.current
    val vm: AppUpdateViewModel = viewModel()
    val state by vm.state.collectAsState()
    fun install() {
        vm.prepareInstall { apk ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            vm.message("已打开系统安装界面；可在取消后再次安装")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) install()
        else vm.message("尚未允许安装应用，可授权后重试")
    }
    MainButton("检查更新", !state.busy) { vm.check() }
    if (state.message.isNotEmpty()) Info(state.message)
    state.release?.let { release ->
        Panel {
            UiText(release.tag, style = MaterialTheme.typography.titleLarge)
            UiText(bytes(release.size))
            if (release.notes.isNotBlank()) UiText(release.notes)
            if (state.downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                UiText("${(state.progress * 100).toInt()}%")
            }
            if (state.apk == null) MainButton("下载更新", !state.busy) { vm.download() }
            else MainButton("安装更新", !state.busy) {
                if (context.packageManager.canRequestPackageInstalls()) install()
                else runCatching {
                    permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }.onFailure { vm.message("无法打开安装授权设置，请在系统设置中允许安装应用") }
            }
        }
    }
    if (state.busy && !state.automaticCheck) MainButton("取消更新操作") { vm.cancel() }
}
