package github.oftx.smsforwarder.ui

import android.content.Context
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    private const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000

    private val absoluteFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

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
            getAbsoluteTime(timestamp)
        }
    }

    fun getAbsoluteTime(timestamp: Long): String {
        return absoluteFormatter.format(Date(timestamp))
    }
}
