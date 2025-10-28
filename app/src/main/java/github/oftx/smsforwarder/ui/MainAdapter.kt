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

data class SmsItem(val sender: String, val content: String, val timestamp: String)

class MainAdapter : ListAdapter<ListItem, RecyclerView.ViewHolder>(MainDiffCallback()) {

    companion object {
        private const val TYPE_SMS = 0
        private const val TYPE_RETRY_JOB = 1
    }

    class SmsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val senderTextView: TextView = view.findViewById(R.id.tv_sender)
        private val contentTextView: TextView = view.findViewById(R.id.tv_content)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_timestamp)

        fun bind(smsItem: SmsItem) {
            senderTextView.text = "From: ${smsItem.sender}"
            contentTextView.text = smsItem.content
            timestampTextView.text = smsItem.timestamp
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
            TYPE_SMS -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms_log, parent, false)
                SmsViewHolder(view)
            }
            // Add layout for TYPE_RETRY_JOB later
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = getItem(position)) {
            is ListItem.Sms -> (holder as SmsViewHolder).bind(currentItem.item)
            is ListItem.RetryJob -> {
                // Bind data for RetryJobViewHolder here
            }
        }
    }
}

class MainDiffCallback : DiffUtil.ItemCallback<ListItem>() {
    override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return when {
            oldItem is ListItem.Sms && newItem is ListItem.Sms -> oldItem.item.timestamp == newItem.item.timestamp && oldItem.item.content == newItem.item.content
            oldItem is ListItem.RetryJob && newItem is ListItem.RetryJob -> oldItem.job.id == newItem.job.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return oldItem == newItem
    }
}
