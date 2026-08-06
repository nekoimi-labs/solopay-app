package cn.solopay.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cn.solopay.CredentialsStore
import cn.solopay.SoloPaySecrets
import cn.solopay.R
import cn.solopay.api.DeviceApiClient
import com.google.zxing.integration.android.IntentIntegrator
import com.xuexiang.xui.XUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class BindScanActivity : AppCompatActivity() {

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        if (scanResult.contents.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.solopay_scan_cancel), Toast.LENGTH_SHORT).show()
            finish()
            return@registerForActivityResult
        }
        val raw = scanResult.contents!!
        lifecycleScope.launch {
            val parsed = try {
                val json = JSONObject(raw)
                val api = json.getString("api").trimEnd('/')
                val token = json.getString("bind_token")
                Triple(api, token, UUID.randomUUID().toString())
            } catch (e: Exception) {
                Toast.makeText(
                    this@BindScanActivity,
                    getString(R.string.solopay_parse_fail, e.message ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
                return@launch
            }
            val (api, token, deviceId) = parsed
            val secret = SoloPaySecrets.randomDeviceSecret()
            val res = withContext(Dispatchers.IO) {
                DeviceApiClient(api, null).bind(deviceId, secret, token)
            }
            if (res.httpStatus in 200..299 && (res.businessCode == null || res.businessCode == 0)) {
                CredentialsStore(this@BindScanActivity).saveBinding(api, deviceId, secret)
                Toast.makeText(this@BindScanActivity, R.string.solopay_bind_ok, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@BindScanActivity,
                    getString(R.string.solopay_bind_fail, res.message ?: res.rawBody),
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        XUI.initTheme(this)
        super.onCreate(savedInstanceState)
        val intent = IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt(getString(R.string.solopay_scan_prompt))
            setOrientationLocked(true)
        }.createScanIntent()
        scanLauncher.launch(intent)
    }
}
