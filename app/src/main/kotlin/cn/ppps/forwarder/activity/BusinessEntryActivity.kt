package cn.ppps.forwarder.activity

import android.app.Activity
import android.os.Bundle

/** Distinct targets prevent Android from treating the two launcher aliases as the same entry. */
abstract class BusinessEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openBusiness()
        finish()
    }

    protected abstract fun openBusiness()
}

class SoloPayEntryActivity : BusinessEntryActivity() {
    override fun openBusiness() = BusinessNavigator.openSoloPay(this)
}

class SmsForwarderEntryActivity : BusinessEntryActivity() {
    override fun openBusiness() = BusinessNavigator.openSmsForwarder(this)
}
