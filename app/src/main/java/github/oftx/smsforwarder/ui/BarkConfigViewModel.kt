package github.oftx.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import github.oftx.smsforwarder.AppDatabase
import github.oftx.smsforwarder.database.BarkConfig
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.worker.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

class BarkConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val ruleDao = AppDatabase.getDatabase(application).forwarderRuleDao()

    private val _existingRule = MutableStateFlow<ForwarderRule?>(null)
    val existingRule = _existingRule.asStateFlow()

    private val _testResultFlow = MutableSharedFlow<String>()
    val testResultFlow = _testResultFlow.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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

    fun testBarkConnection(config: BarkConfig, testTitle: String, testBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (config.key.isBlank()) throw Exception("Bark key is empty")

                val baseUrl = if (config.serverUrl.isNullOrBlank()) {
                    "https://api.day.app"
                } else {
                    config.serverUrl.trim().removeSuffix("/")
                }
                val url = "$baseUrl/${config.key}"

                val payload = BarkPayload(body = testBody, title = testTitle)
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

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Request failed: ${response.code} ${response.message} - ${response.body?.string()}")
                    }
                    _testResultFlow.emit("Success")
                }
            } catch (e: Exception) {
                _testResultFlow.emit("Error: ${e.message}")
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
