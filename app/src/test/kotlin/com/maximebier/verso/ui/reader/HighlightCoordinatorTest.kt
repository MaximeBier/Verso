package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.testLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HighlightCoordinatorTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java)
        .allowMainThreadQueries()
        // Requêtes sur le fil du test : sinon le test reprend sur le fil de Room après chaque appel suspendu.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    private val repository = HighlightRepository(db.highlightDao()) { 1_000L }

    /** Texte du chapitre 2 de [testLocator]. */
    private val chapter = TextQuotes.normalize(
        "L’eau qui court au bord de l’herbe sépare d’une raie blanche la couleur des prés et celle des sillons, et " +
            "la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert.",
    )

    @After fun tearDown() = db.close()

    private suspend fun TestScope.setUp(textAvailable: Boolean = true): Triple<HighlightCoordinator, FakeReaderController, Long> {
        val bookId = db.bookDao().insert(testBook(sha256 = "a"))
        val fake = FakeReaderController(testLocator())
        val coordinator = HighlightCoordinator(
            bookId = bookId,
            highlights = repository,
            scope = backgroundScope,
            chapterText = { if (textAvailable) chapter else null },
            chapterPath = { listOf("Deuxième partie", "I") },
            locationLabel = { "Deuxième partie, chap. I · 30 %" },
            withTotalProgression = { it },
            clock = { 1_000L },
        )
        coordinator.attach(fake)
        return Triple(coordinator, fake, bookId)
    }

    @Test fun selectionShowsItsBeginningInTheBar() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp()
        fake.select("la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert.")
        runCurrent()
        assertThat(coordinator.state.value.selectionText).isEqualTo("la campagne ainsi ressemble à un grand manteau…")
        fake.clearSelection()
        runCurrent()
        assertThat(coordinator.state.value.selectionText).isNull()
    }

    @Test fun highlightCreatesTheRowAndClearsTheSelection() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche", before = "sépare d’une ", after = " la couleur")
        runCurrent()
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val row = repository.forBook(bookId).single()
        assertThat(row.text).isEqualTo("raie blanche")
        assertThat(row.note).isNull()
        assertThat(row.chapterPathList()).containsExactly("Deuxième partie", "I").inOrder()
        assertThat(fake.selectionCleared).isEqualTo(1)
        // Les flux Room émettent depuis leur propre exécuteur : attendre en temps réel que la marque soit poussée.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (fake.shownHighlights.lastOrNull()?.map { it.id } != listOf(row.id)) delay(10) }
        }
    }

    @Test fun overlappingHighlightMergesIntoOne() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche la couleur")
        runCurrent()
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        repository.setNote(repository.forBook(bookId).single().id, "première")
        fake.select("la couleur des prés")
        runCurrent()
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val row = repository.forBook(bookId).single()
        assertThat(row.text).isEqualTo("raie blanche la couleur des prés")
        assertThat(row.note).isEqualTo("première")
    }

    @Test fun unreadableChapterStillHighlights() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp(textAvailable = false)
        fake.select("raie blanche")
        runCurrent()
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        assertThat(repository.forBook(bookId).single().text).isEqualTo("raie blanche")
    }

    @Test fun copyEmitsThePassageWithoutCreatingAnything() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        coordinator.events.test {
            fake.select(" raie\nblanche ")
            runCurrent()
            coordinator.copySelection()
            assertThat(awaitItem()).isEqualTo(HighlightEvent.Copied("raie blanche"))
        }
        assertThat(repository.forBook(bookId)).isEmpty()
        assertThat(fake.selectionCleared).isEqualTo(1)
    }

    @Test fun tappingAHighlightOpensItsSheet() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        runCurrent()
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val id = repository.forBook(bookId).single().id
        fake.highlightTaps.emit(id)
        runCurrent()
        coordinator.awaitIdle()
        assertThat(coordinator.state.value.actions)
            .isEqualTo(HighlightActionsState(id, "raie blanche", null, "Deuxième partie, chap. I · 30 %"))
        coordinator.events.test {
            coordinator.copyHighlight()
            assertThat(awaitItem()).isEqualTo(HighlightEvent.Copied("raie blanche"))
        }
        assertThat(coordinator.state.value.actions).isNull()
    }

    @Test fun noteSheetCreatesNothingUntilSaved() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        runCurrent()
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "", editing = false))
        assertThat(fake.selectionCleared).isEqualTo(1)
        coordinator.onNoteChange("Image du manteau")
        coordinator.cancelNote()
        coordinator.awaitIdle()
        assertThat(coordinator.state.value.noteSheet).isNull()
        assertThat(repository.forBook(bookId)).isEmpty()
    }

    @Test fun saveCreatesTheHighlightWithItsNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        runCurrent()
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        coordinator.onNoteChange("  Image du manteau  ")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.forBook(bookId).single().note).isEqualTo("Image du manteau")
        assertThat(coordinator.state.value.noteSheet).isNull()
    }

    @Test fun blankNoteStillCreatesTheHighlight() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        runCurrent()
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        coordinator.onNoteChange("   ")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.forBook(bookId).single().note).isNull()
    }

    @Test fun editingAnExistingNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        runCurrent()
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val id = repository.forBook(bookId).single().id
        fake.highlightTaps.emit(id)
        runCurrent()
        coordinator.editNote()
        assertThat(coordinator.state.value.actions).isNull()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "", editing = false))
        coordinator.onNoteChange("Premier portrait.")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.get(id)!!.note).isEqualTo("Premier portrait.")
        fake.highlightTaps.emit(id)
        runCurrent()
        coordinator.editNote()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "Premier portrait.", editing = true))
    }

    @Test fun mergingKeepsBothNotes() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche la couleur")
        runCurrent()
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        coordinator.onNoteChange("première")
        coordinator.saveNote()
        coordinator.awaitIdle()
        fake.select("la couleur des prés")
        runCurrent()
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        coordinator.onNoteChange("seconde")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.forBook(bookId).single().note).isEqualTo("première\n\nseconde")
    }

    @Test fun deleteThenUndoRestoresTheHighlight() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        runCurrent()
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val row = repository.forBook(bookId).single()
        fake.highlightTaps.emit(row.id)
        runCurrent()
        coordinator.events.test {
            coordinator.deleteHighlight()
            val deleted = awaitItem() as HighlightEvent.Deleted
            assertThat(repository.forBook(bookId)).isEmpty()
            coordinator.undoDelete(deleted.row)
            coordinator.awaitIdle()
            assertThat(repository.forBook(bookId)).containsExactly(row)
        }
    }
}
