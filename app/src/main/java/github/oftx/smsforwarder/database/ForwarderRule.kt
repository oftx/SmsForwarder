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
    val isEncrypted: Boolean = false,
    val algorithm: String? = ALGORITHM_AES_CBC, // e.g. "AES/CBC"
    val encryptionKey: String? = null,
    val iv: String? = null
) {
    companion object {
        const val ALGORITHM_AES_CBC = "AES/CBC"
        const val ALGORITHM_AES_ECB = "AES/ECB"
    }
}
