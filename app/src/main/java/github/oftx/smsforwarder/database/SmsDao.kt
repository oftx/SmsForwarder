package github.oftx.smsforwarder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sms: SmsEntity): Long

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllSms(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms_logs")
    suspend fun getAllSmsOnce(): List<SmsEntity>

    @Query("SELECT * FROM sms_logs WHERE id = :smsId")
    suspend fun getSmsById(smsId: Long): SmsEntity?
    
    // 为详情弹窗提供 Flow 版本的查询
    @Query("SELECT * FROM sms_logs WHERE id = :smsId")
    fun getSmsByIdAsFlow(smsId: Long): Flow<SmsEntity?>

    @Query("DELETE FROM sms_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM sms_logs WHERE id NOT IN (SELECT id FROM sms_logs ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun enforceLimit(limit: Int)
    
    // <<< FIX: 添加这个缺失的方法 >>>
    @Query("SELECT COUNT(id) FROM sms_logs")
    suspend fun getCount(): Int
}
