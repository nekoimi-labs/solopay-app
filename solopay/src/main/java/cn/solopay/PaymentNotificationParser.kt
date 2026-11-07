package cn.solopay

import java.math.BigDecimal
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
    const val VERSION = 2

    // Never infer money from an unqualified number: "收到1笔转账" contains a
    // transaction count, not a one-yuan payment. Require an explicit currency.
    private const val MONEY = "((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?)"
    // Do not cross notification fields/lines: the next line may start with a
    // payer nickname such as "100元", not the amount of this transfer.
    private const val RECEIPT = "(?:收款(?:成功)?|到账|入账|收到(?:[一1]笔)?转账|向[你您]付款|向[你您]转(?:账|了)?|转账给[你您])(?:金额)?[ \\t:：]*(?:人民币[ \\t]*)?"
    private val amountPatterns = listOf(
        Pattern.compile("${RECEIPT}[¥￥]?[ \\t]*${MONEY}[ \\t]*元"),
        Pattern.compile("${RECEIPT}[¥￥][ \\t]*${MONEY}(?![0-9.,])"),
    )
    private val nonReceiptPattern = Pattern.compile("退款|退回|转出|支出|待支付|待付款|待收款|收款请求")

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        val channel = when (packageName) {
            PKG_WECHAT -> "wxpay"
            PKG_ALIPAY -> "alipay"
            else -> return null
        }
        val blob = "${title.trim()}\n${text.trim()}".trim()
        if (blob.isEmpty() || nonReceiptPattern.matcher(blob).find()) return null

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
        val amounts = mutableSetOf<BigDecimal>()
        for (p in amountPatterns) {
            val m = p.matcher(text)
            while (m.find()) {
                val g = m.group(1)?.replace(",", "") ?: continue
                val amount = runCatching { BigDecimal(g).stripTrailingZeros() }.getOrNull() ?: return null
                amounts.add(amount)
            }
        }
        // Multiple copies of the same amount are normal (title/text/bigText).
        // Only receipt amounts qualify; balances, nicknames and remark codes do
        // not. Multiple distinct receipt amounts remain ambiguous: do not guess.
        return amounts.singleOrNull()
    }

    private fun yuanToCents(yuan: BigDecimal): Long? {
        return try {
            yuan.movePointRight(2).longValueExact()
        } catch (_: Exception) {
            null
        }
    }
}
