package github.oftx.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.AppLogger
import github.oftx.smsforwarder.database.BackupData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ImportStrategy {
    MERGE, REPLACE
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val smsDao = db.smsDao()
    private val ruleDao = db.forwarderRuleDao()

    fun clearAllSms() {
        viewModelScope.launch {
            smsDao.deleteAll()
            AppLogger.log(getApplication(), "[Settings] All SMS messages have been cleared.")
        }
    }

    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val rules = ruleDao.getAllOnce()
        val messages = smsDao.getAllSmsOnce()
        val backupData = BackupData(
            exportTimestamp = System.currentTimeMillis(),
            rules = rules,
            messages = messages
        )
        Json.encodeToString(backupData)
    }

    suspend fun importData(jsonData: String, strategy: ImportStrategy): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupData = Json.decodeFromString<BackupData>(jsonData)

            if (strategy == ImportStrategy.REPLACE) {
                smsDao.deleteAll()
                ruleDao.deleteAll()
                AppLogger.suspendLog(getApplication(), "[Settings] Cleared existing data for import.")
            }

            // Insert all items from backup as new entries (with new auto-generated IDs)
            // This works for both REPLACE (after clearing) and MERGE (adding to existing)
            backupData.rules.forEach { ruleDao.insert(it.copy(id = 0)) }
            backupData.messages.forEach { smsDao.insert(it.copy(id = 0)) }

            AppLogger.suspendLog(getApplication(), "[Settings] Data imported successfully with strategy: $strategy.")
            true
        } catch (e: Exception) {
            AppLogger.suspendLog(getApplication(), "[Settings] Data import failed: ${e.message}")
            false
        }
    }
}

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
