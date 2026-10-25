package cn.nascp.clash.system

import android.service.quicksettings.TileService
import android.service.quicksettings.Tile
import android.content.Intent
import android.app.PendingIntent
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import cn.nascp.clash.MainActivity
import cn.nascp.clash.data.ClashRepository
import cn.nascp.clash.data.VpnPhase
import cn.nascp.clash.vpn.ClashVpnService
import kotlinx.coroutines.*

class VpnTileService:TileService(){
    private var listening:CoroutineScope?=null
    override fun onStartListening(){
        super.onStartListening();listening?.cancel()
        listening=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate).also{scope->scope.launch{
            ClashRepository.get(this@VpnTileService).state.collect{snapshot->qsTile?.apply{
                state=when(snapshot.phase){VpnPhase.RUNNING->Tile.STATE_ACTIVE;VpnPhase.STARTING,VpnPhase.STOPPING->Tile.STATE_UNAVAILABLE;else->Tile.STATE_INACTIVE}
                label="Clash VPN"
                subtitle=when(snapshot.phase){
                    VpnPhase.RUNNING->"已连接"
                    VpnPhase.STARTING->"正在连接"
                    VpnPhase.STOPPING->"正在断开"
                    VpnPhase.FAILED->"连接失败"
                    VpnPhase.STOPPED->"已断开"
                }
                contentDescription="$label，$subtitle"
                updateTile()
            }}
        }}
    }
    override fun onStopListening(){listening?.cancel();listening=null;super.onStopListening()}
    override fun onDestroy(){listening?.cancel();super.onDestroy()}
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // API <34 has no PendingIntent overload.
    override fun onClick(){super.onClick();val repo=ClashRepository.get(this)
        if(repo.state.value.phase==VpnPhase.RUNNING)startService(Intent(this,ClashVpnService::class.java).setAction(ClashVpnService.STOP))
        else if(repo.state.value.phase in listOf(VpnPhase.STARTING,VpnPhase.STOPPING))return
        else if(VpnService.prepare(this)==null&&repo.state.value.ready&&repo.state.value.selected!=null)ContextCompat.startForegroundService(this,Intent(this,ClashVpnService::class.java))
        else {val intent=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE))else @Suppress("DEPRECATION") startActivityAndCollapse(intent)}
    }
}
