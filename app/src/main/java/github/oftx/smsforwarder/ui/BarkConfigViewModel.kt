package github.oftx.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.database.ForwarderRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BarkConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val ruleDao = AppDatabase.getDatabase(application).forwarderRuleDao()

    private val _existingRule = MutableStateFlow<ForwarderRule?>(null)
    val existingRule = _existingRule.asStateFlow()

    fun loadRule(ruleId: Long) {
        if (ruleId == -1L) return
        viewModelScope.launch {
            _existingRule.value = ruleDao.getRuleById(ruleId)
        }
    }

    fun saveRule(rule: ForwarderRule, isUpdating: Boolean) {
        viewModelScope.launch {
            if (isUpdating) {
                ruleDao.update(rule)
            } else {
                ruleDao.insert(rule)
            }
        }
    }
}

class BarkConfigViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BarkConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BarkConfigViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
