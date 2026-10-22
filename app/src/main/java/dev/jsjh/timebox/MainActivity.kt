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
import androidx.lifecycle.lifecycleScope
import dev.jsjh.timebox.ads.AdsConsentManager
import dev.jsjh.timebox.ads.OpeningNativeAdGate
import dev.jsjh.timebox.ads.OpeningNativeAdPreloader
import dev.jsjh.timebox.billing.AdFreeBilling
import dev.jsjh.timebox.analytics.TimeBoxAnalytics
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.diagnostics.StartupTrace
import dev.jsjh.timebox.diagnostics.traceStep
import dev.jsjh.timebox.feature.root.TimeBoxingApp
import dev.jsjh.timebox.feature.root.AppAnnouncementStore
import dev.jsjh.timebox.feature.root.CurrentAppAnnouncement
import dev.jsjh.timebox.feature.settings.AppLanguage
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.notification.ReminderReceiver
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.review.InAppReviewPrompter
import dev.jsjh.timebox.ui.isReadyForExternalUi
import dev.jsjh.timebox.ui.theme.TimeBoxingTheme
import dev.jsjh.timebox.widget.WidgetLaunchRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object {
        const val EXTERNAL_FLOW_SETTLE_DELAY_MS = 800L
    }

    private var keepSystemBarsVisible = false
    private var loginScreenVisible = false
    private var showSystemNavigationBar = false
    private lateinit var appUpdateManager: AppUpdateManager
    private var mobileAdsInitialized = false
    private var adsConsentRequested = false
    private val adFreeBilling by lazy { AdFreeBilling.get(this) }
    private var widgetLaunchRequest by mutableStateOf<WidgetLaunchRequest?>(null)
    private var launchedFromWidget = false
    private var appAnnouncementEligible = false
    private var appUpdateCheckNeeded = false
    private var appUpdateInfoRequestInFlight = false
    private var normalUpdatePromptAttempted = false
    private var appUpdateFlowInProgress = false
    private var pendingImmediateUpdateInfo: AppUpdateInfo? = null
    private var reviewRequestPending = false
    private var windowFocusGeneration = 0
    private var scheduledExternalFlowGeneration: Int? = null
    private val startupTrace = StartupTrace()
    private var startupWatchdog: Job? = null
    private var billingRefreshJob: Job? = null

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

    override fun onCreate(savedInstanceState: Bundle?) = startupTrace.traceStep("activity.initialize") {
        startupTrace.event("activity.create restored=${savedInstanceState != null}")
        setTheme(R.style.Theme_TimeBoxing)
        // Finish native window creation before lifecycle callbacks or SDK workers can touch it.
        startupTrace.traceStep("window.prepare_decor") { window.decorView }
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        startupTrace.traceStep("supabase.initialize") { initSupabase(this) }
        TimeBoxAnalytics.initialize(this)
        recordNotificationOpen(intent)
        widgetLaunchRequest = WidgetLaunchRequest.from(intent)
        launchedFromWidget = widgetLaunchRequest != null
        traceWidgetEntry("create")
        if (savedInstanceState == null) AppAnnouncementStore.beginLaunch()
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
        ReminderScheduler.createChannels(this)
        showSystemNavigationBar = AppSettingsStore(this).read().showSystemNavigationBar
        enableEdgeToEdgeSafely()
        setTimeBoxContent()
        lifecycleScope.launch {
            adFreeBilling.state.collect { billing ->
                if (billing.adsAllowed) {
                    requestAdsConsentIfNeeded()
                } else {
                    OpeningNativeAdPreloader.clear()
                    if (billing.hasAccess) OpeningNativeAdGate.disableForCurrentLaunch()
                }
                if (!billing.purchaseInProgress) scheduleExternalFlowCheck()
            }
        }
        Unit
    }

    private fun setTimeBoxContent() = startupTrace.traceStep("content.install") {
        setContentView(
            ComposeView(this).apply {
                var firstDrawRecorded = false
                viewTreeObserver.addOnDrawListener {
                    if (!firstDrawRecorded) {
                        firstDrawRecorded = true
                        startupTrace.event("compose_view.first_draw")
                    }
                }
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
                            onWidgetLaunchRequestConsumed = {
                                startupTrace.event("widget.request_consumed")
                                widgetLaunchRequest = null
                            },
                            startupTrace = startupTrace
                        )
                    }
                }
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val decor = window.decorView as? ViewGroup
        val content = decor?.findViewById<ViewGroup>(android.R.id.content)
        startupTrace.event(
            "content.tree decorChildren=${decor?.childCount ?: -1} contentChildren=${content?.childCount ?: -1}"
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
        traceWidgetEntry("new_intent")
    }

    override fun onResume() {
        super.onResume()
        adFreeBilling.refresh()
        billingRefreshJob?.cancel()
        if (adFreeBilling.state.value.enabled) {
            billingRefreshJob = lifecycleScope.launch {
                while (true) {
                    delay(60_000)
                    val billing = adFreeBilling.state.value
                    if (!billing.purchaseInProgress && (billing.hasAccess || billing.awaitingConfirmation)) {
                        adFreeBilling.refresh(preserveNotice = true)
                    }
                }
            }
        }
        startupTrace.event("activity.resume")
        startupWatchdog?.cancel()
        startupWatchdog = lifecycleScope.launch {
            delay(5_000L)
            startupTrace.reportPending()
            delay(10_000L)
            startupTrace.reportPending()
        }
        appUpdateCheckNeeded = true
        scheduleExternalFlowCheck()
    }

    override fun onPause() {
        startupTrace.event("activity.pause")
        billingRefreshJob?.cancel()
        startupWatchdog?.cancel()
        invalidateExternalFlowSchedule()
        super.onPause()
    }

    override fun onDestroy() {
        startupTrace.event("activity.destroy changingConfigurations=$isChangingConfigurations")
        super.onDestroy()
    }

    private fun traceWidgetEntry(source: String) {
        val destination = when {
            widgetLaunchRequest?.openTask == true -> "task"
            widgetLaunchRequest?.openAddTask == true -> "add_task"
            widgetLaunchRequest?.openSettings == true -> "settings"
            widgetLaunchRequest != null -> "todo"
            else -> "none"
        }
        startupTrace.event("activity.entry source=$source destination=$destination")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        invalidateExternalFlowSchedule()
        if (hasFocus) {
            scheduleExternalFlowCheck()
        }
    }

    private fun scheduleExternalFlowCheck() {
        val generation = windowFocusGeneration
        if (scheduledExternalFlowGeneration == generation) return
        scheduledExternalFlowGeneration = generation

        window.decorView.postDelayed({
            if (scheduledExternalFlowGeneration == generation) {
                scheduledExternalFlowGeneration = null
            }
            if (generation != windowFocusGeneration) return@postDelayed
            if (!isWindowReadyForExternalFlow()) return@postDelayed

            if (OpeningNativeAdGate.isOverlayActive() || AdsConsentManager.isConsentFlowActive(this)) {
                scheduleExternalFlowCheck()
                return@postDelayed
            }

            updateSystemBarsVisibility()

            pendingImmediateUpdateInfo?.let { appUpdateInfo ->
                startImmediateUpdateSafely(appUpdateInfo)
                return@postDelayed
            }

            if (appUpdateCheckNeeded && !appUpdateInfoRequestInFlight && !appUpdateFlowInProgress) {
                checkForAppUpdate()
                return@postDelayed
            }

            requestReviewIfPending()
        }, EXTERNAL_FLOW_SETTLE_DELAY_MS)
    }

    private fun invalidateExternalFlowSchedule() {
        windowFocusGeneration += 1
        scheduledExternalFlowGeneration = null
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

        if (OpeningNativeAdGate.isOverlayActive() || AdsConsentManager.isConsentFlowActive(this)) {
            scheduleExternalFlowCheck()
            return
        }

        reviewRequestPending = false
        InAppReviewPrompter.requestIfEligible(this) {
            !loginScreenVisible && !OpeningNativeAdGate.isOverlayActive() &&
                !AdsConsentManager.isConsentFlowActive(this) && !appUpdateCheckNeeded &&
                !appUpdateInfoRequestInFlight && !appUpdateFlowInProgress && pendingImmediateUpdateInfo == null
        }
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

    private fun isWindowReadyForExternalFlow(): Boolean =
        isReadyForExternalUi() && !adFreeBilling.state.value.purchaseInProgress

    private fun requestAdsConsentIfNeeded() {
        if (adsConsentRequested || isFinishing || isDestroyed || !adFreeBilling.state.value.adsAllowed) return
        adsConsentRequested = true
        startupTrace.traceStep("ads.request_consent") {
            AdsConsentManager.gatherConsent(this) { initializeMobileAdsIfNeeded() }
        }
    }

    private fun initializeMobileAdsIfNeeded() {
        if (mobileAdsInitialized || !adFreeBilling.state.value.adsAllowed) return
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
        if (!hasWindowFocus()) return
        if (keepSystemBarsVisible) showSystemBars() else hideSystemBars()
    }

    private fun hideSystemBars() {
        val decorView = window.decorView
        if (!decorView.isAttachedToWindow || decorView.windowToken == null || !hasWindowFocus()) return
        WindowCompat.getInsetsController(window, decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun showSystemBars() {
        val decorView = window.decorView
        if (!decorView.isAttachedToWindow || decorView.windowToken == null || !hasWindowFocus()) return
        WindowCompat.getInsetsController(window, decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
