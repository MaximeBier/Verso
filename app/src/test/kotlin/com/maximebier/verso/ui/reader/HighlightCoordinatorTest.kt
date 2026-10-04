package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.core.translation.TranslationFailure
import com.maximebier.verso.core.translation.TranslationResult
import com.maximebier.verso.core.translation.Translator
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.testLocator
import com.maximebier.verso.readium.Locators
import kotlinx.coroutines.CompletableDeferred
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

    private suspend fun TestScope.setUp(
        textAvailable: Boolean = true,
        translator: Translator? = null,
    ): Triple<HighlightCoordinator, FakeReaderController, Long> {
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
            translator = translator,
        )
        coordinator.attach(fake)
        return Triple(coordinator, fake, bookId)
    }

    /** « Note » puis « Enregistrer » sur la sélection en cours. */
    private suspend fun TestScope.annotate(coordinator: HighlightCoordinator, note: String) {
        runCurrent()
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        coordinator.onNoteChange(note)
        coordinator.saveNote()
        coordinator.awaitIdle()
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

    @Test fun noteCreatesTheRowMarksItAndClearsTheSelection() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche", before = "sépare d’une ", after = " la couleur")
        annotate(coordinator, "Image")
        val row = repository.forBook(bookId).single()
        assertThat(row.text).isEqualTo("raie blanche")
        assertThat(row.note).isEqualTo("Image")
        assertThat(row.chapterPathList()).containsExactly("Deuxième partie", "I").inOrder()
        assertThat(fake.selectionCleared).isEqualTo(1)
        // Les flux Room émettent depuis leur propre exécuteur : attendre en temps réel que la marque soit poussée.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (fake.shownHighlights.lastOrNull()?.map { it.id } != listOf(row.id)) delay(10) }
        }
    }

    @Test fun highlightWithoutNoteFromBeforeStaysHiddenAndUnmerged() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        val legacy = db.highlightDao().insert(
            HighlightEntity(
                bookId = bookId, locatorJson = Locators.toJson(testLocator()), text = "raie blanche", note = null,
                progression = 0.3, chapterPath = "", createdAt = 1L, updatedAt = 1L,
            ),
        )
        assertThat(repository.forBook(bookId)).isEmpty()
        fake.select("raie blanche la couleur")
        annotate(coordinator, "nouvelle")
        assertThat(repository.forBook(bookId).single().text).isEqualTo("raie blanche la couleur")
        assertThat(repository.get(legacy)).isNotNull()
    }

    @Test fun unreadableChapterStillSavesTheNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp(textAvailable = false)
        fake.select("raie blanche")
        annotate(coordinator, "Image")
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

    @Test fun tappingANoteOpensItsSheet() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        annotate(coordinator, "Image")
        val id = repository.forBook(bookId).single().id
        fake.highlightTaps.emit(id)
        runCurrent()
        coordinator.awaitIdle()
        assertThat(coordinator.state.value.actions)
            .isEqualTo(HighlightActionsState(id, "raie blanche", "Image", "Deuxième partie, chap. I · 30 %"))
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

    @Test fun saveTrimsTheNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        annotate(coordinator, "  Image du manteau  ")
        assertThat(repository.forBook(bookId).single().note).isEqualTo("Image du manteau")
        assertThat(coordinator.state.value.noteSheet).isNull()
    }

    @Test fun blankNoteSavesNothingAndKeepsTheSheet() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp()
        fake.select("raie blanche")
        annotate(coordinator, "   ")
        assertThat(db.highlightDao().all()).isEmpty()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "   ", editing = false))
    }

    @Test fun editingAnExistingNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        annotate(coordinator, "Premier portrait.")
        val id = repository.forBook(bookId).single().id
        fake.highlightTaps.emit(id)
        runCurrent()
        coordinator.editNote()
        assertThat(coordinator.state.value.actions).isNull()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "Premier portrait.", editing = true))
        coordinator.onNoteChange("Second portrait.")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.get(id)!!.note).isEqualTo("Second portrait.")
    }

    @Test fun mergingKeepsBothNotes() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche la couleur")
        annotate(coordinator, "première")
        fake.select("la couleur des prés")
        annotate(coordinator, "seconde")
        val row = repository.forBook(bookId).single()
        assertThat(row.text).isEqualTo("raie blanche la couleur des prés")
        assertThat(row.note).isEqualTo("première\n\nseconde")
    }

    @Test fun deleteThenUndoRestoresTheNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        annotate(coordinator, "Image")
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

    // --- Traduction ---------------------------------------------------------------------------------

    /** Traducteur piloté : chaque appel attend [answer], ou lève [failure]. */
    private class GatedTranslator : Translator {
        val answer = CompletableDeferred<List<String>>()
        var failure: TranslationFailure? = null
        val calls = mutableListOf<String>()

        override suspend fun lookup(word: String): List<String> {
            calls += word
            failure?.let { throw it }
            return answer.await()
        }

        override suspend fun translate(text: String): String {
            calls += text
            failure?.let { throw it }
            return answer.await().joinToString(" ")
        }
    }

    @Test fun withoutKeyTranslateDoesNothing() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp(translator = null)
        assertThat(coordinator.state.value.canTranslate).isFalse()
        fake.select("acknowledged")
        runCurrent()
        coordinator.translateSelection()
        runCurrent()
        assertThat(coordinator.state.value.translation).isNull()
    }

    @Test fun translateOpensAtOnceThenShowsTheSensesAndKeepsTheSelection() = runTest(UnconfinedTestDispatcher()) {
        val translator = GatedTranslator()
        val (coordinator, fake) = setUp(translator = translator)
        assertThat(coordinator.state.value.canTranslate).isTrue()
        fake.select("acknowledged,")
        runCurrent()
        coordinator.translateSelection()
        runCurrent()
        assertThat(coordinator.state.value.translation).isEqualTo(TranslationSheetState("acknowledged,", short = true, result = null))
        translator.answer.complete(listOf("reconnu", "admis", "avoué", "accepté"))
        runCurrent()
        assertThat(coordinator.state.value.translation?.result)
            .isEqualTo(TranslationResult.Word("reconnu", listOf("admis", "avoué", "accepté")))
        assertThat(translator.calls).containsExactly("acknowledged")
        assertThat(fake.selectionCleared).isEqualTo(0)
        assertThat(coordinator.state.value.selectionText).isNotNull()
    }

    @Test fun offlineThenRetrySendsTheSamePassageAgain() = runTest(UnconfinedTestDispatcher()) {
        val translator = GatedTranslator().apply { failure = TranslationFailure.Offline() }
        val (coordinator, fake) = setUp(translator = translator)
        fake.select("It is a truth universally acknowledged.")
        runCurrent()
        coordinator.translateSelection()
        runCurrent()
        assertThat(coordinator.state.value.translation?.result).isEqualTo(TranslationResult.Offline)
        translator.failure = null
        translator.answer.complete(listOf("C’est une vérité universellement reconnue."))
        coordinator.retryTranslation()
        runCurrent()
        assertThat(coordinator.state.value.translation?.result)
            .isEqualTo(TranslationResult.Passage("C’est une vérité universellement reconnue."))
        assertThat(translator.calls)
            .containsExactly("It is a truth universally acknowledged.", "It is a truth universally acknowledged.")
    }

    @Test fun tapOnTheTextClearsTheSelectionAndClosesTheSheet() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp(translator = GatedTranslator())
        fake.select("acknowledged")
        runCurrent()
        coordinator.translateSelection()
        runCurrent()
        assertThat(coordinator.state.value.translation).isNotNull()
        fake.clearSelection()
        runCurrent()
        assertThat(coordinator.state.value.translation).isNull()
    }

    @Test fun movingTheHandlesClosesTheStaleSheetAndBringsBackTheBar() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp(translator = GatedTranslator())
        fake.select("acknowledged")
        runCurrent()
        coordinator.translateSelection()
        runCurrent()
        fake.select("truth universally acknowledged")
        runCurrent()
        assertThat(coordinator.state.value.translation).isNull()
        assertThat(coordinator.state.value.selectionText).isEqualTo("truth universally acknowledged")
    }

    @Test fun selectionGoneDuringTheJavascriptReadOpensNothing() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp(translator = GatedTranslator())
        fake.select("acknowledged")
        runCurrent()
        // La lecture native répond encore le passage, mais la sélection est déjà effacée.
        fake.nativeSelection = fake.selection.value
        fake.selection.value = null
        runCurrent()
        coordinator.translateSelection()
        runCurrent()
        assertThat(coordinator.state.value.translation).isNull()
    }

    @Test fun closeDismissesTheSheetAndTheSelection() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp(translator = GatedTranslator())
        fake.select("acknowledged")
        runCurrent()
        coordinator.translateSelection()
        runCurrent()
        coordinator.dismissTranslation()
        runCurrent()
        assertThat(coordinator.state.value.translation).isNull()
        assertThat(coordinator.state.value.selectionText).isNull()
        assertThat(fake.selectionCleared).isEqualTo(1)
    }
}
