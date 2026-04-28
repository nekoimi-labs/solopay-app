package cn.solopay.api

import cn.solopay.HmacUtil
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class DeviceApiClient(
    private val baseUrl: String,
    private val deviceSecret: String?,
) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val root = baseUrl.trimEnd('/')

    fun bind(deviceId: String, deviceSecretPlain: String, bindToken: String): ApiResult {
        val bodyJson = gson.toJson(
            mapOf(
                "bind_token" to bindToken,
                "device_id" to deviceId,
                "device_secret" to deviceSecretPlain,
            ),
        )
        val body = RequestBody.create(JSON_MEDIA, bodyJson.toByteArray(Charsets.UTF_8))
        val req = Request.Builder()
            .url("$root/v1/device/bind")
            .post(body)
            .build()
        return executeUnsigned(req)
    }

    fun ping(): ApiResult {
        val path = "/v1/device/ping"
        val req = signedGet(path)
        return executeSigned(req)
    }

    fun postSignal(bodyJson: String): ApiResult {
        require(!deviceSecret.isNullOrBlank()) { "device secret missing" }
        val path = "/v1/device/signals"
        val bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)
        val ts = System.currentTimeMillis() / 1000
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val bodyHex = HmacUtil.sha256Hex(bodyBytes)
        val canon = HmacUtil.canonicalString("POST", path, ts, nonce, bodyHex)
        val sig = HmacUtil.hmacSha256Hex(deviceSecret!!, canon)
        val body = RequestBody.create(JSON_MEDIA, bodyBytes)
        val req = Request.Builder()
            .url("$root$path")
            .addHeader("X-Timestamp", ts.toString())
            .addHeader("X-Nonce", nonce)
            .addHeader("X-Signature", sig)
            .post(body)
            .build()
        return executeSigned(req)
    }

    private fun signedGet(path: String): Request {
        require(!deviceSecret.isNullOrBlank()) { "device secret missing" }
        val ts = System.currentTimeMillis() / 1000
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val bodyHex = HmacUtil.sha256Hex(ByteArray(0))
        val canon = HmacUtil.canonicalString("GET", path, ts, nonce, bodyHex)
        val sig = HmacUtil.hmacSha256Hex(deviceSecret!!, canon)
        return Request.Builder()
            .url("$root$path")
            .addHeader("X-Timestamp", ts.toString())
            .addHeader("X-Nonce", nonce)
            .addHeader("X-Signature", sig)
            .get()
            .build()
    }

    private fun executeUnsigned(request: Request): ApiResult {
        client.newCall(request).execute().use { resp ->
            val raw = resp.body()?.string().orEmpty()
            return parseResult(resp.code(), raw)
        }
    }

    private fun executeSigned(request: Request): ApiResult {
        client.newCall(request).execute().use { resp ->
            val raw = resp.body()?.string().orEmpty()
            return parseResult(resp.code(), raw)
        }
    }

    private fun parseResult(httpStatus: Int, raw: String): ApiResult {
        return try {
            val el = JsonParser.parseString(raw)
            if (el.isJsonObject) {
                val o = el.asJsonObject
                val code = if (o.has("code") && !o.get("code").isJsonNull) o.get("code").asInt else null
                val msg = if (o.has("message") && o.get("message").isJsonPrimitive) o.get("message").asString else null
                ApiResult(httpStatus, code, msg, raw)
            } else {
                ApiResult(httpStatus, null, null, raw)
            }
        } catch (_: Exception) {
            ApiResult(httpStatus, null, null, raw)
        }
    }

    companion object {
        private val JSON_MEDIA = MediaType.parse("application/json; charset=utf-8")!!
    }
}
