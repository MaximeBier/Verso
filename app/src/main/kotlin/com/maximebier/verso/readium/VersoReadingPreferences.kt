@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.readium

import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.Color as CssColor
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Réglages de Verso imposés au navigateur EPUB classique de Readium (Fragment), par-dessus le CSS de l’éditeur. */
object VersoReadingPreferences {

    val LITERATA: FontFamily = FontFamily(ReadingStyle.LITERATA_FAMILY_NAME)
    val ATKINSON: FontFamily = FontFamily(ReadingStyle.ATKINSON_FAMILY_NAME)

    /** « Police du système » : la police sans empattements de la WebView (celle du téléphone). */
    fun fontFamilyOf(font: ReadingFont): FontFamily = when (font) {
        ReadingFont.LITERATA -> LITERATA
        ReadingFont.ATKINSON -> ATKINSON
        ReadingFont.SYSTEM -> FontFamily.SANS_SERIF
    }

    /**
     * @param fontScale échelle de la taille de police d’Android ([ReadingStyle.readingFontScale]).
     * @param widthDp largeur de la surface de lecture : la gouttière de ReadiumCSS s’élargit avec elle.
     */
    fun epub(
        settings: ReadingSettings,
        theme: AppTheme,
        scrollMode: ScrollMode,
        fontScale: Double = 1.0,
        widthDp: Float = 0f,
    ): EpubPreferences {
        val colors = ReadingStyle.colors(theme)
        val lineHeight = settings.lineSpacing.factor
        return EpubPreferences(
            backgroundColor = Color(colors.background),
            // Mode pages : une seule colonne, même en paysage ; les lignes ne sont jamais coupées entre deux pages
            // (les colonnes CSS de ReadiumCSS coupent entre deux lignes).
            columnCount = ColumnCount.ONE,
            textColor = Color(colors.text),
            fontFamily = fontFamilyOf(settings.font),
            fontSize = ReadingStyle.fontSizeFactor(settings.fontSizeSp * fontScale),
            fontWeight = ReadingStyle.fontWeightFactor(theme.isDark),
            hyphens = false,
            letterSpacing = ReadingStyle.readiumLetterSpacing(theme.isDark),
            lineHeight = lineHeight,
            pageMargins = ReadingStyle.fragmentPageMargins(settings.margins.dp, widthDp),
            paragraphSpacing = ReadingStyle.paragraphSpacingRem(lineHeight),
            publisherStyles = false,
            scroll = scrollMode == ScrollMode.CONTINUOUS,
            textAlign = TextAlign.START,
            theme = if (theme.isDark) Theme.DARK else Theme.LIGHT,
        )
    }

    /**
     * Vrai si passer de [before] à [after] change la mise en page du texte (tout sauf les couleurs et l’apparence) :
     * le lecteur doit alors ramener le texte au locator affiché (`FragmentReaderController.relayout`).
     */
    fun changesLayout(before: EpubPreferences, after: EpubPreferences): Boolean =
        before.withoutColors() != after.withoutColors()

    private fun EpubPreferences.withoutColors(): EpubPreferences =
        copy(backgroundColor = null, textColor = null, theme = null)

    /**
     * Literata et Atkinson servies depuis les assets ; le défilement reste dans le chapitre (Verso enchaîne
     * lui-même). Les insets sont posés par Verso (`readerContentInsets`) : Readium ajouterait la découpe de l’écran
     * une seconde fois. La couleur des liens est fixée à la création : elle est commune au clair et au sépia, et au
     * sombre et au noir (`ReadingStyleTest.linkColorIsSharedWithinLightAndDarkFamilies`), et passer de l’un à
     * l’autre groupe recrée l’activité.
     */
    fun EpubNavigatorFragment.Configuration.applyVerso(theme: AppTheme) {
        servedAssets = listOf(ReadingStyle.SERVED_ASSETS_PATTERN)
        // Liens (visités ou non) à la couleur d’accent des jetons, contrastée à 7:1 : en sombre, le mode nuit de
        // ReadiumCSS mettrait les liens visités en #0099E5 (5,8:1). Variables en ligne : elles priment sur ce mode.
        val link = CssColor.Int(ReadingStyle.colors(theme).link)
        readiumCssRsProperties = readiumCssRsProperties.copy(linkColor = link, visitedColor = link)
        disablePageTurnsWhileScrolling = true
        shouldApplyInsetsPadding = false
        declareVariableFont(
            LITERATA,
            ReadingStyle.LITERATA_ASSET_REGULAR,
            ReadingStyle.LITERATA_ASSET_ITALIC,
            ReadingStyle.LITERATA_WEIGHT_AXIS,
        )
        declareVariableFont(
            ATKINSON,
            ReadingStyle.ATKINSON_ASSET_REGULAR,
            ReadingStyle.ATKINSON_ASSET_ITALIC,
            ReadingStyle.ATKINSON_WEIGHT_AXIS,
        )
    }

    private fun EpubNavigatorFragment.Configuration.declareVariableFont(
        family: FontFamily,
        regular: String,
        italic: String,
        weights: IntRange,
    ) {
        addFontFamilyDeclaration(family) {
            addFontFace {
                addSource(regular, preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(weights)
            }
            addFontFace {
                addSource(italic)
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(weights)
            }
        }
    }
}
