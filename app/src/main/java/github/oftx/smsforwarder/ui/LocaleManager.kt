package github.oftx.smsforwarder.ui

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleManager {

    private const val PREF_LANGUAGE_KEY = "pref_language"
    private const val SYSTEM_DEFAULT_LANG = "system"

    fun updateBaseContext(context: Context): Context {
        val language = getPersistedLanguage(context)
        if (language == SYSTEM_DEFAULT_LANG) {
            return context
        }

        val locale = Locale.forLanguageTag(language)
        
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getPersistedLanguage(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(PREF_LANGUAGE_KEY, SYSTEM_DEFAULT_LANG) ?: SYSTEM_DEFAULT_LANG
    }
}
