package com.maximebier.verso.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import org.junit.Test
import org.junit.runner.RunWith

/** Panneaux qui suspendent le temps actif de la session, et parties de l’EPUB déclarées hors lecture. */
@RunWith(AndroidJUnit4::class)
class ReaderPanelsTest {
    private val reading = ReaderUiState()
    private val noHighlight = HighlightUiState()

    @Test
    fun readingAloneIsNotAPanel() {
        assertThat(anyPanelOpen(reading, noHighlight)).isFalse()
        assertThat(anyPanelOpen(reading.copy(barsVisible = true), noHighlight)).isFalse()
    }

    @Test
    fun everyPanelCounts() {
        listOf(
            reading.copy(tocVisible = true),
            reading.copy(journalVisible = true),
            reading.copy(settingsVisible = true),
            reading.copy(searchVisible = true),
            reading.copy(notesVisible = true),
        ).forEach { assertThat(anyPanelOpen(it, noHighlight)).isTrue() }
        listOf(
            HighlightUiState(selectionText = "passage"),
            HighlightUiState(noteSheet = NoteSheetState("passage", "", editing = false)),
            HighlightUiState(actions = HighlightActionsState(1, "passage", null, null)),
        ).forEach { assertThat(anyPanelOpen(reading, it)).isTrue() }
    }

    @Test
    fun nonReadingCheckComparesTheResourceOfThePosition() {
        val check = nonReadingCheck(setOf("OPS/about.xhtml"))
        val about = BookPosition("""{"href":"OPS/about.xhtml#fin","type":"application/xhtml+xml","locations":{}}""", 0.99)
        val chapter = BookPosition("""{"href":"OPS/c1.xhtml","type":"application/xhtml+xml","locations":{}}""", 0.5)
        assertThat(check(about)).isTrue()
        assertThat(check(chapter)).isFalse()
        assertThat(check(about)).isTrue()
        assertThat(nonReadingCheck(emptySet())(about)).isFalse()
    }
}
