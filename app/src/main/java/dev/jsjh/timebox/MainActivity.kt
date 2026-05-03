package dev.jsjh.timebox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.feature.root.TimeBoxingApp
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.ui.theme.TimeBoxingTheme
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : ComponentActivity() {
    private var keepSystemBarsVisible = false
    private lateinit var appUpdateManager: AppUpdateManager

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!keepSystemBarsVisible) hideSystemBars()
    }

    private val appUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (!keepSystemBarsVisible) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TimeBoxing)
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdate()
        initSupabase(this)
        ReminderScheduler.createChannels(this)
        enableEdgeToEdge(
            statusBarStyle     = SystemBarStyle.dark(Color(0xFF121212).toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color(0xFF1E1E1E).toArgb())
        )
        setContent {
            TimeBoxingTheme {
                TimeBoxingApp(
                    onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                    onRequestBatteryOptimizationExemption = ::requestIgnoreBatteryOptimizationsIfNeeded,
                    onLoginScreenVisible = { visible ->
                        keepSystemBarsVisible = visible
                        if (visible) showSystemBars() else hideSystemBars()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeAppUpdateIfNeeded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !keepSystemBarsVisible) hideSystemBars()
    }

    private fun checkForAppUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val updateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val immediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            if (updateAvailable && immediateAllowed) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    appUpdateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    private fun resumeAppUpdateIfNeeded() {
        if (!::appUpdateManager.isInitialized) return
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    appUpdateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestIgnoreBatteryOptimizationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Exception) {
            // Some devices do not expose this settings panel.
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun showSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
