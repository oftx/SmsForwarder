package github.oftx.smsforwarder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardingJobDao {
    @Insert
    suspend fun insert(job: ForwardingJobEntity): Long

    @Query("SELECT * FROM forwarding_jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: Long): ForwardingJobEntity?

    @Query("SELECT * FROM forwarding_jobs WHERE status = :status")
    fun getJobsByStatus(status: String): Flow<List<ForwardingJobEntity>>
    
    @Query("UPDATE forwarding_jobs SET status = :newStatus WHERE id = :jobId")
    suspend fun updateStatus(jobId: Long, newStatus: String)

    @Query("UPDATE forwarding_jobs SET status = :newStatus, errorMessage = :errorMessage, attempts = attempts + 1, lastAttemptTimestamp = :timestamp WHERE id = :jobId")
    suspend fun updateStatusForFailure(jobId: Long, newStatus: String, errorMessage: String?, timestamp: Long)

    @Query("UPDATE forwarding_jobs SET status = 'CANCELLED' WHERE status = 'FAILED_RETRY'")
    suspend fun cancelAllRetryJobs()
}
