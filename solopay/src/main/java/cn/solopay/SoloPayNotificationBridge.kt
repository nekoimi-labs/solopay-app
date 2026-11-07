package cn.solopay

import android.app.Notification
import android.content.Context
import android.util.Log
import android.service.notification.StatusBarNotification
import cn.solopay.db.SignalOutboxEntity
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import cn.solopay.background.DeliveryWakeLock
import kotlinx.coroutines.sync.withLock
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
        val pkg = sbn.packageName
        if (pkg != "com.tencent.mm" && pkg != "com.eg.android.AlipayGphone") return

        val creds = CredentialsStore(context)
        if (!creds.shouldListen()) return
        val binding = creds.binding() ?: return
        SoloPay.ensureInit(context)

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        // A group summary is not a single payment and can duplicate child events.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val shortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()
        // A generic bigText must not overwrite an amount present in EXTRA_TEXT.
        val text = PaymentNotificationText.combine(shortText, bigText, textLines, notification.tickerText?.toString().orEmpty())

        val parsed = PaymentNotificationParser.parse(pkg, title, text)
        if (parsed == null) {
            Log.d("SoloPaySync", "Notification skipped: no supported receipt with a unique explicit amount; package=$pkg parser_version=${PaymentNotificationParser.VERSION}")
            return
        }
        val extId = externalEventId(pkg, sbn.postTime, title, text)
        if (NotificationDedup.shouldSkip("${binding.sessionId}:$extId")) return
        val postTime = sbn.postTime

        // Acquire before leaving the system callback so CPU sleep cannot interrupt queue persistence.
        val lease = DeliveryWakeLock.acquire(context, "payment-notification", 60_000)
        SoloPay.ioScope.launch {
            try {
                val queued = CredentialsStore.bindingMutex.withLock {
                    if (creds.binding()?.sessionId != binding.sessionId) return@withLock false
                    val dao = SoloPay.db.outboxDao()
                    if (dao.byExternalId(extId) != null) return@withLock false

                    val raw = JsonObject()
                    raw.addProperty("title", title.take(200))
                    raw.addProperty("summary", text.take(2000))
                    raw.addProperty("text", shortText.take(2000))
                    raw.addProperty("big_text", bigText.take(2000))
                    raw.add("text_lines", gson.toJsonTree(textLines.take(10).map { it.take(500) }))
                    raw.addProperty("parser_version", PaymentNotificationParser.VERSION)
                    raw.addProperty("post_time", postTime)

                    val body = JsonObject()
                    body.addProperty("schema_version", "1.0")
                    body.addProperty("channel_type", parsed.channelType)
                    body.addProperty("amount_cents", parsed.amountCents.toString())
                    body.addProperty("currency", "CNY")
                    body.addProperty("paid_at", paidAtUtc.get()!!.format(Date(postTime)))
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
                            bindingId = binding.sessionId,
                        ),
                    )
                    inserted != -1L
                }
                if (queued) SoloPaySync.requestImmediate(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A database/scheduling failure must not crash the shared forwarding process.
                Log.e("SoloPaySync", "Payment notification persistence or sync scheduling failed", e)
            } finally {
                lease.close()
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
