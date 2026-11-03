package cn.solopay

import android.content.Context
import android.util.Log
import android.os.SystemClock
import cn.solopay.api.DeviceApiClient
import cn.solopay.db.SignalOutboxEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex

private val outboxMutex = Mutex()

/** Returns true for temporary failures. Binding changes cannot race an in-flight upload. */
suspend fun flushOutbox(context: Context, maxDurationMillis: Long = 120_000): Boolean = outboxMutex.withLock upload@{
    val deadline = SystemClock.elapsedRealtime() + maxDurationMillis
    SoloPay.ensureInit(context)
    val creds = CredentialsStore(context)
    val binding = creds.binding() ?: return@upload false
    val client = DeviceApiClient(binding.apiBaseUrl, binding.secret, callTimeoutSeconds = 15)
    val dao = SoloPay.db.outboxDao()
    CredentialsStore.bindingMutex.withLock {
        if (creds.binding()?.sessionId != binding.sessionId) return@upload false
        dao.retireOtherBindings(binding.sessionId, System.currentTimeMillis())
    }
    val pending = dao.listByStatus(SignalOutboxEntity.STATUS_PENDING, limit = 50)
    for (row in pending) {
        if (SystemClock.elapsedRealtime() >= deadline) return@upload true
        // Release between requests so unbinding waits for at most one in-flight request.
        CredentialsStore.bindingMutex.withLock {
            if (creds.binding()?.sessionId != binding.sessionId) return@upload false
            val now = System.currentTimeMillis()
            if (!binding.owns(row.bindingId)) return@withLock
            val resp = try {
                client.postSignal(row.bodyJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.updateStatus(row.id, SignalOutboxEntity.STATUS_PENDING, now, "network_error")
                Log.w("SoloPaySync", "Signal upload failed; will retry (record=${row.id})", e)
                return@upload true
            }
            when {
                resp.isUnauthorized -> {
                    dao.markAllPendingAsFailed(
                        SignalOutboxEntity.STATUS_PENDING,
                        SignalOutboxEntity.STATUS_FAILED,
                        now,
                        "device_revoked",
                    )
                    creds.clearLocked()
                    return@upload false
                }

                resp.httpStatus == 409 -> {
                    dao.updateStatus(row.id, SignalOutboxEntity.STATUS_SENT, now, null)
                }

                resp.httpStatus in 200..299 && (resp.businessCode == null || resp.businessCode == 0) -> {
                    dao.updateStatus(row.id, SignalOutboxEntity.STATUS_SENT, now, null)
                }

                resp.httpStatus == 408 || resp.httpStatus == 429 || resp.httpStatus >= 500 -> {
                    dao.updateStatus(row.id, SignalOutboxEntity.STATUS_PENDING, now, "http_${resp.httpStatus}")
                    return@upload true
                }

                else -> {
                    dao.updateStatus(row.id, SignalOutboxEntity.STATUS_FAILED, now, resp.message ?: "http_${resp.httpStatus}")
                    Log.w("SoloPaySync", "Signal rejected (record=${row.id}, http=${resp.httpStatus}, code=${resp.businessCode})")
                }
            }
        }
    }
    dao.listByStatus(SignalOutboxEntity.STATUS_PENDING, limit = 1).isNotEmpty()
}
