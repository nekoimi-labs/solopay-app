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
import cn.solopay.SoloPay
import cn.solopay.databinding.FragmentSolopayHomeBinding
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

        val creds = CredentialsStore(requireContext())
        binding.solopayBoundStatus.text = if (creds.isBound()) "已绑定" else "未绑定，请到「设备绑定」扫码"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    SoloPay.db.outboxDao().todaySentAmountCentsFlow(startOfTodayMillis()).collect { cents ->
                        val v = cents ?: 0L
                        binding.solopayTodayCents.text = v.toString()
                    }
                }
                launch {
                    SoloPay.db.outboxDao().recentFlow(3).collect { rows ->
                        if (rows.isEmpty()) {
                            binding.solopayRecentLines.text = "暂无"
                        } else {
                            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                            binding.solopayRecentLines.text = rows.joinToString("\n") { r ->
                                val t = fmt.format(Date(r.createdAt))
                                val yuan = String.format(Locale.getDefault(), "%.2f", r.amountCents / 100.0)
                                val st = when (r.status) {
                                    "sent" -> "已上报"
                                    "pending" -> "待上报"
                                    else -> "失败"
                                }
                                "$t  ¥$yuan  ${r.channelType}  $st"
                            }
                        }
                    }
                }
            }
        }
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
