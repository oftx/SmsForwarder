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
        // 不再需要广播Action
        // const val ACTION_SMS_RECEIVED = "github.oftx.smsforwarder.SMS_RECEIVED"

        @SuppressLint("StaticFieldLeak")
        private var moduleContext: Context? = null

        // 定义ContentProvider的URI地址，与Provider中保持一致
        private val PROVIDER_URI = Uri.parse("content://github.oftx.smsforwarder.provider/sms")
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
                        // START: 关键修改 - 调用ContentProvider而不是发送广播
                        storeSmsViaProvider(sender, content)
                        // END: 关键修改
                    }
                }
            })
            XposedBridge.log("SmsForwarder_LOG: SUCCESS: Hooked SmsMessage.createFromPdu.")
        } catch (e: Throwable) {
            XposedBridge.log("SmsForwarder_FATAL: Failed to hook SmsMessage.createFromPdu: ${e.message}")
        }
    }

    /**
     * 新方法：通过ContentResolver直接将短信数据插入到主应用的数据库中。
     * 这个操作在当前的宿主进程（com.android.mms）中执行。
     */
    private fun storeSmsViaProvider(sender: String, content: String) {
        if (moduleContext == null) {
            XposedBridge.log("SmsForwarder_ERROR: Cannot store SMS, context is null!")
            return
        }
        try {
            XposedBridge.log("SmsForwarder_LOG: Storing SMS via ContentProvider...")
            val contentResolver = moduleContext!!.contentResolver
            val values = ContentValues().apply {
                put("sender", sender)
                put("content", content)
            }
            // 调用insert，触发SmsProvider中的逻辑
            contentResolver.insert(PROVIDER_URI, values)
            XposedBridge.log("SmsForwarder_LOG: SMS stored successfully.")
        } catch (e: Throwable) {
            XposedBridge.log("SmsForwarder_FATAL: Failed to store SMS via ContentProvider: ${e.message}")
            XposedBridge.log(e) // 打印完整堆栈信息以便调试
        }
    }
}