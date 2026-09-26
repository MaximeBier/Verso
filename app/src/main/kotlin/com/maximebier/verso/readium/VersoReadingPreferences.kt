@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.readium

import kotlinx.collections.immutable.persistentListOf
import org.readium.navigator.web.common.FontFamilyDeclarations
import org.readium.navigator.web.common.FontStyle
import org.readium.navigator.web.reflowable.ReflowableWebConfiguration
import org.readium.navigator.web.reflowable.preferences.ReflowableWebPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

/** Réglages V1 imposés au navigateur Compose web (équivalent de `epub(dark)` du contrat). */
object VersoReadingPreferences {

    val ATKINSON: FontFamily = FontFamily(ReadingStyle.FONT_FAMILY_NAME)

    /**
     * @param fontScale échelle de police système. La WebView agrandit déjà le texte ; elle ne sert
     *   ici qu’à garder la marge latérale à 24 dp.
     * @param fontSizeSp taille de lecture ; paramètre pour la simulation « taille changée » (V2).
     */
    fun reflowableWeb(
        dark: Boolean,
        fontScale: Float = 1f,
        fontSizeSp: Double = ReadingStyle.READING_FONT_SIZE_SP,
    ): ReflowableWebPreferences {
        val colors = ReadingStyle.colors(dark)
        return ReflowableWebPreferences(
            backgroundColor = Color(colors.background),
            fontFamily = ATKINSON,
            fontSize = ReadingStyle.fontSizeFactor(fontSizeSp),
            fontWeight = ReadingStyle.fontWeightFactor(dark),
            hyphens = false,
            letterSpacing = ReadingStyle.readiumLetterSpacing(dark),
            lineHeight = ReadingStyle.LINE_HEIGHT,
            linkColor = Color(colors.link),
            minMargins = ReadingStyle.webMinMargins(fontScale),
            overridePublisherColors = true,
            paragraphSpacing = ReadingStyle.paragraphSpacingRem(),
            scroll = true,
            textAlign = TextAlign.START,
            textColor = Color(colors.text),
            visitedColor = Color(colors.link),
        )
    }

    fun reflowableWebConfiguration(): ReflowableWebConfiguration =
        ReflowableWebConfiguration(
            servedAssets = persistentListOf(ReadingStyle.SERVED_ASSETS_PATTERN),
            fontFamilyDeclarations = FontFamilyDeclarations {
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
            },
        )
}
