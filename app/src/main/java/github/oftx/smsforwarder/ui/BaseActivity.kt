package github.oftx.smsforwarder.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply the selected locale before any UI components are created
        super.attachBaseContext(LocaleManager.updateBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the dynamic theme
        ThemeManager.applyTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            window.isNavigationBarContrastEnforced = false

        // Listen for recreate events from the handler
        lifecycleScope.launch {
            RecreateHandler.recreateEvent.collect {
                recreate()
            }
        }
    }
}
