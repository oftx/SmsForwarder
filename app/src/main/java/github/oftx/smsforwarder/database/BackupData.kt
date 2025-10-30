package github.oftx.smsforwarder.database

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportTimestamp: Long,
    val rules: List<ForwarderRule>,
    val messages: List<SmsEntity>
)
