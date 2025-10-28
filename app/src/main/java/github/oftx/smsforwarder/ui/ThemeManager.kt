package github.oftx.smsforwarder.ui

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import github.oftx.smsforwarder.R

object ThemeManager {
    fun applyTheme(context: Context) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val useMonet = sharedPrefs.getBoolean("pref_monet_theme", true)

        if (useMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // This applies the Monet theme defined in values-v31/themes.xml
            DynamicColors.applyToActivityIfAvailable(context as android.app.Activity)
        }
        // If not, the default theme from values/themes.xml is used automatically.
    }
}
