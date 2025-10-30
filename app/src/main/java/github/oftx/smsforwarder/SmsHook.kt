package github.oftx.smsforwarder

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.telephony.SmsMessage
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SmsHook : IXposedHookLoadPackage {

    companion object {
        // 定义 ContentProvider 的 URI
        private val SMS_PROVIDER_URI = Uri.parse("content://github.oftx.smsforwarder.provider/sms")

        private val TARGET_PACKAGES = setOf(
            "com.android.mms",
            "com.google.android.apps.messaging"
        )

        // --- PDU 签名去重逻辑所需变量 ---
        private const val DEBOUNCE_WINDOW_MS = 2000 // 2秒的去重窗口
        @Volatile private var lastPduTimestamp: Long = 0
        private val recentPduSignatures = mutableSetOf<String>()
        private val lock = Any()
        // --- 结束 ---
    }

    private var isHooked = false

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName in TARGET_PACKAGES && !isHooked) {
            XposedBridge.log("SmsFwd: Target package found: ${lpparam.packageName}. Preparing to hook.")
            hookSmsCreateFromPdu()
            isHooked = true
        }
    }

    private fun hookSmsCreateFromPdu() {
        try {
            XposedBridge.log("SmsFwd: Attempting to hook 'SmsMessage.createFromPdu'...")
            val createFromPduMethod = XposedHelpers.findMethodExact(
                SmsMessage::class.java, "createFromPdu", ByteArray::class.java, String::class.java
            )

            // 我们需要在方法执行前就进行去重判断，所以使用 beforeHookedMethod
            XposedBridge.hookMethod(createFromPduMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    synchronized(lock) {
                        val pdu = param.args[0] as ByteArray
                        val pduSignature = pdu.contentToString() // 为PDU字节数组生成唯一签名
                        val now = System.currentTimeMillis()

                        // 如果距离上条短信时间过长，清理签名缓存，防止内存泄漏
                        if (now - lastPduTimestamp > DEBOUNCE_WINDOW_MS) {
                            recentPduSignatures.clear()
                        }

                        // 如果签名集中已存在此签名，说明是重复调用，直接阻止方法执行
                        if (recentPduSignatures.contains(pduSignature)) {
                            XposedBridge.log("SmsFwd: Duplicate PDU detected (signature match). Suppressing.")
                            // 通过返回 null 来阻止原始方法的执行
                            param.result = null
                            return
                        }

                        // 如果是新的 PDU，记录下来
                        recentPduSignatures.add(pduSignature)
                        lastPduTimestamp = now
                        XposedBridge.log("SmsFwd: New unique PDU detected. Allowing method to proceed.")
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    // 如果 beforeHookedMethod 中设置了 result，原始方法就不会执行，param.result 会是 null
                    if (param.result == null) {
                        return
                    }
                    
                    val message = param.result as SmsMessage
                    val sender = message.originatingAddress
                    val content = message.messageBody

                    if (sender != null && content != null) {
                        XposedBridge.log("SmsFwd: HOOK TRIGGERED! SMS Intercepted from: $sender. Inserting via ContentProvider...")
                        insertSmsViaProvider(sender, content)
                    }
                }
            })
            XposedBridge.log("SmsFwd: SUCCESS: Hooked 'SmsMessage.createFromPdu(byte[], String)'.")
        } catch (e: Throwable) {
            XposedBridge.log("SmsFwd: FATAL: Failed to hook 'SmsMessage.createFromPdu': ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun insertSmsViaProvider(sender: String, content: String) {
        try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val application = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as Application
            val context = application.applicationContext

            val values = ContentValues().apply {
                put("sender", sender)
                put("content", content)
            }
            
            // 使用 ContentResolver 插入数据，这将自动唤醒目标应用的 Provider（即使应用已被强制停止）
            context.contentResolver.insert(SMS_PROVIDER_URI, values)
            XposedBridge.log("SmsFwd: SMS data inserted via ContentProvider successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("SmsFwd-FATAL: Failed to insert SMS via ContentProvider.")
            XposedBridge.log(t)
        }
    }
}
