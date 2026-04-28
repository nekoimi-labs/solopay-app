package cn.solopay.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cn.solopay.R
import cn.solopay.databinding.ItemSolopayRecordBinding
import cn.solopay.db.SignalOutboxEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordsAdapter : ListAdapter<SignalOutboxEntity, RecordsAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSolopayRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), fmt)
    }

    class VH(private val binding: ItemSolopayRecordBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(r: SignalOutboxEntity, fmt: SimpleDateFormat) {
            val t = fmt.format(Date(r.createdAt))
            val yuan = String.format(Locale.getDefault(), "%.2f", r.amountCents / 100.0)
            val st = when (r.status) {
                SignalOutboxEntity.STATUS_SENT -> binding.root.context.getString(R.string.solopay_status_sent)
                SignalOutboxEntity.STATUS_PENDING -> binding.root.context.getString(R.string.solopay_status_pending)
                else -> binding.root.context.getString(R.string.solopay_status_failed)
            }
            val ch = when (r.channelType) {
                "wxpay" -> binding.root.context.getString(R.string.solopay_channel_wx)
                "alipay" -> binding.root.context.getString(R.string.solopay_channel_alipay)
                else -> r.channelType
            }
            binding.solopayRecordLine1.text = "$t  ¥$yuan  $ch  $st"
            val err = r.lastError
            if (err.isNullOrBlank()) {
                binding.solopayRecordError.visibility = View.GONE
            } else {
                binding.solopayRecordError.visibility = View.VISIBLE
                binding.solopayRecordError.text = err
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SignalOutboxEntity>() {
            override fun areItemsTheSame(a: SignalOutboxEntity, b: SignalOutboxEntity) = a.id == b.id

            override fun areContentsTheSame(a: SignalOutboxEntity, b: SignalOutboxEntity) =
                a == b
        }
    }
}
