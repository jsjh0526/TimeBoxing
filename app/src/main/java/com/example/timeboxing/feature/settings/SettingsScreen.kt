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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.timeboxing.notification.ReminderSettings

private val ScreenBackground = Color(0xFF121212)
private val CardBackground = Color(0xFF1E1E1E)
private val CardBorder = Color(0xFF2A2A2A)
private val Accent = Color(0xFF8687E7)
private val Green = Color(0xFF7AE582)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF6A7282)
private val ButtonText = Color(0xFFD1D5DC)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    reminderSettings: ReminderSettings,
    onReminderSettingsChange: (ReminderSettings) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Settings",
                    style = TextStyle(
                        color = TextPrimary,
                        fontSize = 28.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.7).sp
                    )
                )
                Text(
                    text = "Manage your preferences",
                    style = TextStyle(color = TextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                )
            }
        }
        item {
            SectionCard(
                title = "Notifications",
                icon = { BellIcon(Accent) }
            ) {
                ToggleRow(
                    title = "Notifications",
                    subtitle = "Allow alerts for time blocks",
                    checked = reminderSettings.notificationsEnabled,
                    onToggle = { onReminderSettingsChange(reminderSettings.copy(notificationsEnabled = it)) }
                )
                ToggleRow(
                    title = "Sound",
                    subtitle = "Play sound when a reminder fires",
                    checked = reminderSettings.soundEnabled,
                    enabled = reminderSettings.notificationsEnabled,
                    onToggle = { onReminderSettingsChange(reminderSettings.copy(soundEnabled = it)) }
                )
                ToggleRow(
                    title = "Vibration",
                    subtitle = "Vibrate with reminders",
                    checked = reminderSettings.vibrationEnabled,
                    enabled = reminderSettings.notificationsEnabled,
                    onToggle = { onReminderSettingsChange(reminderSettings.copy(vibrationEnabled = it)) },
                    showDivider = false
                )
            }
        }
        item {
            SectionCard(
                title = "Data Management",
                icon = { StackIcon(Green) }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "Local data tools",
                        style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Cleanup and reset controls will live here.",
                        style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                ActionButton(label = "Coming Soon", filled = false, icon = { StackIcon(ButtonText) })
            }
        }
        item {
            SectionCard(
                title = "App Info",
                icon = { InfoIcon(Accent) }
            ) {
                ToggleRow(
                    title = "Local First",
                    subtitle = "Tasks are stored on this device",
                    checked = true,
                    enabled = false,
                    onToggle = {},
                    showDivider = false
                )
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "TimeBoxing v1.0.0",
                    style = TextStyle(color = Color(0xFF4A5565), fontSize = 12.sp, lineHeight = 18.sp)
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(0.7.dp, CardBorder, RoundedCornerShape(14.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(51.dp)
                    .border(0.7.dp, CardBorder)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.SemiBold)
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    showDivider: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    enabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onToggle(!checked) }
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    text = subtitle,
                    style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                )
            }
            TogglePill(checked = checked, enabled = enabled, onToggle = onToggle)
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
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked && enabled) Accent else Color(0xFF4A4A4A))
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    filled: Boolean,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (filled) 45.dp else 46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) Accent else ScreenBackground)
            .border(if (filled) 0.dp else 0.7.dp, CardBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = if (filled) Color.White else ButtonText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

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
private fun StackIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.5.dp.toPx()
        repeat(3) { index ->
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.18f, size.height * (0.18f + index * 0.22f)),
                size = Size(size.width * 0.64f, size.height * 0.18f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = stroke)
            )
        }
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
