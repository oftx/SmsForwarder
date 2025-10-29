package github.oftx.smsforwarder.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityMainBinding
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        checkAndShowFirstLaunchPrompt()
        setupRecyclerView()
        observeViewModel()
    }

    private fun checkAndShowFirstLaunchPrompt() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPromptShown = prefs.getBoolean(KEY_FIRST_LAUNCH_PROMPT_SHOWN, false)

        if (!isPromptShown) {
            showFirstLaunchBatteryDialog(prefs)
        }
    }

    private fun showFirstLaunchBatteryDialog(prefs: SharedPreferences) {
        MaterialAlertDialogBuilder(this)
            .setTitle("重要设置提醒")
            .setMessage("为了确保短信转发功能在手机锁屏或长时间静置后依然能正常工作，请将应用的电池用量设置为“无限制”。")
            .setPositiveButton("去设置") { _, _ ->
                openBatterySettings()
            }
            .setNegativeButton("已知晓", null)
            .setOnDismissListener {
                // 确保无论用户点击哪个按钮，提示都只显示一次
                prefs.edit().putBoolean(KEY_FIRST_LAUNCH_PROMPT_SHOWN, true).apply()
            }
            .show()
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "请进入“电池”或“耗电管理”选项进行设置", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法自动跳转，请手动进入系统设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        listAdapter = MainAdapter { itemId, position ->
            if (listAdapter.expandedItems.contains(itemId)) {
                listAdapter.expandedItems.remove(itemId)
            } else {
                listAdapter.expandedItems.add(itemId)
            }
            listAdapter.notifyItemChanged(position)
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
