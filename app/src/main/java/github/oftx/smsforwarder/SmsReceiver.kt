package github.oftx.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_RECEIVED = "github.oftx.smsforwarder.ACTION_SMS_RECEIVED"
        const val ACTION_LOG_RECEIVED = "github.oftx.smsforwarder.ACTION_LOG_RECEIVED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        when (intent.action) {
            ACTION_SMS_RECEIVED -> {
                val sender = intent.getStringExtra("sender")
                val content = intent.getStringExtra("content")
                if (sender != null && content != null) {
                    scope.launch {
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
                    scope.launch {
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

    private suspend fun handleSms(context: Context, sender: String, content: String) {
        val db = AppDatabase.getDatabase(context)
        AppLogger.suspendLog(context, "[Receiver] Received SMS from $sender via Broadcast, saving to DB.")
        
        val newSms = SmsEntity(sender = sender, content = content)
        val newSmsId = db.smsDao().insert(newSms)
        enforceSmsLimit(context, db)

        if (newSmsId > 0) {
            val localizedContext = LocaleManager.updateBaseContext(context)
            val localizedTitle = localizedContext.getString(R.string.worker_new_sms_title, sender)
            scheduleForwarding(context, db, newSmsId, localizedTitle)
        }
    }

    private suspend fun handleLog(context: Context, message: String) {
        val db = AppDatabase.getDatabase(context)
        db.logDao().insert(LogEntity(message = message))
    }

    private suspend fun scheduleForwarding(context: Context, db: AppDatabase, smsId: Long, localizedTitle: String) {
        val enabledRules = db.forwarderRuleDao().getAllEnabled()
        AppLogger.suspendLog(context, "[Receiver] Found ${enabledRules.size} enabled rule(s). Scheduling jobs.")

        for (rule in enabledRules) {
            val newJob = ForwardingJobEntity(
                smsId = smsId, ruleId = rule.id, status = JobStatus.PENDING.value,
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
            AppLogger.suspendLog(context, "[Receiver] Enforcing SMS limit of $limit records.")
            db.smsDao().enforceLimit(limit)
        }
    }
}
