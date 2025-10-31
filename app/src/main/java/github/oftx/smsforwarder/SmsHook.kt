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

        private val TARGET_PACKAGES = setOf(
            "com.android.mms",
            "com.google.android.apps.messaging"
        )

        private const val DEBOUNCE_WINDOW_MS = 2000
        @Volatile private var lastPduTimestamp: Long = 0
        private val recentPduSignatures = mutableSetOf<String>()
        private val lock = Any()
    }

    private var isHooked = false

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName in TARGET_PACKAGES && !isHooked) {
            XposedBridge.log("SmsFwd: Target package found: ${lpparam.packageName}. Preparing to hook.")
            hookSmsCreateFromPdu(lpparam.classLoader)
            isHooked = true
        }
    }

    private fun hookSmsCreateFromPdu(classLoader: ClassLoader) {
        try {
            XposedBridge.log("SmsFwd: Attempting to hook 'SmsMessage.createFromPdu'...")
            val createFromPduMethod = XposedHelpers.findMethodExact(
                SmsMessage::class.java, "createFromPdu", ByteArray::class.java, String::class.java
            )

            XposedBridge.hookMethod(createFromPduMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    synchronized(lock) {
                        val pdu = param.args[0] as ByteArray
                        val pduSignature = pdu.contentToString()
                        val now = System.currentTimeMillis()

                        if (now - lastPduTimestamp > DEBOUNCE_WINDOW_MS) {
                            recentPduSignatures.clear()
                        }
                        if (recentPduSignatures.contains(pduSignature)) {
                            XposedBridge.log("SmsFwd: Duplicate PDU detected (signature match). Suppressing.")
                            param.result = null
                            return
                        }
                        recentPduSignatures.add(pduSignature)
                        lastPduTimestamp = now
                        XposedBridge.log("SmsFwd: New unique PDU detected. Allowing method to proceed.")
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result == null) return
                    
                    val message = param.result as SmsMessage
                    val sender = message.originatingAddress
                    val content = message.messageBody

                    if (sender != null && content != null) {
                        XposedBridge.log("SmsFwd: HOOK TRIGGERED! SMS Intercepted from: $sender. Sending broadcast...")
                        sendSmsBroadcast(classLoader, sender, content)
                    }
                }
            })
            XposedBridge.log("SmsFwd: SUCCESS: Hooked 'SmsMessage.createFromPdu(byte[], String)'.")
        } catch (e: Throwable) {
            XposedBridge.log("SmsFwd: FATAL: Failed to hook 'SmsMessage.createFromPdu': ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun sendSmsBroadcast(classLoader: ClassLoader, sender: String, content: String) {
        try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader)
            val application = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as Application
            val context = application.applicationContext

            val intent = Intent(SmsReceiver.ACTION_SMS_RECEIVED).apply {
                putExtra("sender", sender)
                putExtra("content", content)
                setPackage(MODULE_PACKAGE_NAME)
                // --- START OF FINAL FIX ---
                // This flag is CRITICAL to wake up the app from a "force-stopped" state.
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                // --- END OF FINAL FIX ---
            }
            context.sendBroadcast(intent)
            XposedBridge.log("SmsFwd: Broadcast sent successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("SmsFwd-FATAL: Failed to send broadcast.")
            XposedBridge.log(t)
        }
    }
}
