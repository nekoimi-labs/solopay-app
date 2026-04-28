package cn.solopay.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cn.solopay.db.SignalOutboxEntity
import cn.solopay.databinding.ItemSolopayRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordsAdapter : RecyclerView.Adapter<RecordsAdapter.VH>() {

    private var items: List<SignalOutboxEntity> = emptyList()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun submit(rows: List<SignalOutboxEntity>) {
        items = rows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSolopayRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b.solopayRecordLine)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        val t = fmt.format(Date(r.createdAt))
        val yuan = String.format(Locale.getDefault(), "%.2f", r.amountCents / 100.0)
        val st = when (r.status) {
            "sent" -> "已上报"
            "pending" -> "待上报"
            else -> "失败"
        }
        val ch = when (r.channelType) {
            "wxpay" -> "微信"
            "alipay" -> "支付宝"
            else -> r.channelType
        }
        holder.tv.text = "$t  ¥$yuan  $ch  $st"
    }

    override fun getItemCount(): Int = items.size

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
}
