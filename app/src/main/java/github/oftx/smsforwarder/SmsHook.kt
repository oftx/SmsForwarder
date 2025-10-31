package github.oftx.smsforwarder

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Telephony
import android.util.Xml
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.xmlpull.v1.XmlPullParser
import java.io.File

class SmsHook : IXposedHookLoadPackage {

    companion object {
        private const val MODULE_PACKAGE_NAME = "github.oftx.smsforwarder"
        private const val ACTION_DEBUG_LOG = "github.oftx.smsforwarder.ACTION_DEBUG_LOG"

        private val DEFAULT_TARGET_PACKAGES = setOf("com.android.mms", "com.google.android.apps.messaging")
        private val DEFAULT_RECEIVER_CLASSES = setOf(
            "com.google.android.apps.messaging.shared.receiver.SmsDeliverReceiver",
            "com.google.android.apps.messaging.sms.SmsReceiver",
            "com.android.mms.transaction.SmsReceiver"
        )
    }

    data class HookConfig(val targetApp: String, val customClass: String)

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.appInfo == null || (lpparam.appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
            return
        }

        XposedHelpers.findAndHookMethod(Application::class.java, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val hookedAppContext = param.thisObject as Context
                onApplicationCreate(hookedAppContext, lpparam)
            }
        })
    }

    private fun getHookConfig(): HookConfig {
        var targetApp = ""
        var customClass = ""
        val prefsFile = File("/data/data/$MODULE_PACKAGE_NAME/shared_prefs/${MODULE_PACKAGE_NAME}_preferences.xml")

        if (!prefsFile.exists() || !prefsFile.canRead()) {
            XposedBridge.log("SmsFwd: Preference file does not exist or cannot be read at: ${prefsFile.path}")
            return HookConfig(targetApp, customClass)
        }

        try {
            prefsFile.inputStream().use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "string") {
                        val key = parser.getAttributeValue(null, "name")
                        if ("pref_hook_target_app" == key) {
                            targetApp = parser.nextText()
                        } else if ("pref_hook_custom_class" == key) {
                            customClass = parser.nextText()
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("SmsFwd: Error parsing preference file: ${e.message}")
        }
        XposedBridge.log("SmsFwd: Settings loaded from file: targetApp='$targetApp', customClass='$customClass'")
        return HookConfig(targetApp, customClass)
    }


    private fun onApplicationCreate(hookedAppContext: Context, lpparam: LoadPackageParam) {
        val config = getHookConfig()

        val isTarget = if (config.targetApp.isNotBlank()) {
            lpparam.packageName == config.targetApp
        } else {
            lpparam.packageName in DEFAULT_TARGET_PACKAGES
        }

        if (!isTarget) {
            return
        }

        XposedBridge.log("SmsFwd: Target package found: ${lpparam.packageName}. Initializing hooks...")
        performFunctionalHook(lpparam, config.customClass)
    }

    private fun performFunctionalHook(lpparam: LoadPackageParam, customHookClass: String) {
        val classesToTry = if (customHookClass.isNotBlank()) {
            XposedBridge.log("SmsFwd: Using custom hook class: $customHookClass")
            setOf(customHookClass)
        } else {
            XposedBridge.log("SmsFwd: No custom class set. Trying default classes...")
            DEFAULT_RECEIVER_CLASSES
        }

        var success = false
        for (className in classesToTry) {
            if (tryHookClass(className, lpparam.classLoader)) {
                success = true
                break
            }
        }

        if (!success) {
            XposedBridge.log("SmsFwd: WARNING: Could not find any suitable hook point.")
        }
    }

    private fun tryHookClass(className: String, classLoader: ClassLoader): Boolean {
        try {
            val targetClass = XposedHelpers.findClass(className, classLoader)
            listOfNotNull(targetClass, targetClass.superclass).distinct().forEach { hookableClass ->
                if (hookableClass == Object::class.java) return@forEach
                try {
                    XposedBridge.log("SmsFwd: Attempting to hook onReceive in '${hookableClass.name}'")
                    XposedHelpers.findAndHookMethod(hookableClass, "onReceive", Context::class.java, Intent::class.java, smsReceiverHook)
                    XposedBridge.log("SmsFwd: SUCCESS: Hooked ${hookableClass.name}.onReceive.")
                    return true
                } catch (e: NoSuchMethodError) {
                    // Method not found, continue loop
                }
            }
        } catch (e: XposedHelpers.ClassNotFoundError) {
            XposedBridge.log("SmsFwd: Class $className not found.")
        } catch (e: Throwable) {
            XposedBridge.log("SmsFwd: FATAL: Failed to hook hierarchy for $className: ${e.message}")
        }
        return false
    }

    private val smsReceiverHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val intent = param.args[1] as? Intent ?: return
            if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION && intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            val sender = messages.firstOrNull()?.originatingAddress ?: return
            val content = messages.joinToString("") { it.messageBody ?: "" }
            XposedBridge.log("SmsFwd: Intercepted SMS from: $sender. Sending broadcast...")
            sendSmsBroadcast(param.thisObject.javaClass.classLoader, sender, content)
        }
    }

    private fun sendSmsBroadcast(classLoader: ClassLoader?, sender: String, content: String) {
        try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader)
            val application = XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as Application
            val context = application.applicationContext
            val intent = Intent(SmsReceiver.ACTION_SMS_RECEIVED).apply {
                putExtra("sender", sender); putExtra("content", content); setPackage(MODULE_PACKAGE_NAME)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        } catch (t: Throwable) {
            XposedBridge.log("SmsFwd: FATAL: Failed to send broadcast: ${t.message}")
        }
    }
}
