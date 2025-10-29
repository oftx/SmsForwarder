package github.oftx.smsforwarder.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "forwarder_rules")
data class ForwarderRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val configJson: String,
    val isEnabled: Boolean = true
) {
    companion object {
        const val TYPE_BARK = "BARK"
    }
}

@Serializable
data class BarkConfig(
    val key: String,
    val serverUrl: String? = null,
    val isEncrypted: Boolean = false,
    val algorithm: String? = ALGORITHM_AES_128,
    val mode: String? = MODE_CBC,
    val encryptionKey: String? = null,
    val iv: String? = null
) {
    companion object {
        const val ALGORITHM_AES_128 = "AES-128"
        const val ALGORITHM_AES_192 = "AES-192"
        const val ALGORITHM_AES_256 = "AES-256"

        const val MODE_CBC = "CBC"
        const val MODE_ECB = "ECB"
        const val MODE_GCM = "GCM"
    }
}
