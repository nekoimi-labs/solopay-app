package cn.solopay

import org.junit.Assert.*
import org.junit.Test

class BusinessRuntimeTest {
    @Test
    fun listenerRemainsNeededForEitherBusinessIndependently() {
        // SoloPay alone, including SmsForwarder's pure-client mode.
        assertTrue(BusinessRuntimePolicy.needsNotificationListener(false, false, true, true))
        assertTrue(BusinessRuntimePolicy.needsNotificationListener(true, false, true, true))
        // Pausing/unbinding SoloPay cannot disable an enabled original notification forwarder.
        assertTrue(BusinessRuntimePolicy.needsNotificationListener(false, true, true, false))
        assertTrue(BusinessRuntimePolicy.needsNotificationListener(false, true, false, false))
        // Neither an unbound device nor the original switch in pure-client mode needs the listener.
        assertFalse(BusinessRuntimePolicy.needsNotificationListener(false, false, false, true))
        assertFalse(BusinessRuntimePolicy.needsNotificationListener(true, true, true, false))
    }

    @Test
    fun soloPayFailureDoesNotSwallowForwarderNotification() {
        val delivered = mutableListOf<String>()
        val failures = mutableListOf<String>()
        dispatchBusinessNotification(
            soloPay = { throw IllegalArgumentException("bad payment notification") },
            forwarder = { delivered.add("SmsForwarder") },
            onFailure = { business, _ -> failures.add(business) },
        )
        assertEquals(listOf("SmsForwarder"), delivered)
        assertEquals(listOf("SoloPay"), failures)
    }

    @Test
    fun sameNotificationIsDeliveredToBothConsumers() {
        val delivered = mutableListOf<String>()
        dispatchBusinessNotification(
            soloPay = { delivered.add("SoloPay") },
            forwarder = { delivered.add("SmsForwarder") },
            onFailure = { _, error -> throw AssertionError(error) },
        )
        assertEquals(listOf("SoloPay", "SmsForwarder"), delivered)
    }

    @Test
    fun failuresHaveSeparateBusinessNames() {
        val failures = mutableListOf<String>()
        dispatchBusinessNotification(
            soloPay = { throw IllegalStateException("SoloPay unavailable") },
            forwarder = { throw IllegalStateException("forwarder unavailable") },
            onFailure = { business, _ -> failures.add(business) },
        )
        assertEquals(listOf("SoloPay", "SmsForwarder"), failures)
    }

    @Test
    fun queueRowsCannotBeUploadedUnderAnotherBindingEvenForSameServerAndSecret() {
        val old = DeviceBinding("https://pay.example.com", "secret", "old-session")
        val rebound = DeviceBinding(old.apiBaseUrl, old.secret, "new-session")
        assertTrue(old.owns("old-session"))
        assertFalse(rebound.owns("old-session"))
        assertFalse(rebound.owns(""))
        assertTrue(rebound.owns("new-session"))
        assertFalse(rebound.toString().contains("secret"))
    }
}
