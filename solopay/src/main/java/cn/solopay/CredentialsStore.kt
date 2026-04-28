package cn.solopay

import android.content.Context
import android.content.SharedPreferences

class CredentialsStore(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    fun saveBinding(apiBaseUrl: String, deviceId: String, deviceSecret: String) {
        val base = apiBaseUrl.trimEnd('/')
        sp.edit()
            .putString(KEY_API_BASE, base)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_SECRET, deviceSecret)
            .apply()
    }

    fun clear() {
        sp.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "solopay_credentials"
        private const val KEY_API_BASE = "api_base"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
    }
}
