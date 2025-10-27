package github.oftx.smsforwarder

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "sms_logs")
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis() // 使用 Long 类型存储时间戳，更标准
) {
    // 方便转换的辅助函数
    fun getFormattedTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}