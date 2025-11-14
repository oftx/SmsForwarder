package github.oftx.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import github.oftx.smsforwarder.database.ForwardingJobEntity
import github.oftx.smsforwarder.database.JobStatus
import github.oftx.smsforwarder.database.LogEntity
import github.oftx.smsforwarder.database.SmsEntity
import github.oftx.smsforwarder.ui.LocaleManager
import github.oftx.smsforwarder.worker.SmsForwardWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Data class to hold information about an ongoing message concatenation
private data class MessageSession(
    val content: StringBuilder = StringBuilder(),
    var lastTimestamp: Long = 0L
)

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_RECEIVED = "github.oftx.smsforwarder.ACTION_SMS_RECEIVED"
        const val ACTION_LOG_RECEIVED = "github.oftx.smsforwarder.ACTION_LOG_RECEIVED"

        // Use a companion object to hold the state, making it survive receiver recreation
        private val messageBuffer = HashMap<String, MessageSession>()
        private val handler = Handler(Looper.getMainLooper())
        private const val CONCAT_TIMEOUT_MS = 100L // 0.1 seconds
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_SMS_RECEIVED -> {
                val sender = intent.getStringExtra("sender")
                val content = intent.getStringExtra("content")
                if (sender != null && content != null) {
                    // All processing is done on the main thread with a handler to ensure thread safety
                    handler.post {
                        handleSms(context, sender, content)
                        pendingResult.finish()
                    }
                } else {
                    pendingResult.finish()
                }
            }
            ACTION_LOG_RECEIVED -> {
                val message = intent.getStringExtra("message")
                if (message != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        handleLog(context, message)
                        pendingResult.finish()
                    }
                } else {
                    pendingResult.finish()
                }
            }
            else -> pendingResult.finish()
        }
    }

    private fun handleSms(context: Context, sender: String, content: String) {
        val currentTime = System.currentTimeMillis()
        var session = messageBuffer[sender]

        // Cancel any pending commit for this sender, as a new part has arrived
        handler.removeCallbacksAndMessages(sender) // Use sender as a unique token

        if (session != null && (currentTime - session.lastTimestamp) < CONCAT_TIMEOUT_MS) {
            // This is likely a subsequent part of a long SMS
            session.content.append(content)
            session.lastTimestamp = currentTime
            AppLogger.log(context, "[Receiver] Appended SMS part from $sender. New length: ${session.content.length}")
        } else {
            // This is a new message (or a timeout occurred)
            // 1. Commit the previous message from this sender, if it exists
            if (session != null) {
                AppLogger.log(context, "[Receiver] Timeout or new message. Committing previous message from $sender.")
                commitMessage(context, sender, session)
            }
            // 2. Start a new session for the new message
            session = MessageSession(StringBuilder(content), currentTime)
            messageBuffer[sender] = session
            AppLogger.log(context, "[Receiver] Started new SMS session for $sender.")
        }

        // Schedule a delayed commit for the current message.
        // If another part arrives within the timeout, this will be cancelled.
        val commitTask = Runnable {
            val sessionToCommit = messageBuffer.remove(sender)
            if (sessionToCommit != null) {
                AppLogger.log(context, "[Receiver] Delayed commit triggered for $sender.")
                commitMessage(context, sender, sessionToCommit)
            }
        }
        // Post the delayed task with the sender as a token to identify it for cancellation
        handler.postDelayed(commitTask, CONCAT_TIMEOUT_MS)
    }

    private fun commitMessage(context: Context, sender: String, session: MessageSession) {
        val fullContent = session.content.toString()
        if (fullContent.isEmpty()) return

        AppLogger.log(context, "[Receiver] Committing full message from $sender. Content: \"$fullContent\"")

        // Use a coroutine for database and network operations
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val newSms = SmsEntity(sender = sender, content = fullContent)
            val newSmsId = db.smsDao().insert(newSms)
            enforceSmsLimit(context, db)

            if (newSmsId > 0) {
                val localizedContext = LocaleManager.updateBaseContext(context)
                val localizedTitle = localizedContext.getString(R.string.worker_new_sms_title, sender)
                scheduleForwarding(context, db, newSmsId, localizedTitle)
            }
        }
    }

    private suspend fun handleLog(context: Context, message: String) {
        val db = AppDatabase.getDatabase(context)
        db.logDao().insert(LogEntity(message = message))
    }

    private suspend fun scheduleForwarding(context: Context, db: AppDatabase, smsId: Long, localizedTitle: String) {
        val enabledRules = db.forwarderRuleDao().getAllEnabled()
        AppLogger.suspendLog(context, "[Receiver] Found ${enabledRules.size} enabled rule(s). Scheduling jobs for SMS ID $smsId.")

        for (rule in enabledRules) {
            val newJob = ForwardingJobEntity(
                smsId = smsId, ruleId = rule.id, status = JobStatus.PENDING.value, // <<< FIX: PENDEND -> PENDING
                lastAttemptTimestamp = System.currentTimeMillis(), errorMessage = null
            )
            val jobId = db.forwardingJobDao().insert(newJob)
            val workRequest = OneTimeWorkRequestBuilder<SmsForwardWorker>()
                .setInputData(workDataOf("job_id" to jobId, "localized_title" to localizedTitle))
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    private suspend fun enforceSmsLimit(context: Context, db: AppDatabase) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val limitStr = sharedPrefs.getString("pref_sms_limit", "-1")
        val limit = limitStr?.toIntOrNull() ?: -1

        if (limit > 0) {
            val count = db.smsDao().getCount()
            if (count > limit) {
                AppLogger.suspendLog(context, "[Receiver] Enforcing SMS limit of $limit records.")
                db.smsDao().enforceLimit(limit)
            }
        }
    }
}
