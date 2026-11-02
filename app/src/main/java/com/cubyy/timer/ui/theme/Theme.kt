package com.cubyy.timer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline
)

@Composable
fun CubyyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    colorTheme: String = "DYNAMIC",
    content: @Composable () -> Unit
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val colorScheme = if (colorTheme == "DYNAMIC") baseColorScheme else baseColorScheme.withPalette(colorTheme, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private data class PaletteColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color
)

private fun ColorScheme.withPalette(name: String, dark: Boolean): ColorScheme {
    if (name == "GREEN") return this

    val palette = when (name) {
        "BLUE" -> if (dark) PaletteColors(
            Color(0xFFADC6FF), Color(0xFF002F65), Color(0xFF00458C), Color(0xFFD8E2FF),
            Color(0xFFBEC6DC), Color(0xFF283041), Color(0xFF3E4759), Color(0xFFDAE2F9),
            Color(0xFFDBBCE0), Color(0xFF402843), Color(0xFF583F5C), Color(0xFFF7D8FB)
        ) else PaletteColors(
            Color(0xFF005AC1), Color.White, Color(0xFFD8E2FF), Color(0xFF001A42),
            Color(0xFF575E71), Color.White, Color(0xFFDBE2F9), Color(0xFF141B2C),
            Color(0xFF755178), Color.White, Color(0xFFFFD6FC), Color(0xFF2B1230)
        )
        "YELLOW" -> if (dark) PaletteColors(
            Color(0xFFE6C400), Color(0xFF393000), Color(0xFF544700), Color(0xFFF5E479),
            Color(0xFFC9C6A3), Color(0xFF323119), Color(0xFF49482D), Color(0xFFE6E3BE),
            Color(0xFFE7C0A8), Color(0xFF432B1B), Color(0xFF5C4130), Color(0xFFFFDBC7)
        ) else PaletteColors(
            Color(0xFF6A5D00), Color.White, Color(0xFFF5E479), Color(0xFF211B00),
            Color(0xFF625F42), Color.White, Color(0xFFE8E4BF), Color(0xFF1D1C05),
            Color(0xFF795541), Color.White, Color(0xFFFFDBC7), Color(0xFF2D1609)
        )
        "RED" -> if (dark) PaletteColors(
            Color(0xFFFFB4A8), Color(0xFF690005), Color(0xFF93000A), Color(0xFFFFDAD4),
            Color(0xFFE7BDB5), Color(0xFF442925), Color(0xFF5D3F3A), Color(0xFFFFDAD4),
            Color(0xFFE4C18D), Color(0xFF402D05), Color(0xFF594416), Color(0xFFFFDEA6)
        ) else PaletteColors(
            Color(0xFFBA1B1B), Color.White, Color(0xFFFFDAD4), Color(0xFF410001),
            Color(0xFF775651), Color.White, Color(0xFFFFDAD4), Color(0xFF2C1512),
            Color(0xFF705C2E), Color.White, Color(0xFFFFDEA6), Color(0xFF251A00)
        )
        else -> return this
    }

    return copy(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        tertiary = palette.tertiary,
        onTertiary = palette.onTertiary,
        tertiaryContainer = palette.tertiaryContainer,
        onTertiaryContainer = palette.onTertiaryContainer
    )
}

