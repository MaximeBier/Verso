@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.readium

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Réglages V1 imposés au navigateur EPUB classique de Readium (Fragment). */
object VersoReadingPreferences {

    val ATKINSON: FontFamily = FontFamily(ReadingStyle.FONT_FAMILY_NAME)

    /** @param fontSizeSp taille de lecture ; paramètre pour la simulation « taille changée » (V2). */
    fun epub(dark: Boolean, fontSizeSp: Double = ReadingStyle.READING_FONT_SIZE_SP): EpubPreferences {
        val colors = ReadingStyle.colors(dark)
        return EpubPreferences(
            backgroundColor = Color(colors.background),
            textColor = Color(colors.text),
            fontFamily = ATKINSON,
            fontSize = ReadingStyle.fontSizeFactor(fontSizeSp),
            fontWeight = ReadingStyle.fontWeightFactor(dark),
            hyphens = false,
            letterSpacing = ReadingStyle.readiumLetterSpacing(dark),
            lineHeight = ReadingStyle.LINE_HEIGHT,
            pageMargins = ReadingStyle.fragmentPageMargins(),
            paragraphSpacing = ReadingStyle.paragraphSpacingRem(),
            publisherStyles = false,
            scroll = true,
            textAlign = TextAlign.START,
            theme = if (dark) Theme.DARK else Theme.LIGHT,
        )
    }

    /** Atkinson servie depuis les assets ; le défilement reste dans le chapitre (Verso enchaîne lui-même). */
    fun EpubNavigatorFragment.Configuration.applyVerso() {
        servedAssets = listOf(ReadingStyle.SERVED_ASSETS_PATTERN)
        disablePageTurnsWhileScrolling = true
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
