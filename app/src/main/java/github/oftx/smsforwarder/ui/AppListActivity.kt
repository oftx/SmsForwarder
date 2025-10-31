package github.oftx.smsforwarder.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import github.oftx.smsforwarder.R
import github.oftx.smsforwarder.databinding.ActivityAppListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : BaseActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var adapter: AppListAdapter

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.app_list_activity_title)

        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)

        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = pm.getInstalledApplications(0)
            val appList = packages.map {
                AppInfo(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.packageName,
                    icon = it.loadIcon(pm)
                )
            }.sortedBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                adapter = AppListAdapter(appList) { selectedApp ->
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_PACKAGE_NAME, selectedApp.packageName)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
                binding.recyclerViewApps.adapter = adapter
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_list_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_apps)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
