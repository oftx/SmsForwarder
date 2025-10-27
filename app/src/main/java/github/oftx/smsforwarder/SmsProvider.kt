package github.oftx.smsforwarder

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.runBlocking

/**
 * ContentProvider，用于从Xposed Hook进程安全地写入短信数据到主应用的数据库。
 * 这是实现“寄生执行”策略的核心组件。
 */
class SmsProvider : ContentProvider() {

    private lateinit var smsDao: SmsDao

    // ContentProvider的URI
    companion object {
        val CONTENT_URI: Uri = Uri.parse("content://github.oftx.smsforwarder.provider/sms")
    }

    override fun onCreate(): Boolean {
        // 在ContentProvider创建时，获取数据库DAO实例
        // context!! 在这里是安全的，因为系统会保证在调用onCreate时context已存在
        smsDao = AppDatabase.getDatabase(context!!).smsDao()
        return true
    }

    /**
     * 实现插入操作。Hook进程将通过调用此方法来保存短信。
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val sender = values?.getAsString("sender") ?: return null
        val content = values.getAsString("content") ?: return null

        val newSms = SmsEntity(sender = sender, content = content)

        // 使用 runBlocking 在当前线程（Binder线程）同步执行数据库插入。
        // 对于快速的数据库操作，这是可接受的。
        runBlocking {
            smsDao.insert(newSms)
        }

        // 插入成功后，发送一个应用内广播，通知MainActivity刷新UI（如果它正在前台运行）
        notifyUi(newSms)

        // 返回URI表示成功
        return uri
    }

    private fun notifyUi(entity: SmsEntity) {
        context?.let {
            val uiUpdateIntent = Intent(MainActivity.ACTION_UPDATE_UI).apply {
                putExtra("sender", entity.sender)
                putExtra("content", entity.content)
                putExtra("timestamp", entity.getFormattedTimestamp())
            }
            LocalBroadcastManager.getInstance(it).sendBroadcast(uiUpdateIntent)
        }
    }

    // --- 以下方法在此场景中无需实现，但必须重写 ---

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        return null // 我们只提供插入，不提供查询
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0 // 不支持更新
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0 // 不支持删除
    }
}