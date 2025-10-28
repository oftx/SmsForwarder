package github.oftx.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        // This action is still useful if we want other parts of the app to react,
        // but MainActivity will now primarily rely on the database flow.
        const val ACTION_UPDATE_UI = "github.oftx.smsforwarder.UPDATE_UI"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }
    private lateinit var listAdapter: MainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        listAdapter = MainAdapter()
        binding.rvSms.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                reverseLayout = true // Show latest items at the bottom and scroll down
                stackFromEnd = true
            }
            adapter = listAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.listItems.collectLatest { items ->
                listAdapter.submitList(items)
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
