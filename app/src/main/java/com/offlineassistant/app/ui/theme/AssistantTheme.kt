package com.offlineassistant.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Host-owned colors. Generated applications use semantic roles rather than raw values. */
object DealStudioColors {
    val Primary = Color(0xFF0B5FEA)
    val PrimaryStrong = Color(0xFF0047BC)
    val PrimarySoft = Color(0xFFE8F0FF)
    val Secondary = Color(0xFF087A61)
    val SecondarySoft = Color(0xFFDDF5EC)
    val Tertiary = Color(0xFF8A5B00)
    val TertiarySoft = Color(0xFFFFEEC7)
    val Screen = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF5F7FA)
    val SurfaceRaised = Color(0xFFFAFBFC)
    val Text = Color(0xFF15171C)
    val Muted = Color(0xFF626A78)
    val Border = Color(0xFFDDE1E7)
    val BorderStrong = Color(0xFFB8C0CC)
    val Success = Color(0xFF087A4F)
    val SuccessSoft = Color(0xFFDDF5E8)
    val Warning = Color(0xFF8A5B00)
    val WarningSoft = Color(0xFFFFEEC7)
    val Danger = Color(0xFFB32635)
    val DangerSoft = Color(0xFFFFDADD)
}

/** Compatibility facade while the package namespace is intentionally left unchanged. */
object AssistantColors {
    val Primary = DealStudioColors.Primary
    val PrimarySoft = DealStudioColors.PrimarySoft
    val Screen = DealStudioColors.Screen
    val Surface = DealStudioColors.Surface
    val Text = DealStudioColors.Text
    val Muted = DealStudioColors.Muted
    val Border = DealStudioColors.Border
    val Success = DealStudioColors.Success
    val Warning = DealStudioColors.Warning
    val Danger = DealStudioColors.Danger
}

object DealStudioSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}

object DealStudioElevation {
    val Flat = 0.dp
    val Raised = 1.dp
    val Overlay = 6.dp
}

object DealStudioMotion {
    const val QuickMillis = 120
    const val StandardMillis = 220
    const val EmphasisMillis = 320
}

private val DealStudioLightScheme = lightColorScheme(
    primary = DealStudioColors.Primary,
    onPrimary = Color.White,
    primaryContainer = DealStudioColors.PrimarySoft,
    onPrimaryContainer = DealStudioColors.PrimaryStrong,
    secondary = DealStudioColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = DealStudioColors.SecondarySoft,
    onSecondaryContainer = Color(0xFF064D3E),
    tertiary = DealStudioColors.Tertiary,
    onTertiary = Color.White,
    tertiaryContainer = DealStudioColors.TertiarySoft,
    onTertiaryContainer = Color(0xFF553600),
    background = DealStudioColors.Screen,
    onBackground = DealStudioColors.Text,
    surface = DealStudioColors.Surface,
    onSurface = DealStudioColors.Text,
    surfaceVariant = DealStudioColors.SurfaceSubtle,
    onSurfaceVariant = DealStudioColors.Muted,
    surfaceTint = DealStudioColors.Primary,
    inverseSurface = Color(0xFF2D3036),
    inverseOnSurface = Color(0xFFF2F3F5),
    outline = DealStudioColors.BorderStrong,
    outlineVariant = DealStudioColors.Border,
    error = DealStudioColors.Danger,
    onError = Color.White,
    errorContainer = DealStudioColors.DangerSoft,
    onErrorContainer = Color(0xFF6F1220)
)

private val DealStudioDarkScheme = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF174B94),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFF7CDBC0),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF00513F),
    onSecondaryContainer = Color(0xFF9AF8DC),
    tertiary = Color(0xFFFFC963),
    onTertiary = Color(0xFF492A00),
    tertiaryContainer = Color(0xFF674000),
    onTertiaryContainer = Color(0xFFFFDEA4),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE4E6EB),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE4E6EB),
    surfaceVariant = Color(0xFF24272E),
    onSurfaceVariant = Color(0xFFC2C6D0),
    surfaceTint = Color(0xFFAFC6FF),
    inverseSurface = Color(0xFFE4E6EB),
    inverseOnSurface = Color(0xFF2E3036),
    outline = Color(0xFF8C919C),
    outlineVariant = Color(0xFF41454E),
    error = Color(0xFFFFB2B8),
    onError = Color(0xFF670015),
    errorContainer = Color(0xFF8F001F),
    onErrorContainer = Color(0xFFFFDADC)
)

private val DealStudioTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    )
)

private val DealStudioShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

@Composable
fun DealStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DealStudioDarkScheme else DealStudioLightScheme,
        typography = DealStudioTypography,
        shapes = DealStudioShapes,
        content = content
    )
}

@Composable
fun AssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = DealStudioTheme(darkTheme = darkTheme, content = content)
