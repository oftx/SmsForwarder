package github.oftx.smsforwarder.ui.details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.database.ForwardingJobEntity
import github.oftx.smsforwarder.database.ForwardingJobWithRuleName
import github.oftx.smsforwarder.database.JobStatus
import github.oftx.smsforwarder.database.SmsEntity
import github.oftx.smsforwarder.worker.SmsForwardWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 组合 SMS 详情和其所有任务的数据模型
data class SmsDetailUiState(
    val sms: SmsEntity? = null,
    val jobs: List<ForwardingJobWithRuleName> = emptyList()
)

class SmsDetailViewModel(application: Application, private val smsId: Long) : AndroidViewModel(application) {

    private val smsDao = AppDatabase.getDatabase(application).smsDao()
    private val jobDao = AppDatabase.getDatabase(application).forwardingJobDao()

    val uiState: StateFlow<SmsDetailUiState> =
        combine(
            smsDao.getSmsByIdAsFlow(smsId), // 需要在 SmsDao 中添加此方法
            jobDao.getJobsWithRuleNameForSms(smsId)
        ) { sms, jobs ->
            SmsDetailUiState(sms, jobs)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SmsDetailUiState()
        )

    fun cancelJob(jobId: Long) {
        viewModelScope.launch {
            jobDao.updateStatus(jobId, JobStatus.CANCELLED.value)
        }
    }

    fun retryJob(job: ForwardingJobEntity) {
        viewModelScope.launch {
            // 1. 重置数据库中的任务状态为 PENDING
            jobDao.resetJobForRetry(job.id)

            // 2. 使用 WorkManager 立即调度一个新的工作
            val workRequest = OneTimeWorkRequestBuilder<SmsForwardWorker>()
                .setInputData(workDataOf(
                    "job_id" to job.id,
                    "localized_title" to "New message from: ${uiState.value.sms?.sender}"
                ))
                .build()
            WorkManager.getInstance(getApplication()).enqueue(workRequest)
        }
    }
}

class SmsDetailViewModelFactory(private val application: Application, private val smsId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmsDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SmsDetailViewModel(application, smsId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
