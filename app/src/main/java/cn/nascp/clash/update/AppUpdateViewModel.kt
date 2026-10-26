package cn.nascp.clash.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

internal data class UpdateState(
    val busy: Boolean = false,
    val downloading: Boolean = false,
    val automaticCheck: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val release: AppRelease? = null,
    val apk: File? = null,
)

internal class AppUpdateViewModel(app: Application, private val repository: AppUpdateRepository) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, AppUpdateRepository(app))
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun check(automatic: Boolean = false) = work(quietFailure = true, quietCancellation = automatic) {
        val previous = mutableState.value
        mutableState.value = previous.copy(automaticCheck = automatic, message = if (automatic) "" else "正在检查更新…")
        val release = repository.check()
        val apk = previous.apk?.takeIf { release == previous.release && it.isFile }
        mutableState.value = UpdateState(busy = true, automaticCheck = automatic, release = release, apk = apk,
            message = when {
                release != null -> "发现新版本"
                automatic -> ""
                else -> "暂无可用更新；仓库尚无发布版本或当前版本已是最新"
            })
    }

    fun download() = work {
        val release = mutableState.value.release ?: return@work
        mutableState.value = mutableState.value.copy(downloading = true, progress = 0f, apk = null, message = "正在下载安装包…")
        val apk = repository.download(release) { value -> mutableState.value = mutableState.value.copy(progress = value) }
        mutableState.value = mutableState.value.copy(apk = apk, message = "安装包校验通过，可以安装")
    }

    /** Re-check on each installation attempt, including after returning from Android settings. */
    fun prepareInstall(install: (File) -> Unit) = work {
        val snapshot = mutableState.value
        val apk = snapshot.apk ?: error("请先下载安装包")
        val release = snapshot.release ?: error("请先检查更新")
        repository.verifyForInstallation(apk, release)
        install(apk)
    }

    fun message(value: String) { mutableState.value = mutableState.value.copy(message = value) }
    fun cancel() { job?.cancel(); repository.cancel() }
    private fun work(quietFailure: Boolean = false, quietCancellation: Boolean = false, block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) {
                mutableState.value = mutableState.value.copy(message = if (quietCancellation) "" else "更新操作已取消")
                throw e
            }
            catch (e: Exception) {
                val safeMessage = if (quietFailure) "" else when (e) {
                    is IllegalStateException -> e.message ?: "更新失败，请重试"
                    is org.json.JSONException -> "更新信息无效，请稍后重试"
                    is android.content.ActivityNotFoundException -> "无法打开系统安装器"
                    else -> when {
                        mutableState.value.downloading -> "下载安装包失败，请检查网络或存储空间后重试"
                        mutableState.value.apk != null -> "无法安装更新，请重新检查并下载"
                        else -> "无法检查更新，请检查网络后重试"
                    }
                }
                mutableState.value = mutableState.value.copy(message = safeMessage)
            } finally { mutableState.value = mutableState.value.copy(busy = false, downloading = false, automaticCheck = false) }
        }
    }
    override fun onCleared() { repository.cancel(); super.onCleared() }
}
