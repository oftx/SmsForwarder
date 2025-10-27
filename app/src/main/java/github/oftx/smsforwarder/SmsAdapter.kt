package github.oftx.smsforwarder

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 1. 定义一个密封类，用于表示不同类型的列表项
sealed class ListItem {
    data class Sms(val item: SmsItem) : ListItem()
    data class Log(val message: String, val timestamp: String) : ListItem()
}

// SmsItem 数据类保持不变
data class SmsItem(val sender: String, val content: String, val timestamp: String)

// 2. 修改 Adapter 以支持 ListItem
class SmsAdapter(private val items: List<ListItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 3. 定义不同类型的 ViewHolder
    class SmsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val senderTextView: TextView = view.findViewById(R.id.tv_sender)
        val contentTextView: TextView = view.findViewById(R.id.tv_content)
        val timestampTextView: TextView = view.findViewById(R.id.tv_timestamp)
    }

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logTextView: TextView = view.findViewById(R.id.tv_log_message)
        val timestampTextView: TextView = view.findViewById(R.id.tv_log_timestamp)
    }

    // 4. 定义视图类型常量
    companion object {
        private const val TYPE_SMS = 0
        private const val TYPE_LOG = 1
    }

    // 5. 根据位置返回不同的视图类型
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.Sms -> TYPE_SMS
            is ListItem.Log -> TYPE_LOG
        }
    }

    // 6. 根据视图类型创建不同的 ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SMS -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms_log, parent, false)
                SmsViewHolder(view)
            }
            TYPE_LOG -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
                LogViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    // 7. 绑定数据到对应的 ViewHolder
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val currentItem = items[position]) {
            is ListItem.Sms -> {
                val smsHolder = holder as SmsViewHolder
                smsHolder.senderTextView.text = "From: ${currentItem.item.sender}"
                smsHolder.contentTextView.text = currentItem.item.content
                smsHolder.timestampTextView.text = currentItem.item.timestamp
            }
            is ListItem.Log -> {
                val logHolder = holder as LogViewHolder
                logHolder.logTextView.text = currentItem.message
                logHolder.timestampTextView.text = currentItem.timestamp
                // 让错误日志更显眼
                if (currentItem.message.uppercase().contains("ERROR") || currentItem.message.uppercase().contains("FAILED")) {
                    logHolder.logTextView.setTextColor(Color.RED)
                } else {
                    logHolder.logTextView.setTextColor(Color.DKGRAY)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
