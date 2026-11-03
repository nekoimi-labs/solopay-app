package cn.solopay

import android.app.Activity
import android.content.Intent

/** Navigation and shared background coordination are owned by the host application. */
interface SoloPayHost {
    fun openSmsForwarder(activity: Activity)
    fun onSoloPayStateChanged()
    fun onBusinessLaunchIntent(activity: Activity, intent: Intent)
}
