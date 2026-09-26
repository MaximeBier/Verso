package com.maximebier.verso.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

// Robolectric : l’objet compagnon de ReflowableWebPreferences appelle android.graphics.Color.
@OptIn(ExperimentalReadiumApi::class)
@RunWith(AndroidJUnit4::class)
class VersoReadingPreferencesTest {

    @Test
    fun lightPreferencesImposeVersoLayout() {
        val prefs = VersoReadingPreferences.reflowableWeb(dark = false)

        assertThat(prefs.scroll).isTrue()
        assertThat(prefs.textAlign).isEqualTo(TextAlign.START)
        assertThat(prefs.hyphens).isFalse()
        assertThat(prefs.fontFamily).isEqualTo(VersoReadingPreferences.ATKINSON)
        assertThat(prefs.fontSize!!).isWithin(1e-9).of(1.1875)
        assertThat(prefs.lineHeight!!).isWithin(1e-9).of(1.6)
        assertThat(prefs.paragraphSpacing!!).isWithin(1e-9).of(0.8)
        assertThat(prefs.minMargins!!).isWithin(1e-9).of(0.8)
        assertThat(prefs.overridePublisherColors).isTrue()
        assertThat(prefs.letterSpacing).isNull()
        assertThat(prefs.backgroundColor?.int).isEqualTo(ReadingStyle.colors(dark = false).background)
        assertThat(prefs.textColor?.int).isEqualTo(ReadingStyle.colors(dark = false).text)
        assertThat(prefs.linkColor?.int).isEqualTo(ReadingStyle.colors(dark = false).link)
    }

    @Test
    fun darkPreferencesAreLighterAndSpaced() {
        val prefs = VersoReadingPreferences.reflowableWeb(dark = true)

        assertThat(prefs.fontWeight!!).isWithin(1e-9).of(0.95)
        assertThat(prefs.letterSpacing!!).isWithin(1e-9).of(0.02)
        assertThat(prefs.backgroundColor?.int).isEqualTo(ReadingStyle.colors(dark = true).background)
    }

    @Test
    fun systemFontScaleKeepsFontSizeAndShrinksMarginFactor() {
        // La WebView agrandit déjà le texte ; Readium multiplie la marge par fontScale, on compense.
        val prefs = VersoReadingPreferences.reflowableWeb(dark = false, fontScale = 2f)

        assertThat(prefs.fontSize!!).isWithin(1e-9).of(1.1875)
        assertThat(prefs.minMargins!!).isWithin(1e-9).of(0.4)
    }

    @Test
    fun configurationServesBundledAtkinson() {
        val configuration = VersoReadingPreferences.reflowableWebConfiguration()
        val declaration = configuration.fontFamilyDeclarations.declarations.single()

        assertThat(configuration.servedAssets).contains(ReadingStyle.SERVED_ASSETS_PATTERN)
        assertThat(declaration.fontFamily).isEqualTo(ReadingStyle.FONT_FAMILY_NAME)
        assertThat(declaration.fontFaces).hasSize(2)
    }
}
