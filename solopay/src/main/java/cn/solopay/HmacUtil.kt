package cn.solopay

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacUtil {

    fun sha256Hex(body: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(body)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** 与 SoloPay 服务端 `hmacsign.CanonicalString` 一致 */
    fun canonicalString(
        method: String,
        path: String,
        timestampUnix: Long,
        nonce: String,
        bodySha256Hex: String,
    ): String {
        val m = method.trim().uppercase()
        val p = path.trim()
        val n = nonce.trim()
        val b = bodySha256Hex.trim().lowercase()
        return listOf(m, p, timestampUnix.toString(), n, b).joinToString("\n")
    }

    fun hmacSha256Hex(secretUtf8: String, canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretUtf8.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val out = mac.doFinal(canonical.toByteArray(StandardCharsets.UTF_8))
        return out.joinToString("") { "%02x".format(it) }
    }
}
