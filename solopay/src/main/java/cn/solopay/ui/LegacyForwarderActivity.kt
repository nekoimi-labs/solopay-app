package cn.solopay.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 跳转到原 SmsForwarder 主界面，便于高级排障；不对其源码做删改。
 */
class LegacyForwarderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val clazz = Class.forName("cn.ppps.forwarder.activity.MainActivity")
            startActivity(Intent(this, clazz))
        } catch (_: Throwable) {
            // ignore
        }
        finish()
    }
}
