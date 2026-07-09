package dev.jsjh.timebox

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.jsjh.timebox.ads.AdsConsentManager
import dev.jsjh.timebox.ads.OpeningNativeAdGate
import dev.jsjh.timebox.ads.OpeningNativeAdPreloader
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.feature.root.TimeBoxingApp
import dev.jsjh.timebox.feature.settings.AppLanguage
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.review.InAppReviewPrompter
import dev.jsjh.timebox.ui.theme.TimeBoxingTheme
import dev.jsjh.timebox.widget.WidgetLaunchRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    private var keepSystemBarsVisible = false
    private var loginScreenVisible = false
    private var showSystemNavigationBar = false
    private lateinit var appUpdateManager: AppUpdateManager
    private var mobileAdsInitialized = false
    private var widgetLaunchRequest by mutableStateOf<WidgetLaunchRequest?>(null)
    private var launchedFromWidget = false

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!keepSystemBarsVisible) hideSystemBars()
    }

    private val appUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (!keepSystemBarsVisible) hideSystemBars()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TimeBoxing)
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdate()
        initSupabase(this)
        widgetLaunchRequest = WidgetLaunchRequest.from(intent)
        launchedFromWidget = widgetLaunchRequest != null
        if (savedInstanceState == null) {
            OpeningNativeAdGate.recordLaunch(this, fromWidget = widgetLaunchRequest != null)
            InAppReviewPrompter.recordLaunch(this)
        }
        AdsConsentManager.gatherConsent(this) {
            initializeMobileAdsIfNeeded()
        }
        ReminderScheduler.createChannels(this)
        showSystemNavigationBar = AppSettingsStore(this).read().showSystemNavigationBar
        enableEdgeToEdgeSafely()
        setTimeBoxContent()
    }

    private fun setTimeBoxContent() {
        setContentView(
            ComposeView(this).apply {
                setContent {
                    TimeBoxingTheme {
                        TimeBoxingApp(
                            onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                            onRequestBatteryOptimizationExemption = ::requestIgnoreBatteryOptimizationsIfNeeded,
                            onLoginScreenVisible = { visible ->
                                loginScreenVisible = visible
                                if (!visible && !launchedFromWidget) {
                                    InAppReviewPrompter.requestIfEligible(this@MainActivity)
                                }
                                if (visible) {
                                    OpeningNativeAdGate.disableForCurrentLaunch()
                                    OpeningNativeAdPreloader.clear()
                                }
                                updateSystemBarsVisibility()
                            },
                            initialShowSystemNavigationBar = showSystemNavigationBar,
                            onSystemNavigationBarPreferenceChange = { visible ->
                                showSystemNavigationBar = visible
                                updateSystemBarsVisibility()
                            },
                            widgetLaunchRequest = widgetLaunchRequest,
                            onWidgetLaunchRequestConsumed = { widgetLaunchRequest = null }
                        )
                    }
                }
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun enableEdgeToEdgeSafely() {
        runCatching {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color(0xFF121212).toArgb()),
                navigationBarStyle = SystemBarStyle.dark(Color(0xFF1E1E1E).toArgb())
            )
        }.onFailure {
            window.statusBarColor = Color(0xFF121212).toArgb()
            window.navigationBarColor = Color(0xFF1E1E1E).toArgb()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetLaunchRequest = WidgetLaunchRequest.from(intent)
        launchedFromWidget = widgetLaunchRequest != null
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

    private fun initializeMobileAdsIfNeeded() {
        if (mobileAdsInitialized) return
        mobileAdsInitialized = true
        MobileAds.initialize(this) {}
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

    private fun updateSystemBarsVisibility() {
        keepSystemBarsVisible = loginScreenVisible || showSystemNavigationBar
        if (keepSystemBarsVisible) showSystemBars() else hideSystemBars()
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
