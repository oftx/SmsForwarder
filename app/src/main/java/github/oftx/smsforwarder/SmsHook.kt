package github.oftx.smsforwarder

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
    }

    private var isHooked = false

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName in TARGET_PACKAGES && !isHooked) {
            XposedBridge.log("SmsFwd: Target package found: ${lpparam.packageName}. Preparing to hook.")
            hookSmsReceiverOnReceive(lpparam)
            isHooked = true
        }
    }

    private fun hookSmsReceiverOnReceive(lpparam: LoadPackageParam) {
        // A set of potential SMS receiver classes, with our confirmed target at the top.
        val receiverClasses = setOf(
            "com.google.android.apps.messaging.shared.receiver.SmsDeliverReceiver",
            "com.google.android.apps.messaging.sms.SmsReceiver", // Fallback for other versions
            "com.android.mms.transaction.SmsReceiver"            // Fallback for AOSP
        )

        for (className in receiverClasses) {
            try {
                val receiverClass = XposedHelpers.findClass(className, lpparam.classLoader)
                
                // Based on debugging, the onReceive method is in the superclass.
                // We explicitly target the superclass for the hook.
                val hookTargetClass = receiverClass.superclass
                if (hookTargetClass == null || hookTargetClass == Object::class.java) {
                     continue // Skip if there's no valid superclass
                }

                XposedBridge.log("SmsFwd: Attempting to hook onReceive in '${hookTargetClass.name}' (superclass of '$className').")

                XposedHelpers.findAndHookMethod(
                    hookTargetClass, // Hook the parent class directly
                    "onReceive",
                    Context::class.java,
                    Intent::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args[1] as? Intent ?: return
                            val action = intent.action

                            // Check for the two primary SMS actions. Our debugging confirmed SMS_DELIVER_ACTION is used.
                            // We include SMS_RECEIVED_ACTION for broader compatibility.
                            if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
                                action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                                return
                            }

                            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                            if (messages.isNullOrEmpty()) {
                                return
                            }

                            val sender = messages[0].originatingAddress ?: return
                            val content = messages.joinToString("") { it.messageBody ?: "" }
                            
                            XposedBridge.log("SmsFwd: SUCCESS! HOOK TRIGGERED. Full SMS from: $sender. Sending broadcast...")
                            
                            // It works! Now we send the data to our app.
                            sendSmsBroadcast(lpparam.classLoader, sender, content)
                        }
                    }
                )
                XposedBridge.log("SmsFwd: SUCCESS: Hooked ${hookTargetClass.name}.onReceive.")
                break // Hook was successful, no need to try other classes.
            } catch (e: XposedHelpers.ClassNotFoundError) {
                // This is normal, just log that the class wasn't found.
                XposedBridge.log("SmsFwd: Class $className not found in ${lpparam.packageName}, skipping.")
            } catch (e: Throwable) {
                XposedBridge.log("SmsFwd: FATAL: Failed to hook a method in the hierarchy of ${className}: ${e.message}")
            }
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
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
            XposedBridge.log("SmsFwd: Broadcast sent successfully.")
        } catch (t: Throwable) {
            XposedBridge.log("SmsFwd-FATAL: Failed to send broadcast: ${t.message}")
            XposedBridge.log(t)
        }
    }
}
