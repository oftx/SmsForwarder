package github.oftx.smsforwarder

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsHandlingService : Service() {

    // 创建一个与 Service 生命周期绑定的协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: "Unknown"
        val content = intent?.getStringExtra("content") ?: "No content"

        // 使用协程在IO线程执行数据库插入操作
        serviceScope.launch {
            // 获取数据库实例
            val database = AppDatabase.getDatabase(applicationContext)
            // 创建数据实体
            val smsEntity = SmsEntity(sender = sender, content = content)
            // 插入数据库
            database.smsDao().insert(smsEntity)

            // 任务完成后，通知前台UI刷新
            notifyUiUpdate(smsEntity)
        }

        // START_NOT_STICKY 表示如果服务被系统杀死，不要自动重启
        return START_NOT_STICKY
    }

    private fun notifyUiUpdate(entity: SmsEntity) {
        // 使用 LocalBroadcastManager 发送一个应用内广播，只有我们的App能收到
        // 这样比全局广播更安全、更高效
        val uiUpdateIntent = Intent(MainActivity.ACTION_UPDATE_UI).apply {
            putExtra("sender", entity.sender)
            putExtra("content", entity.content)
            putExtra("timestamp", entity.getFormattedTimestamp()) // 把格式化好的时间戳也传过去
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(uiUpdateIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 当服务销毁时，取消所有正在运行的协程，避免内存泄漏
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 我们不使用绑定服务，所以返回 null
        return null
    }
}