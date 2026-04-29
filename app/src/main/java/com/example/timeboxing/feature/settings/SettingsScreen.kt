package com.example.timeboxing.feature.settings

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeboxing.auth.AuthRepository
import com.example.timeboxing.auth.AuthState
import com.example.timeboxing.data.remote.SyncManager
import com.example.timeboxing.data.remote.SyncState
import com.example.timeboxing.notification.ReminderSettings

private val ScreenBackground = Color(0xFF121212)
private val CardBackground   = Color(0xFF1E1E1E)
private val CardBorder       = Color(0xFF2A2A2A)
private val Accent           = Color(0xFF8687E7)
private val Green            = Color(0xFF7AE582)
private val TextPrimary      = Color.White
private val TextSecondary    = Color(0xFF6A7282)
private val ButtonText       = Color(0xFFD1D5DC)

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

    // Fix 3: 앱 진입 시 1번만 실행 (syncState 변경 시 재실행 제거)
    // syncAll() 완료 시 SyncManager 내부에서 remoteStatus를 이미 업데이트하므로 중복 불필요
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
                Text("Settings", style = TextStyle(color = TextPrimary, fontSize = 28.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp))
                Text("Manage your preferences", style = TextStyle(color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp))
            }
        }

        // ── Notifications ──────────────────────────────────────────────────
        item {
            SectionCard(title = "Notifications", icon = { MaterialSettingsIcon(SettingsIcon.Notifications, Accent) }) {
                ToggleRow("Notifications", "Allow alerts for time blocks", reminderSettings.notificationsEnabled, { onReminderSettingsChange(reminderSettings.copy(notificationsEnabled = it)) })
                ToggleRow("Sound", "Play sound when a reminder fires", reminderSettings.soundEnabled, { onReminderSettingsChange(reminderSettings.copy(soundEnabled = it)) }, enabled = reminderSettings.notificationsEnabled)
                ToggleRow("Vibration", "Vibrate with reminders", reminderSettings.vibrationEnabled, { onReminderSettingsChange(reminderSettings.copy(vibrationEnabled = it)) }, enabled = reminderSettings.notificationsEnabled, showDivider = false)
            }
        }

        // ── Account ────────────────────────────────────────────────────────
        item {
            SectionCard(title = "Account", icon = { MaterialSettingsIcon(SettingsIcon.Account, Accent) }) {
                when (val state = authState) {
                    is AuthState.LoggedIn -> {
                        AccountSummary(
                            name  = state.displayName?.takeIf { it.isNotBlank() },
                            email = state.email.takeIf { it.isNotBlank() } ?: "Signed in"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(label = "Sign Out", filled = false, icon = { MaterialSettingsIcon(SettingsIcon.Logout, ButtonText, 18) }, onClick = onSignOut)
                    }
                    is AuthState.Loading -> AccountSummary(null, "Checking session...")
                    is AuthState.Error   -> AccountSummary(null, state.message)
                    AuthState.Guest      -> {
                        AccountSummary("Guest mode", "Local data only")
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(label = "Sign in with Google", filled = true, icon = { MaterialSettingsIcon(SettingsIcon.Account, Color.White, 18) }, onClick = onSignIn)
                    }
                    AuthState.SignedOut  -> AccountSummary(null, "Not signed in")
                }
            }
        }

        // ── Sync ───────────────────────────────────────────────────────────
        item {
            SectionCard(title = "Sync", icon = { MaterialSettingsIcon(SettingsIcon.Sync, Green) }) {
                val (statusText, statusColor) = when (syncState) {
                    is SyncState.Idle    -> "데이터를 수동으로 동기화할 수 있어요." to TextSecondary
                    is SyncState.Syncing -> "동기화 중..." to Accent
                    is SyncState.Success -> "마지막 동기화: ${(syncState as SyncState.Success).time}" to Green
                    is SyncState.Error   -> "오류: ${(syncState as SyncState.Error).message}" to Color(0xFFFF5F57)
                }
                Text(statusText, style = TextStyle(color = statusColor, fontSize = 13.sp, lineHeight = 19.sp))
                remoteStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Remote: ${status.taskCount} tasks / ${status.templateCount} habits · checked ${status.checkedAt}",
                        style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                val isSyncing  = syncState is SyncState.Syncing
                val isLoggedIn = authState is AuthState.LoggedIn
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLoggedIn && !isSyncing) Accent else Color(0xFF2A2A2A))
                        .clickable(enabled = isLoggedIn && !isSyncing, onClick = onSyncNow),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MaterialSettingsIcon(SettingsIcon.Sync, if (isLoggedIn) Color.White else TextSecondary, 18)
                            Text(
                                text = if (isLoggedIn) "Sync Now" else "로그인 후 사용 가능",
                                style = TextStyle(color = if (isLoggedIn) Color.White else TextSecondary, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Support", icon = { MaterialSettingsIcon(SettingsIcon.Support, Accent) }) {
                SettingsMenuRow(
                    title = "문의하기",
                    subtitle = "앱 사용 중 궁금한 점을 보내주세요",
                    icon = SettingsIcon.Contact,
                    showChevron = true,
                    onClick = { uriHandler.openUri("mailto:support@timeboxing.app?subject=TimeBoxing%20%EB%AC%B8%EC%9D%98") }
                )
                SettingsMenuRow(
                    title = "피드백 보내기",
                    subtitle = "버그나 개선 아이디어를 알려주세요",
                    icon = SettingsIcon.Feedback,
                    showChevron = true,
                    onClick = { uriHandler.openUri("mailto:support@timeboxing.app?subject=TimeBoxing%20%ED%94%BC%EB%93%9C%EB%B0%B1") }
                )
                SettingsMenuRow(
                    title = "공지사항",
                    subtitle = "업데이트 소식과 변경사항",
                    icon = SettingsIcon.Notice,
                    showDivider = false
                )
            }
        }

        item {
            SectionCard(title = "About", icon = { MaterialSettingsIcon(SettingsIcon.Info, Accent) }) {
                SettingsMenuRow("이용약관", "서비스 이용 기준", SettingsIcon.Terms)
                SettingsMenuRow("개인정보처리방침", "계정 및 동기화 데이터 안내", SettingsIcon.Privacy)
                SettingsMenuRow("오픈소스 라이선스", "사용된 라이브러리 정보", SettingsIcon.License)
                SettingsMenuRow("앱 버전", "TimeBoxing v1.0.0", SettingsIcon.Info, showChevron = false, showDivider = false)
            }
        }

    }
}

// ── 공통 컴포넌트 ──────────────────────────────────────────────────────────────

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
            Text(name ?: "Google Account", style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
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
        // 아웃라인 벨 (Canvas)
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
        SettingsIcon.Notifications -> Icons.Filled.Notifications  // 위에서 early return
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
