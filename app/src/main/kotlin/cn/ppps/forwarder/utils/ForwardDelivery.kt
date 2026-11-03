package cn.ppps.forwarder.utils

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.activity.MainActivity
import cn.ppps.forwarder.workers.SendWorker
import cn.solopay.background.DeliveryNotification
import cn.solopay.background.DeliveryWakeLock
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Keep original rule/channel semantics, but prioritize live delivery and protect async callbacks. */
object ForwardDelivery {
    private val windows = ConcurrentHashMap<Long, Closeable>()
    private val handler = Handler(Looper.getMainLooper())
    private val directExecutor = Executor { it.run() }

    fun enqueue(context: Context, msgInfoJson: String): Operation {
        val lease = DeliveryWakeLock.acquire(context, "forward-enqueue", 10_000)
        try {
            val request = OneTimeWorkRequestBuilder<SendWorker>()
                .setInputData(workDataOf(Worker.SEND_MSG_INFO to msgInfoJson))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            return WorkManager.getInstance(context).enqueue(request).also {
                it.result.addListener({ lease.close() }, directExecutor)
            }
        } catch (e: Exception) {
            lease.close()
            throw e
        }
    }

    fun foregroundInfo(context: Context): ForegroundInfo = DeliveryNotification.foregroundInfo(
        context, 11002, context.getString(R.string.smsforwarder_launcher_name),
        context.getString(R.string.forward_delivery_running),
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
    )

    fun begin(logId: Long) {
        if (logId <= 0) return
        val lease = DeliveryWakeLock.acquire(App.context, "forward-send", 120_000)
        windows.put(logId, lease)?.close()
        handler.postDelayed({
            if (windows.remove(logId, lease)) lease.close()
        }, 120_000)
    }

    fun complete(logId: Long?) {
        if (logId != null) windows.remove(logId)?.close()
    }
}
