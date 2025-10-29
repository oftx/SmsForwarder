package github.oftx.smsforwarder.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import github.oftx.smsforwarder.AppLogger
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.database.BarkConfig
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.databinding.ActivityBarkConfigBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BarkConfigActivity : BaseActivity() {

    companion object {
        const val EXTRA_RULE_ID = "EXTRA_RULE_ID"
    }

    private lateinit var binding: ActivityBarkConfigBinding
    private val viewModel: BarkConfigViewModel by viewModels { BarkConfigViewModelFactory(application) }
    private var ruleId: Long = -1L
    private var isUpdating = false

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
            isUpdating = true
            supportActionBar?.title = getString(R.string.bark_config_title_edit)
            viewModel.loadRule(ruleId)
            observeExistingRule()
        } else {
            isUpdating = false
            supportActionBar?.title = getString(R.string.bark_config_title_add)
        }

        binding.buttonSave.setOnClickListener {
            saveRule()
        }
    }

    private fun observeExistingRule() {
        lifecycleScope.launch {
            viewModel.existingRule.collectLatest { rule ->
                rule?.let { populateUi(it) }
            }
        }
    }

    private fun populateUi(rule: ForwarderRule) {
        binding.editTextRuleName.setText(rule.name)
        val config = Json.decodeFromString<BarkConfig>(rule.configJson)
        binding.editTextBarkKey.setText(config.key)
        binding.editTextServerUrl.setText(config.serverUrl)
        binding.switchEncryption.isChecked = config.isEncrypted
        if (config.isEncrypted) {
            binding.dropdownAlgorithm.setText(config.algorithm ?: BarkConfig.ALGORITHM_AES_128, false)
            binding.dropdownMode.setText(config.mode ?: BarkConfig.MODE_CBC, false)
            binding.editTextEncryptionKey.setText(config.encryptionKey)
            binding.editTextIv.setText(config.iv)
        }
    }

    private fun setupEncryptionViews() {
        val algorithms = resources.getStringArray(R.array.encryption_algorithms)
        val modes = resources.getStringArray(R.array.encryption_modes)
        binding.dropdownAlgorithm.setAdapter(ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, algorithms))
        binding.dropdownMode.setAdapter(ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes))
        binding.dropdownAlgorithm.setText(BarkConfig.ALGORITHM_AES_128, false)
        binding.dropdownMode.setText(BarkConfig.MODE_CBC, false)
        binding.switchEncryption.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutEncryptionOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun setupValidation() {
        binding.editTextEncryptionKey.doOnTextChanged { text, _, _, _ ->
            val byteSize = text?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0
            val newAlgorithm = when (byteSize) {
                16 -> BarkConfig.ALGORITHM_AES_128
                24 -> BarkConfig.ALGORITHM_AES_192
                32 -> BarkConfig.ALGORITHM_AES_256
                else -> null
            }
            if (newAlgorithm != null) {
                binding.dropdownAlgorithm.setText(newAlgorithm, false)
                binding.layoutEncryptionKey.error = null
            } else if (!text.isNullOrEmpty()) {
                binding.layoutEncryptionKey.error = getString(R.string.error_key_length_must_be_16_24_32, byteSize)
            } else {
                binding.layoutEncryptionKey.error = null
            }
        }
        binding.dropdownMode.doOnTextChanged { text, _, _, _ ->
            when (text.toString()) {
                BarkConfig.MODE_CBC -> {
                    binding.layoutIv.visibility = View.VISIBLE
                    binding.layoutIv.hint = getString(R.string.iv_cbc_hint)
                }
                BarkConfig.MODE_GCM -> {
                    binding.layoutIv.visibility = View.VISIBLE
                    binding.layoutIv.hint = getString(R.string.iv_gcm_hint)
                }
                BarkConfig.MODE_ECB -> {
                    binding.layoutIv.visibility = View.GONE
                    binding.editTextIv.text?.clear()
                }
            }
        }
    }

    private fun saveRule() {
        val name = binding.editTextRuleName.text.toString().trim()
        val barkKey = binding.editTextBarkKey.text.toString().trim()
        val serverUrl = binding.editTextServerUrl.text.toString().trim()

        if (name.isEmpty() || barkKey.isEmpty()) {
            Snackbar.make(binding.root, R.string.error_name_and_key_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        val isEncrypted = binding.switchEncryption.isChecked
        var encryptionKey: String? = null
        var iv: String? = null
        var algorithm: String? = null
        var mode: String? = null
        if (isEncrypted) {
            encryptionKey = binding.editTextEncryptionKey.text.toString()
            val keyByteSize = encryptionKey.toByteArray(Charsets.UTF_8).size
            if (keyByteSize !in listOf(16, 24, 32)) {
                Snackbar.make(binding.root, R.string.error_invalid_key_length, Snackbar.LENGTH_SHORT).show()
                return
            }
            iv = binding.editTextIv.text.toString()
            algorithm = binding.dropdownAlgorithm.text.toString()
            mode = binding.dropdownMode.text.toString()
            if (mode == BarkConfig.MODE_CBC && iv.toByteArray(Charsets.UTF_8).size != 16) {
                Snackbar.make(binding.root, R.string.error_iv_length_for_cbc_must_be_16, Snackbar.LENGTH_SHORT).show()
                return
            }
        }
        val config = BarkConfig(
            key = barkKey, serverUrl = serverUrl, isEncrypted = isEncrypted, algorithm = algorithm,
            mode = mode, encryptionKey = encryptionKey, iv = iv
        )
        val configJson = Json.encodeToString(config)
        val ruleToSave = if (isUpdating) {
            viewModel.existingRule.value!!.copy(name = name, configJson = configJson)
        } else {
            ForwarderRule(name = name, type = ForwarderRule.TYPE_BARK, configJson = configJson, isEnabled = true)
        }
        viewModel.saveRule(ruleToSave, isUpdating)
        AppLogger.log(this, if(isUpdating) "Updated rule '${ruleToSave.name}'." else "Created new rule '${ruleToSave.name}'.")
        Snackbar.make(binding.root, R.string.rule_saved_successfully, Snackbar.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
