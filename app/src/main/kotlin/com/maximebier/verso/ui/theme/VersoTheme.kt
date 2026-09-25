package com.maximebier.verso.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** Thème Verso : clair ou sombre selon le système (V1). Couleurs générées depuis tokens.json. */
@Composable
fun VersoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) VersoPalette.Dark else VersoPalette.Light
    val covers = if (darkTheme) VersoPalette.CoverDark else VersoPalette.CoverLight
    CompositionLocalProvider(LocalVersoColors provides colors, LocalCoverPalette provides covers) {
        MaterialTheme(
            colorScheme = colorSchemeFor(colors, darkTheme),
            typography = VersoTypography.material,
            shapes = Shapes(
                extraSmall = VersoShapes.cover,
                small = VersoShapes.small,
                medium = VersoShapes.small,
                large = VersoShapes.card,
                extraLarge = VersoShapes.sheet,
            ),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextStyle provides VersoTypography.body,
                content = content,
            )
        }
    }
}

/** Accès aux jetons du thème courant : VersoTheme.colors.accent, VersoTheme.typography.body… */
object VersoTheme {
    val colors: VersoColors
        @Composable @ReadOnlyComposable get() = LocalVersoColors.current
    val coverPalette: List<Color>
        @Composable @ReadOnlyComposable get() = LocalCoverPalette.current
    val typography: VersoTypography
        get() = VersoTypography
}

/** Traduction vers Material 3 pour les composants M3 (Switch, ModalBottomSheet, Snackbar…). */
private fun colorSchemeFor(c: VersoColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.selection,
        onPrimaryContainer = c.onSelection,
        secondary = c.text,
        onSecondary = c.background,
        secondaryContainer = c.selection,
        onSecondaryContainer = c.onSelection,
        tertiary = c.accent,
        onTertiary = c.onAccent,
        background = c.background,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surfaceHigh,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = c.surface,
        surfaceBright = c.surfaceHigh,
        surfaceDim = c.background,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceHigh,
        surfaceContainerHighest = c.surfaceHigh,
        inverseSurface = c.inverse,
        inverseOnSurface = c.onInverse,
        inversePrimary = c.inverseAccent,
        error = c.danger,
        onError = c.onDanger,
        outline = c.outline,
        outlineVariant = c.divider,
        scrim = c.scrim,
    )
}
