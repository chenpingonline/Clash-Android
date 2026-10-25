package cn.nascp.clash.data

import android.content.Context
import android.os.SystemClock
import java.util.UUID

internal data class TrafficTotals(val upload:Long=0,val download:Long=0)

/** Counter deltas survive core resets; an app-process epoch distinguishes a new JNI core.
 * Checkpoints are independent of the UI, as in the fnOS traffic totals tracker. */
internal class TrafficTotalsStore(context:Context,session:String=processSession,storageName:String="traffic-totals") {
    private val prefs=context.getSharedPreferences(storageName,Context.MODE_PRIVATE)
    private val epoch=session
    private var lastUpload=if(prefs.getString("session",null)==session)prefs.getLong("last-upload",0) else 0L
    private var lastDownload=if(prefs.getString("session",null)==session)prefs.getLong("last-download",0) else 0L
    private var totals=TrafficTotals(prefs.getLong("upload",0).coerceAtLeast(0),prefs.getLong("download",0).coerceAtLeast(0))
    private var checkpointAt=SystemClock.elapsedRealtime()
    @Synchronized fun current()=totals
    @Synchronized fun observe(upload:Long,download:Long):TrafficTotals {
        val up=upload.coerceAtLeast(0);val down=download.coerceAtLeast(0)
        fun delta(current:Long,last:Long)=if(current>=last)current-last else current
        fun add(total:Long,amount:Long)=if(Long.MAX_VALUE-total<amount)Long.MAX_VALUE else total+amount
        totals=TrafficTotals(add(totals.upload,delta(up,lastUpload)),add(totals.download,delta(down,lastDownload)))
        lastUpload=up;lastDownload=down
        if(SystemClock.elapsedRealtime()-checkpointAt>=30_000)checkpoint()
        return totals
    }
    @Synchronized fun checkpoint() {
        check(prefs.edit().putLong("upload",totals.upload).putLong("download",totals.download)
            .putLong("last-upload",lastUpload).putLong("last-download",lastDownload).putString("session",epoch).commit()) { "保存累计流量失败" }
        checkpointAt=SystemClock.elapsedRealtime()
    }
    companion object { private val processSession=UUID.randomUUID().toString() }
}
