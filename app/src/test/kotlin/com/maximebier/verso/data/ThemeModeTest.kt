package com.maximebier.verso.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeModeTest {
    @Test
    fun autoFollowsThePhone() {
        assertThat(ThemeMode.AUTO.isDark(systemDark = true)).isTrue()
        assertThat(ThemeMode.AUTO.isDark(systemDark = false)).isFalse()
    }

    @Test
    fun lightAndDarkIgnoreThePhone() {
        assertThat(ThemeMode.LIGHT.isDark(systemDark = true)).isFalse()
        assertThat(ThemeMode.DARK.isDark(systemDark = false)).isTrue()
    }

    @Test
    fun resolveGivesThePaletteToDisplay() {
        assertThat(ThemeMode.AUTO.resolve(systemDark = false)).isEqualTo(AppTheme.LIGHT)
        assertThat(ThemeMode.AUTO.resolve(systemDark = true)).isEqualTo(AppTheme.DARK)
        assertThat(ThemeMode.LIGHT.resolve(systemDark = true)).isEqualTo(AppTheme.LIGHT)
        assertThat(ThemeMode.DARK.resolve(systemDark = false)).isEqualTo(AppTheme.DARK)
    }

    @Test
    fun onlyDarkAndBlackAreDark() {
        assertThat(AppTheme.entries.filter { it.isDark }).containsExactly(AppTheme.DARK, AppTheme.BLACK)
    }

    @Test
    fun sepiaAndBlackIgnoreThePhone() {
        assertThat(ThemeMode.SEPIA.resolve(systemDark = true)).isEqualTo(AppTheme.SEPIA)
        assertThat(ThemeMode.BLACK.resolve(systemDark = false)).isEqualTo(AppTheme.BLACK)
        assertThat(ThemeMode.SEPIA.isDark(systemDark = true)).isFalse()
        assertThat(ThemeMode.BLACK.isDark(systemDark = false)).isTrue()
    }

    @Test
    fun settingsOrderMatchesTheMockups() {
        assertThat(ThemeMode.entries).containsExactly(
            ThemeMode.AUTO, ThemeMode.LIGHT, ThemeMode.SEPIA, ThemeMode.DARK, ThemeMode.BLACK,
        ).inOrder()
    }
}
