package github.oftx.smsforwarder.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.database.BarkConfig
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.databinding.ActivityBarkConfigBinding
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BarkConfigActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RULE_ID = "EXTRA_RULE_ID"
    }

    private lateinit var binding: ActivityBarkConfigBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var ruleId: Long = -1L
    private var existingRule: ForwarderRule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarkConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)

        if (ruleId != -1L) {
            // Edit Mode
            supportActionBar?.title = "编辑Bark规则"
            loadExistingRuleData()
        } else {
            // Add Mode
            supportActionBar?.title = "添加Bark规则"
        }

        binding.buttonSave.setOnClickListener {
            saveRule()
        }
    }

    private fun loadExistingRuleData() {
        lifecycleScope.launch {
            existingRule = db.forwarderRuleDao().getRuleById(ruleId)
            existingRule?.let { rule ->
                binding.editTextRuleName.setText(rule.name)
                val config = Json.decodeFromString<BarkConfig>(rule.configJson)
                binding.editTextBarkKey.setText(config.key)
            }
        }
    }

    private fun saveRule() {
        val name = binding.editTextRuleName.text.toString().trim()
        val key = binding.editTextBarkKey.text.toString().trim()

        if (name.isEmpty() || key.isEmpty()) {
            Snackbar.make(binding.root, "名称和Key不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }

        val config = BarkConfig(key = key)
        val configJson = Json.encodeToString(config)

        lifecycleScope.launch {
            if (existingRule != null) {
                // Update existing rule
                val updatedRule = existingRule!!.copy(
                    name = name,
                    configJson = configJson
                )
                db.forwarderRuleDao().update(updatedRule)
            } else {
                // Insert new rule
                val newRule = ForwarderRule(
                    name = name,
                    type = ForwarderRule.TYPE_BARK,
                    configJson = configJson,
                    isEnabled = true
                )
                db.forwarderRuleDao().insert(newRule)
            }
            Snackbar.make(binding.root, "保存成功", Snackbar.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
