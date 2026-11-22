package com.maximebier.verso.core.journal

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import org.junit.Test

class SessionThresholdsTest {

    private val thresholds = SessionThresholds()

    private fun record(activeMs: Long, wordsRead: Long, from: Double = 0.1, to: Double = 0.2) = SessionRecord(
        id = 0, bookId = 1, startedAt = 0, endedAt = activeMs, activeMs = activeMs,
        start = BookPosition("a", from), end = BookPosition("b", to), wordsRead = wordsRead,
    )

    @Test
    fun keepsAnOrdinaryReadingSession() {
        assertThat(thresholds.keeps(record(activeMs = 60_000, wordsRead = 250), totalWords = 100_000)).isTrue()
    }

    @Test
    fun rejectsTooShortOrTooFewWords() {
        assertThat(thresholds.keeps(record(activeMs = 29_999, wordsRead = 250), totalWords = 100_000)).isFalse()
        assertThat(thresholds.keeps(record(activeMs = 60_000, wordsRead = 149), totalWords = 100_000)).isFalse()
        assertThat(thresholds.keeps(record(activeMs = 60_000, wordsRead = 0), totalWords = 100_000)).isFalse()
    }

    @Test
    fun rejectsSkimmingAboveAThousandWordsPerMinute() {
        assertThat(thresholds.keeps(record(activeMs = 60_000, wordsRead = 1_000), totalWords = 100_000)).isTrue()
        assertThat(thresholds.keeps(record(activeMs = 60_000, wordsRead = 1_001), totalWords = 100_000)).isFalse()
    }

    @Test
    fun bookWithoutCountedWordsIsJudgedOnProgression() {
        assertThat(thresholds.keeps(record(activeMs = 60_000, wordsRead = 0, from = 0.1, to = 0.2), totalWords = 0)).isTrue()
        assertThat(thresholds.keeps(record(activeMs = 60_000, wordsRead = 0, from = 0.1, to = 0.1005), totalWords = 0)).isFalse()
        assertThat(thresholds.keeps(record(activeMs = 20_000, wordsRead = 0, from = 0.1, to = 0.2), totalWords = 0)).isFalse()
    }
}
