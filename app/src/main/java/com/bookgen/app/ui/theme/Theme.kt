package com.bookgen.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bookgen.app.domain.model.ThemePreference

val LocalBookGenExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object BookGenThemeExtras {
    val colors: BookGenExtendedColors
        @Composable get() = LocalBookGenExtendedColors.current
}

@Composable
fun BookGenTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val effectiveMode = when (preference) {
        ThemePreference.SYSTEM -> if (systemDark) ThemePreference.DARK else ThemePreference.LIGHT
        ThemePreference.LIGHT, ThemePreference.DARK, ThemePreference.SEPIA -> preference
    }

    val colorScheme = when (effectiveMode) {
        ThemePreference.SEPIA -> BookGenSepiaColorScheme
        ThemePreference.DARK -> {
            if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                BookGenDarkColorScheme
            }
        }
        ThemePreference.LIGHT, ThemePreference.SYSTEM -> {
            if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else {
                BookGenLightColorScheme
            }
        }
    }

    val extended = when (effectiveMode) {
        ThemePreference.SEPIA -> SepiaExtendedColors
        ThemePreference.DARK -> DarkExtendedColors
        ThemePreference.LIGHT, ThemePreference.SYSTEM -> LightExtendedColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !extended.isDarkAppearance
            controller.isAppearanceLightNavigationBars = !extended.isDarkAppearance
        }
    }

    CompositionLocalProvider(LocalBookGenExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BookGenTypography,
            shapes = BookGenShapes,
            content = content,
        )
    }
}
