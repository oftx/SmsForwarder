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

    // 新增：高效获取所有任务及其关联的规则名称, 以 smsId 为 key 分组
    @Query("""
        SELECT r.name as ruleName, j.* FROM forwarding_jobs as j
        INNER JOIN forwarder_rules as r ON j.ruleId = r.id
    """)
    fun getAllJobsWithRuleNames(): Flow<List<ForwardingJobWithRuleName>>

    // 新增：高效获取指定短信的所有任务及其关联的规则名称
    @Query("""
        SELECT r.name as ruleName, j.* FROM forwarding_jobs as j
        INNER JOIN forwarder_rules as r ON j.ruleId = r.id
        WHERE j.smsId = :smsId
    """)
    fun getJobsWithRuleNameForSms(smsId: Long): Flow<List<ForwardingJobWithRuleName>>

    @Query("SELECT * FROM forwarding_jobs WHERE status = :status")
    fun getJobsByStatus(status: String): Flow<List<ForwardingJobEntity>>
    
    @Query("UPDATE forwarding_jobs SET status = :newStatus WHERE id = :jobId")
    suspend fun updateStatus(jobId: Long, newStatus: String)

    @Query("UPDATE forwarding_jobs SET status = :newStatus, errorMessage = :errorMessage, attempts = attempts + 1, lastAttemptTimestamp = :timestamp WHERE id = :jobId")
    suspend fun updateStatusForFailure(jobId: Long, newStatus: String, errorMessage: String?, timestamp: Long)

    // 新增：用于“立即重试”功能，重置任务状态
    @Query("UPDATE forwarding_jobs SET status = 'PENDING', attempts = 0, errorMessage = null WHERE id = :jobId")
    suspend fun resetJobForRetry(jobId: Long)

    @Query("UPDATE forwarding_jobs SET status = 'CANCELLED' WHERE status = 'FAILED_RETRY'")
    suspend fun cancelAllRetryJobs()
}
