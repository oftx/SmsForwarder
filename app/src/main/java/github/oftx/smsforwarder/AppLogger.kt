package github.oftx.smsforwarder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The single source of truth for logging.
 * All logging methods in this object write to the database via the ContentProvider
 * to ensure a single database file is used across all processes.
 */
object AppLogger {

    private val LOG_PROVIDER_URI = Uri.parse("content://github.oftx.smsforwarder.provider/log")
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Writes a log from the Xposed Hook process.
     */
    fun logFromHook(context: Context, message: String) {
        de.robv.android.xposed.XposedBridge.log("SmsFwd-Hook: $message")
        writeLog(context, "[HOOK] $message")
    }

    /**
     * Writes a log from the main application's UI/background process.
     * This is a fire-and-forget operation on a background thread.
     */
    fun log(context: Context, message: String) {
        scope.launch {
            writeLog(context, "[APP] $message")
        }
    }

    /**
     * A suspending version for writing logs from within a coroutine in the main app.
     */
    suspend fun suspendLog(context: Context, message: String) {
        // Since contentResolver.insert is blocking, we don't need to switch context.
        // It's safe to call from any dispatcher.
        writeLog(context, "[APP] $message")
    }

    /**
     * The core private function that performs the write operation via the ContentProvider.
     */
    private fun writeLog(context: Context, fullMessage: String) {
        try {
            val values = ContentValues().apply {
                put("message", fullMessage)
            }
            // Using applicationContext to avoid memory leaks
            context.applicationContext.contentResolver.insert(LOG_PROVIDER_URI, values)
        } catch (e: Exception) {
            // If this fails, log to the most relevant logcat
            try {
                // If in Xposed context, this will work
                de.robv.android.xposed.XposedBridge.log("SmsFwd-FATAL: Failed to write log to provider: ${e.message}")
            } catch (t: Throwable) {
                // Otherwise, fall back to standard Android log
                android.util.Log.e("SmsForwarder", "Failed to write log to provider", e)
            }
        }
    }
}
