package github.oftx.smsforwarder

import android.content.Context
import android.content.Intent
import github.oftx.smsforwarder.database.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppLogger {

    private const val MODULE_PACKAGE_NAME = "github.oftx.smsforwarder"
    private val scope = CoroutineScope(Dispatchers.IO)

    @Suppress("unused") // Suppress warning: This is called externally via Xposed Hook
    fun logFromHook(context: Context, message: String) {
        val fullMessage = "[HOOK] $message"
        de.robv.android.xposed.XposedBridge.log("SmsFwd-Hook: $message")
        
        try {
            val intent = Intent(SmsReceiver.ACTION_LOG_RECEIVED).apply {
                putExtra("message", fullMessage)
                setPackage(MODULE_PACKAGE_NAME)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            try {
                de.robv.android.xposed.XposedBridge.log("SmsFwd-FATAL: Failed to write log via broadcast: ${e.message}")
            } catch (_: Throwable) { }
        }
    }

    fun log(context: Context, message: String) {
        scope.launch {
            suspendLog(context, "[APP] $message")
        }
    }

    suspend fun suspendLog(context: Context, message: String) {
        try {
            AppDatabase.getDatabase(context).logDao().insert(LogEntity(message = message))
        } catch (e: Exception) {
             android.util.Log.e("SmsForwarderAppLogger", "Failed to write app log to DB", e)
        }
    }
}
