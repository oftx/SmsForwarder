package github.oftx.smsforwarder

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
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
import kotlinx.coroutines.runBlocking

class SmsProvider : ContentProvider() {

    private lateinit var db: AppDatabase
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    companion object {
        private const val AUTHORITY = "github.oftx.smsforwarder.provider"
        private const val PATH_SMS = "sms"
        private const val PATH_LOG = "log"
        private const val CODE_SMS = 1
        private const val CODE_LOG = 2
    }

    init {
        uriMatcher.addURI(AUTHORITY, PATH_SMS, CODE_SMS)
        uriMatcher.addURI(AUTHORITY, PATH_LOG, CODE_LOG)
    }

    override fun onCreate(): Boolean {
        return try {
            db = AppDatabase.getDatabase(context!!)
            true
        } catch (e: Exception) {
            android.util.Log.e("SmsFwd-Provider", "FATAL: Failed to initialize database in provider.", e)
            false
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return try {
            when (uriMatcher.match(uri)) {
                CODE_SMS -> handleSmsInsert(uri, values)
                CODE_LOG -> handleLogInsert(uri, values)
                else -> throw IllegalArgumentException("Unknown URI: $uri")
            }
        } catch (e: Exception) {
            runBlocking {
                try {
                    db.logDao().insert(LogEntity(message = "[Provider-FATAL] Insert failed for URI $uri: ${e.message}"))
                } catch (dbError: Exception) {
                    android.util.Log.e("SmsFwd-Provider", "FATAL: Could not even write error log to DB.", dbError)
                }
            }
            null
        }
    }

    private fun handleSmsInsert(uri: Uri, values: ContentValues?): Uri {
        val sender = values?.getAsString("sender") ?: throw IllegalArgumentException("Sender is required")
        val content = values?.getAsString("content") ?: throw IllegalArgumentException("Content is required")
        val newSms = SmsEntity(sender = sender, content = content)

        val newSmsId = runBlocking {
            AppLogger.suspendLog(context!!, "[Provider] Received SMS from $sender, saving to DB.")
            val insertedId = db.smsDao().insert(newSms)
            enforceSmsLimit()
            insertedId
        }
        if (newSmsId > 0) {
            // Create a context that respects the in-app language setting
            val localizedContext = LocaleManager.updateBaseContext(context!!)
            val localizedTitle = localizedContext.getString(R.string.worker_new_sms_title, sender)
            scheduleForwarding(newSmsId, localizedTitle)
        }
        return ContentUris.withAppendedId(uri, newSmsId)
    }

    private fun handleLogInsert(uri: Uri, values: ContentValues?): Uri {
        val message = values?.getAsString("message") ?: throw IllegalArgumentException("Message is required")
        val newLog = LogEntity(message = message)

        val newLogId = runBlocking { db.logDao().insert(newLog) }
        return ContentUris.withAppendedId(uri, newLogId)
    }

    private fun scheduleForwarding(smsId: Long, localizedTitle: String) {
        runBlocking {
            val ruleDao = db.forwarderRuleDao()
            val jobDao = db.forwardingJobDao()
            val enabledRules = ruleDao.getAllEnabled()

            AppLogger.suspendLog(context!!, "[Provider] Found ${enabledRules.size} enabled rule(s). Scheduling jobs.")

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
                WorkManager.getInstance(context!!).enqueue(workRequest)
            }
        }
    }

    private suspend fun enforceSmsLimit() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context!!)
        val limitStr = sharedPrefs.getString("pref_sms_limit", "-1")
        val limit = limitStr?.toIntOrNull() ?: -1

        if (limit > 0) {
            AppLogger.suspendLog(context!!, "[Provider] Enforcing SMS limit of $limit records.")
            db.smsDao().enforceLimit(limit)
        }
    }

    override fun query(uri: Uri, p1: Array<String>?, p2: String?, p3: Array<String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun update(uri: Uri, p1: ContentValues?, p2: String?, p3: Array<String>?): Int = 0
    override fun delete(uri: Uri, p1: String?, p2: Array<String>?): Int = 0
}
