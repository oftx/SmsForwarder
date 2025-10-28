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
import github.oftx.smsforwarder.database.SmsDao
import github.oftx.smsforwarder.database.SmsEntity

@Database(
    entities = [SmsEntity::class, ForwarderRule::class, ForwardingJobEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao
    abstract fun forwarderRuleDao(): ForwarderRuleDao
    abstract fun forwardingJobDao(): ForwardingJobDao

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
                .addMigrations(MIGRATION_1_2) // Add migration
                .build()
                INSTANCE = instance
                instance
            }
        }

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create forwarder_rules table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `forwarder_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `configJson` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                // Create forwarding_jobs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `forwarding_jobs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `smsId` INTEGER NOT NULL,
                        `ruleId` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL DEFAULT 0,
                        `lastAttemptTimestamp` INTEGER NOT NULL,
                        `errorMessage` TEXT
                    )
                """.trimIndent())
            }
        }
    }
}
