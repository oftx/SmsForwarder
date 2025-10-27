package github.oftx.smsforwarder

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

object AppLogger {

    const val ACTION_LOG_RECEIVED = "github.oftx.smsforwarder.LOG_RECEIVED"

    /**
     * 这个方法从 Hook 进程调用
     */
    fun logFromHook(context: Context, message: String) {
        // 1. 在 Xposed Logcat 中打印，方便 adb 查看
        de.robv.android.xposed.XposedBridge.log("SmsForwarder_LOG: $message")

        // 2. 将日志发送到 UI 进程
        Intent(ACTION_LOG_RECEIVED).apply {
            putExtra("message", message)
            // 目标组件是新的独立日志接收器
            val componentName = "github.oftx.smsforwarder.LogReceiver"
            setClassName("github.oftx.smsforwarder", componentName)
        }.also {
            context.sendBroadcast(it)
        }
    }

    /**
     * 这个方法从 UI 进程调用
     */
    fun logFromUI(context: Context, message: String) {
        // 直接发送一个应用内广播给 MainActivity
        Intent(ACTION_LOG_RECEIVED).apply {
            putExtra("message", message)
        }.also {
            LocalBroadcastManager.getInstance(context).sendBroadcast(it)
        }
    }
}
