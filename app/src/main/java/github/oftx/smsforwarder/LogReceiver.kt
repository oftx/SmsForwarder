package github.oftx.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LogReceiver : BroadcastReceiver() {
    companion object {
        var logListener: ((message: String) -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            if (it.action == AppLogger.ACTION_LOG_RECEIVED) {
                val message = it.getStringExtra("message") ?: "Empty log message"
                // 通过静态接口回调给 MainActivity
                logListener?.invoke(message)
            }
        }
    }
}
