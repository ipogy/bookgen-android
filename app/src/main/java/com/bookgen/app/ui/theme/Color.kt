package com.bookgen.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Primitives mapped cleanly from design/tokens.json
val Alabaster50 = Color(0xFFFAF8F5)
val Stone100 = Color(0xFFF3F0EA)
val Stone200 = Color(0xFFEAE5DC)
val Stone300 = Color(0xFFDED8CE)
val Stone500 = Color(0xFF5E5A54)
val Ink900 = Color(0xFF1A1917)

val Slate950 = Color(0xFF131417)
val Slate900 = Color(0xFF1B1D22)
val Slate850 = Color(0xFF22252B)
val Slate800 = Color(0xFF2B2E36)
val Slate700 = Color(0xFF343842)
val Slate300 = Color(0xFFB4AFA6)
val Slate100 = Color(0xFFECE9E2)

val IndigoInk800 = Color(0xFF1E3A5F)
val IndigoInk700 = Color(0xFF2A4D7A)
val IndigoInk200 = Color(0xFF9EC2F0)
val IndigoInk100 = Color(0xFFD6E3F4)
val IndigoInk950 = Color(0xFF0E2340)

val Terracotta700 = Color(0xFF9A4A2E)
val Terracotta200 = Color(0xFFE8A389)
val Terracotta100 = Color(0xFFF6DDD4)
val Terracotta950 = Color(0xFF3D180B)

val Teal700 = Color(0xFF2C6B6E)
val Teal200 = Color(0xFF7FC8CB)
val Teal100 = Color(0xFFD0ECEC)
val Teal950 = Color(0xFF0D2D2F)

val Sepia50 = Color(0xFFF4ECD8)
val Sepia100 = Color(0xFFEFE5CE)
val Sepia200 = Color(0xFFE4D7BA)
val Sepia300 = Color(0xFFD7C8AA)
val Sepia600 = Color(0xFF6E604F)
val Sepia800 = Color(0xFF6F4423)
val Sepia900 = Color(0xFF3B2F23)

val Danger700 = Color(0xFFB3261E)
val Danger200 = Color(0xFFF2B8B5)
val Danger100 = Color(0xFFF9DEDC)
val Warning700 = Color(0xFF9C5B00)
val Warning200 = Color(0xFFF3C884)
val Success700 = Color(0xFF1E6E3E)
val Success200 = Color(0xFF8CD6A8)

val BookGenLightColorScheme: ColorScheme = lightColorScheme(
    primary = IndigoInk800,
    onPrimary = Alabaster50,
    primaryContainer = IndigoInk100,
    onPrimaryContainer = IndigoInk950,
    secondary = Terracotta700,
    onSecondary = Alabaster50,
    secondaryContainer = Terracotta100,
    onSecondaryContainer = Terracotta950,
    tertiary = Teal700,
    onTertiary = Alabaster50,
    tertiaryContainer = Teal100,
    onTertiaryContainer = Teal950,
    background = Alabaster50,
    onBackground = Ink900,
    surface = Alabaster50,
    onSurface = Ink900,
    surfaceVariant = Stone200,
    onSurfaceVariant = Stone500,
    surfaceContainerLowest = Alabaster50,
    surfaceContainerLow = Stone100,
    surfaceContainer = Stone100,
    surfaceContainerHigh = Stone200,
    surfaceContainerHighest = Stone300,
    outline = Stone500,
    outlineVariant = Stone300,
    error = Danger700,
    onError = Alabaster50,
    errorContainer = Danger100,
    onErrorContainer = Color(0xFF410E0B),
)

val BookGenDarkColorScheme: ColorScheme = darkColorScheme(
    primary = IndigoInk200,
    onPrimary = IndigoInk950,
    primaryContainer = IndigoInk800,
    onPrimaryContainer = IndigoInk100,
    secondary = Terracotta200,
    onSecondary = Terracotta950,
    secondaryContainer = Terracotta950,
    onSecondaryContainer = Terracotta100,
    tertiary = Teal200,
    onTertiary = Teal950,
    tertiaryContainer = Teal950,
    onTertiaryContainer = Teal100,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate950,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    surfaceContainerLowest = Slate950,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    outline = Slate300,
    outlineVariant = Slate700,
    error = Danger200,
    onError = Color(0xFF601410),
    errorContainer = Danger700,
    onErrorContainer = Danger100,
)

val BookGenSepiaColorScheme: ColorScheme = lightColorScheme(
    primary = Sepia800,
    onPrimary = Sepia50,
    primaryContainer = Sepia200,
    onPrimaryContainer = Sepia900,
    secondary = Terracotta700,
    onSecondary = Sepia50,
    secondaryContainer = Sepia200,
    onSecondaryContainer = Sepia900,
    tertiary = Teal700,
    onTertiary = Sepia50,
    tertiaryContainer = Sepia200,
    onTertiaryContainer = Sepia900,
    background = Sepia50,
    onBackground = Sepia900,
    surface = Sepia50,
    onSurface = Sepia900,
    surfaceVariant = Sepia200,
    onSurfaceVariant = Sepia600,
    surfaceContainerLowest = Sepia50,
    surfaceContainerLow = Sepia100,
    surfaceContainer = Sepia100,
    surfaceContainerHigh = Sepia200,
    surfaceContainerHighest = Sepia300,
    outline = Sepia600,
    outlineVariant = Sepia300,
    error = Danger700,
    onError = Sepia50,
    errorContainer = Danger100,
    onErrorContainer = Color(0xFF410E0B),
)

@Immutable
data class BookGenExtendedColors(
    val borderSubtle: Color,
    val warning: Color,
    val success: Color,
    val codeSurface: Color,
    val onCodeSurface: Color,
    val admonitionSurface: Color,
    val isDarkAppearance: Boolean,
)

val LightExtendedColors = BookGenExtendedColors(
    borderSubtle = Stone300,
    warning = Warning700,
    success = Success700,
    codeSurface = Slate900,
    onCodeSurface = Slate100,
    admonitionSurface = Stone100,
    isDarkAppearance = false,
)

val DarkExtendedColors = BookGenExtendedColors(
    borderSubtle = Color(0xFF424754),
    warning = Warning200,
    success = Success200,
    codeSurface = Color(0xFF0D0F13),
    onCodeSurface = Slate100,
    admonitionSurface = Slate850,
    isDarkAppearance = true,
)

val SepiaExtendedColors = BookGenExtendedColors(
    borderSubtle = Sepia300,
    warning = Color(0xFF7D4500),
    success = Color(0xFF175931),
    codeSurface = Color(0xFF2D241C),
    onCodeSurface = Sepia50,
    admonitionSurface = Sepia100,
    isDarkAppearance = false,
)
