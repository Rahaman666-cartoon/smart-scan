package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import com.example.data.SettingsManager.AppThemeAccent
import com.example.data.SettingsManager.ThemeMode

@Composable
fun SmartScanTheme(
    themeMode: ThemeMode,
    themeAccent: AppThemeAccent,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when (themeAccent) {
        AppThemeAccent.DEFAULT -> {
            if (darkTheme) {
                darkColorScheme(
                    primary = DarkBluePrimary,
                    secondary = DarkBlueSecondary,
                    background = SoftBgDark,
                    surface = CardBgDark,
                    onBackground = TextPrimaryDark,
                    onSurface = TextPrimaryDark,
                    primaryContainer = Color(0xFF00497E),
                    onPrimaryContainer = Color(0xFFD1E4FF),
                    secondaryContainer = Color(0xFF43474E),
                    onSecondaryContainer = Color(0xFFC4C6D0),
                    outline = Color(0xFF8C9199),
                    surfaceVariant = Color(0xFF43474E),
                    onSurfaceVariant = Color(0xFFC4C6D0)
                )
            } else {
                lightColorScheme(
                    primary = HighDensityPrimary,
                    secondary = HighDensityOnSurfaceVariant,
                    background = HighDensityBgLight,
                    surface = HighDensitySurface,
                    onBackground = HighDensityOnBgLight,
                    onSurface = HighDensityOnBgLight,
                    primaryContainer = HighDensityPrimaryContainer,
                    onPrimaryContainer = HighDensityOnPrimaryContainer,
                    secondaryContainer = HighDensitySecondaryContainer,
                    onSecondaryContainer = HighDensityOnSecondaryContainer,
                    outline = HighDensityOutline,
                    surfaceVariant = HighDensitySurfaceVariant,
                    onSurfaceVariant = HighDensityOnSurfaceVariant
                )
            }
        }
        AppThemeAccent.INDIGO -> {
            if (darkTheme) {
                darkColorScheme(
                    primary = DarkIndigoPrimary,
                    secondary = DarkIndigoSecondary,
                    background = SoftBgDark,
                    surface = CardBgDark,
                    onBackground = TextPrimaryDark,
                    onSurface = TextPrimaryDark
                )
            } else {
                lightColorScheme(
                    primary = IndigoPrimary,
                    secondary = IndigoSecondary,
                    background = SoftBgLight,
                    surface = CardBgLight,
                    onBackground = TextPrimaryLight,
                    onSurface = TextPrimaryLight
                )
            }
        }
        AppThemeAccent.TEAL -> {
            if (darkTheme) {
                darkColorScheme(
                    primary = DarkTealPrimary,
                    secondary = DarkTealSecondary,
                    background = SoftBgDark,
                    surface = CardBgDark,
                    onBackground = TextPrimaryDark,
                    onSurface = TextPrimaryDark
                )
            } else {
                lightColorScheme(
                    primary = TealPrimary,
                    secondary = TealSecondary,
                    background = SoftBgLight,
                    surface = CardBgLight,
                    onBackground = TextPrimaryLight,
                    onSurface = TextPrimaryLight
                )
            }
        }
        AppThemeAccent.CORAL -> {
            if (darkTheme) {
                darkColorScheme(
                    primary = DarkCoralPrimary,
                    secondary = DarkCoralSecondary,
                    background = SoftBgDark,
                    surface = CardBgDark,
                    onBackground = TextPrimaryDark,
                    onSurface = TextPrimaryDark
                )
            } else {
                lightColorScheme(
                    primary = CoralPrimary,
                    secondary = CoralSecondary,
                    background = SoftBgLight,
                    surface = CardBgLight,
                    onBackground = TextPrimaryLight,
                    onSurface = TextPrimaryLight
                )
            }
        }
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            var ctx = view.context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is android.app.Activity) break
                ctx = ctx.baseContext
            }
            val activity = ctx as? android.app.Activity
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

