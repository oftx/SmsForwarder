package github.oftx.smsforwarder

import android.app.Application
import android.content.Intent
import android.telephony.SmsMessage
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SmsHook : IXposedHookLoadPackage {

    companion object {
        private const val MODULE_PACKAGE_NAME = "github.oftx.smsforwarder"
        private const val ACTION_SMS_RECEIVED = "github.oftx.smsforwarder.ACTION_SMS_RECEIVED"

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
                        XposedBridge.log("SmsFwd: HOOK TRIGGERED! SMS Intercepted from: $sender. Sending broadcast...")
                        sendSmsBroadcast(sender, content)
                    }
                }
            })
            XposedBridge.log("SmsFwd: SUCCESS: Hooked 'SmsMessage.createFromPdu(byte[], String)'.")
        } catch (e: Throwable) {
            XposedBridge.log("SmsFwd: FATAL: Failed to hook 'SmsMessage.createFromPdu': ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun sendSmsBroadcast(sender: String, content: String) {
        try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val application = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as Application
            val context = application.applicationContext

            val intent = Intent(ACTION_SMS_RECEIVED).apply {
                putExtra("sender", sender)
                putExtra("content", content)
                setPackage(MODULE_PACKAGE_NAME)
            }
            context.sendBroadcast(intent)
            XposedBridge.log("SmsFwd: Broadcast sent successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("SmsFwd-FATAL: Failed to send broadcast.")
            XposedBridge.log(t)
        }
    }
}
