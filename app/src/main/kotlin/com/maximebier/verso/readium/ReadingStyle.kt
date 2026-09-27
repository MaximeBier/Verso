package com.maximebier.verso.readium

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoColors
import com.maximebier.verso.ui.theme.VersoPalette

/** Couleurs passées à Readium, tirées des jetons (aucune couleur en dur). */
data class ReadingColors(
    @ColorInt val background: Int,
    @ColorInt val text: Int,
    @ColorInt val link: Int,
)

/** Traduction des réglages de lecture de Verso en valeurs Readium (spec, « Lecture » et « Réglages de la V2 »). */
object ReadingStyle {
    const val ATKINSON_FAMILY_NAME = "Atkinson Hyperlegible Next"
    const val ATKINSON_ASSET_REGULAR = "fonts/atkinson_hyperlegible_next.ttf"
    const val ATKINSON_ASSET_ITALIC = "fonts/atkinson_hyperlegible_next_italic.ttf"
    const val LITERATA_FAMILY_NAME = "Literata"
    const val LITERATA_ASSET_REGULAR = "fonts/literata.ttf"
    const val LITERATA_ASSET_ITALIC = "fonts/literata_italic.ttf"

    /** Motif « glob simple » des assets servis aux WebView (dossier `src/main/assets/fonts/`). */
    const val SERVED_ASSETS_PATTERN = "fonts/.*"

    /** Plages de l’axe `wght` des polices variables. */
    val ATKINSON_WEIGHT_AXIS: IntRange = 200..800
    val LITERATA_WEIGHT_AXIS: IntRange = 200..900

    const val PARAGRAPH_SPACING_IN_LINES = 0.5

    /** Taille racine CSS d’une WebView (px CSS = dp) : `fontSize = 1,0` chez Readium. */
    const val WEBVIEW_ROOT_FONT_SIZE_PX = 16.0

    /** `--RS__pageGutter` de Readium CSS 1 (navigateur Fragment) sous 35 em de large. */
    const val FRAGMENT_PAGE_GUTTER_PX = 20.0

    const val NORMAL_FONT_WEIGHT = 400.0
    const val DARK_FONT_WEIGHT = 380.0
    const val DARK_LETTER_SPACING_EM = 0.01

    /**
     * Échelle du texte de lecture voulue par la taille de police d’Android : taille réelle de `fontSizeSp` (en dp)
     * / `fontSizeSp`. Passe par la conversion sp → dp du système, donc suit l’échelle non linéaire d’Android 14.
     */
    fun readingFontScale(density: Density, fontSizeSp: Int): Double =
        with(density) { fontSizeSp.toFloat().sp.toDp().value } / fontSizeSp.toDouble()

    /** 20 sp ⇒ 20 / 16 = 1,25 (facteur de la racine CSS de 16 px). */
    fun fontSizeFactor(fontSizeSp: Double): Double = fontSizeSp / WEBVIEW_ROOT_FONT_SIZE_PX

    /** Readium pose la valeur en rem en marge haute et basse (fusionnées) : 0,5 × 1,6 = 0,8 rem. */
    fun paragraphSpacingRem(lineHeight: Double): Double = lineHeight * PARAGRAPH_SPACING_IN_LINES

    /** Readium applique `400 × facteur` : 380 ⇒ 0,95. */
    fun fontWeightFactor(dark: Boolean): Double =
        (if (dark) DARK_FONT_WEIGHT else NORMAL_FONT_WEIGHT) / NORMAL_FONT_WEIGHT

    /** Readium divise la valeur par 2 (`Length.Rem(value / 2)`) : 0,01 em ⇒ 0,02. Rien en clair. */
    fun readiumLetterSpacing(dark: Boolean): Double? =
        if (dark) DARK_LETTER_SPACING_EM * 2 else null

    /**
     * Navigateur Fragment : marge voulue (16, 24 ou 32 dp) / gouttière de ReadiumCSS, qui s’élargit avec la largeur
     * de l’écran (paliers de `--RS__pageGutter` en em de 16 px). Indépendant de la taille du texte.
     */
    fun fragmentPageMargins(marginDp: Double, widthDp: Float = 0f): Double = marginDp / fragmentPageGutterPx(widthDp)

    private fun fragmentPageGutterPx(widthDp: Float): Double {
        val widthEm = widthDp / WEBVIEW_ROOT_FONT_SIZE_PX
        return when {
            widthEm >= 75 -> 50.0
            widthEm >= 45 -> 40.0
            widthEm >= 35 -> 30.0
            else -> FRAGMENT_PAGE_GUTTER_PX
        }
    }

    fun palette(theme: AppTheme): VersoColors = when (theme) {
        AppTheme.LIGHT -> VersoPalette.Light
        AppTheme.SEPIA -> VersoPalette.Sepia
        AppTheme.DARK -> VersoPalette.Dark
        AppTheme.BLACK -> VersoPalette.Black
    }

    fun colors(theme: AppTheme): ReadingColors {
        val palette = palette(theme)
        return ReadingColors(
            background = palette.background.toArgb(),
            text = palette.text.toArgb(),
            link = palette.accent.toArgb(),
        )
    }
}
