package com.maximebier.verso.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Couleurs du thème courant (VersoColors est généré depuis tokens.json). */
val LocalVersoColors = staticCompositionLocalOf { VersoPalette.Light }

/** Palette des vignettes générées pour le thème courant. */
val LocalCoverPalette = staticCompositionLocalOf<List<Color>> { VersoPalette.CoverLight }
