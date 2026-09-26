package com.maximebier.verso.readium

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.ui.theme.VersoPalette
import org.junit.Test

class ReadingStyleTest {

    @Test
    fun nineteenSpIsReadiumFactorOfSixteenPixelRoot() {
        assertThat(ReadingStyle.fontSizeFactor()).isWithin(1e-9).of(1.1875)
        assertThat(ReadingStyle.fontSizeFactor(24.0)).isWithin(1e-9).of(1.5)
    }

    @Test
    fun paragraphSpacingIsHalfALineInRem() {
        // 0,5 × interligne 1,6 = 0,8 rem, soit 15,2 dp pour un texte de 19 dp (maquette : 15 dp).
        assertThat(ReadingStyle.paragraphSpacingRem()).isWithin(1e-9).of(0.8)
    }

    @Test
    fun darkThemeIsLighterAndSpaced() {
        assertThat(ReadingStyle.fontWeightFactor(dark = true)).isWithin(1e-9).of(0.95)
        assertThat(ReadingStyle.fontWeightFactor(dark = false)).isWithin(1e-9).of(1.0)
        assertThat(ReadingStyle.readiumLetterSpacing(dark = true)!!).isWithin(1e-9).of(0.02)
        assertThat(ReadingStyle.readiumLetterSpacing(dark = false)).isNull()
    }

    @Test
    fun marginsStayAtTwentyFourDp() {
        assertThat(ReadingStyle.fragmentPageMargins()).isWithin(1e-9).of(1.2)
        assertThat(ReadingStyle.webMinMargins(fontScale = 1f)).isWithin(1e-9).of(0.8)
        assertThat(ReadingStyle.webMinMargins(fontScale = 2f)).isWithin(1e-9).of(0.4)
    }

    @Test
    fun colorsComeFromTokens() {
        val light = ReadingStyle.colors(dark = false)
        val dark = ReadingStyle.colors(dark = true)

        assertThat(light.background).isEqualTo(VersoPalette.Light.background.toArgb())
        assertThat(light.text).isEqualTo(VersoPalette.Light.text.toArgb())
        assertThat(light.link).isEqualTo(VersoPalette.Light.accent.toArgb())
        assertThat(dark.background).isEqualTo(VersoPalette.Dark.background.toArgb())
        assertThat(dark.text).isEqualTo(VersoPalette.Dark.text.toArgb())
    }
}
