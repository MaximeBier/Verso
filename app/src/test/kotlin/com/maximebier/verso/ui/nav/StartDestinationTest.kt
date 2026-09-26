package com.maximebier.verso.ui.nav

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.LibraryRules
import com.maximebier.verso.data.db.BookEntity
import org.junit.Test

class StartDestinationTest {

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun book(lastOpenedAt: Long?) = BookEntity(
        id = 7,
        title = "Madame Bovary",
        author = "Gustave Flaubert",
        filePath = "/books/a.epub",
        sha256 = "a",
        coverPath = null,
        sizeBytes = 1,
        originalFileName = "madame-bovary.epub",
        importedAt = 0,
        lastOpenedAt = lastOpenedAt,
        readingLocatorJson = null,
        progression = 0.31,
        totalWords = 100_000,
    )

    @Test
    fun reopensBookReadLessThanADayAgo() {
        assertThat(StartDestination.bookToReopen(book(now - 2 * hour), reopenEnabled = true, now = now, hasIncomingImport = false))
            .isEqualTo(7L)
    }

    @Test
    fun doesNotReopenAfterTheWindow() {
        val old = book(now - LibraryRules.REOPEN_WINDOW_MS - 1)

        assertThat(StartDestination.bookToReopen(old, reopenEnabled = true, now = now, hasIncomingImport = false)).isNull()
    }

    @Test
    fun doesNotReopenWhenSettingIsOff() {
        assertThat(StartDestination.bookToReopen(book(now - hour), reopenEnabled = false, now = now, hasIncomingImport = false))
            .isNull()
    }

    @Test
    fun pendingImportWinsOverReopen() {
        assertThat(StartDestination.bookToReopen(book(now - hour), reopenEnabled = true, now = now, hasIncomingImport = true))
            .isNull()
    }

    @Test
    fun noBookNoReopen() {
        assertThat(StartDestination.bookToReopen(null, reopenEnabled = true, now = now, hasIncomingImport = false)).isNull()
        assertThat(StartDestination.bookToReopen(book(null), reopenEnabled = true, now = now, hasIncomingImport = false)).isNull()
    }
}
