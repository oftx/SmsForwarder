package github.oftx.smsforwarder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.database.ForwardingJobEntity

// Models for the list
sealed class ListItem {
    data class Sms(val item: SmsItem) : ListItem()
    data class RetryJob(val job: ForwardingJobEntity, val ruleName: String) : ListItem()
}

// Add ID for stable tracking of expanded state
data class SmsItem(val id: Long, val sender: String, val content: String, val timestamp: Long)

class MainAdapter(
    private val onItemClicked: (Long, Int) -> Unit
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(MainDiffCallback()) {

    companion object {
        private const val TYPE_SMS = 0
        private const val TYPE_RETRY_JOB = 1
    }

    // State for expanded items
    val expandedItems = mutableSetOf<Long>()

    inner class SmsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val senderTextView: TextView = view.findViewById(R.id.tv_sender)
        private val contentTextView: TextView = view.findViewById(R.id.tv_content)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_timestamp)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(adapterPosition) as ListItem.Sms
                    onItemClicked(item.item.id, adapterPosition)
                }
            }
        }

        fun bind(smsItem: SmsItem) {
            senderTextView.text = "From: ${smsItem.sender}"
            contentTextView.text = smsItem.content

            // Set timestamp based on expanded state
            if (expandedItems.contains(smsItem.id)) {
                timestampTextView.text = TimeUtil.getAbsoluteTime(smsItem.timestamp)
            } else {
                timestampTextView.text = TimeUtil.formatDefault(itemView.context, smsItem.timestamp)
            }
        }
    }

    class RetryJobViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Define views and bind logic for the retry item layout here
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.Sms -> TYPE_SMS
            is ListItem.RetryJob -> TYPE_RETRY_JOB
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SMS -> SmsViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_sms_log, parent, false))
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = getItem(position)) {
            is ListItem.Sms -> (holder as SmsViewHolder).bind(currentItem.item)
            is ListItem.RetryJob -> { /* Bind retry job holder */ }
        }
    }
}

class MainDiffCallback : DiffUtil.ItemCallback<ListItem>() {
    override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return when {
            oldItem is ListItem.Sms && newItem is ListItem.Sms -> oldItem.item.id == newItem.item.id
            oldItem is ListItem.RetryJob && newItem is ListItem.RetryJob -> oldItem.job.id == newItem.job.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return oldItem == newItem
    }
}
