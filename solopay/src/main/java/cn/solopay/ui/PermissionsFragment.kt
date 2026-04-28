package cn.solopay.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import cn.solopay.databinding.FragmentSolopayPermissionsBinding

class PermissionsFragment : Fragment() {

    private var _binding: FragmentSolopayPermissionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSolopayPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.solopayBtnNotifListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.solopayBtnBattery.setOnClickListener {
            val ctx = requireContext()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
                runCatching { startActivity(i) }
                    .onFailure {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
            } else {
                Toast.makeText(ctx, "当前系统版本无需此设置", Toast.LENGTH_SHORT).show()
            }
        }
        binding.solopayBtnAutostartHint.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "请在系统设置中为应用开启自启动与后台活动权限（各厂商入口不同）。",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
