package github.oftx.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.database.ForwardingJobWithRuleName
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
            jobDao.getAllJobsWithRuleNames().map { list -> list.groupBy { it.job.smsId } } // Group by smsId
        ) { smsList, jobsMap ->
            smsList.map { sms ->
                val jobsForSms = jobsMap[sms.id] ?: emptyList()
                ListItem.Sms(
                    SmsItem(
                        id = sms.id,
                        sender = sms.sender,
                        content = sms.content,
                        timestamp = sms.timestamp,
                        jobs = jobsForSms // Pass raw data
                    )
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )
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
