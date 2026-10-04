package com.maximebier.verso.ui.notes

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.ui.reader.NoteSheetState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NotesListModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).allowMainThreadQueries().build()
    private val highlights = HighlightRepository(db.highlightDao()) { 5L }
    private val books = BookRepository(db.bookDao(), File(context.filesDir, "b"), File(context.filesDir, "c"))
    private val written = mutableMapOf<Uri, String>()
    private var failWrite = false

    private val texts = NotesTexts(
        location = { location, percent -> listOfNotNull(location, "$percent %").joinToString(" · ") },
        shortChapter = { path -> path.takeIf { it.isNotEmpty() }?.joinToString(", ") },
        longChapter = { path -> path.takeIf { it.isNotEmpty() }?.joinToString(", ") },
        exportMeta = { author, count, date -> listOf(author, "$count notes", date).filter(String::isNotEmpty).joinToString(" · ") },
        exportFileName = { title -> "$title – notes.md" },
        exportDate = { "1 octobre 2026" },
    )

    @After fun tearDown() = db.close()

    private fun row(bookId: Long, progression: Double, text: String, note: String?, path: String) = HighlightEntity(
        bookId = bookId, locatorJson = """{"href":"ch.xhtml","type":"application/xhtml+xml"}""", text = text, note = note,
        progression = progression, chapterPath = path, createdAt = 1, updatedAt = 1,
    )

    private suspend fun TestScope.model(): Pair<NotesListModel, Long> {
        val bookId = db.bookDao().insert(testBook(sha256 = "a").copy(title = "Madame Bovary", author = "Gustave Flaubert"))
        db.highlightDao().insert(row(bookId, 0.30, "la campagne", "Image du manteau", "Deuxième partie\nI"))
        db.highlightDao().insert(row(bookId, 0.01, "Il avait les cheveux", "Premier portrait de Charles.", "Première partie\nI"))
        val model = NotesListModel(
            bookId = bookId, highlights = highlights, books = books, scope = backgroundScope, texts = texts,
            writeText = { uri, text -> if (failWrite) error("disque plein") else written[uri] = text },
            clock = { 0L },
        )
        // Les flux Room émettent depuis leur propre exécuteur : attendre la première liste complète.
        model.state.first { it.items.size == 2 }
        return model to bookId
    }

    @Test fun itemsAreInBookOrderWithLocation() = runTest(UnconfinedTestDispatcher()) {
        val (model) = model()
        val state = model.state.value
        assertThat(state.bookTitle).isEqualTo("Madame Bovary")
        assertThat(state.exportFileName).isEqualTo("Madame Bovary – notes.md")
        assertThat(state.items.map { it.text }).containsExactly("Il avait les cheveux", "la campagne").inOrder()
        assertThat(state.items.first().location).isEqualTo("Première partie, I · 1 %")
    }

    @Test fun exportWritesTheMarkdown() = runTest(UnconfinedTestDispatcher()) {
        val (model) = model()
        val uri = Uri.parse("content://test/notes.md")
        model.events.test {
            model.export(uri)
            assertThat(awaitItem()).isEqualTo(NotesEvent.Exported)
        }
        assertThat(written[uri]).startsWith("# Madame Bovary\n\nGustave Flaubert · 2 notes · 1 octobre 2026\n\n## Première partie, I\n")
    }

    @Test fun exportFailureIsReported() = runTest(UnconfinedTestDispatcher()) {
        val (model) = model()
        failWrite = true
        model.events.test {
            model.export(Uri.parse("content://test/x.md"))
            assertThat(awaitItem()).isEqualTo(NotesEvent.ExportFailed)
        }
    }

    @Test fun editDeleteAndUndo() = runTest(UnconfinedTestDispatcher()) {
        val (model, bookId) = model()
        val first = model.state.value.items.first()
        model.editNote(first.id)
        assertThat(model.state.value.noteSheet).isEqualTo(NoteSheetState("Il avait les cheveux", "Premier portrait de Charles.", editing = true))
        model.onNoteChange("Portrait.")
        model.saveNote()
        model.awaitIdle()
        assertThat(highlights.get(first.id)!!.note).isEqualTo("Portrait.")
        model.events.test {
            model.delete(first.id)
            val deleted = awaitItem() as NotesEvent.Deleted
            assertThat(highlights.forBook(bookId)).hasSize(1)
            model.undoDelete(deleted.row)
            model.awaitIdle()
            assertThat(highlights.forBook(bookId)).hasSize(2)
        }
    }
}

