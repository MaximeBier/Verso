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

/**
 * Contraste minimal (WCAG, ratio de luminance relative) entre `background` et `surface` d'une palette, pour que
 * les feuilles et cartes restent visuellement distinctes du fond. Sous le plus faible des thèmes des maquettes
 * (Nuit, ≈ 1,07 : 1 ; Sombre 1,08, Sépia et Clair 1,11) ; une régression comme celle du sépia avant l'étape 13
 * (`surface` presque identique au fond, ≈ 1,02 : 1) est détectée bien avant d'atteindre 1 : 1 (aucune différence).
 */
const val MIN_BACKGROUND_SURFACE_CONTRAST = 1.05
