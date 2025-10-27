package github.oftx.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var smsRecyclerView: RecyclerView
    private lateinit var listItems: ArrayList<ListItem>
    private lateinit var listAdapter: SmsAdapter

    // 用于接收应用内日志的广播接收器
    private val uiLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: "Empty log"
            addLogToList(message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        smsRecyclerView = findViewById(R.id.rv_sms)
        listItems = ArrayList()
        listAdapter = SmsAdapter(listItems)
        smsRecyclerView.layoutManager = LinearLayoutManager(this) // 日志模式下，从上往下更自然
        smsRecyclerView.adapter = listAdapter

        // --- 设置监听器 ---

        // 监听来自 Hook 的短信
        SmsReceiver.smsListener = { sender, content ->
            runOnUiThread {
                addSmsToList(sender, content)
            }
        }

        // 监听来自 Hook 的日志
        LogReceiver.logListener = { message ->
            runOnUiThread {
                addLogToList(message)
            }
        }

        // 注册接收器以监听来自 UI 自身的日志
        LocalBroadcastManager.getInstance(this).registerReceiver(uiLogReceiver, IntentFilter(AppLogger.ACTION_LOG_RECEIVED))

        Toast.makeText(this, "Logger and SMS listener ready", Toast.LENGTH_SHORT).show()
        AppLogger.logFromUI(this, "MainActivity created and listeners are set.")
    }

    private fun addLogToList(message: String) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newItem = ListItem.Log(message, "[$currentTime]")
        listItems.add(newItem)
        val newPosition = listItems.size - 1
        listAdapter.notifyItemInserted(newPosition)
        smsRecyclerView.scrollToPosition(newPosition)
    }

    private fun addSmsToList(sender: String, content: String) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newItem = ListItem.Sms(SmsItem(sender, content, currentTime))
        listItems.add(newItem)
        val newPosition = listItems.size - 1
        listAdapter.notifyItemInserted(newPosition)
        smsRecyclerView.scrollToPosition(newPosition)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理所有静态监听器和接收器
        SmsReceiver.smsListener = null
        LogReceiver.logListener = null
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uiLogReceiver)
    }
}
