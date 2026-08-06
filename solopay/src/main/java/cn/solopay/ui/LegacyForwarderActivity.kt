package cn.solopay.ui

import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 跳转到原 SmsForwarder 主界面，便于高级排障；不对其源码做删改。
 * 通过 TaskStackBuilder 将 SoloPayMainActivity 压在返回栈底，系统返回可回到 SoloPay。
 */
class LegacyForwarderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            @Suppress("UNCHECKED_CAST")
            val clazz = Class.forName("cn.ppps.forwarder.activity.MainActivity") as Class<*>
            val soloPayIntent = Intent(this, SoloPayMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val forwarderIntent = Intent(this, clazz)
            TaskStackBuilder.create(this)
                .addNextIntent(soloPayIntent)
                .addNextIntent(forwarderIntent)
                .startActivities()
        } catch (_: Throwable) {
            // ignore
        }
        finish()
    }
}
