package github.oftx.smsforwarder.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
@Entity(tableName = "sms_logs")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    // The @Transient annotation was removed from here as it's not needed and caused a compile error.
    fun getFormattedTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}
