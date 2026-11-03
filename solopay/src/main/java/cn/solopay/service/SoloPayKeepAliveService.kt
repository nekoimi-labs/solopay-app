package cn.solopay.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import cn.solopay.R
import cn.solopay.SoloPaySync
import cn.solopay.background.DeliveryNotification
import cn.solopay.background.DeliveryWakeLock
import cn.solopay.flushOutbox
import cn.solopay.ui.SoloPayMainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A short foreground sync, never a second permanent notification listener. */
class SoloPayKeepAliveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var dirty = false
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        dirty = true
        try {
            val info = DeliveryNotification.foregroundInfo(
                this, 11001, getString(R.string.solopay_app_name),
                getString(R.string.solopay_keep_alive_text),
                Intent(this, SoloPayMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(info.notificationId, info.notification, info.foregroundServiceType)
            } else startForeground(info.notificationId, info.notification)
        } catch (e: Exception) {
            Log.w("SoloPaySync", "Foreground sync unavailable; keeping durable retry", e)
            runCatching { SoloPaySync.enqueue(this) }
                .onFailure { Log.e("SoloPaySync", "Retry scheduling failed; record remains in the outbox", it) }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val lease = DeliveryWakeLock.acquire(this, "payment-foreground-sync", 60_000)
        job = scope.launch {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            try {
                do {
                    dirty = false
                    val retry = withContext(Dispatchers.IO) {
                        flushOutbox(this@SoloPayKeepAliveService, maxDurationMillis = 30_000)
                    }
                    if (retry) break // Persisted expedited work applies network backoff.
                } while (dirty && SystemClock.elapsedRealtime() < deadline)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SoloPaySync", "Foreground upload deferred to saved retry", e)
            } finally {
                lease.close()
                @Suppress("DEPRECATION")
                stopForeground(true)
                stopSelfResult(latestStartId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Android 12+ can deny background FGS starts; the caller must retain its queued retry. */
        fun tryStart(context: Context): Boolean = try {
            val intent = Intent(context, SoloPayKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            true
        } catch (e: IllegalStateException) {
            Log.d("SoloPaySync", "Background foreground-service start denied; using current execution window")
            false
        } catch (e: SecurityException) {
            Log.w("SoloPaySync", "Foreground-service permission unavailable", e)
            false
        }
    }
}
