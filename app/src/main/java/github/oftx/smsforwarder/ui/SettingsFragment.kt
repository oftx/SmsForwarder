package github.oftx.smsforwarder.ui

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import github.oftx.smsforwarder.R
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireActivity().application)
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    val jsonString = viewModel.exportData()
                    requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray())
                    }
                    Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            showImportConfirmationDialog(it)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val monetPref = findPreference<SwitchPreferenceCompat>("pref_monet_theme")
        val languagePref = findPreference<ListPreference>("pref_language")
        val smsLimitPref = findPreference<EditTextPreference>("pref_sms_limit")
        val clearSmsPref = findPreference<Preference>("pref_clear_sms")
        val retryAttemptsPref = findPreference<EditTextPreference>("pref_max_retry_attempts")
        val viewLogsPref = findPreference<Preference>("pref_view_logs")
        val batteryPref = findPreference<Preference>("pref_ignore_battery_optimizations")
        val exportPref = findPreference<Preference>("pref_export_data")
        val importPref = findPreference<Preference>("pref_import_data")

        // 1. Monet/Dynamic Color Preference
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            monetPref?.setOnPreferenceChangeListener { _, _ ->
                // Use post to delay, ensuring the preference value is saved before recreation
                view?.post { triggerRecreate() }
                true
            }
        } else {
            monetPref?.isVisible = false
        }

        // 2. Language Preference
        languagePref?.setOnPreferenceChangeListener { _, _ ->
            // Use post to delay, ensuring the preference value is saved before recreation
            view?.post { triggerRecreate() }
            true
        }

        // 3. SMS Limit Preference
        smsLimitPref?.summaryProvider =
            Preference.SummaryProvider<EditTextPreference> { preference ->
                val text = preference.text
                val value = text?.toIntOrNull() ?: -1
                if (value <= 0) {
                    getString(R.string.pref_sms_limit_summary_unlimited)
                } else {
                    getString(R.string.pref_sms_limit_summary_format, text)
                }
            }
        smsLimitPref?.setOnBindEditTextListener { editText ->
            editText.hint = getString(R.string.pref_sms_limit_dialog_hint)
        }

        // 4. Max Retry Attempts Preference
        retryAttemptsPref?.summaryProvider =
            Preference.SummaryProvider<EditTextPreference> { preference ->
                val text = preference.text
                // Use default value if text is invalid
                val value = text?.toIntOrNull() ?: 5
                getString(R.string.pref_max_retry_attempts_summary, value.toString())
            }
        retryAttemptsPref?.dialogTitle = getString(R.string.pref_max_retry_attempts_dialog_title)

        // 5. Click listeners
        viewLogsPref?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), LogActivity::class.java))
            true
        }
        clearSmsPref?.setOnPreferenceClickListener {
            showClearSmsConfirmationDialog()
            true
        }
        batteryPref?.setOnPreferenceClickListener {
            openBatterySettings()
            true
        }
        exportPref?.setOnPreferenceClickListener {
            val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = simpleDateFormat.format(Date())
            val fileName = "smsforwarder_backup_$timestamp.json"
            exportLauncher.launch(fileName)
            true
        }
        importPref?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json"))
            true
        }
    }

    private fun showImportConfirmationDialog(uri: Uri) {
        val strategies = arrayOf(
            getString(R.string.import_strategy_merge),
            getString(R.string.import_strategy_replace)
        )
        var selectedStrategy = ImportStrategy.MERGE

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_dialog_title)
            // FIXED: Removed .setMessage() as it conflicts with setSingleChoiceItems()
            .setSingleChoiceItems(strategies, 0) { _, which ->
                selectedStrategy = if (which == 0) ImportStrategy.MERGE else ImportStrategy.REPLACE
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.import_action) { _, _ ->
                importDataFromFile(uri, selectedStrategy)
            }
            .show()
    }

    private fun importDataFromFile(uri: Uri, strategy: ImportStrategy) {
        lifecycleScope.launch {
            try {
                val stringBuilder = StringBuilder()
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                val success = viewModel.importData(stringBuilder.toString(), strategy)
                if (success) {
                    Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val errorMessage = getString(R.string.import_failed) + ": " + e.localizedMessage
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun triggerRecreate() {
        lifecycleScope.launch {
            RecreateHandler.triggerRecreate()
        }
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = "package:${requireContext().packageName}".toUri()
            }
            startActivity(intent)
            Toast.makeText(
                requireContext(),
                R.string.battery_settings_toast,
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.cannot_open_settings_toast, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun showClearSmsConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_sms_dialog_title)
            .setMessage(R.string.clear_sms_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                viewModel.clearAllSms()
                Toast.makeText(requireContext(), R.string.all_messages_cleared, Toast.LENGTH_SHORT)
                    .show()
            }
            .show()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference) {
            showListPreference(preference)
        } else if (preference is EditTextPreference) {
            showEditTextPreference(preference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }


    private fun showListPreference(preference: ListPreference) {
        val selectionIndex = listOf(*preference.entryValues)
            .indexOf(preference.value)
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(preference.title)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setSingleChoiceItems(
            preference.entries,
            selectionIndex
        ) { dialog: DialogInterface?, index: Int ->
            val newValue = preference.entryValues[index].toString()
            if (preference.callChangeListener(newValue)) {
                preference.setValue(newValue)
            }
            dialog!!.dismiss()
        }
        builder.show()
    }

    fun showEditTextPreference(preference: EditTextPreference) {
        val dialogView = View.inflate(requireContext(), R.layout.dialog_edit_text, null)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        val input = dialogView.findViewById<TextInputEditText?>(R.id.textInput)

        if (preference.key == "pref_sms_limit") {
            textInputLayout.hint = getString(R.string.pref_sms_limit_dialog_hint)
        }
        input?.setText(preference.text)

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(preference.title)
        builder.setIcon(R.drawable.ic_edit)
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setPositiveButton(android.R.string.ok, { dialog, i ->
            val newValue = input?.getText().toString()
            if (preference.callChangeListener(newValue)) {
                preference.setText(newValue)
            }
            dialog.dismiss()
        })
        builder.setView(dialogView)
        val dialog: Dialog = builder.create()
        dialog.show()
    }
}
