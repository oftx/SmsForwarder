package github.oftx.smsforwarder.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

abstract class BaseActivity : AppCompatActivity() {

    companion object {
        const val ACTION_RECREATE_ACTIVITIES = "github.oftx.smsforwarder.ACTION_RECREATE_ACTIVITIES"
    }

    private val recreateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_RECREATE_ACTIVITIES) {
                recreate()
            }
        }
    }

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

        // Register the receiver
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(recreateReceiver, IntentFilter(ACTION_RECREATE_ACTIVITIES))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to prevent memory leaks
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recreateReceiver)
    }
}
