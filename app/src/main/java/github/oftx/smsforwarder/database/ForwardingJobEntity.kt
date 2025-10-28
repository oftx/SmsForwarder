package github.oftx.smsforwarder.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forwarding_jobs")
data class ForwardingJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsId: Long,
    val ruleId: Long,
    var status: String,
    var attempts: Int = 0,
    var lastAttemptTimestamp: Long,
    var errorMessage: String?
)

enum class JobStatus(val value: String) {
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED_RETRY("FAILED_RETRY"),
    CANCELLED("CANCELLED"),
    FAILED_PERMANENTLY("FAILED_PERMANENTLY")
}
