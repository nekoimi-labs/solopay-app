package cn.solopay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentNotificationParserTest {
    private val alipay = "com.eg.android.AlipayGphone"
    private val wechat = "com.tencent.mm"

    @Test
    fun transactionCountsAreNeverPaymentAmounts() {
        for (count in listOf("1", "2", "99", "1234")) {
            assertNull(PaymentNotificationParser.parse(alipay, "你收到${count}笔转账", "nekoimi 已成功向你转了${count}笔钱，立即查看>>"))
        }
        assertNull(PaymentNotificationParser.parse(alipay, "你收到1笔转账", "收款通知：1笔，点击查看详情"))
    }

    @Test
    fun alipayScanReceiptParsesTheRealDeviceSample() {
        val text = "nekoimi通过扫码向你付款0.01元"
        val parsed = PaymentNotificationParser.parse(alipay, "收款通知", text)
        assertNotNull(parsed)
        assertEquals("alipay", parsed!!.channelType)
        assertEquals(1L, parsed.amountCents)
        assertEquals("收款通知\n$text", parsed.remark)

        // Numbers in the payer name cannot replace the actual receipt amount.
        assertEquals(1L, PaymentNotificationParser.parse(alipay, "收款通知", "100元通过扫码向你付款0.01元")?.amountCents)
        assertEquals(12345L, PaymentNotificationParser.parse(alipay, "收款通知", "某某通过扫码向您付款123.45元")?.amountCents)
        assertNull(PaymentNotificationParser.parse(alipay, "收款通知", "你通过扫码向某某付款0.01元"))
        assertNull(PaymentNotificationParser.parse(alipay, "收款通知", "某某通过扫码向你付款1笔"))
    }

    @Test
    fun receiptRequiresExplicitCurrencyAndReceiptContext() {
        val samples = listOf(
            Triple("微信支付", "微信支付收款0.01元", 1L),
            Triple("支付宝", "成功收款1.23元", 123L),
            Triple("支付宝", "收款成功 ￥20.10", 2010L),
            Triple("支付宝", "收款金额：人民币 1,234.56元", 123456L),
            Triple("到账通知", "到账：¥18", 1800L),
            Triple("支付宝", "张三向你转账10.00元", 1000L),
            Triple("支付宝", "张三向您转了10元", 1000L),
            Triple("支付宝", "收到一笔转账 0.10元", 10L),
        )
        for ((title, text, cents) in samples) {
            val parsed = PaymentNotificationParser.parse(if (title == "微信支付") wechat else alipay, title, text)
            assertNotNull("Receipt should parse: $title / $text", parsed)
            assertEquals("Wrong amount: $text", cents, parsed!!.amountCents)
            assertEquals(if (title == "微信支付") "wxpay" else "alipay", parsed.channelType)
        }
        for (text in listOf("收款20", "余额100元", "优惠券￥88", "验证码1234", "您向张三转账10元", "收款请求：10元", "退款到账10元")) {
            assertNull("Not a supported receipt: $text", PaymentNotificationParser.parse(alipay, "支付宝", text))
        }
        assertNull(PaymentNotificationParser.parse("other.app", "收款通知", "收款10元"))
    }

    @Test
    fun nicknamesBalancesAndConfirmationCodesCannotBecomeTheReceiptAmount() {
        val parsed = PaymentNotificationParser.parse(alipay, "收款通知", "收款1.23元\n余额999元\n付款备注：9527")
        assertEquals(123L, parsed!!.amountCents)
        assertEquals("收款通知\n收款1.23元\n余额999元\n付款备注：9527", parsed.remark)
        assertNull(PaymentNotificationParser.parse(alipay, "你收到1笔转账", "100元 已成功向你转了1笔钱，立即查看>>"))
    }

    @Test
    fun repeatedAmountIsAcceptedButDistinctReceiptAmountsAreAmbiguous() {
        assertEquals(123L, PaymentNotificationParser.parse(alipay, "收款1.23元", "收款1.23元")?.amountCents)
        assertEquals(123L, PaymentNotificationParser.parse(alipay, "收款1.23元", "收款￥1.23")?.amountCents)
        assertNull(PaymentNotificationParser.parse(alipay, "收款通知", "收款1.23元\n收款2.34元"))
    }

    @Test
    fun malformedOrOutOfRangeMoneyIsNeverRoundedOrWrapped() {
        for (amount in listOf("0元", "-1元", "+1元", "￥-1", "-￥1元", "￥1.005", "1.005元", "1,00元", "￥1,00", "100000.01元", "184467440737095516.17元")) {
            assertNull("Unsafe amount accepted: $amount", PaymentNotificationParser.parse(alipay, "支付宝", "收款$amount"))
        }
        assertEquals(10_000_000L, PaymentNotificationParser.parse(alipay, "支付宝", "收款100000元")?.amountCents)
    }

    @Test
    fun bigTextCannotEraseAmountInShortTextAndTextLinesAreCollected() {
        val text = PaymentNotificationText.combine("收款1.23元", "你收到1笔转账", listOf("付款备注：9527", "收款1.23元"), "旧收款999元")
        assertEquals("收款1.23元\n你收到1笔转账\n付款备注：9527", text)
        assertEquals(123L, PaymentNotificationParser.parse(alipay, "支付宝", text)?.amountCents)
        val linesOnly = PaymentNotificationText.combine("", "", listOf("收款0.01元"), "")
        assertEquals(1L, PaymentNotificationParser.parse(alipay, "支付宝", linesOnly)?.amountCents)
        assertEquals("收款2元", PaymentNotificationText.combine("", "", emptyList(), "收款2元"))
    }
}
