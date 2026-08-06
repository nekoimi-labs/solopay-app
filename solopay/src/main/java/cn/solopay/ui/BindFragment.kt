package cn.solopay.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import cn.solopay.CredentialsStore
import cn.solopay.R
import cn.solopay.databinding.FragmentSolopayBindBinding

class BindFragment : Fragment() {

    private var _binding: FragmentSolopayBindBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSolopayBindBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val c = CredentialsStore(requireContext())
        binding.solopayApiBase.text = c.apiBaseUrl() ?: "—"
        binding.solopayDeviceId.text = c.deviceId() ?: "—"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.solopayTitleBar.setTitle(getString(R.string.solopay_bind))
        binding.solopayTitleBar.disableLeftView()
        binding.solopayBtnScan.setOnClickListener {
            startActivity(Intent(requireContext(), BindScanActivity::class.java))
        }
        binding.solopayBtnAdvanced.setOnClickListener {
            startActivity(Intent(requireContext(), LegacyForwarderActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
