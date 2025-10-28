package github.oftx.smsforwarder

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.telephony.SmsMessage
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SmsHook : IXposedHookLoadPackage {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var moduleContext: Context? = null
        private val SMS_PROVIDER_URI = Uri.parse("content://github.oftx.smsforwarder.provider/sms")
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
                    val context = param.thisObject as? Context ?: return
                    moduleContext = context
                    AppLogger.logFromHook(context, "Successfully got context from [${lpparam.processName}]")
                }
            }
        )
    }

    private fun hookSmsMessage(lpparam: LoadPackageParam) {
        val context = moduleContext
        if (context == null) {
            // This is unlikely if onCreate hook worked, but as a safeguard.
            XposedBridge.log("SmsFwd-Hook-FATAL: Cannot hook SMS methods, moduleContext is null.")
            return
        }

        try {
            AppLogger.logFromHook(context, "In process [${lpparam.processName}], trying to hook SmsMessage.createFromPdu...")
            val createFromPdu = XposedHelpers.findMethodExact(
                SmsMessage::class.java, "createFromPdu", ByteArray::class.java, String::class.java
            )
            XposedBridge.hookMethod(createFromPdu, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val message = param.result as? SmsMessage ?: return
                    val sender = message.originatingAddress
                    val content = message.messageBody

                    if (sender != null && content != null) {
                        AppLogger.logFromHook(context, "SMS Intercepted! From: $sender")
                        storeSmsViaProvider(sender, content)
                    }
                }
            })
            AppLogger.logFromHook(context, "SUCCESS: Hooked SmsMessage.createFromPdu.")
        } catch (e: Throwable) {
            AppLogger.logFromHook(context, "FATAL: Failed to hook SmsMessage.createFromPdu: ${e.message}")
        }
    }

    private fun storeSmsViaProvider(sender: String, content: String) {
        val context = moduleContext
        if (context == null) {
            XposedBridge.log("SmsFwd-Hook-ERROR: Cannot store SMS, context is null!")
            return
        }
        try {
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put("sender", sender)
                put("content", content)
            }
            contentResolver.insert(SMS_PROVIDER_URI, values)
            AppLogger.logFromHook(context, "SMS from $sender passed to provider successfully.")
        } catch (e: Throwable) {
            AppLogger.logFromHook(context, "FATAL: Failed to store SMS via ContentProvider: ${e.message}")
        }
    }
}
