package github.oftx.smsforwarder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.R

sealed class ListItem {
    data class Sms(val item: SmsItem) : ListItem()
}

// statusSummary 现在是 CharSequence
data class SmsItem(
    val id: Long,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val statusSummary: CharSequence // 类型已更改
)

class MainAdapter(
    private val onItemClicked: (Long) -> Unit
) : ListAdapter<ListItem, MainAdapter.SmsViewHolder>(MainDiffCallback()) {

    inner class SmsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val senderTextView: TextView = view.findViewById(R.id.tv_sender)
        private val contentTextView: TextView = view.findViewById(R.id.tv_content)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_timestamp)
        private val statusSummaryTextView: TextView = view.findViewById(R.id.tv_status_summary)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position) as ListItem.Sms
                    onItemClicked(item.item.id)
                }
            }
        }

        fun bind(smsItem: SmsItem) {
            senderTextView.text =
                itemView.context.getString(R.string.sms_sender_format, smsItem.sender)
            contentTextView.text = smsItem.content
            timestampTextView.text = TimeUtil.formatDefault(itemView.context, smsItem.timestamp)

            if (smsItem.statusSummary.isNotEmpty()) {
                statusSummaryTextView.visibility = View.VISIBLE
                statusSummaryTextView.text = smsItem.statusSummary
            } else {
                statusSummaryTextView.visibility = View.GONE
            }
        }
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
        // CharSequence.toString() is used for content comparison
        return oldItem.toString() == newItem.toString()
    }
}
