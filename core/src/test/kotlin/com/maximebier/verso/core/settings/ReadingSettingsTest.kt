package com.maximebier.verso.core.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingSettingsTest {

    @Test
    fun defaultsMatchTheSpec() {
        val settings = ReadingSettings()
        assertThat(settings.font).isEqualTo(ReadingFont.LITERATA)
        assertThat(settings.fontSizeSp).isEqualTo(20)
        assertThat(settings.lineSpacing).isEqualTo(LineSpacing.NORMAL)
        assertThat(settings.margins).isEqualTo(Margins.NORMAL)
        assertThat(settings.defaultScrollMode).isEqualTo(ScrollMode.CONTINUOUS)
    }

    @Test
    fun lineSpacingAndMarginsHaveTheSpecValues() {
        assertThat(LineSpacing.entries.map { it.factor }).containsExactly(1.4, 1.6, 1.8).inOrder()
        assertThat(Margins.entries.map { it.dp }).containsExactly(16.0, 24.0, 32.0).inOrder()
    }

    @Test
    fun sizeGoesUpAndDownByOneSp() {
        val settings = ReadingSettings()
        assertThat(settings.larger().fontSizeSp).isEqualTo(21)
        assertThat(settings.smaller().fontSizeSp).isEqualTo(19)
    }

    @Test
    fun sizeStopsAtTheBounds() {
        val smallest = ReadingSettings(fontSizeSp = ReadingSettingsLimits.MIN_FONT_SIZE_SP)
        val largest = ReadingSettings(fontSizeSp = ReadingSettingsLimits.MAX_FONT_SIZE_SP)
        assertThat(smallest.canShrink).isFalse()
        assertThat(smallest.canGrow).isTrue()
        assertThat(smallest.smaller()).isEqualTo(smallest)
        assertThat(largest.canGrow).isFalse()
        assertThat(largest.canShrink).isTrue()
        assertThat(largest.larger()).isEqualTo(largest)
    }

    @Test
    fun withFontSizeClampsToTheRange() {
        assertThat(ReadingSettings().withFontSize(8).fontSizeSp).isEqualTo(14)
        assertThat(ReadingSettings().withFontSize(99).fontSizeSp).isEqualTo(32)
        assertThat(ReadingSettings().withFontSize(24).fontSizeSp).isEqualTo(24)
    }

    @Test
    fun outOfRangeSizeIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { ReadingSettings(fontSizeSp = 13) }
        assertThrows(IllegalArgumentException::class.java) { ReadingSettings(fontSizeSp = 33) }
    }
}
