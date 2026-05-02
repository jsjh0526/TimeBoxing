package dev.jsjh.timebox.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jsjh.timebox.R
import kotlinx.coroutines.launch

private val Background    = Color(0xFF121212)
private val Accent        = Color(0xFF8687E7)
private val TextPrimary   = Color.White
private val TextSecondary = Color(0xFF99A1AF)
private val TextTertiary  = Color(0xFF4A5565)
private val GuestBorder   = Color(0xFF2A2A2A)

private const val FigmaWidth  = 393.318f
private const val FigmaHeight = 852.422f

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope   = rememberCoroutineScope()
    var isLoading    by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .navigationBarsPadding()
    ) {
        val scale = minOf(maxWidth.value / FigmaWidth, maxHeight.value / FigmaHeight)
        fun fdp(value: Float): Dp = (value * scale).dp

        LoginBackground()

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = fdp(244f))
                .width(fdp(345.334f))
                .height(fdp(430f))
        ) {
            // ?? ???꾩씠肄???????????????????????????????????????????????????
            AppMark(
                modifier   = Modifier.align(Alignment.TopCenter),
                markSize   = fdp(63.993f),
                radius     = fdp(16f)
            )

            // ?? ???대쫫 ????????????????????????????????????????????????????
            Text(
                text     = "Timebox",
                modifier = Modifier.offset(x = 0.dp, y = fdp(85.88f)).width(fdp(345.334f)),
                style    = TextStyle(color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.75).sp, lineHeight = 36.sp, textAlign = TextAlign.Center)
            )

            // ?? ?쒕툕??댄? ?????????????????????????????????????????????????
            Text(
                text     = "Focus on what matters most.",
                modifier = Modifier.offset(x = 0.dp, y = fdp(131.27f)).width(fdp(345.334f)),
                style    = TextStyle(color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, textAlign = TextAlign.Center)
            )

            // ?? 援ш? 濡쒓렇??踰꾪듉 ???????????????????????????????????????????
            GoogleSignInButton(
                modifier  = Modifier.offset(x = 0.dp, y = fdp(199.95f)),
                width     = fdp(345.334f),
                height    = fdp(51.986f),
                isLoading = isLoading,
                scale     = scale,
                onClick   = {
                    scope.launch {
                        isLoading    = true
                        errorMessage = null
                        AuthRepository.signInWithGoogle(context)
                        when (val state = AuthRepository.authState.value) {
                            is AuthState.LoggedIn -> onLoginSuccess()
                            is AuthState.Error    -> errorMessage = state.message
                            else -> Unit
                        }
                        isLoading = false
                    }
                }
            )

            // ?? 寃뚯뒪??踰꾪듉 ????????????????????????????????????????????????
            GuestButton(
                modifier = Modifier.offset(x = 0.dp, y = fdp(266f)),
                width    = fdp(345.334f),
                height   = fdp(51.986f),
                scale    = scale,
                onClick  = {
                    AuthRepository.continueAsGuest(context)
                    onLoginSuccess()
                }
            )

            // ?? ?먮윭 硫붿떆吏 ????????????????????????????????????????????????
            if (errorMessage != null) {
                Text(
                    text     = errorMessage.orEmpty(),
                    modifier = Modifier.offset(x = fdp(12f), y = fdp(382f)).width(fdp(321f)),
                    style    = TextStyle(color = Color(0xFFFF6B6B), fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
                )
            }

            // ?? ?쎄? ???????????????????????????????????????????????????????
            Text(
                text     = "By continuing, you agree to our Terms & Conditions.",
                modifier = Modifier.offset(x = fdp(28.04f), y = fdp(338f)).width(fdp(289.258f)),
                style    = TextStyle(color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
private fun GuestButton(
    modifier: Modifier,
    width: Dp,
    height: Dp,
    scale: Float,
    onClick: () -> Unit
) {
    fun fdp(value: Float): Dp = (value * scale).dp

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(fdp(14f)))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = "Continue without account",
            style = TextStyle(
                color      = TextSecondary,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                textAlign  = TextAlign.Center
            )
        )
    }
}

@Composable
private fun LoginBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Background)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF8687E7).copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.46f),
                radius = size.width * 0.72f
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.5f, size.height * 0.46f)
        )
        drawRect(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Background.copy(alpha = 0.55f), Background.copy(alpha = 0.92f))))
        val waveColor = Color.White.copy(alpha = 0.038f)
        repeat(9) { index ->
            val y    = size.height * (0.10f + index * 0.082f)
            val path = Path().apply {
                moveTo(-size.width * 0.28f, y)
                cubicTo(size.width * 0.1f, y - size.height * 0.08f, size.width * 0.68f, y + size.height * 0.06f, size.width * 1.28f, y - size.height * 0.13f)
            }
            drawPath(path = path, color = waveColor, style = Stroke(width = 1.1.dp.toPx()))
        }
        val accentWave = Accent.copy(alpha = 0.028f)
        repeat(4) { index ->
            val y    = size.height * (0.36f + index * 0.12f)
            val path = Path().apply {
                moveTo(-size.width * 0.18f, y)
                cubicTo(size.width * 0.22f, y + size.height * 0.08f, size.width * 0.72f, y - size.height * 0.10f, size.width * 1.18f, y + size.height * 0.03f)
            }
            drawPath(path = path, color = accentWave, style = Stroke(width = 1.dp.toPx()))
        }
        val grainColor = Color.White.copy(alpha = 0.020f)
        repeat(42) { index ->
            val xSeed = ((index * 37) % 101) / 100f
            val ySeed = ((index * 53) % 113) / 112f
            drawCircle(color = grainColor, radius = 0.7.dp.toPx(), center = Offset(x = size.width * xSeed, y = size.height * (0.08f + ySeed * 0.78f)))
        }
    }
}

@Composable
private fun AppMark(modifier: Modifier, markSize: Dp, radius: Dp) {
    Box(
        modifier = modifier
            .size(markSize)
            .shadow(elevation = 14.dp, shape = RoundedCornerShape(radius), ambientColor = Accent.copy(alpha = 0.30f), spotColor = Accent.copy(alpha = 0.30f))
            .clip(RoundedCornerShape(radius))
            .background(Accent),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_timebox_logo),
            contentDescription = null,
            modifier = Modifier.size(markSize * 0.92f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun GoogleSignInButton(modifier: Modifier, width: Dp, height: Dp, isLoading: Boolean, scale: Float, onClick: () -> Unit) {
    fun fdp(value: Float): Dp = (value * scale).dp
    Box(
        modifier = modifier
            .width(width).height(height)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(fdp(14f)), ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(fdp(14f)))
            .background(Color.White)
            .clickable(enabled = !isLoading, onClick = onClick)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(fdp(22f)), color = Accent, strokeWidth = 2.dp)
        } else {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(fdp(14f))
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = "Google",
                    modifier = Modifier.size(fdp(20f))
                )
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
