package github.oftx.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.databinding.ActivityForwarderListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ForwarderListActivity : BaseActivity() {
    private lateinit var binding: ActivityForwarderListBinding
    private val viewModel: ForwarderListViewModel by viewModels {
        ForwarderListViewModelFactory(application)
    }
    private lateinit var ruleAdapter: ForwarderRuleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityForwarderListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        observeRules()

        binding.fabAddRule.setOnClickListener {
            startActivity(Intent(this, BarkConfigActivity::class.java))
        }

        // 保存原始 bottom margin（避免重复叠加）
        val origBottomMargin =
            (binding.fabAddRule.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        // 监听 window insets（system bars）
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabAddRule) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 更新 margin：原始 margin + 导航栏高度
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = origBottomMargin + sysBars.bottom + 48
            }
            // 返回 insets 保持传播（不消费）
            insets
        }

        // 确保触发一次 Insets 应用（有时首次不会自动触发）
        ViewCompat.requestApplyInsets(binding.fabAddRule)
    }

    private fun setupRecyclerView() {
        ruleAdapter = ForwarderRuleAdapter(
            onSwitchChanged = { rule, isEnabled ->
                val updatedRule = rule.copy(isEnabled = isEnabled)
                viewModel.updateRule(updatedRule)
            },
            onItemClicked = { rule ->
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
            .setTitle(R.string.delete_rule_dialog_title)
            .setMessage(getString(R.string.delete_rule_dialog_message, rule.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteRule(rule)
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
