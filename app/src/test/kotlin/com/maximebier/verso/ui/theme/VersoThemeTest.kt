package com.maximebier.verso.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VersoThemeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun eachThemeProvidesItsPaletteAndDarkness() {
        val expected = mapOf(
            AppTheme.LIGHT to VersoPalette.Light,
            AppTheme.SEPIA to VersoPalette.Sepia,
            AppTheme.DARK to VersoPalette.Dark,
            AppTheme.BLACK to VersoPalette.Black,
        )
        val seen = mutableMapOf<AppTheme, Triple<VersoColors, Boolean, AppTheme>>()
        composeRule.setContent {
            for (theme in AppTheme.entries) {
                VersoTheme(theme = theme) { seen[theme] = Triple(VersoTheme.colors, VersoTheme.isDark, VersoTheme.theme) }
            }
        }
        composeRule.waitForIdle()
        for ((theme, palette) in expected) {
            assertThat(seen.getValue(theme).first).isEqualTo(palette)
            assertThat(seen.getValue(theme).second).isEqualTo(theme.isDark)
            assertThat(seen.getValue(theme).third).isEqualTo(theme)
        }
    }

    @Test
    fun darkPalettesUseTheDarkCovers() {
        var black: List<androidx.compose.ui.graphics.Color>? = null
        var sepia: List<androidx.compose.ui.graphics.Color>? = null
        composeRule.setContent {
            VersoTheme(theme = AppTheme.BLACK) { black = VersoTheme.coverPalette }
            VersoTheme(theme = AppTheme.SEPIA) { sepia = VersoTheme.coverPalette }
        }
        composeRule.waitForIdle()
        assertThat(black).isEqualTo(VersoPalette.CoverDark)
        assertThat(sepia).isEqualTo(VersoPalette.CoverLight)
    }
}
