package github.oftx.smsforwarder

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SmsHook : IXposedHookLoadPackage {

    companion object {
        const val ACTION_SMS_RECEIVED = "github.oftx.smsforwarder.SMS_RECEIVED"
        // 使用 @SuppressLint 来避免IDE警告，因为我们知道这个 context 是在特定时机被赋值的
        @SuppressLint("StaticFieldLeak")
        private var moduleContext: Context? = null
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // 我们的目标现在非常明确，就是 com.android.mms
        if (lpparam.packageName == "com.android.mms") {
            // 第一步：想办法获取到目标应用的 Context
            hookApplication(lpparam)
            // 第二步：执行我们的核心 Hook 逻辑
            hookSmsMessage(lpparam)
        }
    }

    private fun hookApplication(lpparam: LoadPackageParam) {
        // Application 的 onCreate 是一个绝佳的 Hook 点，因为它执行得很早，并且持有 Context
        XposedHelpers.findAndHookMethod(
            Application::class.java, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // param.thisObject 就是 Application 实例，它本身就是一个 Context
                    moduleContext = param.thisObject as? Context
                    if (moduleContext != null) {
                        XposedBridge.log("SmsForwarder_LOG: Successfully got context from [${lpparam.processName}]")
                    } else {
                        XposedBridge.log("SmsForwarder_FATAL: Failed to get context.")
                    }
                }
            }
        )
    }

    private fun hookSmsMessage(lpparam: LoadPackageParam) {
        try {
            XposedBridge.log("SmsForwarder_LOG: In process [${lpparam.processName}], trying to hook SmsMessage.createFromPdu...")
            val createFromPdu = XposedHelpers.findMethodExact(
                SmsMessage::class.java, "createFromPdu", ByteArray::class.java, String::class.java
            )
            XposedBridge.hookMethod(createFromPdu, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    XposedBridge.log("SmsForwarder_LOG: Hook triggered: SmsMessage.createFromPdu()")
                    val message = param.result as? SmsMessage ?: return
                    val sender = message.originatingAddress
                    val content = message.messageBody

                    if (sender != null && content != null) {
                        XposedBridge.log("SmsForwarder_LOG: SMS Intercepted! From: $sender")
                        // 现在我们可以安全地使用之前获取到的 context 来发送广播了
                        sendBroadcastToUI(sender, content)
                    }
                }
            })
            XposedBridge.log("SmsForwarder_LOG: SUCCESS: Hooked SmsMessage.createFromPdu.")
        } catch (e: Throwable) {
            XposedBridge.log("SmsForwarder_FATAL: Failed to hook SmsMessage.createFromPdu: ${e.message}")
        }
    }

    private fun sendBroadcastToUI(sender: String, content: String) {
        if (moduleContext == null) {
            XposedBridge.log("SmsForwarder_ERROR: Cannot send broadcast, context is null!")
            return
        }
        XposedBridge.log("SmsForwarder_LOG: Sending SMS broadcast to UI...")
        Intent(ACTION_SMS_RECEIVED).apply {
            putExtra("sender", sender)
            putExtra("content", content)
            // 明确指定接收者，更安全高效
            setClassName("github.oftx.smsforwarder", "github.oftx.smsforwarder.SmsReceiver")
        }.also {
            // 使用我们保存的 context 来发送广播
            moduleContext!!.sendBroadcast(it)
        }
        XposedBridge.log("SmsForwarder_LOG: Broadcast sent.")
    }
}