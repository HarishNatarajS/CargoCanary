package com.example.cargocanary.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Semantic status colours that Material 3 does not provide. Use via MaterialTheme.status. */
@Immutable
data class StatusColors(
    val ok: Color,
    val okContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val danger: Color,
    val dangerContainer: Color
)

private val LightStatus = StatusColors(OkLight, OkContainerLight, WarnLight, WarnContainerLight, DangerLight, DangerContainerLight)
private val DarkStatus = StatusColors(OkDark, OkContainerDark, WarnDark, WarnContainerDark, DangerDark, DangerContainerDark)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

val MaterialTheme.status: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current

private val DarkColorScheme = darkColorScheme(
    primary = Canary,
    onPrimary = Color(0xFF2A2100),
    primaryContainer = NightCanaryContainer,
    onPrimaryContainer = CanaryTint,
    secondary = Color(0xFF9CC3BE),
    onSecondary = Color(0xFF0B2220),
    secondaryContainer = Color(0xFF1F3A38),
    onSecondaryContainer = Color(0xFFD0EAE6),
    tertiary = Color(0xFF8FC8E8),
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightVariant,
    onSurfaceVariant = NightTextMuted,
    outline = Color(0xFF4A6064),
    outlineVariant = NightOutline,
    error = DangerDark,
    errorContainer = DangerContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Teal900,
    onPrimary = Color.White,
    primaryContainer = CanaryTint,
    onPrimaryContainer = OnCanaryTint,
    secondary = Teal700,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E6E3),
    onSecondaryContainer = Color(0xFF0F2927),
    tertiary = Color(0xFF1F5F85),
    background = PaperBackground,
    onBackground = InkText,
    surface = PaperSurface,
    onSurface = InkText,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkTextMuted,
    outline = Color(0xFF8A9C9F),
    outlineVariant = PaperOutline,
    error = DangerLight,
    errorContainer = DangerContainerLight,
    onErrorContainer = Color(0xFF410002)
)

private val CanaryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CargocanaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the Cargo Canary palette stays consistent on every device.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = CanaryShapes,
            content = content
        )
    }
}
