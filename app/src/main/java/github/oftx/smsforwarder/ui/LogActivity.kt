package github.oftx.smsforwarder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityLogBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private val viewModel: LogViewModel by viewModels { LogViewModelFactory(application) }
    private lateinit var logAdapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        observeLogs()
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter()
        binding.recyclerViewLogs.adapter = logAdapter
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            viewModel.logs.collectLatest { logs ->
                logAdapter.submitList(logs)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.log_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_logs -> {
                showClearConfirmationDialog()
                true
            }
            R.id.action_copy_logs -> {
                copyLogsToClipboard()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空日志")
            .setMessage("确定要删除所有日志记录吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ ->
                viewModel.clearLogs()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun copyLogsToClipboard() {
        lifecycleScope.launch {
            val logString = viewModel.getLogsAsString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SmsForwarder Logs", logString)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this@LogActivity, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
