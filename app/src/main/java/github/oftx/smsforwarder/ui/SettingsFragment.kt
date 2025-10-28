package github.oftx.smsforwarder.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import github.oftx.smsforwarder.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
