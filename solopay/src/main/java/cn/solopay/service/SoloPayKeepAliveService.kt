package cn.solopay.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.solopay.R
import cn.solopay.SoloPay
import cn.solopay.flushOutbox
import kotlinx.coroutines.launch

/**
 * 前台服务：短时启动以完成队列上报（满足 Android 8+ 对 startForegroundService 的时限要求）。
 */
class SoloPayKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SoloPay.ensureInit(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SoloPay.ensureInit(this)
        ensureChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.solopay_keep_alive_channel))
            .setContentText("正在同步收款记录到 SoloPay")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
        SoloPay.ioScope.launch {
            try {
                flushOutbox(this@SoloPayKeepAliveService)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.solopay_keep_alive_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "solopay_keep_alive"
        private const val NOTIF_ID = 11001

        fun start(context: Context) {
            val i = Intent(context, SoloPayKeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
