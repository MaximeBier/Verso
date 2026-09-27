package com.maximebier.verso.readium

import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
@RunWith(AndroidJUnit4::class)
class ReadingFontScaleTest {

    @Test
    fun readingScaleFollowsTheSystemFontSize() {
        assertThat(ReadingStyle.readingFontScale(Density(2.625f, fontScale = 1f), fontSizeSp = 20)).isWithin(1e-6).of(1.0)
        // Android 14 : échelle non linéaire, 20 sp à 200 % ne doublent pas (les grandes tailles grossissent moins).
        assertThat(ReadingStyle.readingFontScale(Density(2.625f, fontScale = 2f), fontSizeSp = 20))
            .isIn(com.google.common.collect.Range.open(1.5, 2.0))
    }

    @Test
    fun readingTextGrowsWithTheScale() {
        val prefs = VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS, fontScale = 2.0)
        assertThat(prefs.fontSize!!).isWithin(1e-9).of(2.5)
        assertThat(prefs.pageMargins!!).isWithin(1e-9).of(1.2)
    }
}
