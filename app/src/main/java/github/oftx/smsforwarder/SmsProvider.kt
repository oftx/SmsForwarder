package github.oftx.smsforwarder

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import github.oftx.smsforwarder.database.ForwardingJobEntity
import github.oftx.smsforwarder.database.JobStatus
import github.oftx.smsforwarder.database.SmsEntity
import github.oftx.smsforwarder.worker.SmsForwardWorker
import kotlinx.coroutines.runBlocking

class SmsProvider : ContentProvider() {

    private lateinit var db: AppDatabase

    override fun onCreate(): Boolean {
        // The context is guaranteed to be non-null here.
        db = AppDatabase.getDatabase(context!!)
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val sender = values?.getAsString("sender") ?: return null
        val content = values.getAsString("content") ?: return null
        val newSms = SmsEntity(sender = sender, content = content)

        runBlocking {
            // Insert the SMS into the database. The UI will reactively update
            // because it is observing the database via a Flow.
            val newSmsId = db.smsDao().insert(newSms)
            if (newSmsId > 0) {
                scheduleForwarding(newSmsId)
            }
        }
        
        // Return the original URI to signify success
        return uri
    }

    private fun scheduleForwarding(smsId: Long) {
        val ruleDao = db.forwarderRuleDao()
        val jobDao = db.forwardingJobDao()

        runBlocking {
            val enabledRules = ruleDao.getAllEnabled()
            for (rule in enabledRules) {
                val newJob = ForwardingJobEntity(
                    smsId = smsId,
                    ruleId = rule.id,
                    status = JobStatus.PENDING.value,
                    lastAttemptTimestamp = System.currentTimeMillis(),
                    errorMessage = null
                )
                val jobId = jobDao.insert(newJob)

                val workRequest = OneTimeWorkRequestBuilder<SmsForwardWorker>()
                    .setInputData(workDataOf("job_id" to jobId))
                    .build()
                WorkManager.getInstance(context!!).enqueue(workRequest)
            }
        }
    }

    // --- Unused ContentProvider methods ---
    override fun query(uri: Uri, p1: Array<String>?, p2: String?, p3: Array<String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun update(uri: Uri, p1: ContentValues?, p2: String?, p3: Array<String>?): Int = 0
    override fun delete(uri: Uri, p1: String?, p2: Array<String>?): Int = 0
}
