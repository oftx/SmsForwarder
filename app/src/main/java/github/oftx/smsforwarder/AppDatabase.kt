package github.oftx.smsforwarder

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SmsEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao

    companion object {
        // @Volatile 确保 INSTANCE 的值在所有线程中都是最新的
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // 如果 INSTANCE 不为 null，则直接返回
            // 否则，在一个同步块中创建数据库实例，防止多线程问题
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_forwarder_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}