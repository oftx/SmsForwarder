package github.oftx.smsforwarder.ui

import android.app.Application
import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
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

    private fun formatStatusSummary(jobs: List<ForwardingJobWithRuleName>): CharSequence {
        if (jobs.isEmpty()) return ""

        val total = jobs.size
        val successCount = jobs.count { it.job.status == JobStatus.SUCCESS.value }
        val cancelledCount = jobs.count { it.job.status == JobStatus.CANCELLED.value }
        val context = getApplication<Application>().applicationContext

        if (successCount == total) {
            return context.getString(R.string.status_summary_all_sent)
        }
        if (cancelledCount == total && total > 0) {
             // 如果全部都取消了，也给一个整体状态
            return context.getString(R.string.status_summary_all_cancelled)
        }

        val summaryBuilder = SpannableStringBuilder()
        val hasPendingOrRetry = jobs.any { it.job.status in listOf(JobStatus.PENDING.value, JobStatus.FAILED_RETRY.value) }

        jobs.forEachIndexed { index, item ->
            val start = summaryBuilder.length
            summaryBuilder.append(item.ruleName)
            
            val marker = when(item.job.status) {
                JobStatus.SUCCESS.value -> "(✓)"
                JobStatus.FAILED_RETRY.value, JobStatus.FAILED_PERMANENTLY.value -> "(✘)"
                else -> ""
            }
            summaryBuilder.append(marker)

            if (item.job.status == JobStatus.CANCELLED.value) {
                summaryBuilder.setSpan(StrikethroughSpan(), start, summaryBuilder.length, 0)
            }

            if (index < jobs.size - 1) {
                summaryBuilder.append("、")
            }
        }

        if (hasPendingOrRetry) {
            val prefix = context.getString(R.string.status_summary_retrying)
            summaryBuilder.insert(0, prefix)
        }

        return summaryBuilder
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
