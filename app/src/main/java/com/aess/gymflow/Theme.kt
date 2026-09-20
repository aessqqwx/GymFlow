package com.aess.gymflow

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private data class Palette(
    val primaryDark: Color,
    val primaryContainerDark: Color,
    val secondaryContainerDark: Color,
    val primaryLight: Color,
    val primaryContainerLight: Color,
    val secondaryContainerLight: Color
)

private val Blue = Palette(
    Color(0xFFADC6FF), Color(0xFF294A7E), Color(0xFF394456),
    Color(0xFF3F5F96), Color(0xFFD8E2FF), Color(0xFFDDE3F2)
)
private val Brown = Palette(
    Color(0xFFE7C09C), Color(0xFF5E402B), Color(0xFF4B4038),
    Color(0xFF76563E), Color(0xFFFFDCC3), Color(0xFFEFE0D4)
)
private val Mono = Palette(
    Color(0xFFD7D7D7), Color(0xFF464646), Color(0xFF373737),
    Color(0xFF5D5D5D), Color(0xFFE3E3E3), Color(0xFFE5E5E5)
)
private val Green = Palette(
    Color(0xFFA9D7B5), Color(0xFF31543C), Color(0xFF36483A),
    Color(0xFF486B53), Color(0xFFC9ECCC), Color(0xFFDCEADD)
)
private val Purple = Palette(
    Color(0xFFD6BCFF), Color(0xFF543B73), Color(0xFF493E55),
    Color(0xFF6A4E86), Color(0xFFEBDDFF), Color(0xFFE8DFF0)
)

private fun paletteFor(style: String): Palette = when (style) {
    "BROWN" -> Brown
    "MONO" -> Mono
    "GREEN" -> Green
    "PURPLE" -> Purple
    else -> Blue
}

private fun darkScheme(p: Palette): ColorScheme = darkColorScheme(
    primary = p.primaryDark,
    onPrimary = Color(0xFF172033),
    primaryContainer = p.primaryContainerDark,
    onPrimaryContainer = Color(0xFFF1F3F9),
    secondary = Color(0xFFC3C8D2),
    onSecondary = Color(0xFF252A33),
    secondaryContainer = p.secondaryContainerDark,
    onSecondaryContainer = Color(0xFFF0F1F5),
    tertiary = p.primaryDark,
    onTertiary = Color(0xFF1B2432),
    tertiaryContainer = p.primaryContainerDark,
    onTertiaryContainer = Color(0xFFF3F3F7),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE7E9EE),
    surface = Color(0xFF101318),
    onSurface = Color(0xFFE7E9EE),
    surfaceVariant = Color(0xFF1B1F27),
    onSurfaceVariant = Color(0xFFC4C8D1),
    outline = Color(0xFF8E929C),
    outlineVariant = Color(0xFF41464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private fun lightScheme(p: Palette): ColorScheme = lightColorScheme(
    primary = p.primaryLight,
    onPrimary = Color.White,
    primaryContainer = p.primaryContainerLight,
    onPrimaryContainer = Color(0xFF182033),
    secondary = Color(0xFF596170),
    onSecondary = Color.White,
    secondaryContainer = p.secondaryContainerLight,
    onSecondaryContainer = Color(0xFF1B202A),
    tertiary = p.primaryLight,
    onTertiary = Color.White,
    tertiaryContainer = p.primaryContainerLight,
    onTertiaryContainer = Color(0xFF221A2A),
    background = Color(0xFFF9FAFD),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFF9FAFD),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF45474E),
    outline = Color(0xFF76777E),
    outlineVariant = Color(0xFFC7C8CF),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)


private fun refinedSystemScheme(base: ColorScheme, dark: Boolean): ColorScheme = if (dark) {
    base.copy(
        background = Color(0xFF101214),
        onBackground = Color(0xFFE7E8EA),
        surface = Color(0xFF101214),
        onSurface = Color(0xFFE7E8EA),
        surfaceVariant = Color(0xFF1C2024),
        onSurfaceVariant = Color(0xFFC5C7CB),
        outlineVariant = Color(0xFF3E444B)
    )
} else {
    base.copy(
        background = Color(0xFFF8F9FB),
        onBackground = Color(0xFF1B1B1D),
        surface = Color(0xFFF8F9FB),
        onSurface = Color(0xFF1B1B1D),
        surfaceVariant = Color(0xFFE7E9EC),
        onSurfaceVariant = Color(0xFF45474B),
        outlineVariant = Color(0xFFC8CACD)
    )
}

@Composable
fun GymFlowTheme(
    themeMode: String,
    colorStyle: String,
    appLanguage: String,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }
    val context = LocalContext.current
    val scheme = if (colorStyle == "SYSTEM" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        refinedSystemScheme(if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), dark)
    } else {
        val palette = paletteFor(colorStyle)
        if (dark) darkScheme(palette) else lightScheme(palette)
    }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalAppLanguage provides appLanguage, LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content
        )
    }
}
