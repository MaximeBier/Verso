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
}
