package cn.ppps.forwarder.activity

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.ppps.forwarder.utils.SettingUtils
import cn.solopay.ui.SoloPayMainActivity

object BusinessNavigator {
    fun openSoloPay(activity: Activity) = open(activity, SoloPayMainActivity::class.java)

    fun openSmsForwarder(activity: Activity) = open(activity, MainActivity::class.java)

    fun handleLauncherIntent(activity: Activity, intent: Intent) {
        // When reusing a task Android can deliver the original launcher intent to its top page.
        when (intent.component?.className) {
            "${activity.packageName}.SoloPayLauncher" -> if (activity !is SoloPayMainActivity) openSoloPay(activity)
            "${activity.packageName}.SmsForwarderLauncher" -> if (activity !is MainActivity) openSmsForwarder(activity)
        }
    }

    private fun open(activity: Activity, target: Class<out Activity>) {
        // Reuse each business page without clearing the other business's state.
        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    fun applyRecentsPreference(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.appTasks.forEach { it.setExcludeFromRecents(SettingUtils.enableExcludeFromRecents) }
    }
}
