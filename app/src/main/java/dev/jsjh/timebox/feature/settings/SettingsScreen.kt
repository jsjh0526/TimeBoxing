package dev.jsjh.timebox.feature.settings

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dev.jsjh.timebox.BuildConfig
import dev.jsjh.timebox.R
import dev.jsjh.timebox.ads.AdsConsentManager
import dev.jsjh.timebox.auth.AuthRepository
import dev.jsjh.timebox.auth.AuthState
import dev.jsjh.timebox.data.remote.SyncErrorType
import dev.jsjh.timebox.data.remote.SyncManager
import dev.jsjh.timebox.data.remote.SyncState
import dev.jsjh.timebox.notification.ReminderSettings
import dev.jsjh.timebox.widget.TodoWidgetUpdater
import dev.jsjh.timebox.widget.WidgetAccessStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

private val ScreenBackground = Color(0xFF121212)
private val CardBackground   = Color(0xFF1E1E1E)
private val CardBorder       = Color(0xFF2A2A2A)
private val Accent           = Color(0xFF8687E7)
private val Green            = Color(0xFF7AE582)
private val TextPrimary      = Color.White
private val TextSecondary    = Color(0xFF6A7282)
private val ButtonText       = Color(0xFFD1D5DC)

private const val NOTICE_URL = "https://debonair-approval-27a.notion.site/TimeBox-353a223f3eb38026b1f4ea3b9457d251"
private const val TERMS_URL = "https://debonair-approval-27a.notion.site/TimeBox-354a223f3eb3801dade1c5fc23d587e2"
private const val PRIVACY_URL = "https://debonair-approval-27a.notion.site/TimeBox-353a223f3eb380979595cbbfb50fa400"
private const val CONTACT_EMAIL = "jsjh.dev@gmail.com"

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onAppSettingsChange: (AppSettings) -> Unit,
    reminderSettings: ReminderSettings,
    onReminderSettingsChange: (ReminderSettings) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onRefreshStatus: (String) -> Unit = {}
) {
    val authState by AuthRepository.authState.collectAsState()
    val syncState by SyncManager.state.collectAsState()
    val remoteStatus by SyncManager.remoteStatus.collectAsState()
    val loggedInUserId = (authState as? AuthState.LoggedIn)?.userId
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val contactMailSubject = stringResource(R.string.settings_mail_subject_contact)
    val feedbackMailSubject = stringResource(R.string.settings_mail_subject_feedback)
    val openSourceTitle = stringResource(R.string.settings_open_source)
    var languageDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(loggedInUserId) {
        val userId = loggedInUserId ?: return@LaunchedEffect
        onRefreshStatus(userId)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(ScreenBackground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_title), style = TextStyle(color = TextPrimary, fontSize = 28.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp))
                Text(stringResource(R.string.settings_subtitle), style = TextStyle(color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp))
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_sync), icon = { MaterialSettingsIcon(SettingsIcon.Sync, Green) }) {
                val (statusText, statusColor) = when (syncState) {
                    is SyncState.Idle -> stringResource(R.string.settings_sync_idle) to TextSecondary
                    is SyncState.Syncing -> stringResource(R.string.settings_syncing) to Accent
                    is SyncState.Success -> stringResource(R.string.settings_sync_success, (syncState as SyncState.Success).time) to Green
                    is SyncState.Error -> stringResource(R.string.settings_sync_error, syncErrorMessage((syncState as SyncState.Error).type)) to Color(0xFFFF5F57)
                }
                Text(statusText, style = TextStyle(color = statusColor, fontSize = 13.sp, lineHeight = 19.sp))
                remoteStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.settings_server_status, status.taskCount, status.templateCount, status.checkedAt),
                        style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                val isSyncing = syncState is SyncState.Syncing
                val isLoggedIn = authState is AuthState.LoggedIn
                val buttonEnabled = isLoggedIn && !isSyncing
                val buttonBg by animateColorAsState(
                    targetValue = when {
                        isSyncing -> Accent.copy(alpha = 0.22f)
                        isLoggedIn -> Accent
                        else -> Color(0xFF2A2A2A)
                    },
                    animationSpec = tween(160),
                    label = "syncButtonBg"
                )
                val buttonContentColor by animateColorAsState(
                    targetValue = if (isLoggedIn || isSyncing) Color.White else TextSecondary,
                    animationSpec = tween(160),
                    label = "syncButtonContent"
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(buttonBg).clickable(enabled = buttonEnabled, onClick = onSyncNow),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.animateContentSize(animationSpec = tween(140)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                        } else {
                            MaterialSettingsIcon(SettingsIcon.Sync, buttonContentColor, 18)
                        }
                        Text(
                            text = when {
                                isSyncing -> stringResource(R.string.settings_syncing)
                                isLoggedIn -> stringResource(R.string.settings_sync_now)
                                else -> stringResource(R.string.settings_sync_login_required)
                            },
                            style = TextStyle(color = buttonContentColor, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        item {
            WidgetAccessCard()
        }

        item {
            SupportAdCard()
        }

        item {
            SectionCard(title = stringResource(R.string.settings_app), icon = { MaterialSettingsIcon(SettingsIcon.Display, Accent) }) {
                SettingsMenuRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = currentLanguageLabel(),
                    icon = SettingsIcon.Language,
                    showChevron = true,
                    onClick = { languageDialogVisible = true }
                )
                ToggleRow(
                    stringResource(R.string.settings_system_nav),
                    stringResource(R.string.settings_system_nav_subtitle),
                    appSettings.showSystemNavigationBar,
                    { onAppSettingsChange(appSettings.copy(showSystemNavigationBar = it)) }
                )
                DayStartSelector(
                    selectedHour = appSettings.dayStartHour,
                    onSelect = { hour -> onAppSettingsChange(appSettings.copy(dayStartHour = hour)) }
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_notifications), icon = { MaterialSettingsIcon(SettingsIcon.Notifications, Accent) }) {
                ToggleRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_subtitle), reminderSettings.notificationsEnabled, { onReminderSettingsChange(reminderSettings.copy(notificationsEnabled = it)) })
                ToggleRow(stringResource(R.string.settings_sound), stringResource(R.string.settings_sound_subtitle), reminderSettings.soundEnabled, { onReminderSettingsChange(reminderSettings.copy(soundEnabled = it)) }, enabled = reminderSettings.notificationsEnabled)
                ToggleRow(stringResource(R.string.settings_vibration), stringResource(R.string.settings_vibration_subtitle), reminderSettings.vibrationEnabled, { onReminderSettingsChange(reminderSettings.copy(vibrationEnabled = it)) }, enabled = reminderSettings.notificationsEnabled, showDivider = false)
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_account), icon = { MaterialSettingsIcon(SettingsIcon.Account, Accent) }) {
                when (val state = authState) {
                    is AuthState.LoggedIn -> {
                        AccountSummary(
                            name = state.displayName?.takeIf { it.isNotBlank() },
                            email = state.email.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_logged_in)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(label = stringResource(R.string.settings_sign_out), filled = false, icon = { MaterialSettingsIcon(SettingsIcon.Logout, ButtonText, 18) }, onClick = onSignOut)
                    }
                    is AuthState.Loading -> AccountSummary(null, stringResource(R.string.settings_checking_login))
                    is AuthState.Error -> AccountSummary(null, state.message)
                    AuthState.Guest -> {
                        AccountSummary(stringResource(R.string.settings_guest_mode), stringResource(R.string.settings_guest_subtitle))
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(label = stringResource(R.string.settings_sign_in_google), filled = true, icon = { MaterialSettingsIcon(SettingsIcon.Account, Color.White, 18) }, onClick = onSignIn)
                    }
                    AuthState.SignedOut -> AccountSummary(null, stringResource(R.string.settings_signed_out))
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_support), icon = { MaterialSettingsIcon(SettingsIcon.Support, Accent) }) {
                SettingsMenuRow(stringResource(R.string.settings_notice), stringResource(R.string.settings_notice_subtitle), SettingsIcon.Notice, showChevron = true, onClick = { uriHandler.openUri(NOTICE_URL) })
                SettingsMenuRow(stringResource(R.string.settings_contact), stringResource(R.string.settings_contact_subtitle), SettingsIcon.Contact, showChevron = true, onClick = { context.startEmailIntent(CONTACT_EMAIL, contactMailSubject) })
                SettingsMenuRow(stringResource(R.string.settings_feedback), stringResource(R.string.settings_feedback_subtitle), SettingsIcon.Feedback, showChevron = true, showDivider = false, onClick = { context.startEmailIntent(CONTACT_EMAIL, feedbackMailSubject) })
            }
        }

        item {
            SectionCard(title = stringResource(R.string.settings_about), icon = { MaterialSettingsIcon(SettingsIcon.Info, Accent) }) {
                SettingsMenuRow(stringResource(R.string.settings_terms), stringResource(R.string.settings_terms_subtitle), SettingsIcon.Terms, showChevron = true, onClick = { uriHandler.openUri(TERMS_URL) })
                SettingsMenuRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_subtitle), SettingsIcon.Privacy, showChevron = true, onClick = { uriHandler.openUri(PRIVACY_URL) })
                SettingsMenuRow(stringResource(R.string.settings_open_source), stringResource(R.string.settings_open_source_subtitle), SettingsIcon.License, showChevron = true, onClick = {
                    OssLicensesMenuActivity.setActivityTitle(openSourceTitle)
                    context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                })
                SettingsMenuRow(stringResource(R.string.settings_version), "TimeBox v${BuildConfig.VERSION_NAME}", SettingsIcon.Info, showChevron = false, showDivider = false)
                if (AdsConsentManager.privacyOptionsRequired) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(CardBorder))
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsMenuRow(
                        stringResource(R.string.settings_privacy_choices),
                        stringResource(R.string.settings_privacy_choices_subtitle),
                        SettingsIcon.Privacy,
                        showChevron = true,
                        showDivider = false,
                        onClick = { context.findActivity()?.let(AdsConsentManager::showPrivacyOptions) }
                    )
                }
            }
        }
    }

    if (languageDialogVisible) {
        LanguageDialog(onDismiss = { languageDialogVisible = false })
    }
}

@Composable
private fun SupportAdCard() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val adUnitId = BuildConfig.ADMOB_SUPPORT_REWARDED_AD_UNIT_ID
    val loadingMessage = stringResource(R.string.settings_support_ad_loading)
    val notReadyMessage = stringResource(R.string.settings_support_ad_not_ready)
    val unavailableMessage = stringResource(R.string.settings_support_ad_unavailable)
    val thanksMessage = stringResource(R.string.settings_support_ad_thanks)

    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadAd() {
        if (!AdsConsentManager.canRequestAds || adUnitId.isBlank() || isLoading || rewardedAd != null) return
        isLoading = true
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    LaunchedEffect(AdsConsentManager.canRequestAds, adUnitId) {
        loadAd()
    }

    val buttonEnabled = adUnitId.isNotBlank() && AdsConsentManager.canRequestAds
    val buttonLabel = stringResource(R.string.settings_support_ad_button)
    val buttonIconColor = if (buttonEnabled) Color.White else TextSecondary

    SectionCard(title = stringResource(R.string.settings_support_ad_title), icon = { MaterialSettingsIcon(SettingsIcon.Support, Accent) }) {
        Text(
            stringResource(R.string.settings_support_ad_watch_title),
            style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_support_ad_subtitle),
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        ActionButton(
            label = buttonLabel,
            filled = true,
            enabled = buttonEnabled,
            icon = { MaterialSettingsIcon(SettingsIcon.Support, buttonIconColor, 18) },
            onClick = {
                val ad = rewardedAd
                if (activity == null) {
                    Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
                    return@ActionButton
                }
                if (ad == null) {
                    if (isLoading) {
                        Toast.makeText(context, loadingMessage, Toast.LENGTH_SHORT).show()
                        return@ActionButton
                    }
                    loadAd()
                    Toast.makeText(context, notReadyMessage, Toast.LENGTH_SHORT).show()
                    return@ActionButton
                }
                rewardedAd = null
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        loadAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
                        loadAd()
                    }
                }
                ad.show(activity) {
                    Toast.makeText(context, thanksMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun WidgetAccessCard() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val adUnitId = BuildConfig.ADMOB_WIDGET_REWARDED_AD_UNIT_ID
    val loadingMessage = stringResource(R.string.settings_support_ad_loading)
    val notReadyMessage = stringResource(R.string.settings_support_ad_not_ready)
    val unavailableMessage = stringResource(R.string.settings_support_ad_unavailable)
    val unlockedMessage = stringResource(R.string.settings_widget_unlocked_toast)

    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val remainingMillis by androidx.compose.runtime.produceState(
        initialValue = WidgetAccessStore.remainingMillis(context),
        key1 = refreshKey
    ) {
        while (true) {
            value = WidgetAccessStore.remainingMillis(context)
            delay(60_000)
        }
    }
    val canExtend = WidgetAccessStore.canExtend(context)
    val isUnlocked = remainingMillis > 0L

    fun loadAd() {
        if (!AdsConsentManager.canRequestAds || adUnitId.isBlank() || isLoading || rewardedAd != null || !WidgetAccessStore.canExtend(context)) return
        isLoading = true
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    LaunchedEffect(AdsConsentManager.canRequestAds, adUnitId, canExtend) {
        loadAd()
    }

    val buttonEnabled = adUnitId.isNotBlank() && AdsConsentManager.canRequestAds && canExtend
    val buttonLabel = when {
        !canExtend -> stringResource(R.string.settings_widget_unlock_maxed)
        isUnlocked -> stringResource(R.string.settings_widget_unlock_extend)
        else -> stringResource(R.string.settings_widget_unlock_open)
    }
    val statusText = if (isUnlocked) {
        formatWidgetAccessRemaining(remainingMillis)
    } else {
        stringResource(R.string.settings_widget_expired)
    }
    val buttonIconColor = if (buttonEnabled) Color.White else TextSecondary

    SectionCard(title = stringResource(R.string.settings_widget_title), icon = { MaterialSettingsIcon(SettingsIcon.Widget, Accent) }) {
        Text(
            stringResource(R.string.settings_widget_subtitle),
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2A2A2A))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    statusText,
                    style = TextStyle(color = if (isUnlocked) Green else TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    stringResource(R.string.settings_widget_unlock_limit),
                    style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        ActionButton(
            label = buttonLabel,
            filled = true,
            enabled = buttonEnabled,
            icon = { MaterialSettingsIcon(SettingsIcon.Widget, buttonIconColor, 18) },
            onClick = {
                val ad = rewardedAd
                if (activity == null) {
                    Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
                    return@ActionButton
                }
                if (ad == null) {
                    if (isLoading) {
                        Toast.makeText(context, loadingMessage, Toast.LENGTH_SHORT).show()
                        return@ActionButton
                    }
                    loadAd()
                    Toast.makeText(context, notReadyMessage, Toast.LENGTH_SHORT).show()
                    return@ActionButton
                }
                rewardedAd = null
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        loadAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
                        loadAd()
                    }
                }
                ad.show(activity) {
                    WidgetAccessStore.extendByReward(context)
                    refreshKey++
                    TodoWidgetUpdater.requestUpdate(context)
                    Toast.makeText(context, unlockedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun formatWidgetAccessRemaining(remainingMillis: Long): String {
    val safeRemaining = remainingMillis.coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    val days = TimeUnit.MILLISECONDS.toDays(safeRemaining)
    val hours = TimeUnit.MILLISECONDS.toHours(safeRemaining) % 24
    if (days > 0) return stringResource(R.string.settings_widget_remaining_days, days, hours)

    val minutes = TimeUnit.MILLISECONDS.toMinutes(safeRemaining) % 60
    return if (hours > 0) {
        stringResource(R.string.settings_widget_remaining_hours, hours, minutes)
    } else {
        stringResource(R.string.settings_widget_remaining_minutes, minutes.coerceAtLeast(1))
    }
}

@Composable
private fun syncErrorMessage(type: SyncErrorType): String = when (type) {
    SyncErrorType.AccountMismatch -> stringResource(R.string.settings_sync_error_account_mismatch)
    SyncErrorType.VerificationFailed -> stringResource(R.string.settings_sync_error_verification_failed)
    SyncErrorType.RowLevelSecurity -> stringResource(R.string.settings_sync_error_rls)
    SyncErrorType.Conflict -> stringResource(R.string.settings_sync_error_conflict)
    SyncErrorType.Network -> stringResource(R.string.settings_sync_error_network)
    SyncErrorType.Unknown -> stringResource(R.string.settings_sync_error_unknown)
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        title = { Text(stringResource(R.string.settings_language), style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LanguageOptionRow(stringResource(R.string.settings_language_system), "") { onDismiss() }
                LanguageOptionRow(stringResource(R.string.settings_language_english), "en") { onDismiss() }
                LanguageOptionRow(stringResource(R.string.settings_language_korean), "ko") { onDismiss() }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun LanguageOptionRow(label: String, languageTag: String, onSelected: () -> Unit) {
    val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags() == languageTag
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Accent.copy(alpha = 0.22f) else Color(0xFF2A2A2A)).clickable {
            AppCompatDelegate.setApplicationLocales(
                if (languageTag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(languageTag)
            )
            onSelected()
        }.padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, style = TextStyle(color = if (selected) Accent else TextPrimary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
    }
}

@Composable
private fun currentLanguageLabel(): String {
    return when (AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
        "en" -> stringResource(R.string.settings_language_english)
        "ko" -> stringResource(R.string.settings_language_korean)
        else -> stringResource(R.string.settings_language_system)
    }
}

@Composable
private fun SectionCard(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).border(0.7.dp, CardBorder, RoundedCornerShape(14.dp))) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().height(51.dp).border(0.7.dp, CardBorder).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.SemiBold))
            }
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit, showDivider: Boolean = true, enabled: Boolean = true) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onToggle(!checked) }.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium))
                Text(subtitle, style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp))
            }
            TogglePill(checked, enabled, onToggle)
        }
        if (showDivider) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(CardBorder))
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun TogglePill(checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Box(modifier = Modifier.width(44.dp).height(24.dp).clip(RoundedCornerShape(999.dp)).background(if (checked && enabled) Accent else Color(0xFF4A4A4A)).clickable(enabled = enabled) { onToggle(!checked) }.padding(horizontal = 4.dp, vertical = 4.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun DayStartSelector(selectedHour: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.settings_day_start), style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium))
            Text(stringResource(R.string.settings_day_start_subtitle), style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0..3, 4..6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { hour ->
                        DayStartChip(label = "%02d:00".format(hour), selected = selectedHour == hour, onClick = { onSelect(hour) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayStartChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(34.dp).clip(RoundedCornerShape(999.dp)).background(if (selected) Accent.copy(alpha = 0.28f) else Color(0xFF2A2A2A)).border(0.7.dp, if (selected) Accent else CardBorder, RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(color = if (selected) Accent else ButtonText, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun ActionButton(label: String, filled: Boolean, icon: @Composable () -> Unit, enabled: Boolean = true, onClick: () -> Unit = {}) {
    val backgroundColor = when {
        !enabled -> Color(0xFF2A2A2A)
        filled -> Accent
        else -> ScreenBackground
    }
    val contentColor = when {
        !enabled -> TextSecondary
        filled -> Color.White
        else -> ButtonText
    }
    Box(modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(backgroundColor).border(if (filled) 0.dp else 0.7.dp, CardBorder, RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = TextStyle(color = contentColor, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun AccountSummary(name: String?, email: String) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            MaterialSettingsIcon(SettingsIcon.Account, Color.White, 22)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name ?: stringResource(R.string.settings_google_account), style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(email, style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp), maxLines = 1)
        }
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    subtitle: String,
    icon: SettingsIcon,
    showDivider: Boolean = true,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column {
        val rowModifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 4.dp, vertical = 2.dp)

        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                MaterialSettingsIcon(icon, ButtonText, 18)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(subtitle, style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp), maxLines = 1)
            }
            if (showChevron && onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(CardBorder))
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun android.content.Context.startEmailIntent(address: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private enum class SettingsIcon {
    Notifications, Account, Sync, Logout, Support, Contact, Feedback, Notice, Info, Terms, Privacy, License, Display, Language, Widget
}

@Composable
private fun MaterialSettingsIcon(icon: SettingsIcon, color: Color, size: Int = 18) {
    if (icon == SettingsIcon.Notifications) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val stroke = (size * 0.09f).dp.toPx()
            val w = this.size.width; val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.09f)
                cubicTo(w * 0.22f, h * 0.09f, w * 0.17f, h * 0.30f, w * 0.17f, h * 0.52f)
                lineTo(w * 0.11f, h * 0.70f); lineTo(w * 0.89f, h * 0.70f)
                lineTo(w * 0.83f, h * 0.52f)
                cubicTo(w * 0.83f, h * 0.30f, w * 0.78f, h * 0.09f, w * 0.5f, h * 0.09f)
            }
            drawPath(path, color, style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round))
            drawArc(color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.37f, h * 0.70f), size = Size(w * 0.26f, h * 0.16f), style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        return
    }
    val imageVector = when (icon) {
        SettingsIcon.Notifications -> Icons.Filled.Notifications
        SettingsIcon.Account -> Icons.Filled.AccountCircle
        SettingsIcon.Sync -> Icons.Filled.Sync
        SettingsIcon.Logout -> Icons.AutoMirrored.Filled.Logout
        SettingsIcon.Support -> Icons.AutoMirrored.Filled.HelpOutline
        SettingsIcon.Contact -> Icons.Filled.Mail
        SettingsIcon.Feedback -> Icons.Filled.Feedback
        SettingsIcon.Notice -> Icons.Filled.Campaign
        SettingsIcon.Info -> Icons.Filled.Info
        SettingsIcon.Terms -> Icons.Filled.Description
        SettingsIcon.Privacy -> Icons.Filled.Policy
        SettingsIcon.License -> Icons.Filled.Description
        SettingsIcon.Display -> Icons.Filled.Settings
        SettingsIcon.Language -> Icons.Filled.Language
        SettingsIcon.Widget -> Icons.Filled.Widgets
    }
    Icon(imageVector = imageVector, contentDescription = null, tint = color, modifier = Modifier.size(size.dp))
}
