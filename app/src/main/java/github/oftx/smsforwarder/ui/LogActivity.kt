package github.oftx.smsforwarder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityLogBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogActivity : BaseActivity() {

    private lateinit var binding: ActivityLogBinding
    private val viewModel: LogViewModel by viewModels { LogViewModelFactory(application) }
    private lateinit var logAdapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
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
            .setTitle(R.string.clear_logs_dialog_title)
            .setMessage(R.string.clear_logs_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                viewModel.clearLogs()
                Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun copyLogsToClipboard() {
        lifecycleScope.launch {
            val logString = viewModel.getLogsAsString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SmsForwarder Logs", logString)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this@LogActivity, R.string.logs_copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
