package github.oftx.smsforwarder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: LogEntity): Long

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<LogEntity>

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}
