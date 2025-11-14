package github.oftx.smsforwarder.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_segments",
    indices = [Index(value = ["referenceNumber", "sender"], name = "idx_ref_sender")]
)
data class SmsSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val referenceNumber: Int,
    val totalParts: Int,
    val sequenceNumber: Int,
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
