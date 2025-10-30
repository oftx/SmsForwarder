package github.oftx.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.database.ForwardingJobWithRuleName
import github.oftx.smsforwarder.database.JobStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val smsDao = AppDatabase.getDatabase(application).smsDao()
    private val jobDao = AppDatabase.getDatabase(application).forwardingJobDao()

    val listItems: StateFlow<List<ListItem.Sms>> =
        combine(
            smsDao.getAllSms(),
            jobDao.getAllJobsWithRuleNames().map { list -> list.groupBy { it.job.smsId } } // 转为 Map
        ) { smsList, jobsMap ->
            smsList.map { sms ->
                val jobsForSms = jobsMap[sms.id] ?: emptyList()
                ListItem.Sms(
                    SmsItem(
                        id = sms.id,
                        sender = sms.sender,
                        content = sms.content,
                        timestamp = sms.timestamp,
                        statusSummary = formatStatusSummary(jobsForSms) // 格式化状态摘要
                    )
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private fun formatStatusSummary(jobs: List<ForwardingJobWithRuleName>): String {
        if (jobs.isEmpty()) return "" // 对于旧短信，没有任务，不显示状态

        val total = jobs.size
        val successCount = jobs.count { it.job.status == JobStatus.SUCCESS.value }
        val cancelledCount = jobs.count { it.job.status == JobStatus.CANCELLED.value }
        val context = getApplication<Application>().applicationContext

        // --- START OF REFACTORED LOGIC ---

        // 1. 处理所有任务都处于同一种最终状态的简单情况
        if (successCount == total) {
            return context.getString(R.string.status_summary_all_sent)
        }
        if (cancelledCount == total) {
            return context.getString(R.string.status_summary_all_cancelled)
        }

        // 2. 处理混合状态：构建每个任务的状态部分
        val hasPendingOrRetry = jobs.any { it.job.status in listOf(JobStatus.PENDING.value, JobStatus.FAILED_RETRY.value) }

        val statusParts = jobs.map {
            val marker = when(it.job.status) {
                JobStatus.SUCCESS.value -> "(✓)"
                JobStatus.CANCELLED.value -> context.getString(R.string.status_summary_marker_cancelled) // e.g., "(Cancelled)"
                JobStatus.FAILED_RETRY.value, JobStatus.FAILED_PERMANENTLY.value -> "(✘)"
                else -> "" // PENDING or other states get no marker
            }
            it.ruleName + marker
        }

        // 3. 根据是否存在待处理任务来确定前缀
        val prefix = if (hasPendingOrRetry) {
            context.getString(R.string.status_summary_retrying)
        } else {
            // 如果没有待重试任务，但不是全部成功，说明是混合状态（如部分成功、部分取消）
            context.getString(R.string.status_summary_forwarding_to)
        }

        return prefix + statusParts.joinToString("、")
        // --- END OF REFACTORED LOGIC ---
    }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
