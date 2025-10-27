package github.oftx.smsforwarder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SmsDao {
    // `suspend` 关键字表示这是一个挂起函数，必须在协程中调用，避免阻塞主线程
    @Insert
    suspend fun insert(sms: SmsEntity)

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC") // 按时间倒序查询
    suspend fun getAllSms(): List<SmsEntity>
}