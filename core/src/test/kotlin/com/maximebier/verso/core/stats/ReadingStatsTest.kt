package com.maximebier.verso.core.stats

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import com.maximebier.verso.core.text.remainingMinutes
import org.junit.Test

class ReadingStatsTest {

    private val minute = 60_000L

    private fun session(minutes: Long, wpm: Long) = SessionStat(activeMs = minutes * minute, wordsRead = minutes * wpm)

    @Test
    fun noSessionGivesNoSpeedAndTheDefaultRate() {
        val stats = readingStats(emptyList())
        assertThat(stats.totalActiveMs).isEqualTo(0)
        assertThat(stats.sessionCount).isEqualTo(0)
        assertThat(stats.wordsPerMinute).isNull()
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(DEFAULT_WORDS_PER_MINUTE)
    }

    @Test
    fun regularSessionsGiveTheirSpeed() {
        // 2 h 28 en 5 sessions à 240 mots par minute (maquette 2.08).
        val sessions = List(5) { SessionStat(activeMs = 148 * minute / 5, wordsRead = 240L * 148 / 5) }
        val stats = readingStats(sessions)
        assertThat(stats.totalActiveMs).isEqualTo(148 * minute)
        assertThat(stats.sessionCount).isEqualTo(5)
        assertThat(stats.wordsPerMinute).isEqualTo(240)
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(240)
    }

    @Test
    fun speedIsTheMedianWeightedByActiveTime() {
        // 40 min à 200, 10 min à 300, 10 min à 400 : la moitié du temps (30 min) tombe dans la session à 200.
        val stats = readingStats(listOf(session(10, 400), session(40, 200), session(10, 300)))
        assertThat(stats.wordsPerMinute).isEqualTo(200)
        // Mots / temps aurait donné 250 : la session rapide ne tire plus la vitesse.
    }

    @Test
    fun skimmingAndVerySlowSessionsAreLeftOutOfTheSpeedButCountInTheTime() {
        val stats = readingStats(listOf(session(20, 240), session(2, 1_500), session(30, 40)))
        assertThat(stats.wordsPerMinute).isEqualTo(240)
        assertThat(stats.totalActiveMs).isEqualTo(52 * minute)
        assertThat(stats.sessionCount).isEqualTo(3)
    }

    @Test
    fun speedBoundsAreInclusive() {
        assertThat(readingStats(listOf(session(5, StatsThresholds.MIN_WORDS_PER_MINUTE.toLong()))).wordsPerMinute)
            .isEqualTo(StatsThresholds.MIN_WORDS_PER_MINUTE)
        assertThat(readingStats(listOf(session(5, StatsThresholds.MAX_WORDS_PER_MINUTE.toLong()))).wordsPerMinute)
            .isEqualTo(StatsThresholds.MAX_WORDS_PER_MINUTE)
    }

    @Test
    fun tooLittleActiveTimeGivesNoSpeed() {
        val stats = readingStats(listOf(SessionStat(activeMs = StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED - 1, wordsRead = 200)))
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
    fun shortHistoryUsesTheSpeedOfAllBooks() {
        // 5 min mesurées sur ce livre : moins que le seuil, la vitesse de tous les livres l’emporte.
        val stats = readingStats(listOf(session(5, 400)), fallbackWordsPerMinute = 220)
        assertThat(stats.wordsPerMinute).isEqualTo(400)
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(220)
        // Sans vitesse globale, celle du livre, même courte, vaut mieux que 250.
        assertThat(readingStats(listOf(session(5, 400))).effectiveWordsPerMinute).isEqualTo(400)
    }

    @Test
    fun enoughHistoryUsesTheBookSpeed() {
        val stats = readingStats(listOf(session(12, 300)), fallbackWordsPerMinute = 220)
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(300)
        assertThat(readingStats(emptyList(), fallbackWordsPerMinute = 220).effectiveWordsPerMinute).isEqualTo(220)
    }

    @Test
    fun finishedBookHasNothingLeftAtAnyRate() {
        val rate = readingStats(listOf(SessionStat(10 * minute, 2_000))).effectiveWordsPerMinute
        assertThat(remainingMinutes(totalWords = 150_000, progression = 1.0, wordsPerMinute = rate)).isEqualTo(0)
    }

    @Test
    fun thresholdsMatchTheDesign() {
        assertThat(StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED).isEqualTo(60_000L)
        assertThat(StatsThresholds.MIN_WORDS_PER_MINUTE).isEqualTo(60)
        assertThat(StatsThresholds.MAX_WORDS_PER_MINUTE).isEqualTo(1_000)
        assertThat(StatsThresholds.MIN_BOOK_ACTIVE_MS).isEqualTo(10 * 60_000L)
    }
}
