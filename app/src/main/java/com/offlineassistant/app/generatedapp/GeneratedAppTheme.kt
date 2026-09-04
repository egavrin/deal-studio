package com.offlineassistant.app.generatedapp

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
    val surface: String = DEFAULT_SURFACE
) {
    fun asDealUiArguments(): String = buildString {
        append("primary: \"")
        append(primary)
        append("\", secondary: \"")
        append(secondary)
        append("\", style: \"")
        append(style)
        append("\", shape: \"")
        append(shape)
        append("\", density: \"")
        append(density)
        append("\", surface: \"")
        append(surface)
        append('"')
    }

    companion object {
        const val DEFAULT_PRIMARY = "#2563EB"
        const val DEFAULT_SECONDARY = "#0F766E"
        const val DEFAULT_STYLE = "clean"
        const val DEFAULT_SHAPE = "rounded"
        const val DEFAULT_DENSITY = "comfortable"
        const val DEFAULT_SURFACE = "tonal"

        val DEFAULT = GeneratedAppThemeSpec()
        val THEME_KEYS = setOf("primary", "secondary", "style", "shape", "density", "surface")
        val STYLES = setOf("clean", "soft", "expressive")
        val SHAPES = setOf("geometric", "rounded", "pill")
        val DENSITIES = setOf("compact", "comfortable", "spacious")
        val SURFACES = setOf("flat", "tonal", "elevated")

        fun fromTool(value: JsonObject): GeneratedAppThemeSpec = validated(
            primary = value.string("primary"),
            secondary = value.string("secondary"),
            style = value.string("style"),
            shape = value.string("shape"),
            density = value.string("density"),
            surface = value.string("surface")
        )

        fun validated(
            primary: String,
            secondary: String,
            style: String,
            shape: String,
            density: String,
            surface: String
        ): GeneratedAppThemeSpec {
            require(primary.matches(HEX_COLOR)) { "Theme primary must be a six-digit hex colour" }
            require(secondary.matches(HEX_COLOR)) { "Theme secondary must be a six-digit hex colour" }
            require(style in STYLES) { "Unsupported generated-app theme style: $style" }
            require(shape in SHAPES) { "Unsupported generated-app theme shape: $shape" }
            require(density in DENSITIES) { "Unsupported generated-app theme density: $density" }
            require(surface in SURFACES) { "Unsupported generated-app surface treatment: $surface" }
            return GeneratedAppThemeSpec(
                primary = primary.uppercase(),
                secondary = secondary.uppercase(),
                style = style,
                shape = shape,
                density = density,
                surface = surface
            )
        }

        private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
    }
}

internal data class GeneratedAppVisuals(
    val densityScale: Float,
    val cardElevation: Dp,
    val borderAlpha: Float,
    val minimumRootPadding: Dp
)

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

internal val LocalGeneratedAppVisuals = staticCompositionLocalOf {
    GeneratedAppVisuals(
        densityScale = 1f,
        cardElevation = 0.dp,
        borderAlpha = 0.12f,
        minimumRootPadding = 20.dp
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
        else -> 0.88f
    }
    val background = when (spec.surface) {
        "flat" -> Color(0xFFF8F9FC)
        "elevated" -> mix(primary, Color.White, 0.975f)
        else -> mix(primary, Color.White, 0.955f)
    }
    val surface = when (spec.surface) {
        "tonal" -> mix(primary, Color.White, 0.985f)
        else -> Color.White
    }
    val scheme = lightColorScheme(
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
        outline = mix(primary, NEUTRAL, 0.82f),
        outlineVariant = mix(primary, Color.White, 0.88f),
        error = ERROR,
        onError = Color.White,
        errorContainer = mix(ERROR, Color.White, 0.84f),
        onErrorContainer = mix(ERROR, Color.Black, 0.25f),
        inverseSurface = INK,
        inverseOnSurface = Color.White,
        inversePrimary = mix(primary, Color.White, 0.35f)
    )
    val typography = when (spec.style) {
        "expressive" -> MaterialTheme.typography.copy(
            displaySmall = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )

        else -> MaterialTheme.typography
    }
    val shapes = generatedShapes(spec.shape)
    val visuals = generatedVisuals(spec)
    CompositionLocalProvider(
        LocalGeneratedAppVisuals provides visuals,
        LocalGeneratedAppSemanticColors provides generatedSemanticColors()
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
    surface: String
): GeneratedAppThemeSpec = runCatching {
    GeneratedAppThemeSpec.validated(primary, secondary, style, shape, density, surface)
}.getOrDefault(this)

private fun generatedShapes(shape: String): Shapes = when (shape) {
    "geometric" -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(8.dp)
    )

    "pill" -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(50),
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
    val densityScale = when (spec.density) {
        "compact" -> 0.82f
        "spacious" -> 1.18f
        else -> 1f
    }
    val elevation = when (spec.surface) {
        "elevated" -> 2.dp
        else -> 0.dp
    }
    val borderAlpha = when (spec.surface) {
        "flat" -> 0.16f
        "elevated" -> 0.08f
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
        }
    )
}

private fun generatedSemanticColors() = GeneratedAppSemanticColors(
    positive = POSITIVE,
    onPositive = Color.White,
    positiveContainer = mix(POSITIVE, Color.White, 0.84f),
    onPositiveContainer = mix(POSITIVE, Color.Black, 0.2f),
    warning = WARNING,
    onWarning = Color.White,
    warningContainer = mix(WARNING, Color.White, 0.82f),
    onWarningContainer = mix(WARNING, Color.Black, 0.28f)
)

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.contentOrNull
    ?: error("Theme $key must be a string")

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

private fun contrastRatio(first: Color, second: Color): Float {
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
