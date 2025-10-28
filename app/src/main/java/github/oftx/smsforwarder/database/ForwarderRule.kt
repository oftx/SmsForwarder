package github.oftx.smsforwarder.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "forwarder_rules")
data class ForwarderRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "BARK"
    val configJson: String, // e.g., '{"key":"your_bark_key"}'
    val isEnabled: Boolean = true
) {
    companion object {
        const val TYPE_BARK = "BARK"
    }
}

@Serializable
data class BarkConfig(val key: String)
