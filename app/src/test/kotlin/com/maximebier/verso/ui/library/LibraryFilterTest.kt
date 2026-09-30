package com.maximebier.verso.ui.library

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookStatus
import org.junit.Test

class LibraryFilterTest {
    @Test
    fun eachFilterAcceptsOnlyItsState() {
        BookStatus.entries.forEach { assertThat(LibraryFilter.ALL.accepts(it)).isTrue() }
        assertThat(BookStatus.entries.filter(LibraryFilter.IN_PROGRESS::accepts)).containsExactly(BookStatus.IN_PROGRESS)
        assertThat(BookStatus.entries.filter(LibraryFilter.TO_READ::accepts)).containsExactly(BookStatus.TO_READ)
        assertThat(BookStatus.entries.filter(LibraryFilter.FINISHED::accepts)).containsExactly(BookStatus.FINISHED)
    }

    @Test
    fun resumeBookIsNotRepeatedInTheFilteredList() {
        val state = LibrarySamples.list.copy(filter = LibraryFilter.IN_PROGRESS)
        assertThat(state.filteredBooks.map { it.title }).contains("Madame Bovary")
        assertThat(state.listedBooks.map { it.title }).doesNotContain("Madame Bovary")
        assertThat(state.listedBooks.map { it.status }.toSet()).containsExactly(BookStatus.IN_PROGRESS)
    }
}
