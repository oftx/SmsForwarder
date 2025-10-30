package github.oftx.smsforwarder.ui

import android.content.Context
import android.text.format.DateUtils
import androidx.preference.PreferenceManager
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000
    private const val PREF_TIME_FORMAT_KEY = "pref_time_format"
    private const val SYSTEM_DEFAULT_FORMAT = "system"
    private const val FALLBACK_FORMAT = "yyyy/MM/dd HH:mm:ss"

    fun formatDefault(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        return if (now - timestamp < TWENTY_FOUR_HOURS_MS) {
            DateUtils.getRelativeTimeSpanString(
                timestamp,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } else {
            getAbsoluteTime(context, timestamp)
        }
    }

    fun getAbsoluteTime(context: Context, timestamp: Long): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val formatPattern = prefs.getString(PREF_TIME_FORMAT_KEY, SYSTEM_DEFAULT_FORMAT)

        val formatter = if (formatPattern == SYSTEM_DEFAULT_FORMAT) {
            // Respects the user's system-wide date and time format settings
            DateFormat.getDateTimeInstance()
        } else {
            try {
                SimpleDateFormat(formatPattern, Locale.getDefault())
            } catch (e: IllegalArgumentException) {
                // Fallback to a safe default if the pattern is invalid
                SimpleDateFormat(FALLBACK_FORMAT, Locale.getDefault())
            }
        }
        return formatter.format(Date(timestamp))
    }
}
