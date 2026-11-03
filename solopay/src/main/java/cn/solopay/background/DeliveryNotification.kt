package cn.solopay.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import cn.solopay.R

/** Foreground fallback required by expedited WorkManager jobs on Android 11 and earlier. */
object DeliveryNotification {
    fun foregroundInfo(context: Context, id: Int, title: String, text: String, target: Intent): ForegroundInfo {
        val channelId = "delivery_sync"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(
                channelId, context.getString(R.string.solopay_delivery_channel), NotificationManager.IMPORTANCE_LOW,
            ))
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(PendingIntent.getActivity(context, id, target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id, notification)
    }
}
