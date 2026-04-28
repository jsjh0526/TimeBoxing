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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit
) {
    val authState by AuthRepository.authState.collectAsState()
    val syncState by SyncManager.state.collectAsState()

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
            SectionCard(title = "Notifications", icon = { BellIcon(Accent) }) {
                ToggleRow("Notifications", "Allow alerts for time blocks", reminderSettings.notificationsEnabled, { onReminderSettingsChange(reminderSettings.copy(notificationsEnabled = it)) })
                ToggleRow("Sound", "Play sound when a reminder fires", reminderSettings.soundEnabled, { onReminderSettingsChange(reminderSettings.copy(soundEnabled = it)) }, enabled = reminderSettings.notificationsEnabled)
                ToggleRow("Vibration", "Vibrate with reminders", reminderSettings.vibrationEnabled, { onReminderSettingsChange(reminderSettings.copy(vibrationEnabled = it)) }, enabled = reminderSettings.notificationsEnabled, showDivider = false)
            }
        }

        // ── Account ────────────────────────────────────────────────────────
        item {
            SectionCard(title = "Account", icon = { AccountIcon(Accent) }) {
                when (val state = authState) {
                    is AuthState.LoggedIn -> {
                        AccountSummary(
                            name  = state.displayName?.takeIf { it.isNotBlank() },
                            email = state.email.takeIf { it.isNotBlank() } ?: "Signed in"
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(label = "Sign Out", filled = false, icon = { LogoutIcon(ButtonText) }, onClick = onSignOut)
                    }
                    is AuthState.Loading -> AccountSummary(null, "Checking session...")
                    is AuthState.Error   -> AccountSummary(null, state.message)
                    AuthState.Guest      -> AccountSummary(null, "Not signed in")
                }
            }
        }

        // ── Sync ───────────────────────────────────────────────────────────
        item {
            SectionCard(title = "Sync", icon = { SyncIcon(Green) }) {
                // 상태 메시지
                val (statusText, statusColor) = when (syncState) {
                    is SyncState.Idle    -> "데이터를 수동으로 동기화할 수 있어요." to TextSecondary
                    is SyncState.Syncing -> "동기화 중..." to Accent
                    is SyncState.Success -> "마지막 동기화: ${(syncState as SyncState.Success).time}" to Green
                    is SyncState.Error   -> "오류: ${(syncState as SyncState.Error).message}" to Color(0xFFFF5F57)
                }
                Text(statusText, style = TextStyle(color = statusColor, fontSize = 13.sp, lineHeight = 19.sp))
                Spacer(modifier = Modifier.height(14.dp))

                // 동기화 버튼
                val isSyncing = syncState is SyncState.Syncing
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
                            SyncIcon(if (isLoggedIn) Color.White else TextSecondary)
                            Text(
                                text = if (isLoggedIn) "Sync Now" else "로그인 후 사용 가능",
                                style = TextStyle(
                                    color = if (isLoggedIn) Color.White else TextSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── App Info ───────────────────────────────────────────────────────
        item {
            SectionCard(title = "App Info", icon = { InfoIcon(Accent) }) {
                ToggleRow("Local First", "Tasks are stored on this device", true, {}, enabled = false, showDivider = false)
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text("TimeBoxing v1.0.0", style = TextStyle(color = Color(0xFF4A5565), fontSize = 12.sp, lineHeight = 18.sp))
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
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) { AccountIcon(Color.White) }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name ?: "Google Account", style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text(email, style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp), maxLines = 1)
        }
    }
}

// ── 아이콘 ────────────────────────────────────────────────────────────────────

@Composable
private fun BellIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        drawArc(color = color, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(size.width * 0.2f, size.height * 0.15f), size = Size(size.width * 0.6f, size.height * 0.62f), style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.64f), Offset(size.width * 0.72f, size.height * 0.64f), stroke, StrokeCap.Round)
        drawCircle(color, radius = 1.8.dp.toPx(), center = Offset(size.width * 0.5f, size.height * 0.78f))
    }
}

@Composable
private fun SyncIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.7.dp.toPx()
        // 위쪽 화살표 (시계 방향 반원)
        drawArc(color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
            size = Size(size.width * 0.76f, size.height * 0.55f),
            style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.72f, size.height * 0.12f), Offset(size.width * 0.88f, size.height * 0.2f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.88f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.35f), stroke, StrokeCap.Round)
        // 아래쪽 화살표
        drawArc(color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(size.width * 0.12f, size.height * 0.28f),
            size = Size(size.width * 0.76f, size.height * 0.55f),
            style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.88f), Offset(size.width * 0.12f, size.height * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.12f, size.height * 0.8f), Offset(size.width * 0.2f, size.height * 0.65f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun AccountIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        drawCircle(color = color, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.5f, size.height * 0.32f), style = Stroke(width = stroke))
        drawArc(color = color, startAngle = 200f, sweepAngle = 140f, useCenter = false, topLeft = Offset(size.width * 0.22f, size.height * 0.44f), size = Size(size.width * 0.56f, size.height * 0.34f), style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun LogoutIcon(color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(color = color, topLeft = Offset(size.width * 0.16f, size.height * 0.2f), size = Size(size.width * 0.38f, size.height * 0.6f), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(width = stroke))
        drawLine(color, Offset(size.width * 0.48f, size.height * 0.5f), Offset(size.width * 0.86f, size.height * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.68f, size.height * 0.32f), Offset(size.width * 0.86f, size.height * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.68f, size.height * 0.68f), Offset(size.width * 0.86f, size.height * 0.5f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun InfoIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.5.dp.toPx()
        drawCircle(color = color, radius = size.minDimension * 0.38f, center = center, style = Stroke(width = stroke))
        drawLine(color, Offset(center.x, size.height * 0.44f), Offset(center.x, size.height * 0.68f), stroke, StrokeCap.Round)
        drawCircle(color = color, radius = 1.3.dp.toPx(), center = Offset(center.x, size.height * 0.3f))
    }
}
