package cn.nascp.clash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ClashRepository.get(app)
    val state = repo.state
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    fun run(action: suspend () -> Unit) {
        if (working.value) return
        viewModelScope.launch {
            working.value = true
            repo.clearMessage()
            try { action() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { repo.message(e.message ?: "操作失败", cn.nascp.clash.data.MessageKind.ERROR) }
            finally { working.value = false }
        }
    }
}
