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
    fun onlyDarkAndNightAreDark() {
        assertThat(AppTheme.entries.filter { it.isDark }).containsExactly(AppTheme.DARK, AppTheme.NIGHT)
    }

    @Test
    fun sepiaAndNightIgnoreThePhone() {
        assertThat(ThemeMode.SEPIA.resolve(systemDark = true)).isEqualTo(AppTheme.SEPIA)
        assertThat(ThemeMode.NIGHT.resolve(systemDark = false)).isEqualTo(AppTheme.NIGHT)
        assertThat(ThemeMode.SEPIA.isDark(systemDark = true)).isFalse()
        assertThat(ThemeMode.NIGHT.isDark(systemDark = false)).isTrue()
    }

    @Test
    fun autoAppliesTheChosenDarkThemeWhenThePhoneIsDark() {
        assertThat(ThemeMode.AUTO.resolve(systemDark = true, darkVariant = DarkThemeVariant.NIGHT)).isEqualTo(AppTheme.NIGHT)
        assertThat(ThemeMode.AUTO.resolve(systemDark = true, darkVariant = DarkThemeVariant.DARK)).isEqualTo(AppTheme.DARK)
        assertThat(ThemeMode.AUTO.resolve(systemDark = false, darkVariant = DarkThemeVariant.NIGHT)).isEqualTo(AppTheme.LIGHT)
    }

    @Test
    fun explicitThemesIgnoreTheDarkThemeChoice() {
        assertThat(ThemeMode.DARK.resolve(systemDark = true, darkVariant = DarkThemeVariant.NIGHT)).isEqualTo(AppTheme.DARK)
        assertThat(ThemeMode.NIGHT.resolve(systemDark = true, darkVariant = DarkThemeVariant.DARK)).isEqualTo(AppTheme.NIGHT)
        assertThat(ThemeMode.SEPIA.resolve(systemDark = true, darkVariant = DarkThemeVariant.NIGHT)).isEqualTo(AppTheme.SEPIA)
    }

    @Test
    fun settingsOrderMatchesTheMockups() {
        assertThat(ThemeMode.entries).containsExactly(
            ThemeMode.AUTO, ThemeMode.LIGHT, ThemeMode.SEPIA, ThemeMode.DARK, ThemeMode.NIGHT,
        ).inOrder()
    }
}
