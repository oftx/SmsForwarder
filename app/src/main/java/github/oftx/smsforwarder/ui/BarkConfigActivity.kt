package github.oftx.smsforwarder.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.R
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

        setupEncryptionViews()
        setupValidation()

        ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)

        if (ruleId != -1L) {
            supportActionBar?.title = "编辑Bark规则"
            loadExistingRuleData()
        } else {
            supportActionBar?.title = "添加Bark规则"
        }

        binding.buttonSave.setOnClickListener {
            saveRule()
        }
    }

    private fun setupEncryptionViews() {
        // Populate dropdown
        val algorithms = resources.getStringArray(R.array.encryption_algorithms)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, algorithms)
        binding.dropdownAlgorithm.setAdapter(adapter)

        // Set default selection
        binding.dropdownAlgorithm.setText(BarkConfig.ALGORITHM_AES_CBC, false)

        // Toggle visibility
        binding.switchEncryption.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutEncryptionOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // IV is only needed for CBC mode
        binding.dropdownAlgorithm.doOnTextChanged { text, _, _, _ ->
            binding.editTextIv.isEnabled = text.toString() == BarkConfig.ALGORITHM_AES_CBC
            if (!binding.editTextIv.isEnabled) {
                binding.editTextIv.text?.clear()
            }
        }
    }

    private fun setupValidation() {
        binding.editTextEncryptionKey.doOnTextChanged { text, _, _, _ ->
            val byteSize = text?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0
            if (text.isNullOrEmpty()) {
                binding.editTextEncryptionKey.error = null
            } else if (byteSize !in listOf(16, 24, 32)) {
                binding.editTextEncryptionKey.error = "密钥长度必须为 16, 24, 或 32 字节 (当前: $byteSize)"
            } else {
                binding.editTextEncryptionKey.error = null
            }
        }
    }

    private fun loadExistingRuleData() {
        lifecycleScope.launch {
            existingRule = db.forwarderRuleDao().getRuleById(ruleId)
            existingRule?.let { rule ->
                binding.editTextRuleName.setText(rule.name)
                val config = Json.decodeFromString<BarkConfig>(rule.configJson)
                binding.editTextBarkKey.setText(config.key)
                binding.switchEncryption.isChecked = config.isEncrypted
                if (config.isEncrypted) {
                    binding.dropdownAlgorithm.setText(config.algorithm ?: BarkConfig.ALGORITHM_AES_CBC, false)
                    binding.editTextEncryptionKey.setText(config.encryptionKey)
                    binding.editTextIv.setText(config.iv)
                }
            }
        }
    }

    private fun saveRule() {
        val name = binding.editTextRuleName.text.toString().trim()
        val barkKey = binding.editTextBarkKey.text.toString().trim()

        if (name.isEmpty() || barkKey.isEmpty()) {
            Snackbar.make(binding.root, "名称和Bark Key不能为空", Snackbar.LENGTH_SHORT).show()
            return
        }

        val isEncrypted = binding.switchEncryption.isChecked
        var encryptionKey: String? = null
        var iv: String? = null
        var algorithm: String? = null

        if (isEncrypted) {
            encryptionKey = binding.editTextEncryptionKey.text.toString()
            val keyByteSize = encryptionKey.toByteArray(Charsets.UTF_8).size
            if (keyByteSize !in listOf(16, 24, 32)) {
                Snackbar.make(binding.root, "密钥长度无效", Snackbar.LENGTH_SHORT).show()
                return
            }
            iv = binding.editTextIv.text.toString()
            algorithm = binding.dropdownAlgorithm.text.toString()
            if (algorithm == BarkConfig.ALGORITHM_AES_CBC && iv.toByteArray(Charsets.UTF_8).size != 16) {
                Snackbar.make(binding.root, "CBC模式下IV长度必须为16字节", Snackbar.LENGTH_SHORT).show()
                return
            }
        }

        val config = BarkConfig(
            key = barkKey,
            isEncrypted = isEncrypted,
            algorithm = algorithm,
            encryptionKey = encryptionKey,
            iv = iv
        )
        val configJson = Json.encodeToString(config)

        lifecycleScope.launch {
            val ruleToSave = existingRule?.copy(
                name = name,
                configJson = configJson
            ) ?: ForwarderRule(
                name = name,
                type = ForwarderRule.TYPE_BARK,
                configJson = configJson,
                isEnabled = true
            )

            if (existingRule != null) {
                db.forwarderRuleDao().update(ruleToSave)
            } else {
                db.forwarderRuleDao().insert(ruleToSave)
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
