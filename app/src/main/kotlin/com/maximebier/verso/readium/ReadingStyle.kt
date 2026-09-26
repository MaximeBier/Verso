package com.maximebier.verso.readium

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import com.maximebier.verso.ui.theme.VersoPalette

/** Couleurs passées à Readium, tirées des jetons (aucune couleur en dur). */
data class ReadingColors(
    @ColorInt val background: Int,
    @ColorInt val text: Int,
    @ColorInt val link: Int,
)

/**
 * Réglages de lecture imposés en V1 (spec, « Lecture ») et leur traduction en valeurs Readium.
 * Les réglages utilisateur de la V2 remplaceront ces constantes par des préférences.
 */
object ReadingStyle {
    const val FONT_FAMILY_NAME = "Atkinson Hyperlegible Next"
    const val FONT_ASSET_REGULAR = "fonts/atkinson_hyperlegible_next.ttf"
    const val FONT_ASSET_ITALIC = "fonts/atkinson_hyperlegible_next_italic.ttf"

    /** Motif « glob simple » des assets servis aux WebView (dossier `src/main/assets/fonts/`). */
    const val SERVED_ASSETS_PATTERN = "fonts/.*"

    /** Plage de l’axe `wght` de la police variable. */
    val FONT_WEIGHT_AXIS: IntRange = 200..800

    const val READING_FONT_SIZE_SP = 19.0
    const val LINE_HEIGHT = 1.6
    const val PARAGRAPH_SPACING_IN_LINES = 0.5
    const val SIDE_MARGIN_DP = 24.0

    /** Taille racine CSS d’une WebView (px CSS = dp) : `fontSize = 1,0` chez Readium. */
    const val WEBVIEW_ROOT_FONT_SIZE_PX = 16.0

    /** `--RS__pageGutter` de Readium CSS 1 (navigateur Fragment) sous 35 em de large. */
    const val FRAGMENT_PAGE_GUTTER_PX = 20.0

    /** `LayoutConstants.baseMinMargins` du navigateur Compose web. */
    const val WEB_BASE_MIN_MARGINS_DP = 30.0

    const val NORMAL_FONT_WEIGHT = 400.0
    const val DARK_FONT_WEIGHT = 380.0
    const val DARK_LETTER_SPACING_EM = 0.01

    /** 19 sp ⇒ 19 / 16 = 1,1875 (facteur de la racine CSS de 16 px). */
    fun fontSizeFactor(fontSizeSp: Double = READING_FONT_SIZE_SP): Double =
        fontSizeSp / WEBVIEW_ROOT_FONT_SIZE_PX

    /** Readium pose la valeur en rem en marge haute et basse (fusionnées) : 0,5 × 1,6 = 0,8 rem. */
    fun paragraphSpacingRem(): Double = LINE_HEIGHT * PARAGRAPH_SPACING_IN_LINES

    /** Readium applique `400 × facteur` : 380 ⇒ 0,95. */
    fun fontWeightFactor(dark: Boolean): Double =
        (if (dark) DARK_FONT_WEIGHT else NORMAL_FONT_WEIGHT) / NORMAL_FONT_WEIGHT

    /** Readium divise la valeur par 2 (`Length.Rem(value / 2)`) : 0,01 em ⇒ 0,02. Rien en clair. */
    fun readiumLetterSpacing(dark: Boolean): Double? =
        if (dark) DARK_LETTER_SPACING_EM * 2 else null

    /** Navigateur Fragment : 24 dp / gouttière de 20 px = 1,2 (indépendant de la taille du texte). */
    fun fragmentPageMargins(): Double = SIDE_MARGIN_DP / FRAGMENT_PAGE_GUTTER_PX

    /** Navigateur Compose : marge = 30 dp × facteur × fontScale ⇒ facteur = 24 / (30 × fontScale). */
    fun webMinMargins(fontScale: Float): Double =
        SIDE_MARGIN_DP / (WEB_BASE_MIN_MARGINS_DP * fontScale)

    fun colors(dark: Boolean): ReadingColors {
        val palette = if (dark) VersoPalette.Dark else VersoPalette.Light
        return ReadingColors(
            background = palette.background.toArgb(),
            text = palette.text.toArgb(),
            link = palette.accent.toArgb(),
        )
    }
}
