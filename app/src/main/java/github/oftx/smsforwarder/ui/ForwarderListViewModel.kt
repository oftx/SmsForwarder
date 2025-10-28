package github.oftx.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.database.ForwarderRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ForwarderListViewModel(application: Application) : AndroidViewModel(application) {

    private val ruleDao = AppDatabase.getDatabase(application).forwarderRuleDao()

    val rules: StateFlow<List<ForwarderRule>> = ruleDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun updateRule(rule: ForwarderRule) {
        viewModelScope.launch {
            ruleDao.update(rule)
        }
    }

    fun deleteRule(rule: ForwarderRule) {
        viewModelScope.launch {
            ruleDao.delete(rule)
        }
    }
}

class ForwarderListViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ForwarderListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ForwarderListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
