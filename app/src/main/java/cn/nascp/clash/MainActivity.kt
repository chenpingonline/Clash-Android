package cn.nascp.clash

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.ui.ClashApp
import cn.nascp.clash.vpn.ClashVpnService

class MainActivity : ComponentActivity() {
    private val authorize = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startVpn() else ClashRepository.get(this).message("VPN授权未通过，配置已保留", cn.nascp.clash.data.MessageKind.ERROR)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ClashApp(onStart = {
            val permission = VpnService.prepare(this)
            if (permission != null) authorize.launch(permission) else startVpn()
        }, onStop = { startService(Intent(this, ClashVpnService::class.java).setAction(ClashVpnService.STOP)) }) }
    }
    private fun startVpn() {
        ContextCompat.startForegroundService(this, Intent(this, ClashVpnService::class.java)) }
}
