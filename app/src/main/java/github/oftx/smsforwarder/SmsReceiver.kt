package github.oftx.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsReceiver : BroadcastReceiver() {

    companion object {
        // 静态接口，用于将数据回调给 Activity
        var smsListener: ((sender: String, content: String) -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            // 使用 UI Logger 记录日志，方便调试
            AppLogger.logFromUI(it, "SmsReceiver: onReceive triggered for SMS!")
        }

        intent?.let {
            if (it.action == SmsHook.ACTION_SMS_RECEIVED) {
                val sender = it.getStringExtra("sender") ?: "Unknown sender"
                val content = it.getStringExtra("content") ?: "No content"

                // 通过接口将数据传递给正在前台运行的 Activity
                smsListener?.invoke(sender, content)
            }
        }
    }
}
