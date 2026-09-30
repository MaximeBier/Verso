package com.maximebier.verso.core.stats

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import com.maximebier.verso.core.text.remainingMinutes
import org.junit.Test

class ReadingStatsTest {

    private val minute = 60_000L

    @Test
    fun noSessionGivesNoSpeedAndTheDefaultRate() {
        val stats = readingStats(emptyList())
        assertThat(stats.totalActiveMs).isEqualTo(0)
        assertThat(stats.sessionCount).isEqualTo(0)
        assertThat(stats.wordsPerMinute).isNull()
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(DEFAULT_WORDS_PER_MINUTE)
    }

    @Test
    fun speedIsWordsReadOverActiveTime() {
        // 2 h 28 en 5 sessions à 240 mots par minute (maquette 2.08).
        val sessions = List(5) { SessionStat(activeMs = 148 * minute / 5, wordsRead = 240L * 148 / 5) }
        val stats = readingStats(sessions)
        assertThat(stats.totalActiveMs).isEqualTo(148 * minute)
        assertThat(stats.sessionCount).isEqualTo(5)
        assertThat(stats.wordsPerMinute).isEqualTo(240)
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(240)
    }

    @Test
    fun tooLittleActiveTimeGivesNoSpeed() {
        val stats = readingStats(listOf(SessionStat(activeMs = StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED - 1, wordsRead = 400)))
        assertThat(stats.sessionCount).isEqualTo(1)
        assertThat(stats.wordsPerMinute).isNull()
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(DEFAULT_WORDS_PER_MINUTE)
    }

    @Test
    fun noWordReadGivesNoSpeed() {
        assertThat(readingStats(listOf(SessionStat(activeMs = 10 * minute, wordsRead = 0))).wordsPerMinute).isNull()
    }

    @Test
    fun negativeValuesAreIgnored() {
        val stats = readingStats(listOf(SessionStat(activeMs = -5 * minute, wordsRead = -10), SessionStat(10 * minute, 2_000)))
        assertThat(stats.totalActiveMs).isEqualTo(10 * minute)
        assertThat(stats.wordsPerMinute).isEqualTo(200)
    }

    @Test
    fun speedIsNeverZero() {
        assertThat(readingStats(listOf(SessionStat(activeMs = 600 * minute, wordsRead = 1))).wordsPerMinute).isEqualTo(1)
    }

    @Test
    fun finishedBookHasNothingLeftAtAnyRate() {
        val rate = readingStats(listOf(SessionStat(10 * minute, 2_000))).effectiveWordsPerMinute
        assertThat(remainingMinutes(totalWords = 150_000, progression = 1.0, wordsPerMinute = rate)).isEqualTo(0)
    }

    @Test
    fun thresholdMatchesTheDesign() {
        assertThat(StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED).isEqualTo(60_000L)
    }
}
