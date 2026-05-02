package dev.jsjh.timebox.feature.settings

import android.content.Intent
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
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jsjh.timebox.auth.AuthRepository
import dev.jsjh.timebox.auth.AuthState
import dev.jsjh.timebox.data.remote.SyncManager
import dev.jsjh.timebox.data.remote.SyncState
import dev.jsjh.timebox.notification.ReminderSettings
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity

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
                Text("설정", style = TextStyle(color = TextPrimary, fontSize = 28.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp))
                Text("Manage your preferences", style = TextStyle(color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp))
            }
        }

        item {
            SectionCard(title = "계정", icon = { MaterialSettingsIcon(SettingsIcon.Account, Accent) }) {
                when (val state = authState) {
                    is AuthState.LoggedIn -> {
                        AccountSummary(
                            name  = state.displayName?.takeIf { it.isNotBlank() },
                            email = state.email.takeIf { it.isNotBlank() } ?: "로그인됨"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(label = "로그아웃", filled = false, icon = { MaterialSettingsIcon(SettingsIcon.Logout, ButtonText, 18) }, onClick = onSignOut)
                    }
                    is AuthState.Loading -> AccountSummary(null, "로그인 상태 확인 중...")
                    is AuthState.Error   -> AccountSummary(null, state.message)
                    AuthState.Guest      -> {
                        AccountSummary("게스트 모드", "이 기기에만 저장돼요")
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(label = "Google로 로그인", filled = true, icon = { MaterialSettingsIcon(SettingsIcon.Account, Color.White, 18) }, onClick = onSignIn)
                    }
                    AuthState.SignedOut  -> AccountSummary(null, "로그인되지 않음")
                }
            }
        }

        item {
            SectionCard(title = "동기화", icon = { MaterialSettingsIcon(SettingsIcon.Sync, Green) }) {
                val (statusText, statusColor) = when (syncState) {
                    is SyncState.Idle    -> "수동 동기화 준비 완료" to TextSecondary
                    is SyncState.Syncing -> "최근 변경사항 동기화 중..." to Accent
                    is SyncState.Success -> "마지막 동기화 ${(syncState as SyncState.Success).time}" to Green
                    is SyncState.Error   -> "오류: ${(syncState as SyncState.Error).message}" to Color(0xFFFF5F57)
                }
                Text(statusText, style = TextStyle(color = statusColor, fontSize = 13.sp, lineHeight = 19.sp))
                remoteStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "서버: 할일 ${status.taskCount}개 / 습관 ${status.templateCount}개 · 확인 ${status.checkedAt}",
                        style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                val isSyncing  = syncState is SyncState.Syncing
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(buttonBg)
                        .clickable(enabled = buttonEnabled, onClick = onSyncNow),
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
                                isSyncing -> "동기화 중..."
                                isLoggedIn -> "지금 동기화"
                                else -> "로그인 후 동기화"
                            },
                            style = TextStyle(color = buttonContentColor, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "알림", icon = { MaterialSettingsIcon(SettingsIcon.Notifications, Accent) }) {
                ToggleRow("알림", "타임블록 시작 알림을 받아요", reminderSettings.notificationsEnabled, { onReminderSettingsChange(reminderSettings.copy(notificationsEnabled = it)) })
                ToggleRow("소리", "알림이 올 때 소리를 재생해요", reminderSettings.soundEnabled, { onReminderSettingsChange(reminderSettings.copy(soundEnabled = it)) }, enabled = reminderSettings.notificationsEnabled)
                ToggleRow("진동", "알림이 올 때 진동으로 알려줘요", reminderSettings.vibrationEnabled, { onReminderSettingsChange(reminderSettings.copy(vibrationEnabled = it)) }, enabled = reminderSettings.notificationsEnabled, showDivider = false)
            }
        }

        item {
            SectionCard(title = "Support", icon = { MaterialSettingsIcon(SettingsIcon.Support, Accent) }) {
                SettingsMenuRow(
                    title = "공지사항",
                    subtitle = "업데이트 소식과 변경사항",
                    icon = SettingsIcon.Notice,
                    showChevron = true,
                    onClick = { uriHandler.openUri(NOTICE_URL) }
                )
                SettingsMenuRow(
                    title = "문의하기",
                    subtitle = "앱 사용 중 궁금한 점을 보내주세요",
                    icon = SettingsIcon.Contact,
                    showChevron = true,
                    onClick = { uriHandler.openUri("mailto:$CONTACT_EMAIL?subject=TimeBoxing%20%EB%AC%B8%EC%9D%98") }
                )
                SettingsMenuRow(
                    title = "피드백 보내기",
                    subtitle = "버그나 개선 아이디어를 알려주세요",
                    icon = SettingsIcon.Feedback,
                    showChevron = true,
                    showDivider = false,
                    onClick = { uriHandler.openUri("mailto:$CONTACT_EMAIL?subject=TimeBoxing%20%ED%94%BC%EB%93%9C%EB%B0%B1") }
                )
            }
        }

        item {
            SectionCard(title = "About", icon = { MaterialSettingsIcon(SettingsIcon.Info, Accent) }) {
                SettingsMenuRow("이용약관", "서비스 이용 기준", SettingsIcon.Terms, showChevron = true, onClick = { uriHandler.openUri(TERMS_URL) })
                SettingsMenuRow("개인정보처리방침", "계정 및 동기화 데이터 안내", SettingsIcon.Privacy, showChevron = true, onClick = { uriHandler.openUri(PRIVACY_URL) })
                SettingsMenuRow("오픈소스 라이선스", "사용된 라이브러리 정보", SettingsIcon.License, showChevron = true, onClick = {
                    OssLicensesMenuActivity.setActivityTitle("오픈소스 라이선스")
                    context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                })
                SettingsMenuRow("앱 버전", "TimeBox v1.0.0", SettingsIcon.Info, showChevron = false, showDivider = false)
            }
        }

    }
}

// ?? 怨듯넻 而댄룷?뚰듃 ??????????????????????????????????????????????????????????????

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
private fun ActionButton(label: String, filled: Boolean, icon: @Composable () -> Unit, onClick: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(if (filled) Accent else ScreenBackground).border(if (filled) 0.dp else 0.7.dp, CardBorder, RoundedCornerShape(10.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = TextStyle(color = if (filled) Color.White else ButtonText, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold))
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
            Text(name ?: "Google 계정", style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
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
        val rowModifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp)

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

private enum class SettingsIcon {
    Notifications,
    Account,
    Sync,
    Logout,
    Support,
    Contact,
    Feedback,
    Notice,
    Info,
    Terms,
    Privacy,
    License
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
            drawArc(color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(w * 0.37f, h * 0.70f), size = Size(w * 0.26f, h * 0.16f),
                style = Stroke(width = stroke, cap = StrokeCap.Round))
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
    }
    Icon(imageVector = imageVector, contentDescription = null, tint = color, modifier = Modifier.size(size.dp))
}
