package cn.solopay

import java.security.MessageDigest

/** The local session changes even when the same device is bound again. Never log its secret. */
data class DeviceBinding(val apiBaseUrl: String, val secret: String, val sessionId: String) {
    fun owns(session: String): Boolean = session.isNotBlank() && session == sessionId

    override fun toString(): String = "DeviceBinding(sessionId=$sessionId)"

    companion object {
        fun legacyId(api: String, secret: String): String = MessageDigest.getInstance("SHA-256")
            .digest("$api\n$secret".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
