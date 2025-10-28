package github.oftx.smsforwarder.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply the selected locale before any UI components are created
        super.attachBaseContext(LocaleManager.updateBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the dynamic theme
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
    }
}
