package com.maximebier.verso.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryRulesTest {

    private val now = 1_790_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test
    fun neverOpenedBookIsToRead() {
        assertThat(LibraryRules.status(hasReadingLocator = false, progression = 0.0)).isEqualTo(BookStatus.TO_READ)
        assertThat(LibraryRules.status(hasReadingLocator = false, progression = 0.5)).isEqualTo(BookStatus.TO_READ)
    }

    @Test
    fun manualChoiceWinsOverTheComputedState() {
        assertThat(LibraryRules.status(false, 0.0, override = BookStatus.FINISHED)).isEqualTo(BookStatus.FINISHED)
        assertThat(LibraryRules.status(true, 0.995, override = BookStatus.TO_READ)).isEqualTo(BookStatus.TO_READ)
        assertThat(LibraryRules.status(true, 0.995, override = BookStatus.IN_PROGRESS)).isEqualTo(BookStatus.IN_PROGRESS)
    }

    @Test
    fun noManualChoiceKeepsTheComputedState() {
        assertThat(LibraryRules.status(true, 0.31, override = null)).isEqualTo(BookStatus.IN_PROGRESS)
        assertThat(LibraryRules.status(true, 0.99, override = null)).isEqualTo(BookStatus.FINISHED)
    }

    @Test
    fun openedBookBelowThresholdIsInProgress() {
        assertThat(LibraryRules.status(true, 0.0)).isEqualTo(BookStatus.IN_PROGRESS)
        assertThat(LibraryRules.status(true, 0.31)).isEqualTo(BookStatus.IN_PROGRESS)
        assertThat(LibraryRules.status(true, 0.989)).isEqualTo(BookStatus.IN_PROGRESS)
    }

    @Test
    fun bookAtOrAbove99PercentIsFinished() {
        assertThat(LibraryRules.status(true, 0.99)).isEqualTo(BookStatus.FINISHED)
        assertThat(LibraryRules.status(true, 1.0)).isEqualTo(BookStatus.FINISHED)
    }

    @Test
    fun constantsMatchSpec() {
        assertThat(LibraryRules.FINISHED_PROGRESSION).isEqualTo(0.99)
        assertThat(LibraryRules.REOPEN_WINDOW_MS).isEqualTo(24 * hour)
    }

    @Test
    fun reopensBookReadLessThan24HoursAgo() {
        assertThat(LibraryRules.shouldReopen(enabled = true, lastOpenedAt = now - hour, now = now)).isTrue()
        assertThat(LibraryRules.shouldReopen(true, now - 24 * hour + 1, now)).isTrue()
    }

    @Test
    fun doesNotReopenAfter24Hours() {
        assertThat(LibraryRules.shouldReopen(true, now - 24 * hour, now)).isFalse()
        assertThat(LibraryRules.shouldReopen(true, now - 72 * hour, now)).isFalse()
    }

    @Test
    fun doesNotReopenWhenDisabledOrNeverOpened() {
        assertThat(LibraryRules.shouldReopen(enabled = false, lastOpenedAt = now - hour, now = now)).isFalse()
        assertThat(LibraryRules.shouldReopen(enabled = true, lastOpenedAt = null, now = now)).isFalse()
    }

    @Test
    fun clockSetBackStillCountsAsRecent() {
        assertThat(LibraryRules.shouldReopen(true, now + 5_000, now)).isTrue()
    }
}
