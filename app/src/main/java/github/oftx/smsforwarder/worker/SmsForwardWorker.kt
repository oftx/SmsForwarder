package github.oftx.smsforwarder.worker

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.AppLogger
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.database.BarkConfig
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.database.JobStatus
import github.oftx.smsforwarder.database.SmsEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class BarkPayload(val body: String, val title: String, val group: String = "SmsForwarder")

class SmsForwardWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val db = AppDatabase.getDatabase(appContext)
    private val smsDao = db.smsDao()
    private val ruleDao = db.forwarderRuleDao()
    private val jobDao = db.forwardingJobDao()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val jobId = inputData.getLong("job_id", -1L)
        if (jobId == -1L) {
            AppLogger.suspendLog(appContext, "[Worker] FATAL: Worker started with invalid job ID.")
            return Result.failure()
        }

        AppLogger.suspendLog(appContext, "[Worker] Starting job $jobId.")

        val job = jobDao.getJobById(jobId) ?: run {
            AppLogger.suspendLog(appContext, "[Worker] Job $jobId not found in DB (possibly deleted). Stopping.")
            return Result.success()
        }

        // --- START OF FIX ---
        // 在执行任何操作之前，检查任务是否已被用户手动取消。
        if (job.status == JobStatus.CANCELLED.value) {
            AppLogger.suspendLog(appContext, "[Worker] Job $jobId was cancelled by the user. Stopping execution.")
            // 返回 success 告知 WorkManager 此任务已完成，不要再安排重试。
            return Result.success()
        }
        // --- END OF FIX ---

        val rule = ruleDao.getRuleById(job.ruleId) ?: run {
            AppLogger.suspendLog(appContext, "[Worker] Rule ${job.ruleId} for job $jobId not found. Failing.")
            jobDao.updateStatusForFailure(jobId, JobStatus.FAILED_PERMANENTLY.value, "Rule not found", System.currentTimeMillis())
            return Result.failure()
        }
        val sms = smsDao.getSmsById(job.smsId) ?: run {
            AppLogger.suspendLog(appContext, "[Worker] SMS ${job.smsId} for job $jobId not found. Failing.")
            jobDao.updateStatusForFailure(jobId, JobStatus.FAILED_PERMANENTLY.value, "SMS not found", System.currentTimeMillis())
            return Result.failure()
        }

        val localizedTitle = inputData.getString("localized_title")
        val finalTitle = if (!localizedTitle.isNullOrBlank()) {
            localizedTitle
        } else {
            // Fallback in case the title wasn't passed, though it should be.
            appContext.getString(R.string.worker_new_sms_title, sms.sender)
        }

        return try {
            when (rule.type) {
                ForwarderRule.TYPE_BARK -> forwardToBark(rule, sms, finalTitle)
                else -> throw IllegalArgumentException("Unsupported rule type: ${rule.type}")
            }
            AppLogger.suspendLog(appContext, "[Worker] Job $jobId to '${rule.name}' completed successfully.")
            jobDao.updateStatus(jobId, JobStatus.SUCCESS.value)
            Result.success()
        } catch (e: Exception) {
            handleFailure(jobId, e.message ?: "Unknown error", rule.name)
        }
    }

    private fun forwardToBark(rule: ForwarderRule, sms: SmsEntity, title: String) {
        val config = Json.decodeFromString<BarkConfig>(rule.configJson)
        if (config.key.isBlank()) throw Exception("Bark key is empty")

        val baseUrl = if (config.serverUrl.isNullOrBlank()) {
            "https://api.day.app"
        } else {
            config.serverUrl.trim().removeSuffix("/")
        }
        val url = "$baseUrl/${config.key}"

        val payload = BarkPayload(body = sms.content, title = title)
        val payloadJson = Json.encodeToString(payload)

        val request: Request

        if (config.isEncrypted && !config.encryptionKey.isNullOrBlank() && !config.mode.isNullOrBlank()) {
            val iv = config.iv.orEmpty()
            val ciphertext = CryptoUtils.encrypt(
                payload = payloadJson, mode = config.mode, key = config.encryptionKey, iv = iv
            )
            val formBodyBuilder = FormBody.Builder().add("ciphertext", ciphertext)
            if (iv.isNotEmpty() && (config.mode == BarkConfig.MODE_CBC || config.mode == BarkConfig.MODE_GCM)) {
                 formBodyBuilder.add("iv", iv)
            }
            request = Request.Builder().url(url).post(formBodyBuilder.build()).build()
        } else {
            request = Request.Builder().url(url)
                .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Bark request failed: ${response.code} ${response.message} - ${response.body?.string()}")
        }
    }

    private suspend fun handleFailure(jobId: Long, errorMessage: String, ruleName: String): Result {
        AppLogger.suspendLog(appContext, "[Worker] Job $jobId to '$ruleName' FAILED. Reason: $errorMessage")
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        val shouldRetry = sharedPrefs.getBoolean("pref_retry_on_fail", true)

        val newStatus = if (shouldRetry) JobStatus.FAILED_RETRY.value else JobStatus.FAILED_PERMANENTLY.value
        jobDao.updateStatusForFailure(jobId, newStatus, errorMessage, System.currentTimeMillis())

        if (shouldRetry) {
            AppLogger.suspendLog(appContext, "[Worker] Scheduling retry for job $jobId.")
            return Result.retry()
        } else {
            AppLogger.suspendLog(appContext, "[Worker] Job $jobId will not be retried.")
            return Result.failure()
        }
    }
}
