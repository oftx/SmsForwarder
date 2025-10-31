package github.oftx.smsforwarder.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
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
import github.oftx.smsforwarder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireActivity().application)
    }

    private val appSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra(AppListActivity.EXTRA_PACKAGE_NAME)?.let { packageName ->
                    saveTargetAppPackage(packageName)
                }
            }
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
        // This is the key change. It forces the preference file to be created with permissions
        // that allow other processes (like the Xposed hook) to read it.
        // Although deprecated, it is the standard and necessary method for Xposed modules.
        preferenceManager.sharedPreferencesMode = Context.MODE_WORLD_READABLE
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        setupHookPreferences()
        setupOtherPreferences()
    }

    override fun onResume() {
        super.onResume()
        updateHookSummaries()
        // As a fallback, manually set permissions again when the screen is viewed.
        setPreferencesReadable()
    }

    private fun setPreferencesReadable() {
        // This makes sure the preference file is readable by the hook.
        try {
            val prefsDir = File(requireContext().applicationInfo.dataDir, "shared_prefs")
            val prefsFile = File(prefsDir, "${requireContext().packageName}_preferences.xml")
            if (prefsFile.exists()) {
                // Set read permission for "others".
                prefsFile.setReadable(true, false)
            }
        } catch (e: Exception) {
            // Log or show a toast if you want to debug permission issues
        }
    }

    private fun setupHookPreferences() {
        val hookTargetAppPref = findPreference<Preference>("pref_hook_target_app")
        val customHookClassPref = findPreference<EditTextPreference>("pref_hook_custom_class")

        // When a hook preference changes, we must immediately ensure the file permissions are correct.
        val listener = Preference.OnPreferenceChangeListener { _, _ ->
            // The value is saved automatically. We just need to trigger the permission change.
            // Post it to the message queue to run after the preference has been saved.
            view?.post { setPreferencesReadable() }
            true // Allow the value to be saved
        }

        hookTargetAppPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showTargetAppSelectionDialog()
            true
        }
        customHookClassPref?.onPreferenceChangeListener = listener

        findPreference<Preference>("pref_start_hook_debug")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), HookDebugLogActivity::class.java))
            true
        }

        findPreference<Preference>("pref_force_stop_target_app")?.setOnPreferenceClickListener {
            forceStopTargetApp()
            true
        }
        updateHookSummaries()
    }

    private fun showTargetAppSelectionDialog() {
        val items = arrayOf(getString(R.string.select_from_list), getString(R.string.enter_manually))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_input_method)
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(requireContext(), AppListActivity::class.java)
                        appSelectionLauncher.launch(intent)
                    }
                    1 -> showManualEntryDialog()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showManualEntryDialog() {
        lifecycleScope.launch {
            val packageNames = withContext(Dispatchers.IO) {
                val pm = requireContext().packageManager
                pm.getInstalledApplications(0).map { it.packageName }.sorted()
            }

            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_autocomplete_edittext, null)
            val autoCompleteTextView = dialogView.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextView)
            val adapter = FuzzySearchAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, packageNames)
            autoCompleteTextView.setAdapter(adapter)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_hook_target_app_title)
                .setView(dialogView)
                .setPositiveButton(R.string.save) { dialog, _ ->
                    val packageName = autoCompleteTextView.text.toString()
                    if (packageName.isNotBlank()) {
                        saveTargetAppPackage(packageName)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun saveTargetAppPackage(packageName: String) {
        findPreference<Preference>("pref_hook_target_app")?.sharedPreferences?.edit {
            putString("pref_hook_target_app", packageName)
            // Use commit() to write synchronously and then immediately set permissions.
            commit()
        }
        setPreferencesReadable()
        updateHookSummaries()
    }
    
    // ... (The rest of the SettingsFragment file remains the same) ...

    private fun updateHookSummaries() {
        val hookTargetAppPref = findPreference<Preference>("pref_hook_target_app")
        val customHookClassPref = findPreference<EditTextPreference>("pref_hook_custom_class")

        val targetAppSummary = getString(R.string.pref_hook_target_app_summary)
        val currentTargetApp = hookTargetAppPref?.sharedPreferences?.getString("pref_hook_target_app", "")
        hookTargetAppPref?.summary = if (currentTargetApp.isNullOrEmpty()) {
            targetAppSummary + "\n" + getString(R.string.current_value_is, getString(R.string.not_set))
        } else {
            targetAppSummary + "\n" + getString(R.string.current_value_is, currentTargetApp)
        }

        customHookClassPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val baseSummary = getString(R.string.pref_hook_custom_class_summary)
            val value = pref.text
            if (value.isNullOrEmpty()) {
                baseSummary + "\n" + getString(R.string.current_value_is, getString(R.string.not_set))
            } else {
                baseSummary + "\n" + getString(R.string.current_value_is, value)
            }
        }
    }

    private fun forceStopTargetApp() {
        val targetPackage = findPreference<Preference>("pref_hook_target_app")?.sharedPreferences?.getString("pref_hook_target_app", "")
        if (targetPackage.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.force_stop_no_target, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $targetPackage"))
            process.waitFor()
            Toast.makeText(requireContext(), getString(R.string.force_stop_success, targetPackage), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.force_stop_failed, targetPackage), Toast.LENGTH_LONG).show()
        }
    }

    private fun setupOtherPreferences() {
        findPreference<SwitchPreferenceCompat>("pref_monet_theme")?.let { monetPref ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                monetPref.setOnPreferenceChangeListener { _, _ ->
                    view?.post { triggerRecreate() }
                    true
                }
            } else {
                monetPref.isVisible = false
            }
        }
        findPreference<Preference>("pref_language")?.setOnPreferenceChangeListener { _, _ ->
            view?.post { triggerRecreate() }
            true
        }
        findPreference<EditTextPreference>("pref_sms_limit")?.let {
            it.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
                val text = preference.text
                val value = text?.toIntOrNull() ?: -1
                if (value <= 0) getString(R.string.pref_sms_limit_summary_unlimited)
                else getString(R.string.pref_sms_limit_summary_format, text)
            }
        }
        findPreference<Preference>("pref_view_logs")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), LogActivity::class.java))
            true
        }
        findPreference<Preference>("pref_clear_sms")?.setOnPreferenceClickListener {
            showClearSmsConfirmationDialog()
            true
        }
        findPreference<Preference>("pref_ignore_battery_optimizations")?.setOnPreferenceClickListener {
            openBatterySettings()
            true
        }
        findPreference<Preference>("pref_export_data")?.setOnPreferenceClickListener {
            val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = simpleDateFormat.format(Date())
            val fileName = "smsforwarder_backup_$timestamp.json"
            exportLauncher.launch(fileName)
            true
        }
        findPreference<Preference>("pref_import_data")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json"))
            true
        }
    }

    private fun triggerRecreate() {
        lifecycleScope.launch { RecreateHandler.triggerRecreate() }
    }
    
    private fun showImportConfirmationDialog(uri: Uri) {
        val strategies = arrayOf(getString(R.string.import_strategy_merge), getString(R.string.import_strategy_replace))
        var selectedStrategy = ImportStrategy.MERGE
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_dialog_title)
            .setSingleChoiceItems(strategies, 0) { _, which ->
                selectedStrategy = if (which == 0) ImportStrategy.MERGE else ImportStrategy.REPLACE
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.import_action) { _, _ -> importDataFromFile(uri, selectedStrategy) }
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
                if (success) Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_SHORT).show()
                else Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "${getString(R.string.import_failed)}: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${requireContext().packageName}".toUri())
            startActivity(intent)
            Toast.makeText(requireContext(), R.string.battery_settings_toast, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.cannot_open_settings_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearSmsConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_sms_dialog_title)
            .setMessage(R.string.clear_sms_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                viewModel.clearAllSms()
                Toast.makeText(requireContext(), R.string.all_messages_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is EditTextPreference) {
            showEditTextPreference(preference)
        } else if (preference is ListPreference) {
            showListPreference(preference)
        }
        else {
            super.onDisplayPreferenceDialog(preference)
        }
    }
    
    private fun showListPreference(preference: ListPreference) {
        val selectionIndex = preference.findIndexOfValue(preference.value)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.title)
            .setSingleChoiceItems(preference.entries, selectionIndex) { dialog, which ->
                val newValue = preference.entryValues[which].toString()
                if (preference.callChangeListener(newValue)) {
                    preference.value = newValue
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditTextPreference(preference: EditTextPreference) {
        val dialogView = View.inflate(requireContext(), R.layout.dialog_edit_text, null)
        val input = dialogView.findViewById<TextInputEditText?>(R.id.textInput)
        input?.setText(preference.text)
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(preference.title)
        builder.setView(dialogView)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            val newValue = input?.text.toString()
            if (preference.callChangeListener(newValue)) {
                preference.text = newValue
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }
}
