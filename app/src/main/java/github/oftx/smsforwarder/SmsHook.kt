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
        @SuppressLint("StaticFieldLeak")
        private var moduleContext: Context? = null
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "com.android.mms") {
            hookApplication(lpparam)
            hookSmsMessage(lpparam)
        }
    }

    private fun hookApplication(lpparam: LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            Application::class.java, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
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
            // --- START: 修改广播接收者 ---
            // 将广播目标修改为新的 SmsBroadcastReceiver
            setClassName("github.oftx.smsforwarder", "github.oftx.smsforwarder.SmsBroadcastReceiver")
            // --- END: 修改广播接收者 ---
        }.also {
            moduleContext!!.sendBroadcast(it)
        }
        XposedBridge.log("SmsForwarder_LOG: Broadcast sent.")
    }
}