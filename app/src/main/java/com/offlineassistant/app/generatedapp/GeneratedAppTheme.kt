package com.offlineassistant.app.generatedapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class GeneratedAppThemeSpec(
    val primary: String = DEFAULT_PRIMARY,
    val secondary: String = DEFAULT_SECONDARY,
    val style: String = DEFAULT_STYLE,
    val shape: String = DEFAULT_SHAPE,
    val density: String = DEFAULT_DENSITY,
    val surface: String = DEFAULT_SURFACE,
    val typography: String = DEFAULT_TYPOGRAPHY,
    val contrast: String = DEFAULT_CONTRAST,
    val background: String = DEFAULT_BACKGROUND,
    val motion: String = DEFAULT_MOTION
) {
    fun asDealUiArguments(): String = buildString {
        append("primary: \"")
        append(primary)
        append("\", secondary: \"")
        append(secondary)
        append("\", style: ui.theme${style.tokenSuffix()}, shape: ui.shape${shape.tokenSuffix()}, ")
        append("density: ui.density${density.tokenSuffix()}, surface: ui.surface${surface.tokenSuffix()}, ")
        append("typography: ui.typography${typography.tokenSuffix()}, contrast: ui.contrast${contrast.tokenSuffix()}, ")
        append("background: ui.background${background.tokenSuffix()}, motion: ui.motion${motion.tokenSuffix()}")
    }

    companion object {
        const val DEFAULT_PRIMARY = "#2563EB"
        const val DEFAULT_SECONDARY = "#0F766E"
        const val DEFAULT_STYLE = "clean"
        const val DEFAULT_SHAPE = "rounded"
        const val DEFAULT_DENSITY = "comfortable"
        const val DEFAULT_SURFACE = "tonal"
        const val DEFAULT_TYPOGRAPHY = "neutral"
        const val DEFAULT_CONTRAST = "standard"
        const val DEFAULT_BACKGROUND = "solid"
        const val DEFAULT_MOTION = "restrained"

        val DEFAULT = GeneratedAppThemeSpec()
        val THEME_KEYS = setOf("primary", "secondary", "style", "shape", "density", "surface", "typography", "contrast", "background", "motion")
        val STYLES = setOf("clean", "soft", "expressive", "editorial", "technical", "playful")

        // Preserve narrow runtime input aliases so previously checked in-memory IR fails softly during rollout.
        val SHAPES = setOf("geometric", "rounded", "soft", "pill-controls", "pill")
        val DENSITIES = setOf("compact", "comfortable", "spacious")
        val SURFACES = setOf("flat", "tonal", "outlined", "layered", "elevated")
        val TYPOGRAPHIES = setOf("neutral", "editorial", "technical", "friendly", "expressive")
        val CONTRASTS = setOf("standard", "high")
        val BACKGROUNDS = setOf("solid", "tonal", "atmospheric")
        val MOTIONS = setOf("none", "restrained", "expressive")

        fun fromTool(value: JsonObject): GeneratedAppThemeSpec = validated(
            primary = value.string("primary"),
            secondary = value.string("secondary"),
            style = value.string("style"),
            shape = value.string("shape"),
            density = value.string("density"),
            surface = value.string("surface"), typography = value.string("typography"),
            contrast = value.string("contrast"), background = value.string("background"), motion = value.string("motion")
        )

        fun validated(
            primary: String,
            secondary: String,
            style: String,
            shape: String,
            density: String,
            surface: String,
            typography: String = DEFAULT_TYPOGRAPHY,
            contrast: String = DEFAULT_CONTRAST,
            background: String = DEFAULT_BACKGROUND,
            motion: String = DEFAULT_MOTION
        ): GeneratedAppThemeSpec {
            require(primary.matches(HEX_COLOR)) { "Theme primary must be a six-digit hex colour" }
            require(secondary.matches(HEX_COLOR)) { "Theme secondary must be a six-digit hex colour" }
            require(style in STYLES) { "Unsupported generated-app theme style: $style" }
            require(shape in SHAPES) { "Unsupported generated-app theme shape: $shape" }
            require(density in DENSITIES) { "Unsupported generated-app theme density: $density" }
            require(surface in SURFACES) { "Unsupported generated-app surface treatment: $surface" }
            require(typography in TYPOGRAPHIES) { "Unsupported generated-app typography: $typography" }
            require(contrast in CONTRASTS) { "Unsupported generated-app contrast: $contrast" }
            require(background in BACKGROUNDS) { "Unsupported generated-app background: $background" }
            require(motion in MOTIONS) { "Unsupported generated-app motion: $motion" }
            return GeneratedAppThemeSpec(
                primary = primary.uppercase(),
                secondary = secondary.uppercase(),
                style = style,
                shape = shape,
                density = density,
                surface = surface, typography = typography, contrast = contrast, background = background, motion = motion
            )
        }

        private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
    }
}

private fun String.tokenSuffix(): String = when (this) {
    "pill" -> "PillControls"
    "elevated" -> "Layered"
    else -> split('-').joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
}

internal data class GeneratedAppVisuals(
    val densityScale: Float,
    val cardElevation: Dp,
    val borderAlpha: Float,
    val minimumRootPadding: Dp,
    val atmosphericBackground: Boolean,
    val motionEnabled: Boolean,
    val motionDurationMillis: Int
)

internal data class GeneratedMotionPolicy(val enabled: Boolean, val durationMillis: Int)

internal fun generatedMotionPolicy(motion: String): GeneratedMotionPolicy = when (motion) {
    "none" -> GeneratedMotionPolicy(false, 0)
    "expressive" -> GeneratedMotionPolicy(true, 360)
    else -> GeneratedMotionPolicy(true, 220)
}

internal data class GeneratedAppSemanticColors(
    val positive: Color,
    val onPositive: Color,
    val positiveContainer: Color,
    val onPositiveContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

internal enum class GeneratedAppHostAppearance { System, Light, Dark }

internal val LocalGeneratedAppHostAppearance = staticCompositionLocalOf { GeneratedAppHostAppearance.System }

internal fun resolveGeneratedAppDark(appearance: GeneratedAppHostAppearance, systemDark: Boolean): Boolean = when (appearance) {
    GeneratedAppHostAppearance.System -> systemDark
    GeneratedAppHostAppearance.Light -> false
    GeneratedAppHostAppearance.Dark -> true
}

internal val LocalGeneratedAppVisuals = staticCompositionLocalOf {
    GeneratedAppVisuals(
        densityScale = 1f,
        cardElevation = 0.dp,
        borderAlpha = 0.12f,
        minimumRootPadding = 20.dp,
        atmosphericBackground = false,
        motionEnabled = true,
        motionDurationMillis = 220
    )
}

internal val LocalGeneratedAppSemanticColors = staticCompositionLocalOf {
    generatedSemanticColors()
}

@Composable
internal fun GeneratedAppTheme(
    spec: GeneratedAppThemeSpec,
    content: @Composable () -> Unit
) {
    val primary = ensureWhiteTextContrast(parseHex(spec.primary))
    val secondary = ensureWhiteTextContrast(parseHex(spec.secondary))
    val containerBlend = when (spec.style) {
        "soft" -> 0.82f
        "expressive" -> 0.74f
        "editorial" -> 0.9f
        "technical" -> 0.94f
        "playful" -> 0.7f
        else -> 0.88f
    }
    val background = when (spec.background) {
        "solid" -> Color(0xFFF8F9FC)
        "atmospheric" -> mix(primary, Color.White, 0.94f)
        else -> mix(primary, Color.White, 0.965f)
    }
    val surface = when (spec.surface) {
        "tonal" -> mix(primary, Color.White, 0.985f)
        else -> Color.White
    }
    val dark = resolveGeneratedAppDark(LocalGeneratedAppHostAppearance.current, isSystemInDarkTheme())
    val lightScheme = lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = mix(primary, Color.White, containerBlend),
        onPrimaryContainer = mix(primary, Color.Black, 0.18f),
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = mix(secondary, Color.White, containerBlend),
        onSecondaryContainer = mix(secondary, Color.Black, 0.18f),
        tertiary = WARNING,
        onTertiary = Color.White,
        tertiaryContainer = mix(WARNING, Color.White, 0.82f),
        onTertiaryContainer = mix(WARNING, Color.Black, 0.28f),
        background = background,
        onBackground = INK,
        surface = surface,
        onSurface = INK,
        surfaceVariant = mix(secondary, Color.White, 0.92f),
        onSurfaceVariant = MUTED_INK,
        outline = if (spec.contrast == "high") mix(primary, Color.Black, 0.55f) else mix(primary, NEUTRAL, 0.82f),
        outlineVariant = mix(primary, Color.White, 0.88f),
        error = ERROR,
        onError = Color.White,
        errorContainer = mix(ERROR, Color.White, 0.84f),
        onErrorContainer = mix(ERROR, Color.Black, 0.25f),
        inverseSurface = INK,
        inverseOnSurface = Color.White,
        inversePrimary = mix(primary, Color.White, 0.35f)
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = mix(primary, Color.White, 0.28f),
            onPrimary = Color.Black,
            primaryContainer = mix(primary, Color.Black, 0.38f),
            onPrimaryContainer = Color.White,
            secondary = mix(secondary, Color.White, 0.28f),
            onSecondary = Color.Black,
            secondaryContainer = mix(secondary, Color.Black, 0.4f),
            onSecondaryContainer = Color.White,
            background = when (spec.background) {
                "solid" -> Color(0xFF101216)
                "atmospheric" -> mix(primary, Color.Black, 0.82f)
                else -> Color(0xFF151820)
            },
            onBackground = Color(0xFFF3F4F7),
            surface = Color(0xFF171A20),
            onSurface = Color(0xFFF3F4F7),
            surfaceVariant = Color(0xFF242832),
            onSurfaceVariant = Color(0xFFC8CCD6),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            outline = if (spec.contrast == "high") Color(0xFFE2E5ED) else Color(0xFF8E929D)
        )
    } else {
        lightScheme
    }
    val typography = when (spec.typography) {
        "technical" -> MaterialTheme.typography.copy(
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
        )

        "editorial" -> MaterialTheme.typography.copy(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Serif),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
        )

        "expressive" -> MaterialTheme.typography.copy(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )

        "friendly" -> MaterialTheme.typography.copy(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
        )

        else -> MaterialTheme.typography
    }
    val shapes = generatedShapes(spec.shape)
    val visuals = generatedVisuals(spec)
    CompositionLocalProvider(
        LocalGeneratedAppVisuals provides visuals,
        LocalGeneratedAppSemanticColors provides generatedSemanticColors(dark)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

internal fun GeneratedAppThemeSpec.withRuntimeValues(
    primary: String,
    secondary: String,
    style: String,
    shape: String,
    density: String,
    surface: String,
    typography: String = this.typography,
    contrast: String = this.contrast,
    background: String = this.background,
    motion: String = this.motion
): GeneratedAppThemeSpec = runCatching {
    GeneratedAppThemeSpec.validated(primary, secondary, style, shape, density, surface, typography, contrast, background, motion)
}.getOrDefault(this)

private fun generatedShapes(shape: String): Shapes = when (shape) {
    "geometric" -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(8.dp)
    )

    "pill-controls", "pill" -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(50),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(8.dp)
    )

    "soft" -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(8.dp)
    )

    else -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(8.dp)
    )
}

private fun generatedVisuals(spec: GeneratedAppThemeSpec): GeneratedAppVisuals {
    val motion = generatedMotionPolicy(spec.motion)
    val densityScale = when (spec.density) {
        "compact" -> 0.82f
        "spacious" -> 1.18f
        else -> 1f
    }
    val elevation = when (spec.surface) {
        "layered", "elevated" -> 2.dp
        "outlined" -> 0.dp
        else -> 0.dp
    }
    val borderAlpha = when (spec.surface) {
        "flat" -> 0.16f
        "layered", "elevated" -> 0.08f
        "outlined" -> 0.3f
        else -> 0.11f
    }
    return GeneratedAppVisuals(
        densityScale = densityScale,
        cardElevation = elevation,
        borderAlpha = borderAlpha,
        minimumRootPadding = when (spec.density) {
            "compact" -> 16.dp
            "spacious" -> 24.dp
            else -> 20.dp
        },
        atmosphericBackground = spec.background == "atmospheric",
        motionEnabled = motion.enabled,
        motionDurationMillis = motion.durationMillis
    )
}

internal fun generatedSemanticColors(dark: Boolean = false) = GeneratedAppSemanticColors(
    positive = POSITIVE,
    onPositive = Color.White,
    positiveContainer = if (dark) mix(POSITIVE, Color.Black, 0.55f) else mix(POSITIVE, Color.White, 0.84f),
    onPositiveContainer = if (dark) mix(POSITIVE, Color.White, 0.78f) else mix(POSITIVE, Color.Black, 0.2f),
    warning = WARNING,
    onWarning = Color.White,
    warningContainer = if (dark) mix(WARNING, Color.Black, 0.62f) else mix(WARNING, Color.White, 0.82f),
    onWarningContainer = if (dark) mix(WARNING, Color.White, 0.78f) else mix(WARNING, Color.Black, 0.36f)
)

private fun JsonObject.string(key: String): String = get(key)?.jsonPrimitive?.contentOrNull
    ?: when (key) {
        "typography" -> GeneratedAppThemeSpec.DEFAULT_TYPOGRAPHY
        "contrast" -> GeneratedAppThemeSpec.DEFAULT_CONTRAST
        "background" -> GeneratedAppThemeSpec.DEFAULT_BACKGROUND
        "motion" -> GeneratedAppThemeSpec.DEFAULT_MOTION
        else -> error("Theme $key must be a string")
    }

private fun parseHex(value: String): Color {
    val rgb = value.removePrefix("#").toLong(16)
    return Color(
        red = ((rgb shr 16) and 0xFF).toFloat() / 255f,
        green = ((rgb shr 8) and 0xFF).toFloat() / 255f,
        blue = (rgb and 0xFF).toFloat() / 255f,
        alpha = 1f
    )
}

private fun ensureWhiteTextContrast(input: Color): Color {
    var result = input
    repeat(12) {
        if (contrastRatio(result, Color.White) >= MIN_TEXT_CONTRAST) return result
        result = mix(result, Color.Black, 0.1f)
    }
    return result
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val bright = maxOf(relativeLuminance(first), relativeLuminance(second))
    val dark = minOf(relativeLuminance(first), relativeLuminance(second))
    return (bright + 0.05f) / (dark + 0.05f)
}

private fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float = if (value <= 0.03928f) {
        value / 12.92f
    } else {
        Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

private fun mix(first: Color, second: Color, secondRatio: Float): Color {
    val ratio = secondRatio.coerceIn(0f, 1f)
    return Color(
        red = first.red * (1f - ratio) + second.red * ratio,
        green = first.green * (1f - ratio) + second.green * ratio,
        blue = first.blue * (1f - ratio) + second.blue * ratio,
        alpha = first.alpha * (1f - ratio) + second.alpha * ratio
    )
}

private const val MIN_TEXT_CONTRAST = 4.5f
private val INK = Color(0xFF171A21)
private val MUTED_INK = Color(0xFF5F6673)
private val NEUTRAL = Color(0xFF737986)
private val POSITIVE = Color(0xFF087A61)
private val WARNING = Color(0xFF9A5B00)
private val ERROR = Color(0xFFB42318)
