package cn.solopay.background

import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/** CPU-only, time-limited execution window. It does not bypass Doze network restrictions. */
object DeliveryWakeLock {
    fun acquire(context: Context, tag: String, timeoutMillis: Long = 60_000): Closeable {
        val lock = try {
            val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoloPay:$tag").apply {
                setReferenceCounted(false)
                acquire(timeoutMillis)
            }
        } catch (e: Exception) {
            Log.w("DeliveryWakeLock", "CPU wake window unavailable ($tag)", e)
            null
        }
        val closed = AtomicBoolean(false)
        return Closeable {
            if (closed.compareAndSet(false, true) && lock != null) {
                // The timeout can release the lock before the completion callback runs.
                runCatching { if (lock.isHeld) lock.release() }
                    .onFailure { Log.w("DeliveryWakeLock", "CPU wake window release failed ($tag)", it) }
            }
        }
    }
}
