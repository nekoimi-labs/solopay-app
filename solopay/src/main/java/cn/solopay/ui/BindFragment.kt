package cn.solopay.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cn.solopay.CredentialsStore
import cn.solopay.R
import cn.solopay.SoloPayHost
import cn.solopay.databinding.FragmentSolopayBindBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
        refreshBinding()
    }

    private fun refreshBinding() {
        val c = CredentialsStore(requireContext())
        binding.solopayApiBase.text = c.apiBaseUrl() ?: "—"
        binding.solopayDeviceId.text = c.deviceId() ?: "—"
        binding.solopayListeningEnabled.setOnCheckedChangeListener(null)
        binding.solopayListeningEnabled.isChecked = c.listeningEnabled() && c.isBound()
        binding.solopayListeningEnabled.isEnabled = c.isBound()
        binding.solopayBtnUnbind.isEnabled = c.isBound()
        binding.solopayListeningEnabled.setOnCheckedChangeListener { _, enabled ->
            c.setListeningEnabled(enabled)
            (requireActivity().application as? SoloPayHost)?.onSoloPayStateChanged()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.solopayBtnScan.setOnClickListener {
            startActivity(Intent(requireContext(), BindScanActivity::class.java))
        }
        binding.solopayBtnUnbind.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.solopay_unbind_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.solopay_unbind) { _, _ ->
                    binding.solopayBtnUnbind.isEnabled = false
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            CredentialsStore(requireContext()).clear()
                            (requireActivity().application as? SoloPayHost)?.onSoloPayStateChanged()
                            if (_binding != null) {
                                refreshBinding()
                                Toast.makeText(requireContext(), R.string.solopay_unbind_done, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("SoloPayBinding", "Unbind failed", e)
                            if (_binding != null) {
                                refreshBinding()
                                Toast.makeText(requireContext(), R.string.solopay_unbind_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
