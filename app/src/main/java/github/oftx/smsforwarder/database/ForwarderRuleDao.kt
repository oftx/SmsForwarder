package github.oftx.smsforwarder.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwarderRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ForwarderRule)

    @Update
    suspend fun update(rule: ForwarderRule)

    @Delete
    suspend fun delete(rule: ForwarderRule)

    @Query("DELETE FROM forwarder_rules")
    suspend fun deleteAll()

    @Query("SELECT * FROM forwarder_rules ORDER BY id DESC")
    fun getAll(): Flow<List<ForwarderRule>>

    @Query("SELECT * FROM forwarder_rules")
    suspend fun getAllOnce(): List<ForwarderRule>

    @Query("SELECT * FROM forwarder_rules WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<ForwarderRule>
    
    @Query("SELECT * FROM forwarder_rules WHERE id = :ruleId")
    suspend fun getRuleById(ruleId: Long): ForwarderRule?
}
