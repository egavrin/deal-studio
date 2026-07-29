package com.offlineassistant.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AssistantColors {
    val Primary = Color(0xFF176BEF)
    val PrimarySoft = Color(0xFFEAF2FF)
    val Screen = Color(0xFFFCFCFD)
    val Surface = Color.White
    val Text = Color(0xFF111827)
    val Muted = Color(0xFF697386)
    val Border = Color(0xFFE6EAF0)
    val Success = Color(0xFF16845B)
    val Warning = Color(0xFF9A6700)
    val Danger = Color(0xFFC2414B)
}

private val AssistantColorScheme = lightColorScheme(
    primary = AssistantColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AssistantColors.PrimarySoft,
    onPrimaryContainer = AssistantColors.Text,
    background = AssistantColors.Screen,
    onBackground = AssistantColors.Text,
    surface = AssistantColors.Surface,
    onSurface = AssistantColors.Text,
    surfaceVariant = Color(0xFFF5F7FA),
    onSurfaceVariant = AssistantColors.Muted,
    outline = AssistantColors.Border,
    error = AssistantColors.Danger
)

private val AssistantDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9FC5FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF12477C),
    onPrimaryContainer = Color(0xFFD4E4FF),
    background = Color(0xFF101419),
    onBackground = Color(0xFFE2E7EF),
    surface = Color(0xFF171C22),
    onSurface = Color(0xFFE2E7EF),
    surfaceVariant = Color(0xFF222931),
    onSurfaceVariant = Color(0xFFBAC3CF),
    outline = Color(0xFF414A55),
    error = Color(0xFFFFB3B8),
    onError = Color(0xFF680014)
)

private val AssistantTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
)

@Composable
fun AssistantTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AssistantDarkColorScheme else AssistantColorScheme,
        typography = AssistantTypography,
        content = content
    )
}
