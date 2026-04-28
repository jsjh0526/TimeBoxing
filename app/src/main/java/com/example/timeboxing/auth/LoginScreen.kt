package com.example.timeboxing.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Background = Color(0xFF121212)
private val Accent = Color(0xFF8687E7)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF99A1AF)
private val TextTertiary = Color(0xFF4A5565)

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        val contentTop = maxHeight * 0.324f

        LoginBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(contentTop))

            AppMark()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Timebox",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.75).sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Focus on what matters most.",
                style = TextStyle(
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            GoogleSignInButton(
                isLoading = isLoading,
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        AuthRepository.signInWithGoogle(context)
                        when (val state = AuthRepository.authState.value) {
                            is AuthState.LoggedIn -> onLoginSuccess()
                            is AuthState.Error -> errorMessage = state.message
                            else -> Unit
                        }
                        isLoading = false
                    }
                }
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    style = TextStyle(
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(if (errorMessage == null) 32.dp else 18.dp))

            Text(
                text = "By continuing, you agree to our Terms & Conditions.",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun LoginBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF101010),
                    Color(0xFF151515),
                    Background
                )
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF8687E7).copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.46f),
                radius = size.width * 0.7f
            ),
            radius = size.width * 0.7f,
            center = Offset(size.width * 0.5f, size.height * 0.46f)
        )

        val waveColor = Color.White.copy(alpha = 0.018f)
        repeat(7) { index ->
            val y = size.height * (0.16f + index * 0.075f)
            val path = Path().apply {
                moveTo(-size.width * 0.25f, y)
                cubicTo(
                    size.width * 0.15f,
                    y - size.height * 0.09f,
                    size.width * 0.65f,
                    y + size.height * 0.05f,
                    size.width * 1.25f,
                    y - size.height * 0.14f
                )
            }
            drawPath(path = path, color = waveColor, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Accent.copy(alpha = 0.35f),
                spotColor = Accent.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Accent),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val stroke = 3.dp.toPx()
            drawLine(
                color = Color.White,
                start = Offset(size.width * 0.5f, size.height * 0.08f),
                end = Offset(size.width * 0.5f, size.height * 0.92f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width * 0.08f, size.height * 0.5f),
                end = Offset(size.width * 0.92f, size.height * 0.5f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(enabled = !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Accent,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GoogleLogo(modifier = Modifier.size(20.dp))
                Text(
                    text = "Continue with Google",
                    style = TextStyle(
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val stroke = size.minDimension * 0.16f
        val arcSize = androidx.compose.ui.geometry.Size(r * 2, r * 2)
        val topLeft = Offset(cx - r, cy - r)

        drawArc(Color(0xFFEA4335), -10f, 100f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(Color(0xFFFBBC05), 90f, 90f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(Color(0xFF34A853), 180f, 90f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(Color(0xFF4285F4), 270f, 80f, false, topLeft, arcSize, style = Stroke(stroke))
        drawLine(Color(0xFF4285F4), Offset(cx, cy), Offset(cx + r * 0.85f, cy), stroke, StrokeCap.Round)
    }
}
