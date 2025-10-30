package github.oftx.smsforwarder.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.database.ForwardingJobWithRuleName
import github.oftx.smsforwarder.database.JobStatus
import github.oftx.smsforwarder.databinding.ItemForwardingStatusBinding

class ForwardingStatusAdapter(
    private val onRetryClicked: (ForwardingJobWithRuleName) -> Unit,
    private val onCancelClicked: (ForwardingJobWithRuleName) -> Unit,
) : ListAdapter<ForwardingJobWithRuleName, ForwardingStatusAdapter.StatusViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemForwardingStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StatusViewHolder(private val binding: ItemForwardingStatusBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnRetryJob.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onRetryClicked(getItem(bindingAdapterPosition))
                }
            }
            binding.btnCancelJob.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onCancelClicked(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(item: ForwardingJobWithRuleName) {
            val context = binding.root.context
            binding.tvRuleName.text = item.ruleName

            when (item.job.status) {
                JobStatus.PENDING.value -> {
                    binding.tvJobStatus.text = context.getString(R.string.status_pending)
                    binding.btnRetryJob.visibility = View.GONE
                    binding.btnCancelJob.visibility = View.GONE
                }
                JobStatus.SUCCESS.value -> {
                    binding.tvJobStatus.text = context.getString(R.string.status_sent)
                    binding.btnRetryJob.visibility = View.GONE
                    binding.btnCancelJob.visibility = View.GONE
                }
                JobStatus.FAILED_RETRY.value -> {
                    val error = item.job.errorMessage?.take(50) ?: ""
                    binding.tvJobStatus.text = "${context.getString(R.string.status_retrying)} ($error...)"
                    binding.btnRetryJob.visibility = View.VISIBLE
                    binding.btnCancelJob.visibility = View.VISIBLE
                }
                JobStatus.CANCELLED.value -> {
                    binding.tvJobStatus.text = context.getString(R.string.status_cancelled)
                    binding.btnRetryJob.visibility = View.VISIBLE
                    binding.btnCancelJob.visibility = View.GONE
                }
                JobStatus.FAILED_PERMANENTLY.value -> {
                    binding.tvJobStatus.text = context.getString(R.string.status_failed)
                    binding.btnRetryJob.visibility = View.GONE
                    binding.btnCancelJob.visibility = View.GONE
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ForwardingJobWithRuleName>() {
        override fun areItemsTheSame(oldItem: ForwardingJobWithRuleName, newItem: ForwardingJobWithRuleName): Boolean {
            return oldItem.job.id == newItem.job.id
        }

        override fun areContentsTheSame(oldItem: ForwardingJobWithRuleName, newItem: ForwardingJobWithRuleName): Boolean {
            return oldItem == newItem
        }
    }
}
