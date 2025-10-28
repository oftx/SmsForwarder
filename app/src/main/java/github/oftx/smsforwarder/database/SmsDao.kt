package github.oftx.smsforwarder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Insert
    suspend fun insert(sms: SmsEntity): Long

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC") // Correct order: newest first
    fun getAllSms(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms_logs WHERE id = :smsId")
    suspend fun getSmsById(smsId: Long): SmsEntity?
}
