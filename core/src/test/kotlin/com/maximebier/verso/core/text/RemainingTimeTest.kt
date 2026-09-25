package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class RemainingTimeTest {

    @Test
    fun defaultSpeedIs250WordsPerMinute() {
        assertThat(DEFAULT_WORDS_PER_MINUTE).isEqualTo(250)
        assertThat(remainingMinutes(totalWords = 75_000, progression = 0.0)).isEqualTo(300)
    }

    @Test
    fun remainingWordsAreProportionalToProgression() {
        assertThat(remainingMinutes(100_000, 0.31)).isEqualTo(276)
        assertThat(remainingMinutes(100_000, 0.5)).isEqualTo(200)
    }

    @Test
    fun lessThanOneMinuteIsZero() {
        assertThat(remainingMinutes(100, 0.0)).isEqualTo(0)
        assertThat(remainingMinutes(249, 0.0)).isEqualTo(0)
    }

    @Test
    fun otherwiseRoundsToNearestMinuteHalfUp() {
        assertThat(remainingMinutes(250, 0.0)).isEqualTo(1)
        assertThat(remainingMinutes(374, 0.0)).isEqualTo(1) // 1,496 min
        assertThat(remainingMinutes(375, 0.0)).isEqualTo(2) // 1,5 min
    }

    @Test
    fun finishedOrOutOfRangeProgressionIsClamped() {
        assertThat(remainingMinutes(100_000, 1.0)).isEqualTo(0)
        assertThat(remainingMinutes(100_000, 1.5)).isEqualTo(0)
        assertThat(remainingMinutes(100_000, -0.2)).isEqualTo(400)
        assertThat(remainingMinutes(100_000, Double.NaN)).isEqualTo(400)
    }

    @Test
    fun emptyBookHasNothingLeft() {
        assertThat(remainingMinutes(0, 0.0)).isEqualTo(0)
        assertThat(remainingMinutes(-5, 0.0)).isEqualTo(0)
    }

    @Test
    fun customSpeedIsUsed() {
        assertThat(remainingMinutes(90_000, 0.0, wordsPerMinute = 300)).isEqualTo(300)
    }

    @Test
    fun nonPositiveSpeedIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { remainingMinutes(1_000, 0.0, wordsPerMinute = 0) }
    }

    @Test
    fun durationSplitsHoursAndMinutes() {
        assertThat(durationOfMinutes(330)).isEqualTo(DurationParts(hours = 5, minutes = 30))
        assertThat(durationOfMinutes(65)).isEqualTo(DurationParts(1, 5))
        assertThat(durationOfMinutes(300)).isEqualTo(DurationParts(5, 0))
        assertThat(durationOfMinutes(45)).isEqualTo(DurationParts(0, 45))
        assertThat(durationOfMinutes(0)).isEqualTo(DurationParts(0, 0))
    }

    @Test
    fun negativeDurationIsZero() {
        assertThat(durationOfMinutes(-3)).isEqualTo(DurationParts(0, 0))
    }
}
