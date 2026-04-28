package cn.solopay.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import cn.solopay.SoloPay
import cn.solopay.databinding.FragmentSolopayRecordsBinding
import kotlinx.coroutines.launch

class RecordsFragment : Fragment() {

    private var _binding: FragmentSolopayRecordsBinding? = null
    private val binding get() = _binding!!

    private val adapter = RecordsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSolopayRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SoloPay.ensureInit(requireContext())
        binding.solopayRecordsList.layoutManager = LinearLayoutManager(requireContext())
        binding.solopayRecordsList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SoloPay.db.outboxDao().recentFlow(200).collect { rows ->
                    adapter.submit(rows)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
