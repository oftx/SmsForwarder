package github.oftx.smsforwarder.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityMainBinding
import github.oftx.smsforwarder.ui.details.SmsDetailDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }
    private lateinit var listAdapter: MainAdapter

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_FIRST_LAUNCH_PROMPT_SHOWN = "first_launch_prompt_shown"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        checkAndShowFirstLaunchPrompt()
        setupRecyclerView()
        observeViewModel()
    }

    private fun checkAndShowFirstLaunchPrompt() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isPromptShown = prefs.getBoolean(KEY_FIRST_LAUNCH_PROMPT_SHOWN, false)

        if (!isPromptShown) {
            showFirstLaunchBatteryDialog(prefs)
        }
    }

    private fun showFirstLaunchBatteryDialog(prefs: SharedPreferences) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.first_launch_dialog_title)
            .setMessage(R.string.first_launch_dialog_message)
            .setPositiveButton(R.string.first_launch_dialog_positive_button) { _, _ ->
                openBatterySettings()
            }
            .setNegativeButton(R.string.first_launch_dialog_negative_button, null)
            .setOnDismissListener {
                // Use KTX extension for conciseness
                prefs.edit {
                    putBoolean(KEY_FIRST_LAUNCH_PROMPT_SHOWN, true)
                }
            }
            .show()
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                // Use KTX extension to convert String to Uri
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
            Toast.makeText(this, R.string.battery_settings_toast, Toast.LENGTH_LONG).show()
        } catch (_: Exception) { // Unused parameter 'e' replaced with '_'
            Toast.makeText(this, R.string.cannot_open_settings_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        listAdapter = MainAdapter { smsId ->
            SmsDetailDialogFragment.newInstance(smsId)
                .show(supportFragmentManager, SmsDetailDialogFragment.TAG)
        }
        binding.rvSms.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = listAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.listItems.collectLatest { items ->
                if (items.isEmpty()) {
                    binding.rvSms.visibility = View.GONE
                    binding.tvEmptyView.visibility = View.VISIBLE
                } else {
                    binding.rvSms.visibility = View.VISIBLE
                    binding.tvEmptyView.visibility = View.GONE
                }

                val wasAtTop = (binding.rvSms.layoutManager as LinearLayoutManager)
                    .findFirstCompletelyVisibleItemPosition() == 0
                listAdapter.submitList(items) {
                    if (wasAtTop || listAdapter.itemCount <= 1) {
                        binding.rvSms.scrollToPosition(0)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_forwarder -> {
                startActivity(Intent(this, ForwarderListActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
