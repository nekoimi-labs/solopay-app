package cn.solopay

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import cn.solopay.db.SignalOutboxEntity
import cn.solopay.service.SoloPayKeepAliveService
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 从 SmsForwarder [NotificationService] 单点接入，不改动其业务分支。
 */
object SoloPayNotificationBridge {

    private val gson = Gson()
    private val paidAtUtc: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification) {
        SoloPay.ensureInit(context)
        val pkg = sbn.packageName
        if (pkg != "com.tencent.mm" && pkg != "com.eg.android.AlipayGphone") return

        val creds = CredentialsStore(context)
        if (!creds.isBound()) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            if (bigText.isNotEmpty()) text = bigText
        }
        if (text.isEmpty() && notification.tickerText != null) {
            text = notification.tickerText.toString()
        }

        val parsed = PaymentNotificationParser.parse(pkg, title, text) ?: return
        val extId = externalEventId(pkg, sbn.postTime, title, text)
        if (NotificationDedup.shouldSkip(extId)) return

        SoloPay.ioScope.launch {
            val dao = SoloPay.db.outboxDao()
            if (dao.byExternalId(extId) != null) return@launch

            val raw = JsonObject()
            raw.addProperty("title", title.take(200))
            raw.addProperty("summary", text.take(2000))
            raw.addProperty("parser_version", 1)
            raw.addProperty("post_time", sbn.postTime)

            val body = JsonObject()
            body.addProperty("schema_version", "1.0")
            body.addProperty("channel_type", parsed.channelType)
            body.addProperty("amount_cents", parsed.amountCents.toString())
            body.addProperty("currency", "CNY")
            body.addProperty("paid_at", paidAtUtc.get()!!.format(Date(sbn.postTime)))
            body.addProperty("remark", parsed.remark)
            body.addProperty("external_event_id", extId)
            body.add("raw_payload", raw)

            val bodyJson = gson.toJson(body)
            val now = System.currentTimeMillis()
            val inserted = dao.insert(
                SignalOutboxEntity(
                    externalEventId = extId,
                    bodyJson = bodyJson,
                    channelType = parsed.channelType,
                    amountCents = parsed.amountCents,
                    status = SignalOutboxEntity.STATUS_PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (inserted != -1L) {
                SoloPayKeepAliveService.start(context)
            }
        }
    }

    private fun externalEventId(pkg: String, postTime: Long, title: String, text: String): String {
        val s = "$pkg|$postTime|$title|$text"
        val md = MessageDigest.getInstance("SHA-256")
        val hex = md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return hex.take(48)
    }
}
