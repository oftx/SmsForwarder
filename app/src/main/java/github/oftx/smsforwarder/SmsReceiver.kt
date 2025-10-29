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
import github.oftx.smsforwarder.database.SmsEntity
import github.oftx.smsforwarder.ui.LocaleManager
import github.oftx.smsforwarder.worker.SmsForwardWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "github.oftx.smsforwarder.ACTION_SMS_RECEIVED") {
            val sender = intent.getStringExtra("sender")
            val content = intent.getStringExtra("content")

            if (sender == null || content == null) {
                AppLogger.log(context, "[SmsReceiver] Received broadcast with missing data.")
                return
            }

            scope.launch {
                handleReceivedSms(context, sender, content)
            }
        }
    }

    private suspend fun handleReceivedSms(context: Context, sender: String, content: String) {
        val db = AppDatabase.getDatabase(context)
        val newSms = SmsEntity(sender = sender, content = content)

        AppLogger.suspendLog(context, "[SmsReceiver] Received SMS from $sender via broadcast, saving to DB.")
        val newSmsId = db.smsDao().insert(newSms)
        enforceSmsLimit(context, db)

        if (newSmsId > 0) {
            // Create a context that respects the in-app language setting
            val localizedContext = LocaleManager.updateBaseContext(context)
            val localizedTitle = localizedContext.getString(R.string.worker_new_sms_title, sender)
            scheduleForwarding(context, db, newSmsId, localizedTitle)
        }
    }

    private suspend fun enforceSmsLimit(context: Context, db: AppDatabase) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val limitStr = sharedPrefs.getString("pref_sms_limit", "-1")
        val limit = limitStr?.toIntOrNull() ?: -1

        if (limit > 0) {
            AppLogger.suspendLog(context, "[SmsReceiver] Enforcing SMS limit of $limit records.")
            db.smsDao().enforceLimit(limit)
        }
    }

    private suspend fun scheduleForwarding(context: Context, db: AppDatabase, smsId: Long, localizedTitle: String) {
        val ruleDao = db.forwarderRuleDao()
        val jobDao = db.forwardingJobDao()
        val enabledRules = ruleDao.getAllEnabled()

        AppLogger.suspendLog(context, "[SmsReceiver] Found ${enabledRules.size} enabled rule(s). Scheduling jobs.")

        for (rule in enabledRules) {
            val newJob = ForwardingJobEntity(
                smsId = smsId, ruleId = rule.id, status = JobStatus.PENDING.value,
                lastAttemptTimestamp = System.currentTimeMillis(), errorMessage = null
            )
            val jobId = jobDao.insert(newJob)
            val workRequest = OneTimeWorkRequestBuilder<SmsForwardWorker>()
                .setInputData(workDataOf(
                    "job_id" to jobId,
                    "localized_title" to localizedTitle
                )).build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
