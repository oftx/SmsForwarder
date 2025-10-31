package github.oftx.smsforwarder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import github.oftx.smsforwarder.R

class HookDebugLogAdapter(private val logs: List<String>) :
    RecyclerView.Adapter<HookDebugLogAdapter.LogViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hook_debug_log, parent, false) as TextView
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    inner class LogViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(logMessage: String) {
            textView.text = logMessage
        }
    }
}
