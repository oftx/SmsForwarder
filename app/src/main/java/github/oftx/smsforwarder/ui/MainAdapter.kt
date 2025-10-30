package github.oftx.smsforwarder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.R

// Models for the list
sealed class ListItem {
    // SmsItem 现在包含 statusSummary
    data class Sms(val item: SmsItem) : ListItem()
}

// SmsItem 现在包含 statusSummary
data class SmsItem(
    val id: Long,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val statusSummary: String // 新增字段
)

class MainAdapter(
    private val onItemClicked: (Long) -> Unit // 回调只传递 smsId
) : ListAdapter<ListItem, MainAdapter.SmsViewHolder>(MainDiffCallback()) {

    companion object {
        private const val TYPE_SMS = 0
    }

    inner class SmsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val senderTextView: TextView = view.findViewById(R.id.tv_sender)
        private val contentTextView: TextView = view.findViewById(R.id.tv_content)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_timestamp)
        private val statusSummaryTextView: TextView = view.findViewById(R.id.tv_status_summary) // 新增 View

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position) as ListItem.Sms
                    onItemClicked(item.item.id) // 传递 ID
                }
            }
        }

        fun bind(smsItem: SmsItem) {
            senderTextView.text =
                itemView.context.getString(R.string.sms_sender_format, smsItem.sender)
            contentTextView.text = smsItem.content
            timestampTextView.text = TimeUtil.formatDefault(itemView.context, smsItem.timestamp)

            // 绑定状态摘要
            if (smsItem.statusSummary.isNotEmpty()) {
                statusSummaryTextView.visibility = View.VISIBLE
                statusSummaryTextView.text = smsItem.statusSummary
            } else {
                statusSummaryTextView.visibility = View.GONE
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return TYPE_SMS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        return SmsViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_sms_log, parent, false)
        )
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        holder.bind((getItem(position) as ListItem.Sms).item)
    }
}

class MainDiffCallback : DiffUtil.ItemCallback<ListItem>() {
    override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return (oldItem as ListItem.Sms).item.id == (newItem as ListItem.Sms).item.id
    }

    override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return oldItem == newItem
    }
}
