package dev.jsjh.timebox.feature.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jsjh.timebox.R

@Composable
internal fun AppAnnouncementDialog(
    announcement: AppAnnouncement,
    displayCount: Int,
    onOpenSurvey: () -> Boolean,
    onDismiss: (source: String, suppress: Boolean) -> Unit
) {
    val accent = Color(0xFF8687E7)
    val textMuted = Color(0xFFB0B5C0)
    val title = stringResource(announcement.titleRes)
    val message = stringResource(announcement.messageRes)
    val surveyLabel = stringResource(R.string.announcement_take_survey)
    val suppressLabel = stringResource(R.string.announcement_do_not_show_again)
    val closeLabel = stringResource(announcement.confirmRes)
    val canSuppress = displayCount >= 4
    var suppressedAnnouncementId by rememberSaveable { mutableStateOf<String?>(null) }
    val suppress = suppressedAnnouncementId == announcement.id
    fun dismiss(source: String) = onDismiss(source, canSuppress && suppress)

    AlertDialog(
        onDismissRequest = { dismiss("outside") },
        modifier = Modifier.testTag("app_announcement"),
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        title = {
            Text(
                title,
                modifier = Modifier.testTag("announcement_title"),
                style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("announcement_content"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    message,
                    modifier = Modifier.testTag("announcement_message"),
                    style = TextStyle(color = textMuted, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp)
                )
                if (canSuppress) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(suppress, role = Role.Checkbox, onValueChange = {
                                suppressedAnnouncementId = if (it) announcement.id else null
                            })
                            .testTag("announcement_suppress"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(suppress, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = accent))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            suppressLabel,
                            style = TextStyle(color = textMuted, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { if (onOpenSurvey()) dismiss("survey") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("announcement_survey"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(surveyLabel, textAlign = TextAlign.Center, style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp))
                }
                TextButton(
                    onClick = { dismiss("confirm") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("announcement_close"),
                    colors = ButtonDefaults.textButtonColors(contentColor = textMuted)
                ) { Text(closeLabel, style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp)) }
            }
        }
    )
}
