package github.oftx.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }
    private lateinit var listAdapter: MainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState) // BaseActivity's onCreate handles theme
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        observeViewModel()
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
                // Handle empty state visibility
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
