package cn.ppps.forwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.ppps.forwarder.App
import cn.ppps.forwarder.utils.BusinessRuntimeCoordinator
import cn.ppps.forwarder.utils.Log

@Suppress("PrivatePropertyName")
class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG: String = BootCompletedReceiver::class.java.simpleName

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED)) return
        if (!BusinessRuntimeCoordinator.isUnlocked(context)) return

        try {
            Log.d(TAG, "恢复已启用的业务后台，不打开界面")
            (context.applicationContext as App).initializeRuntime()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "恢复业务后台失败:${e.message}")
        }

    }
}
