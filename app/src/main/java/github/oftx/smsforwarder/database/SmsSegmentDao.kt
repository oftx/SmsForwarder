package github.oftx.smsforwarder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SmsSegmentDao {
    @Insert
    suspend fun insert(segment: SmsSegmentEntity)

    @Query("SELECT * FROM sms_segments WHERE referenceNumber = :referenceNumber AND sender = :sender ORDER BY sequenceNumber ASC")
    suspend fun findByReferenceAndSender(referenceNumber: Int, sender: String): List<SmsSegmentEntity>

    @Query("DELETE FROM sms_segments WHERE referenceNumber = :referenceNumber AND sender = :sender")
    suspend fun deleteByReferenceAndSender(referenceNumber: Int, sender: String)

    @Query("DELETE FROM sms_segments WHERE timestamp < :timeoutTimestamp")
    suspend fun deleteOlderThan(timeoutTimestamp: Long)
}
