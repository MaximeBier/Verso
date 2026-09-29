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
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.data.AppTheme

/** Thème Verso : une des quatre palettes de tokens.json (clair, sépia, sombre, nuit), dans la police choisie. */
@Composable
fun VersoTheme(
    theme: AppTheme = if (isSystemInDarkTheme()) AppTheme.DARK else AppTheme.LIGHT,
    font: ReadingFont = ReadingFont.LITERATA,
    content: @Composable () -> Unit,
) {
    val colors = paletteOf(theme)
    val covers = if (theme.isDark) VersoPalette.CoverDark else VersoPalette.CoverLight
    val typography = VersoTypography.of(font)
    CompositionLocalProvider(
        LocalVersoColors provides colors,
        LocalCoverPalette provides covers,
        LocalVersoDarkTheme provides theme.isDark,
        LocalVersoTheme provides theme,
        LocalVersoTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(colors, theme.isDark),
            typography = typography.material,
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
                LocalTextStyle provides typography.body,
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
    /** Thème sombre affiché : à utiliser à la place de isSystemInDarkTheme(). */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalVersoDarkTheme.current
    /** Palette affichée. */
    val theme: AppTheme
        @Composable @ReadOnlyComposable get() = LocalVersoTheme.current
    /** Styles de texte dans la police choisie : VersoTheme.typography.body… */
    val typography: VersoTypography
        @Composable @ReadOnlyComposable get() = LocalVersoTypography.current
}

/** Palette de tokens.json pour un thème. */
fun paletteOf(theme: AppTheme): VersoColors = when (theme) {
    AppTheme.LIGHT -> VersoPalette.Light
    AppTheme.SEPIA -> VersoPalette.Sepia
    AppTheme.DARK -> VersoPalette.Dark
    AppTheme.NIGHT -> VersoPalette.Night
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
