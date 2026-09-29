package com.maximebier.verso.readium

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoPalette
import com.maximebier.verso.ui.theme.paletteOf
import org.junit.Test

class ReadingStyleTest {

    @Test
    fun spIsReadiumFactorOfSixteenPixelRoot() {
        assertThat(ReadingStyle.fontSizeFactor(20.0)).isWithin(1e-9).of(1.25)
        assertThat(ReadingStyle.fontSizeFactor(24.0)).isWithin(1e-9).of(1.5)
    }

    @Test
    fun paragraphSpacingIsHalfALineInRem() {
        assertThat(ReadingStyle.paragraphSpacingRem(1.6)).isWithin(1e-9).of(0.8)
        assertThat(ReadingStyle.paragraphSpacingRem(1.4)).isWithin(1e-9).of(0.7)
        assertThat(ReadingStyle.paragraphSpacingRem(1.8)).isWithin(1e-9).of(0.9)
    }

    @Test
    fun darkThemeIsLighterAndSpaced() {
        // tokens.json, typography.darkThemeAdjust : Atkinson 380 et +0,02 em, Literata 370 et +0,015 em.
        assertThat(ReadingStyle.fontWeightFactor(dark = true, ReadingFont.ATKINSON)).isWithin(1e-9).of(0.95)
        assertThat(ReadingStyle.fontWeightFactor(dark = true, ReadingFont.LITERATA)).isWithin(1e-9).of(0.925)
        assertThat(ReadingStyle.fontWeightFactor(dark = true, ReadingFont.SYSTEM)).isWithin(1e-9).of(0.95)
        assertThat(ReadingStyle.fontWeightFactor(dark = false, ReadingFont.LITERATA)).isWithin(1e-9).of(1.0)
        assertThat(ReadingStyle.readiumLetterSpacing(dark = true, ReadingFont.ATKINSON)!!).isWithin(1e-9).of(0.04)
        assertThat(ReadingStyle.readiumLetterSpacing(dark = true, ReadingFont.LITERATA)!!).isWithin(1e-9).of(0.03)
        assertThat(ReadingStyle.readiumLetterSpacing(dark = false, ReadingFont.ATKINSON)).isNull()
    }

    @Test
    fun darkThemeAddsATenthOfLineHeight() {
        assertThat(ReadingStyle.lineHeight(1.6, dark = true)).isWithin(1e-9).of(1.7)
        assertThat(ReadingStyle.lineHeight(1.4, dark = true)).isWithin(1e-9).of(1.5)
        assertThat(ReadingStyle.lineHeight(1.6, dark = false)).isWithin(1e-9).of(1.6)
    }

    @Test
    fun marginsFollowTheChoiceAndTheGutter() {
        // Gouttière de ReadiumCSS : 20 px sous 35 em, 30 px dès 35 em, 40 px dès 45 em, 50 px dès 75 em.
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 390f)).isWithin(1e-9).of(1.2)
        assertThat(ReadingStyle.fragmentPageMargins(16.0, widthDp = 390f)).isWithin(1e-9).of(0.8)
        assertThat(ReadingStyle.fragmentPageMargins(32.0, widthDp = 390f)).isWithin(1e-9).of(1.6)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 600f)).isWithin(1e-9).of(0.8)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 844f)).isWithin(1e-9).of(0.6)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 1_280f)).isWithin(1e-9).of(0.48)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 0f)).isWithin(1e-9).of(1.2)
    }

    @Test
    fun colorsComeFromTheTokensOfEachTheme() {
        val palettes = mapOf(
            AppTheme.LIGHT to VersoPalette.Light,
            AppTheme.SEPIA to VersoPalette.Sepia,
            AppTheme.DARK to VersoPalette.Dark,
            AppTheme.NIGHT to VersoPalette.Night,
        )
        for ((theme, palette) in palettes) {
            val colors = ReadingStyle.colors(theme)
            assertThat(colors.background).isEqualTo(palette.background.toArgb())
            assertThat(colors.text).isEqualTo(palette.text.toArgb())
            assertThat(colors.link).isEqualTo(paletteOf(ReadingStyle.linkTheme(theme)).accent.toArgb())
        }
    }

    /**
     * La couleur des liens est posée à la création du navigateur (configuration), pas par les préférences : elle
     * doit être la même pour les palettes qui ne recréent pas l'activité entre elles (clair ↔ sépia, sombre ↔ nuit).
     */
    @Test
    fun linkColorIsSharedWithinLightAndDarkFamilies() {
        assertThat(ReadingStyle.colors(AppTheme.SEPIA).link).isEqualTo(ReadingStyle.colors(AppTheme.LIGHT).link)
        assertThat(ReadingStyle.colors(AppTheme.NIGHT).link).isEqualTo(ReadingStyle.colors(AppTheme.DARK).link)
    }
}
