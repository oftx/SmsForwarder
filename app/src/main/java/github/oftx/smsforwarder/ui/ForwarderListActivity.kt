package github.oftx.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.databinding.ActivityForwarderListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ForwarderListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForwarderListBinding
    private val viewModel: ForwarderListViewModel by viewModels {
        ForwarderListViewModelFactory(application)
    }
    private lateinit var ruleAdapter: ForwarderRuleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForwarderListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        observeRules()

        binding.fabAddRule.setOnClickListener {
            // Start BarkConfigActivity in "add mode" (no ID passed)
            startActivity(Intent(this, BarkConfigActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        ruleAdapter = ForwarderRuleAdapter(
            onSwitchChanged = { rule, isEnabled ->
                val updatedRule = rule.copy(isEnabled = isEnabled)
                viewModel.updateRule(updatedRule)
            },
            onItemClicked = { rule ->
                // Start BarkConfigActivity in "edit mode" by passing the rule ID
                val intent = Intent(this, BarkConfigActivity::class.java)
                intent.putExtra(BarkConfigActivity.EXTRA_RULE_ID, rule.id)
                startActivity(intent)
            },
            onItemLongClicked = { rule ->
                showDeleteConfirmationDialog(rule)
            }
        )

        binding.recyclerViewRules.apply {
            adapter = ruleAdapter
            layoutManager = LinearLayoutManager(this@ForwarderListActivity)
        }
    }

    private fun observeRules() {
        lifecycleScope.launch {
            viewModel.rules.collectLatest { rules ->
                ruleAdapter.submitList(rules)
            }
        }
    }

    private fun showDeleteConfirmationDialog(rule: ForwarderRule) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除规则")
            .setMessage("您确定要删除规则 \"${rule.name}\" 吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteRule(rule)
            }
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
