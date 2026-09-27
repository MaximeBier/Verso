package com.maximebier.verso.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

// Robolectric : Color de Readium s’appuie sur android.graphics.Color.
@OptIn(ExperimentalReadiumApi::class)
@RunWith(AndroidJUnit4::class)
class VersoReadingPreferencesTest {

    @Test
    fun lightPreferencesImposeVersoLayout() {
        val prefs = VersoReadingPreferences.epub(dark = false)

        assertThat(prefs.scroll).isTrue()
        assertThat(prefs.textAlign).isEqualTo(TextAlign.START)
        assertThat(prefs.hyphens).isFalse()
        assertThat(prefs.publisherStyles).isFalse()
        assertThat(prefs.fontFamily).isEqualTo(VersoReadingPreferences.ATKINSON)
        assertThat(prefs.fontSize!!).isWithin(1e-9).of(1.1875)
        assertThat(prefs.lineHeight!!).isWithin(1e-9).of(1.6)
        assertThat(prefs.paragraphSpacing!!).isWithin(1e-9).of(0.8)
        assertThat(prefs.pageMargins!!).isWithin(1e-9).of(1.2)
        assertThat(prefs.letterSpacing).isNull()
        assertThat(prefs.theme).isEqualTo(Theme.LIGHT)
        assertThat(prefs.backgroundColor?.int).isEqualTo(ReadingStyle.colors(dark = false).background)
        assertThat(prefs.textColor?.int).isEqualTo(ReadingStyle.colors(dark = false).text)
    }

    @Test
    fun darkPreferencesAreLighterAndSpaced() {
        val prefs = VersoReadingPreferences.epub(dark = true)

        assertThat(prefs.fontWeight!!).isWithin(1e-9).of(0.95)
        assertThat(prefs.letterSpacing!!).isWithin(1e-9).of(0.02)
        assertThat(prefs.theme).isEqualTo(Theme.DARK)
        assertThat(prefs.backgroundColor?.int).isEqualTo(ReadingStyle.colors(dark = true).background)
    }

    @Test
    fun configurationServesBundledFontsAndKeepsScrollInsideTheChapter() {
        val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso() } }

        assertThat(configuration.servedAssets).contains(ReadingStyle.SERVED_ASSETS_PATTERN)
        assertThat(configuration.disablePageTurnsWhileScrolling).isTrue()
    }

    @Test
    fun configurationLeavesInsetsToVerso() {
        val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso() } }

        assertThat(configuration.shouldApplyInsetsPadding).isFalse()
    }
}
