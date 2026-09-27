package com.maximebier.verso.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingFont
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypographyTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun eachFontHasItsFamily() {
        assertThat(fontFamilyFor(ReadingFont.LITERATA)).isSameInstanceAs(LiterataFamily)
        assertThat(fontFamilyFor(ReadingFont.ATKINSON)).isSameInstanceAs(AtkinsonFamily)
        assertThat(fontFamilyFor(ReadingFont.SYSTEM)).isEqualTo(FontFamily.Default)
    }

    @Test
    fun typographyKeepsTheV1ScaleWithTheChosenFamily() {
        val atkinson = VersoTypography.of(ReadingFont.ATKINSON)
        val literata = VersoTypography.of(ReadingFont.LITERATA)
        assertThat(atkinson.body.fontFamily).isSameInstanceAs(AtkinsonFamily)
        assertThat(literata.body.fontFamily).isSameInstanceAs(LiterataFamily)
        assertThat(literata.body.fontSize).isEqualTo(atkinson.body.fontSize)
        assertThat(literata.screenTitle.lineHeight).isEqualTo(atkinson.screenTitle.lineHeight)
        assertThat(literata.material.bodyLarge).isEqualTo(literata.body)
    }

    @Test
    fun themeProvidesTheChosenTypography() {
        var family: FontFamily? = null
        composeRule.setContent {
            VersoTheme(darkTheme = false, font = ReadingFont.ATKINSON) {
                family = VersoTheme.typography.body.fontFamily
                Text("Aa")
            }
        }
        composeRule.waitForIdle()
        assertThat(family).isSameInstanceAs(AtkinsonFamily)
    }

    @Test
    fun literataIsTheDefault() {
        var family: FontFamily? = null
        composeRule.setContent { VersoTheme(darkTheme = false) { family = VersoTheme.typography.body.fontFamily } }
        composeRule.waitForIdle()
        assertThat(family).isSameInstanceAs(LiterataFamily)
    }
}
