package github.oftx.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // 确认是我们定义的 Action
        if (intent?.action == SmsHook.ACTION_SMS_RECEIVED) {
            val sender = intent.getStringExtra("sender")
            val content = intent.getStringExtra("content")

            if (sender != null && content != null) {
                // 创建一个启动服务的 Intent，并将短信数据传递过去
                val serviceIntent = Intent(context, SmsHandlingService::class.java).apply {
                    putExtra("sender", sender)
                    putExtra("content", content)
                }
                // 在后台启动服务
                context.startService(serviceIntent)
            }
        }
    }
}