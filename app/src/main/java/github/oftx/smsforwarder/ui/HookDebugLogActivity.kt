package github.oftx.smsforwarder.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityHookDebugLogBinding

class HookDebugLogActivity : BaseActivity() {

    private lateinit var binding: ActivityHookDebugLogBinding
    private val logMessages = mutableListOf<String>()
    private lateinit var adapter: HookDebugLogAdapter
    private var targetPackageName: String? = null

    companion object {
        const val ACTION_DEBUG_LOG_RECEIVED = "github.oftx.smsforwarder.ACTION_DEBUG_LOG"
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("message")?.let {
                logMessages.add(it)
                adapter.notifyItemInserted(logMessages.size - 1)
                binding.recyclerViewHookLogs.scrollToPosition(logMessages.size - 1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHookDebugLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        loadTargetApp()
    }

    private fun loadTargetApp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        targetPackageName = prefs.getString("pref_hook_target_app", "")
        // Invalidate the options menu to redraw it with the new data
        invalidateOptionsMenu()
    }

    private fun setupRecyclerView() {
        adapter = HookDebugLogAdapter(logMessages)
        binding.recyclerViewHookLogs.adapter = adapter
        binding.recyclerViewHookLogs.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        loadTargetApp() // Refresh in case the setting was changed
        val filter = IntentFilter(ACTION_DEBUG_LOG_RECEIVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.hook_debug_log_menu, menu)

        val forceStopItem = menu.findItem(R.id.action_force_stop)
        val actionView = forceStopItem?.actionView

        if (targetPackageName.isNullOrEmpty()) {
            forceStopItem?.isVisible = false
        } else {
            forceStopItem?.isVisible = true
            val appNameTextView = actionView?.findViewById<TextView>(R.id.tv_target_app_name)
            val forceStopButton = actionView?.findViewById<ImageButton>(R.id.btn_force_stop)
            
            // It's better to show just the last part of the package name if it's too long
            val displayName = targetPackageName!!.substringAfterLast('.')
            appNameTextView?.text = displayName

            actionView?.setOnClickListener {
                forceStopTargetApp()
            }
        }
        return true
    }
    
    private fun forceStopTargetApp() {
        if (targetPackageName.isNullOrEmpty()) {
            Toast.makeText(this, R.string.force_stop_no_target, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $targetPackageName"))
            process.waitFor()
            Toast.makeText(this, getString(R.string.force_stop_success, targetPackageName), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.force_stop_failed, targetPackageName), Toast.LENGTH_LONG).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_logs -> {
                val size = logMessages.size
                logMessages.clear()
                adapter.notifyItemRangeRemoved(0, size)
                true
            }
            R.id.action_copy_all -> {
                copyAllLogsToClipboard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyAllLogsToClipboard() {
        if (logMessages.isEmpty()) {
            return
        }
        val allLogs = logMessages.joinToString("\n")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Hook Debug Logs", allLogs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.logs_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
