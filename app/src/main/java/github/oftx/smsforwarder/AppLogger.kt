package github.oftx.smsforwarder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppLogger {

    private val LOG_PROVIDER_URI = Uri.parse("content://github.oftx.smsforwarder.provider/log")
    private val scope = CoroutineScope(Dispatchers.IO)

    fun logFromHook(context: Context, message: String) {
        de.robv.android.xposed.XposedBridge.log("SmsFwd-Hook: $message")
        writeLog(context, "[HOOK] $message")
    }

    fun log(context: Context, message: String) {
        scope.launch {
            writeLog(context, "[APP] $message")
        }
    }

    suspend fun suspendLog(context: Context, message: String) {
        writeLog(context, "[APP] $message")
    }

    private fun writeLog(context: Context, fullMessage: String) {
        try {
            val values = ContentValues().apply {
                put("message", fullMessage)
            }
            context.applicationContext.contentResolver.insert(LOG_PROVIDER_URI, values)
        } catch (e: Exception) {
            try {
                de.robv.android.xposed.XposedBridge.log("SmsFwd-FATAL: Failed to write log to provider: ${e.message}")
            } catch (t: Throwable) {
                android.util.Log.e("SmsForwarder", "Failed to write log to provider", e)
            }
        }
    }
}
