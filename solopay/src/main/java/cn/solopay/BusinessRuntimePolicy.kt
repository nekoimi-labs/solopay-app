package cn.solopay

object BusinessRuntimePolicy {
    fun needsNotificationListener(
        pureClientMode: Boolean,
        forwardNotifications: Boolean,
        soloPayBound: Boolean,
        soloPayEnabled: Boolean,
    ): Boolean = (!pureClientMode && forwardNotifications) || (soloPayBound && soloPayEnabled)
}

/** A failing business consumer must not prevent delivery to the other consumer. */
fun dispatchBusinessNotification(
    soloPay: () -> Unit,
    forwarder: () -> Unit,
    onFailure: (String, Exception) -> Unit,
) {
    for ((business, consumer) in listOf("SoloPay" to soloPay, "SmsForwarder" to forwarder)) {
        try {
            consumer()
        } catch (e: Exception) {
            onFailure(business, e)
        }
    }
}
