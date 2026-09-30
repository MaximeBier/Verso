package com.maximebier.verso.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.Color as CssColor
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

// Robolectric : Color de Readium s’appuie sur android.graphics.Color.
@OptIn(ExperimentalReadiumApi::class)
@RunWith(AndroidJUnit4::class)
class VersoReadingPreferencesTest {

    private fun prefs(
        settings: ReadingSettings = ReadingSettings(),
        theme: AppTheme = AppTheme.LIGHT,
        scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
    ) = VersoReadingPreferences.epub(settings, theme, scrollMode)

    @Test
    fun defaultsImposeVersoLayout() {
        val p = prefs()
        assertThat(p.scroll).isTrue()
        assertThat(p.textAlign).isEqualTo(TextAlign.START)
        assertThat(p.hyphens).isFalse()
        assertThat(p.publisherStyles).isFalse()
        assertThat(p.fontFamily).isEqualTo(VersoReadingPreferences.LITERATA)
        assertThat(p.fontSize!!).isWithin(1e-9).of(1.25)
        assertThat(p.lineHeight!!).isWithin(1e-9).of(1.6)
        assertThat(p.paragraphSpacing!!).isWithin(1e-9).of(0.8)
        assertThat(p.pageMargins!!).isWithin(1e-9).of(1.2)
        assertThat(p.letterSpacing).isNull()
        assertThat(p.theme).isEqualTo(Theme.LIGHT)
        assertThat(p.backgroundColor?.int).isEqualTo(ReadingStyle.colors(AppTheme.LIGHT).background)
        assertThat(p.textColor?.int).isEqualTo(ReadingStyle.colors(AppTheme.LIGHT).text)
    }

    @Test
    fun userSettingsAreApplied() {
        val p = prefs(
            ReadingSettings(font = ReadingFont.ATKINSON, fontSizeSp = 24, lineSpacing = LineSpacing.AIRY, margins = Margins.WIDE),
        )
        assertThat(p.fontFamily).isEqualTo(VersoReadingPreferences.ATKINSON)
        assertThat(p.fontSize!!).isWithin(1e-9).of(1.5)
        assertThat(p.lineHeight!!).isWithin(1e-9).of(1.8)
        assertThat(p.paragraphSpacing!!).isWithin(1e-9).of(0.9)
        assertThat(p.pageMargins!!).isWithin(1e-9).of(1.6)
    }

    @Test
    fun systemFontIsTheWebViewSansSerif() {
        assertThat(prefs(ReadingSettings(font = ReadingFont.SYSTEM)).fontFamily).isEqualTo(FontFamily.SANS_SERIF)
    }

    @Test
    fun pagesModeIsPaginatedOnOneColumnAndKeepsVersoTypography() {
        val p = VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.PAGES)
        assertThat(p.scroll).isFalse()
        assertThat(p.columnCount).isEqualTo(ColumnCount.ONE)
        assertThat(p.textAlign).isEqualTo(TextAlign.START)
        assertThat(p.hyphens).isFalse()
        assertThat(p.publisherStyles).isFalse()
    }

    @Test
    fun continuousModeScrolls() {
        assertThat(VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS).scroll).isTrue()
    }

    @Test
    fun switchingBetweenContinuousAndPagesChangesTheLayout() {
        assertThat(
            VersoReadingPreferences.changesLayout(prefs(scrollMode = ScrollMode.CONTINUOUS), prefs(scrollMode = ScrollMode.PAGES)),
        ).isTrue()
    }

    @Test
    fun darkPalettesAreLighterAndSpaced() {
        for (theme in listOf(AppTheme.DARK, AppTheme.NIGHT)) {
            val p = prefs(theme = theme)
            // Réglages par défaut : Literata (370, +0,015 em), interligne Normal 1,6 + 0,1.
            assertThat(p.fontWeight!!).isWithin(1e-9).of(0.925)
            assertThat(p.letterSpacing!!).isWithin(1e-9).of(0.03)
            assertThat(p.lineHeight!!).isWithin(1e-9).of(1.7)
            assertThat(p.paragraphSpacing!!).isWithin(1e-9).of(0.85)
            assertThat(p.theme).isEqualTo(Theme.DARK)
            assertThat(p.backgroundColor?.int).isEqualTo(ReadingStyle.colors(theme).background)
        }
        assertThat(prefs(theme = AppTheme.SEPIA).fontWeight!!).isWithin(1e-9).of(1.0)
        assertThat(prefs(theme = AppTheme.SEPIA).lineHeight!!).isWithin(1e-9).of(1.6)
        assertThat(prefs(theme = AppTheme.SEPIA).theme).isEqualTo(Theme.LIGHT)
    }

    @Test
    fun configurationServesBundledFontsAndKeepsScrollInsideTheChapter() {
        val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso(AppTheme.LIGHT) } }
        assertThat(configuration.servedAssets).contains(ReadingStyle.SERVED_ASSETS_PATTERN)
        assertThat(configuration.disablePageTurnsWhileScrolling).isTrue()
    }

    @Test
    fun linksUseTheAccentColorInEveryTheme() {
        for (theme in AppTheme.entries) {
            val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso(theme) } }
            val accent = CssColor.Int(ReadingStyle.colors(theme).link)
            assertThat(configuration.readiumCssRsProperties.linkColor).isEqualTo(accent)
            assertThat(configuration.readiumCssRsProperties.visitedColor).isEqualTo(accent)
        }
    }

    @Test
    fun onlyColorsChangeKeepsTheLayout() {
        assertThat(VersoReadingPreferences.changesLayout(prefs(), prefs(theme = AppTheme.SEPIA))).isFalse()
        assertThat(VersoReadingPreferences.changesLayout(prefs(theme = AppTheme.DARK), prefs(theme = AppTheme.NIGHT))).isFalse()
        assertThat(VersoReadingPreferences.changesLayout(prefs(), prefs())).isFalse()
    }

    @Test
    fun textSettingsChangeTheLayout() {
        assertThat(VersoReadingPreferences.changesLayout(prefs(), prefs(ReadingSettings(fontSizeSp = 21)))).isTrue()
        assertThat(VersoReadingPreferences.changesLayout(prefs(), prefs(ReadingSettings(font = ReadingFont.ATKINSON)))).isTrue()
        assertThat(VersoReadingPreferences.changesLayout(prefs(), prefs(ReadingSettings(lineSpacing = LineSpacing.TIGHT)))).isTrue()
        assertThat(VersoReadingPreferences.changesLayout(prefs(), prefs(ReadingSettings(margins = Margins.NARROW)))).isTrue()
        // Graisse et approche du texte sombre : les lignes se coupent ailleurs.
        assertThat(VersoReadingPreferences.changesLayout(prefs(), prefs(theme = AppTheme.DARK))).isTrue()
    }

    @Test
    fun searchMatchesUseTheVersoHighlightTemplate() {
        val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso(AppTheme.LIGHT) } }
        val template = configuration.decorationTemplates[Decoration.Style.Highlight::class]
        val decoration = SearchMatchDecoration.decorations(Locator(Url("c1.xhtml")!!, MediaType.XHTML), AppTheme.LIGHT).single()
        assertThat(template?.element?.invoke(decoration)).isEqualTo(SearchMatchDecoration.element(decoration))
    }

    @Test
    fun configurationLeavesInsetsToVerso() {
        val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso(AppTheme.LIGHT) } }
        assertThat(configuration.shouldApplyInsetsPadding).isFalse()
    }
}
