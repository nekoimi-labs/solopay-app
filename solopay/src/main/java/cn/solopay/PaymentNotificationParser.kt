package cn.solopay

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.regex.Pattern

data class ParsedPayment(
    val channelType: String,
    val amountCents: Long,
    val remark: String,
)

object PaymentNotificationParser {

    private const val PKG_WECHAT = "com.tencent.mm"
    private const val PKG_ALIPAY = "com.eg.android.AlipayGphone"
    private const val MAX_CENTS = 10_000_000L

    /** 常见收款通知金额匹配 */
    private val amountPatterns = listOf(
        Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元"),
        Pattern.compile("[¥￥]\\s*(\\d+(?:\\.\\d+)?)"),
        Pattern.compile("收款.*?([\\d,]+(?:\\.\\d+)?)"),
        Pattern.compile("收到.*?(?:转账)?.*?([\\d,]+(?:\\.\\d+)?)"),
    )

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        val channel = when (packageName) {
            PKG_WECHAT -> "wxpay"
            PKG_ALIPAY -> "alipay"
            else -> return null
        }
        val blob = "${title.trim()}\n${text.trim()}".trim()
        if (blob.isEmpty()) return null

        val amountYuan = extractAmountYuan(blob) ?: return null
        val cents = yuanToCents(amountYuan) ?: return null
        if (cents <= 0 || cents > MAX_CENTS) return null

        return ParsedPayment(
            channelType = channel,
            amountCents = cents,
            remark = blob.take(512),
        )
    }

    private fun extractAmountYuan(text: String): BigDecimal? {
        var best: BigDecimal? = null
        for (p in amountPatterns) {
            val m = p.matcher(text)
            while (m.find()) {
                val g = m.group(1)?.replace(",", "") ?: continue
                runCatching { BigDecimal(g) }.getOrNull()?.let { cur ->
                    if (best == null || cur > best) best = cur
                }
            }
        }
        return best
    }

    private fun yuanToCents(yuan: BigDecimal): Long? {
        return try {
            val scaled = yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
            scaled.toLong()
        } catch (_: Exception) {
            null
        }
    }
}
