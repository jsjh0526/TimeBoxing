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
import androidx.lifecycle.Lifecycle
import dev.jsjh.timebox.ads.AdsConsentManager
import dev.jsjh.timebox.ads.OpeningNativeAdGate
import dev.jsjh.timebox.ads.OpeningNativeAdPreloader
import dev.jsjh.timebox.analytics.TimeBoxAnalytics
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.feature.root.TimeBoxingApp
import dev.jsjh.timebox.feature.root.AppAnnouncementStore
import dev.jsjh.timebox.feature.root.CurrentAppAnnouncement
import dev.jsjh.timebox.feature.settings.AppLanguage
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.notification.ReminderReceiver
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.review.InAppReviewPrompter
import dev.jsjh.timebox.ui.theme.TimeBoxingTheme
import dev.jsjh.timebox.widget.WidgetLaunchRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
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
    private var appAnnouncementEligible = false
    private var appUpdateCheckNeeded = false
    private var appUpdateInfoRequestInFlight = false
    private var normalUpdatePromptAttempted = false
    private var appUpdateFlowInProgress = false
    private var pendingImmediateUpdateInfo: AppUpdateInfo? = null
    private var reviewRequestPending = false

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        TimeBoxAnalytics.notificationPermissionResult(granted)
    }

    private val appUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        appUpdateFlowInProgress = false
        appUpdateCheckNeeded = true
        scheduleExternalFlowCheck()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TimeBoxing)
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        initSupabase(this)
        TimeBoxAnalytics.initialize(this)
        recordNotificationOpen(intent)
        widgetLaunchRequest = WidgetLaunchRequest.from(intent)
        launchedFromWidget = widgetLaunchRequest != null
        appAnnouncementEligible = AppAnnouncementStore.shouldShow(
            this,
            CurrentAppAnnouncement.value
        )
        if (savedInstanceState == null) {
            OpeningNativeAdGate.recordLaunch(
                this,
                fromWidget = widgetLaunchRequest != null ||
                    appAnnouncementEligible
            )
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
                                if (
                                    !visible &&
                                    !launchedFromWidget &&
                                    !appAnnouncementEligible
                                ) {
                                    reviewRequestPending = true
                                    scheduleExternalFlowCheck()
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
        recordNotificationOpen(intent)
        widgetLaunchRequest = WidgetLaunchRequest.from(intent)
        launchedFromWidget = widgetLaunchRequest != null
    }

    override fun onResume() {
        super.onResume()
        appUpdateCheckNeeded = true
        scheduleExternalFlowCheck()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            if (!keepSystemBarsVisible) hideSystemBars()
            scheduleExternalFlowCheck()
        }
    }

    private fun scheduleExternalFlowCheck() {
        window.decorView.post {
            if (!isWindowReadyForExternalFlow()) return@post

            pendingImmediateUpdateInfo?.let { appUpdateInfo ->
                startImmediateUpdateSafely(appUpdateInfo)
                return@post
            }

            if (appUpdateCheckNeeded && !appUpdateInfoRequestInFlight && !appUpdateFlowInProgress) {
                checkForAppUpdate()
                return@post
            }

            requestReviewIfPending()
        }
    }

    private fun checkForAppUpdate() {
        if (!::appUpdateManager.isInitialized || appUpdateInfoRequestInFlight || appUpdateFlowInProgress) return
        if (!isWindowReadyForExternalFlow()) return

        appUpdateCheckNeeded = false
        appUpdateInfoRequestInFlight = true
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            appUpdateInfoRequestInFlight = false
            val updateInProgress =
                appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            val updateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val immediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)

            if (updateInProgress || (updateAvailable && immediateAllowed && !normalUpdatePromptAttempted)) {
                if (updateAvailable) normalUpdatePromptAttempted = true
                reviewRequestPending = false
                pendingImmediateUpdateInfo = appUpdateInfo
                scheduleExternalFlowCheck()
            } else {
                requestReviewIfPending()
            }
        }.addOnFailureListener {
            appUpdateInfoRequestInFlight = false
            requestReviewIfPending()
        }
    }

    private fun requestReviewIfPending() {
        if (!reviewRequestPending || appUpdateCheckNeeded) return
        if (appUpdateInfoRequestInFlight || appUpdateFlowInProgress || pendingImmediateUpdateInfo != null) return
        if (!isWindowReadyForExternalFlow()) return

        reviewRequestPending = false
        InAppReviewPrompter.requestIfEligible(this)
    }

    private fun startImmediateUpdateSafely(appUpdateInfo: AppUpdateInfo) {
        if (appUpdateFlowInProgress) return
        if (!isWindowReadyForExternalFlow()) return

        pendingImmediateUpdateInfo = null
        appUpdateFlowInProgress = true
        val started = runCatching {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                appUpdateLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )
        }.getOrDefault(false)

        if (!started) {
            appUpdateFlowInProgress = false
            appUpdateCheckNeeded = true
        }
    }

    private fun isWindowReadyForExternalFlow(): Boolean {
        if (isFinishing || isDestroyed) return false
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        val decorView = window.decorView
        return decorView.isAttachedToWindow && hasWindowFocus()
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

    private fun recordNotificationOpen(intent: Intent?) {
        if (intent?.getBooleanExtra(ReminderReceiver.EXTRA_OPENED_FROM_REMINDER, false) != true) return
        TimeBoxAnalytics.notificationOpened()
        intent.removeExtra(ReminderReceiver.EXTRA_OPENED_FROM_REMINDER)
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
        val decorView = window.decorView
        if (!decorView.isAttachedToWindow) return
        WindowCompat.getInsetsController(window, decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun showSystemBars() {
        val decorView = window.decorView
        if (!decorView.isAttachedToWindow) return
        WindowCompat.getInsetsController(window, decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
