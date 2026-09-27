@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.readium

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.Color as CssColor
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Réglages V1 imposés au navigateur EPUB classique de Readium (Fragment). */
object VersoReadingPreferences {

    val ATKINSON: FontFamily = FontFamily(ReadingStyle.FONT_FAMILY_NAME)

    /**
     * @param fontSizeSp taille de lecture ; paramètre pour la simulation « taille changée » (V2).
     * @param fontScale échelle de la taille de police d’Android ([ReadingStyle.readingFontScale]).
     * @param widthDp largeur de la surface de lecture : la gouttière de ReadiumCSS s’élargit avec elle.
     */
    fun epub(
        dark: Boolean,
        fontSizeSp: Double = ReadingStyle.READING_FONT_SIZE_SP,
        fontScale: Double = 1.0,
        widthDp: Float = 0f,
    ): EpubPreferences {
        val colors = ReadingStyle.colors(dark)
        return EpubPreferences(
            backgroundColor = Color(colors.background),
            textColor = Color(colors.text),
            fontFamily = ATKINSON,
            fontSize = ReadingStyle.fontSizeFactor(fontSizeSp * fontScale),
            fontWeight = ReadingStyle.fontWeightFactor(dark),
            hyphens = false,
            letterSpacing = ReadingStyle.readiumLetterSpacing(dark),
            lineHeight = ReadingStyle.LINE_HEIGHT,
            pageMargins = ReadingStyle.fragmentPageMargins(widthDp),
            paragraphSpacing = ReadingStyle.paragraphSpacingRem(),
            publisherStyles = false,
            scroll = true,
            textAlign = TextAlign.START,
            theme = if (dark) Theme.DARK else Theme.LIGHT,
        )
    }

    /**
     * Atkinson servie depuis les assets ; le défilement reste dans le chapitre (Verso enchaîne lui-même). Les insets
     * sont posés par Verso (`readerContentInsets`) : Readium ajouterait la découpe de l’écran une seconde fois.
     */
    fun EpubNavigatorFragment.Configuration.applyVerso(dark: Boolean = false) {
        servedAssets = listOf(ReadingStyle.SERVED_ASSETS_PATTERN)
        // Liens (visités ou non) à la couleur d’accent des jetons, contrastée à 7:1 : en sombre, le mode nuit de
        // ReadiumCSS mettrait les liens visités en #0099E5 (5,8:1). Variables en ligne : elles priment sur ce mode.
        val link = CssColor.Int(ReadingStyle.colors(dark).link)
        readiumCssRsProperties = readiumCssRsProperties.copy(linkColor = link, visitedColor = link)
        disablePageTurnsWhileScrolling = true
        shouldApplyInsetsPadding = false
        addFontFamilyDeclaration(ATKINSON) {
            addFontFace {
                addSource(ReadingStyle.FONT_ASSET_REGULAR, preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(ReadingStyle.FONT_WEIGHT_AXIS)
            }
            addFontFace {
                addSource(ReadingStyle.FONT_ASSET_ITALIC)
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(ReadingStyle.FONT_WEIGHT_AXIS)
            }
        }
    }
}
