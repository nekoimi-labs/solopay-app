package cn.ppps.forwarder.utils

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.service.notification.NotificationListenerService
import cn.ppps.forwarder.service.NotificationService
import cn.solopay.BusinessRuntimePolicy
import cn.solopay.CredentialsStore

object BusinessRuntimeCoordinator {
    fun isUnlocked(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
        (context.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked

    fun needsNotificationListener(context: Context): Boolean {
        if (!isUnlocked(context)) return false
        val credentials = CredentialsStore(context)
        return BusinessRuntimePolicy.needsNotificationListener(
            SettingUtils.enablePureClientMode, SettingUtils.enableAppNotify,
            credentials.isBound(), credentials.listeningEnabled(),
        )
    }

    fun restoreNotificationListener(context: Context) {
        if (!needsNotificationListener(context) || !CommonUtils.isNotificationListenerServiceEnabled(context)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(ComponentName(context, NotificationService::class.java))
            } else {
                CommonUtils.toggleNotificationListenerService(context)
            }
        } catch (e: Exception) {
            android.util.Log.e("BusinessRuntime", "Notification listener recovery failed", e)
        }
    }
}
