package github.oftx.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        // 定义一个 Action，用于接收来自 Service 的 UI 更新通知
        const val ACTION_UPDATE_UI = "github.oftx.smsforwarder.UPDATE_UI"
    }

    private lateinit var smsRecyclerView: RecyclerView
    private lateinit var listItems: ArrayList<ListItem>
    private lateinit var listAdapter: SmsAdapter
    private lateinit var database: AppDatabase

    // 用于接收应用内日志的广播接收器
    private val uiLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: "Empty log"
            addLogToList(message)
        }
    }

    // 用于接收来自 Service 的新短信通知，并刷新UI
    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra("sender")
            val content = intent?.getStringExtra("content")
            val timestamp = intent?.getStringExtra("timestamp")
            if (sender != null && content != null && timestamp != null) {
                addSmsToList(sender, content, timestamp)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        smsRecyclerView = findViewById(R.id.rv_sms)
        listItems = ArrayList()
        listAdapter = SmsAdapter(listItems)
        smsRecyclerView.layoutManager = LinearLayoutManager(this)
        smsRecyclerView.adapter = listAdapter

        loadSmsHistory()
        setupListeners()
    }

    private fun loadSmsHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val history = database.smsDao().getAllSms()
            withContext(Dispatchers.Main) {
                val mappedHistory = history.map { entity ->
                    ListItem.Sms(SmsItem(entity.sender, entity.content, entity.getFormattedTimestamp()))
                }
                listItems.clear() // 先清空旧数据
                listItems.addAll(mappedHistory)
                listAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupListeners() {
        // --- START: 修改监听逻辑 ---
        // 移除旧的 SmsReceiver.smsListener 和 LogReceiver.logListener
        // 注册应用内广播接收器
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(uiLogReceiver, IntentFilter(AppLogger.ACTION_LOG_RECEIVED))
        localBroadcastManager.registerReceiver(uiUpdateReceiver, IntentFilter(ACTION_UPDATE_UI))
        // --- END: 修改监听逻辑 ---
    }

    private fun addLogToList(message: String) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newItem = ListItem.Log(message, "[$currentTime]")
        listItems.add(0, newItem)
        listAdapter.notifyItemInserted(0)
        smsRecyclerView.scrollToPosition(0)
    }

    private fun addSmsToList(sender: String, content: String, timestamp: String) {
        val newItem = ListItem.Sms(SmsItem(sender, content, timestamp))
        listItems.add(0, newItem)
        listAdapter.notifyItemInserted(0)
        smsRecyclerView.scrollToPosition(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        // --- START: 修改清理逻辑 ---
        // 取消注册应用内广播接收器
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.unregisterReceiver(uiLogReceiver)
        localBroadcastManager.unregisterReceiver(uiUpdateReceiver)
        // --- END: 修改清理逻辑 ---
    }
}