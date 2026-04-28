package cn.solopay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cn.solopay.CredentialsStore
import cn.solopay.R
import cn.solopay.SoloPay
import cn.solopay.databinding.FragmentSolopayHomeBinding
import cn.solopay.db.SignalOutboxEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentSolopayHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSolopayHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SoloPay.ensureInit(requireContext())
        binding.solopayTitleBar.setTitle(getString(R.string.solopay_home))

        val creds = CredentialsStore(requireContext())
        binding.solopayBoundStatus.text =
            if (creds.isBound()) getString(R.string.solopay_bound_yes) else getString(R.string.solopay_bound_no)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    SoloPay.db.outboxDao().todaySentAmountCentsFlow(
                        startOfTodayMillis(),
                        SignalOutboxEntity.STATUS_SENT,
                    ).collect { cents ->
                        val v = cents ?: 0L
                        val yuan = String.format(Locale.getDefault(), "%.2f", v / 100.0)
                        binding.solopayTodayCents.text = yuan
                    }
                }
                launch {
                    SoloPay.db.outboxDao().recentFlow(3).collect { rows ->
                        if (rows.isEmpty()) {
                            binding.solopayRecentLines.text = getString(R.string.solopay_recent_empty)
                        } else {
                            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            binding.solopayRecentLines.text = rows.joinToString("\n") { r ->
                                val t = fmt.format(Date(r.createdAt))
                                val yuan = String.format(Locale.getDefault(), "%.2f", r.amountCents / 100.0)
                                val st = when (r.status) {
                                    SignalOutboxEntity.STATUS_SENT -> getString(R.string.solopay_status_sent)
                                    SignalOutboxEntity.STATUS_PENDING -> getString(R.string.solopay_status_pending)
                                    else -> getString(R.string.solopay_status_failed)
                                }
                                val ch = when (r.channelType) {
                                    "wxpay" -> getString(R.string.solopay_channel_wx)
                                    "alipay" -> getString(R.string.solopay_channel_alipay)
                                    else -> r.channelType
                                }
                                "$t  ¥$yuan  $ch  $st"
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val creds = CredentialsStore(requireContext())
        binding.solopayBoundStatus.text =
            if (creds.isBound()) getString(R.string.solopay_bound_yes) else getString(R.string.solopay_bound_no)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
