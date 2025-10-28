package github.oftx.smsforwarder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.databinding.ItemForwarderRuleBinding

class ForwarderRuleAdapter(
    private val onSwitchChanged: (ForwarderRule, Boolean) -> Unit,
    private val onItemClicked: (ForwarderRule) -> Unit,
    private val onItemLongClicked: (ForwarderRule) -> Unit
) : ListAdapter<ForwarderRule, ForwarderRuleAdapter.RuleViewHolder>(RuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemForwarderRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RuleViewHolder(private val binding: ItemForwarderRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClicked(getItem(adapterPosition))
                }
            }
            binding.root.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemLongClicked(getItem(adapterPosition))
                }
                true
            }
        }

        fun bind(rule: ForwarderRule) {
            binding.tvRuleName.text = rule.name
            binding.tvRuleType.text = rule.type
            
            // Set listener to null before changing checked state to prevent infinite loops
            binding.switchRuleEnabled.setOnCheckedChangeListener(null)
            binding.switchRuleEnabled.isChecked = rule.isEnabled
            binding.switchRuleEnabled.setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(rule, isChecked)
            }
        }
    }

    class RuleDiffCallback : DiffUtil.ItemCallback<ForwarderRule>() {
        override fun areItemsTheSame(oldItem: ForwarderRule, newItem: ForwarderRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ForwarderRule, newItem: ForwarderRule): Boolean {
            return oldItem == newItem
        }
    }
}
