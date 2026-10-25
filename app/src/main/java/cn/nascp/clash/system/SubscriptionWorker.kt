package cn.nascp.clash.system

import android.content.Context
import androidx.work.*
import cn.nascp.clash.data.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class SubscriptionWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val repo=ClashRepository.get(applicationContext)
        return try {
            repo.initialized()
            val result=repo.updateSubscriptions(false)
            repo.saveDraft("automation-last","${System.currentTimeMillis()} · $result")
            Result.success()
        }catch(e:CancellationException){throw e}
        catch(e:Exception){repo.saveDraft("automation-last","${System.currentTimeMillis()} · 更新失败，原配置已保留");if(runAttemptCount<3)Result.retry()else Result.failure()}
    }
}
fun scheduleSubscriptions(context:Context,minutes:Long,enabled:Boolean){
    val manager=WorkManager.getInstance(context)
    if(!enabled){manager.cancelUniqueWork("subscriptions");return}
    require(minutes>=15){"后台周期至少15分钟"}
    manager.enqueueUniquePeriodicWork("subscriptions",ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<SubscriptionWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
}
suspend fun ClashRepository.updateSubscriptions(force:Boolean=true):String {
    var updated=0;var staged=0;var failed=0
    state.value.profiles.filter{it.source.isNotBlank()}.forEach { p->
        val interval=draft("profile-interval.${p.id}").toLongOrNull()?:draft("automation-minutes").toLongOrNull()?:720L
        val last=maxOf(draft("auto-last.${p.id}").toLongOrNull()?:0L,draft("auto-attempt.${p.id}").toLongOrNull()?:0L,p.updated)
        if(!force && System.currentTimeMillis()-last<interval.coerceAtLeast(15)*60_000L)return@forEach
        try{
            saveDraft("auto-attempt.${p.id}",System.currentTimeMillis().toString())
            val yaml=download(p.source,p.userAgent)
            if(state.value.activeId==p.id || state.value.phase in listOf(VpnPhase.STARTING,VpnPhase.STOPPING)){
                validate(yaml,p.id);saveDraft("pending-update.${p.id}",yaml);staged++
            }else{saveProfile(p.name,yaml,p.id,p.source,select=false);updated++}
            saveDraft("auto-last.${p.id}",System.currentTimeMillis().toString())
        }catch(e:CancellationException){throw e}catch(_:Exception){failed++}
    }
    return "已保存 $updated · 待应用 $staged · 失败 $failed"
}
