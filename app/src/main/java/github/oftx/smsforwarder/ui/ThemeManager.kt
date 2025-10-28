package github.oftx.smsforwarder.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors

object ThemeManager {
    fun applyTheme(context: Context) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val useMonet = sharedPrefs.getBoolean("pref_monet_theme", true)

        if (useMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(context as Activity)
        }
    }
}
