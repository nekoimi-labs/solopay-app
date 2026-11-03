package cn.solopay

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class CredentialsStore(ctx: Context) {
    private val ctxForDatabase = ctx.applicationContext

    private val sp: SharedPreferences = synchronized(PREFS) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            !app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_API_BASE)
        ) {
            app.moveSharedPreferencesFrom(app.createDeviceProtectedStorageContext(), PREFS)
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun apiBaseUrl(): String? {
        val s = sp.getString(KEY_API_BASE, null) ?: return null
        val t = s.trim()
        return t.ifBlank { null }
    }

    fun deviceId(): String? {
        val s = sp.getString(KEY_DEVICE_ID, null) ?: return null
        val t = s.trim()
        return t.ifBlank { null }
    }

    fun deviceSecret(): String? {
        val s = sp.getString(KEY_DEVICE_SECRET, null) ?: return null
        val t = s.trim()
        return t.ifBlank { null }
    }

    fun isBound(): Boolean = !deviceSecret().isNullOrEmpty() && !apiBaseUrl().isNullOrEmpty()

    fun listeningEnabled(): Boolean = sp.getBoolean(KEY_LISTENING_ENABLED, true)

    fun shouldListen(): Boolean = isBound() && listeningEnabled()

    fun setListeningEnabled(enabled: Boolean) {
        sp.edit().putBoolean(KEY_LISTENING_ENABLED, enabled).apply()
    }

    fun binding(): DeviceBinding? {
        val values = sp.all
        val api = (values[KEY_API_BASE] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val secret = (values[KEY_DEVICE_SECRET] as? String)?.takeIf { it.isNotBlank() } ?: return null
        // Existing bindings get a stable identity until they are explicitly replaced.
        val session = values[KEY_BINDING_ID] as? String ?: DeviceBinding.legacyId(api, secret)
        return DeviceBinding(api, secret, session)
    }

    suspend fun saveBinding(apiBaseUrl: String, deviceId: String, deviceSecret: String) = bindingMutex.withLock {
        val base = apiBaseUrl.trimEnd('/')
        val sessionId = UUID.randomUUID().toString()
        SoloPay.ensureInit(ctxForDatabase)
        SoloPay.db.outboxDao().retireOtherBindings(sessionId, System.currentTimeMillis())
        sp.edit()
            .putString(KEY_API_BASE, base)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_SECRET, deviceSecret)
            .putString(KEY_BINDING_ID, sessionId)
            .putBoolean(KEY_LISTENING_ENABLED, true)
            .apply()
    }

    suspend fun clear() = bindingMutex.withLock {
        SoloPay.ensureInit(ctxForDatabase)
        SoloPay.db.outboxDao().markAllPendingAsFailed("pending", "failed", System.currentTimeMillis(), "device_unbound")
        clearLocked()
    }

    /** Caller must hold bindingMutex so unbinding cannot race an in-flight upload. */
    internal fun clearLocked() {
        sp.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "solopay_credentials"
        private const val KEY_API_BASE = "api_base"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_BINDING_ID = "binding_id"
        private const val KEY_LISTENING_ENABLED = "listening_enabled"
        internal val bindingMutex = Mutex()
    }
}
