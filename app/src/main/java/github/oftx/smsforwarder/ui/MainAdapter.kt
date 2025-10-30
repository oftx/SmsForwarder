package github.oftx.smsforwarder.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.database.ForwardingJobWithRuleName
import github.oftx.smsforwarder.database.JobStatus

sealed class ListItem {
    data class Sms(val item: SmsItem) : ListItem()
}

data class SmsItem(
    val id: Long,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val jobs: List<ForwardingJobWithRuleName>
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

            val summaryText = formatStatusSummary(itemView.context, smsItem.jobs)
            if (summaryText.isNotEmpty()) {
                statusSummaryTextView.visibility = View.VISIBLE
                statusSummaryTextView.text = summaryText
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

    private fun formatStatusSummary(context: Context, jobs: List<ForwardingJobWithRuleName>): CharSequence {
        if (jobs.isEmpty()) return ""

        val total = jobs.size
        val successCount = jobs.count { it.job.status == JobStatus.SUCCESS.value }
        val cancelledCount = jobs.count { it.job.status == JobStatus.CANCELLED.value }

        if (successCount == total) {
            return context.getString(R.string.status_summary_all_sent)
        }
        // FIX: The condition 'total > 0' is redundant here
        if (cancelledCount == total) {
            return context.getString(R.string.status_summary_all_cancelled)
        }

        val summaryBuilder = SpannableStringBuilder()

        jobs.forEachIndexed { index, item ->
            val start = summaryBuilder.length
            summaryBuilder.append(item.ruleName)

            val marker = when (item.job.status) {
                JobStatus.SUCCESS.value -> "(✓)"
                JobStatus.FAILED_RETRY.value, JobStatus.FAILED_PERMANENTLY.value -> "(✘)"
                else -> ""
            }
            summaryBuilder.append(marker)

            if (item.job.status == JobStatus.CANCELLED.value) {
                summaryBuilder.setSpan(StrikethroughSpan(), start, summaryBuilder.length, 0)
            }

            if (index < jobs.size - 1) {
                summaryBuilder.append("、")
            }
        }

        val isRetrying = jobs.any { it.job.status == JobStatus.FAILED_RETRY.value }
        val isForwarding = jobs.any { it.job.status == JobStatus.PENDING.value }

        if (isRetrying) {
            summaryBuilder.insert(0, context.getString(R.string.status_summary_retrying))
        } else if (isForwarding) {
            summaryBuilder.insert(0, context.getString(R.string.status_summary_forwarding_to))
        }

        return summaryBuilder
    }
}

class MainDiffCallback : DiffUtil.ItemCallback<ListItem>() {
    override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        // Correctly compare IDs for item identity
        return (oldItem as ListItem.Sms).item.id == (newItem as ListItem.Sms).item.id
    }

    override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        // FIX: Use the data class's generated equals() for proper content comparison
        return oldItem == newItem
    }
}
