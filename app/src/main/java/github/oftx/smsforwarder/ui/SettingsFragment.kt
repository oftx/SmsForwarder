package github.oftx.smsforwarder.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
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
        val smsLimitPref = findPreference<EditTextPreference>("pref_sms_limit")
        val clearSmsPref = findPreference<Preference>("pref_clear_sms")
        val viewLogsPref = findPreference<Preference>("pref_view_logs")

        // 1. Monet/Dynamic Color Preference
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            monetPref?.setOnPreferenceChangeListener { _, _ ->
                // The value is saved automatically. We just need to restart the activity.
                activity?.recreate()
                true
            }
        } else {
            // Hide the setting on older Android versions
            monetPref?.isVisible = false
        }

        // 2. SMS Limit Preference
        smsLimitPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            val text = preference.text
            val value = text?.toIntOrNull() ?: -1
            if (value <= 0) "无上限" else "$value 条消息"
        }
        smsLimitPref?.setOnBindEditTextListener { editText ->
            editText.hint = "设置为 -1 或 0 则无上限"
        }

        // 3. Click listeners
        viewLogsPref?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), LogActivity::class.java))
            true
        }
        clearSmsPref?.setOnPreferenceClickListener {
            showClearSmsConfirmationDialog()
            true
        }
    }

    private fun showClearSmsConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清空所有消息")
            .setMessage("您确定要删除所有已保存的短信记录吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ ->
                viewModel.clearAllSms()
                Toast.makeText(requireContext(), "所有消息已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
