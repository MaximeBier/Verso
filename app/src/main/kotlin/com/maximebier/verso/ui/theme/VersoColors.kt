package com.maximebier.verso.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.maximebier.verso.data.AppTheme

/** Couleurs du thème courant (VersoColors est généré depuis tokens.json). */
val LocalVersoColors = staticCompositionLocalOf { VersoPalette.Light }

/** Palette des vignettes générées pour le thème courant. */
val LocalCoverPalette = staticCompositionLocalOf<List<Color>> { VersoPalette.CoverLight }

/** Thème sombre affiché (réglage Thème de Verso, ou celui du téléphone en automatique). */
val LocalVersoDarkTheme = staticCompositionLocalOf { false }

/** Palette affichée (réglage Thème de Verso, ou celle du téléphone en automatique). */
val LocalVersoTheme = staticCompositionLocalOf { AppTheme.LIGHT }
