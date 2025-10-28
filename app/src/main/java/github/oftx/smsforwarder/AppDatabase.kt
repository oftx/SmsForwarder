package github.oftx.smsforwarder

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import github.oftx.smsforwarder.database.ForwarderRule
import github.oftx.smsforwarder.database.ForwarderRuleDao
import github.oftx.smsforwarder.database.ForwardingJobDao
import github.oftx.smsforwarder.database.ForwardingJobEntity
import github.oftx.smsforwarder.database.LogDao
import github.oftx.smsforwarder.database.LogEntity
import github.oftx.smsforwarder.database.SmsDao
import github.oftx.smsforwarder.database.SmsEntity

@Database(
    entities = [SmsEntity::class, ForwarderRule::class, ForwardingJobEntity::class, LogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao
    abstract fun forwarderRuleDao(): ForwarderRuleDao
    abstract fun forwardingJobDao(): ForwardingJobDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Add new migration
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `forwarder_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `configJson` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL DEFAULT 1)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `forwarding_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `smsId` INTEGER NOT NULL, `ruleId` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL DEFAULT 0, `lastAttemptTimestamp` INTEGER NOT NULL, `errorMessage` TEXT)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `message` TEXT NOT NULL)")
            }
        }
    }
}
