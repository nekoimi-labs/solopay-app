package cn.solopay

import android.content.Context
import cn.solopay.api.DeviceApiClient
import cn.solopay.db.SignalOutboxEntity

suspend fun flushOutbox(context: Context) {
    SoloPay.ensureInit(context)
    val creds = CredentialsStore(context)
    if (!creds.isBound()) return

    val apiBase = creds.apiBaseUrl() ?: return
    val secret = creds.deviceSecret() ?: return
    val client = DeviceApiClient(apiBase, secret)
    val dao = SoloPay.db.outboxDao()
    val pending = dao.listByStatus(SignalOutboxEntity.STATUS_PENDING, limit = 50)
    val now = System.currentTimeMillis()
    for (row in pending) {
        val resp = try {
            client.postSignal(row.bodyJson)
        } catch (e: Exception) {
            dao.updateStatus(row.id, SignalOutboxEntity.STATUS_FAILED, now, e.message)
            continue
        }
        when {
            resp.isUnauthorized -> {
                creds.clear()
                dao.updateStatus(row.id, SignalOutboxEntity.STATUS_FAILED, now, "unauthorized")
                return
            }

            resp.httpStatus in 200..299 && (resp.businessCode == null || resp.businessCode == 0) -> {
                dao.updateStatus(row.id, SignalOutboxEntity.STATUS_SENT, now, null)
            }

            else -> {
                dao.updateStatus(row.id, SignalOutboxEntity.STATUS_FAILED, now, resp.message ?: resp.rawBody)
            }
        }
    }
}
