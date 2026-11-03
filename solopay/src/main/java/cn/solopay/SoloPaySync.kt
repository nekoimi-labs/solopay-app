package cn.solopay

import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import cn.solopay.background.DeliveryNotification
import cn.solopay.background.DeliveryWakeLock
import cn.solopay.service.SoloPayKeepAliveService
import cn.solopay.ui.SoloPayMainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

object SoloPaySync {
    private const val IMMEDIATE = "solopay-outbox-sync"
    private const val PERIODIC = "solopay-outbox-recovery"
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    private val directAttempt = Mutex()

    fun restore(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!CredentialsStore(context).isBound()) {
            manager.cancelUniqueWork(PERIODIC)
            manager.cancelUniqueWork(IMMEDIATE)
            return
        }
        manager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SoloPaySyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network).build(),
        )
        requestImmediate(context)
    }

    /** Queue a durable retry before trying a short foreground sync or an in-process attempt. */
    fun requestImmediate(context: Context) {
        runCatching { enqueue(context) }
            .onFailure { Log.e("SoloPaySync", "Retry scheduling failed; record remains in the outbox", it) }
        if (SoloPayKeepAliveService.tryStart(context)) return
        // Android can reject background foreground-service launches. The notification listener's
        // current process can still try; the saved queue survives if the process is later stopped.
        if (!directAttempt.tryLock()) return
        val lease = DeliveryWakeLock.acquire(context, "payment-upload", 60_000)
        SoloPay.ioScope.launch {
            try {
                flushOutbox(context.applicationContext, maxDurationMillis = 30_000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SoloPaySync", "Immediate upload deferred to saved retry", e)
            } finally {
                lease.close()
                directAttempt.unlock()
            }
        }
    }

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SoloPaySyncWorker>()
                .setConstraints(network)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun stopRecovery(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}

class SoloPaySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = DeliveryNotification.foregroundInfo(
        applicationContext, 11003,
        applicationContext.getString(R.string.solopay_app_name),
        applicationContext.getString(R.string.solopay_keep_alive_text),
        Intent(applicationContext, SoloPayMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val retry = flushOutbox(applicationContext)
            if (!CredentialsStore(applicationContext).isBound()) SoloPaySync.stopRecovery(applicationContext)
            if (retry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SoloPaySync", "Outbox sync failed", e)
            Result.retry()
        }
    }
}
