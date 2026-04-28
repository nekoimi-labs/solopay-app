package cn.solopay.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import cn.solopay.R
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
        binding.solopayTitleBar.setTitle(getString(R.string.solopay_permissions))

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.solopayBtnPostNotifications.visibility = View.VISIBLE
            binding.solopayBtnPostNotifications.setOnClickListener {
                val ctx = requireContext()
                when {
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED -> {
                        Toast.makeText(ctx, "已授予通知权限", Toast.LENGTH_SHORT).show()
                    }

                    shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        })
                    }

                    else -> {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIF)
                    }
                }
            }
        } else {
            binding.solopayBtnPostNotifications.visibility = View.GONE
        }

        binding.solopayBtnAutostartHint.setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.solopay_perm_autostart_toast,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode == REQ_POST_NOTIF && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "已授予通知权限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQ_POST_NOTIF = 4101
    }
}
