package github.oftx.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import github.oftx.smsforwarder.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "pref_view_logs") {
            val intent = Intent(requireContext(), LogActivity::class.java)
            startActivity(intent)
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }
}
