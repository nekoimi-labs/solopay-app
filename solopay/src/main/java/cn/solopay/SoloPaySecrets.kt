package cn.solopay

import java.security.SecureRandom

object SoloPaySecrets {
    fun randomDeviceSecret(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
