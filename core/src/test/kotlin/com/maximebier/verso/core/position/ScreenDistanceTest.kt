package com.maximebier.verso.core.position

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import org.junit.Test

class ScreenDistanceTest {

    private fun at(progression: Double) = BookPosition(locatorJson = "{\"p\":$progression}", totalProgression = progression)

    @Test
    fun progressionDistanceConvertsProgressionToScreens() {
        val distance = ProgressionScreenDistance(250.0)
        assertThat(distance.screensBetween(at(0.10), at(0.12))).isWithin(1e-9).of(5.0)
    }

    @Test
    fun progressionDistanceIsSymmetric() {
        val distance = ProgressionScreenDistance(250.0)
        assertThat(distance.screensBetween(at(0.12), at(0.10))).isWithin(1e-9).of(5.0)
        assertThat(distance.screensBetween(at(0.3), at(0.3))).isEqualTo(0.0)
    }

    @Test
    fun bookShorterThanOneScreenCountsAsOneScreen() {
        assertThat(ProgressionScreenDistance(0.0).screensBetween(at(0.0), at(1.0))).isWithin(1e-9).of(1.0)
        assertThat(ProgressionScreenDistance(0.4).screensBetween(at(0.0), at(1.0))).isWithin(1e-9).of(1.0)
        assertThat(ProgressionScreenDistance(Double.NaN).screensBetween(at(0.0), at(0.5))).isWithin(1e-9).of(0.5)
    }

    @Test
    fun estimateBookScreensUsesLinesPerScreen() {
        // 60 000 mots / 6 mots par ligne = 10 000 lignes ; 2 000 px / 50 px = 40 lignes par écran.
        assertThat(estimateBookScreens(60_000, 2_000, 50f)).isWithin(1e-9).of(250.0)
    }

    @Test
    fun estimateBookScreensHonoursWordsPerLine() {
        assertThat(estimateBookScreens(60_000, 2_000, 50f, wordsPerLine = 5.0)).isWithin(1e-9).of(300.0)
    }

    @Test
    fun estimateBookScreensNeverReturnsLessThanOneScreen() {
        assertThat(estimateBookScreens(0, 2_000, 50f)).isEqualTo(1.0)
        assertThat(estimateBookScreens(-10, 2_000, 50f)).isEqualTo(1.0)
        assertThat(estimateBookScreens(12, 2_000, 50f)).isEqualTo(1.0)
        assertThat(estimateBookScreens(60_000, 0, 50f)).isEqualTo(1.0)
        assertThat(estimateBookScreens(60_000, 2_000, 0f)).isEqualTo(1.0)
        assertThat(estimateBookScreens(60_000, 2_000, Float.NaN)).isEqualTo(1.0)
        assertThat(estimateBookScreens(60_000, 2_000, 50f, wordsPerLine = 0.0)).isEqualTo(1.0)
    }

    @Test
    fun defaultThresholdsMatchSpec() {
        val t = ReadingThresholds()
        assertThat(t.flingScreensPerSecond).isEqualTo(4.0)
        assertThat(t.navigationWindowMs).isEqualTo(5_000L)
        assertThat(t.navigationWindowScreens).isEqualTo(3.0)
        assertThat(t.returnCardMinScreens).isEqualTo(1.0)
        assertThat(t.confirmReadingMs).isEqualTo(25_000L)
        assertThat(t.confirmMaxDriftScreens).isEqualTo(1.0)
        assertThat(t.saveDebounceMs).isEqualTo(500L)
    }
}
