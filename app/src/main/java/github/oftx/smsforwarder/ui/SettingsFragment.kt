package github.oftx.smsforwarder.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import github.oftx.smsforwarder.R

class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireActivity().application)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val monetPref = findPreference<SwitchPreferenceCompat>("pref_monet_theme")
        val languagePref = findPreference<ListPreference>("pref_language")
        val smsLimitPref = findPreference<EditTextPreference>("pref_sms_limit")
        val clearSmsPref = findPreference<Preference>("pref_clear_sms")
        val viewLogsPref = findPreference<Preference>("pref_view_logs")
        val batteryPref = findPreference<Preference>("pref_ignore_battery_optimizations")

        // 1. Monet/Dynamic Color Preference
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            monetPref?.setOnPreferenceChangeListener { _, _ ->
                activity?.recreate()
                true
            }
        } else {
            monetPref?.isVisible = false
        }

        // 2. Language Preference
        languagePref?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            true
        }

        // 3. SMS Limit Preference
        smsLimitPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
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

        // 4. Click listeners
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
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
            Toast.makeText(requireContext(), "请进入“电池”或“耗电管理”选项进行设置", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法自动跳转，请手动进入系统设置", Toast.LENGTH_SHORT).show()
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
}
