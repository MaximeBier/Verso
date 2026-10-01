package com.maximebier.verso.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.VersoDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlightRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 100L
    private val repository = HighlightRepository(db.highlightDao()) { now }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(bookId: Long = 1, path: String = "") = HighlightEntity(
        bookId = bookId, locatorJson = "{}", text = "passage", note = null, progression = 0.3,
        chapterPath = path, createdAt = now, updatedAt = now,
    )

    private suspend fun newHighlight(): HighlightEntity {
        val bookId = db.bookDao().insert(testBook("a"))
        return repository.get(repository.saveMerged(row(bookId), emptyList()))!!
    }

    @Test
    fun setNoteTrimsAndBlankBecomesNull() = runTest {
        val row = newHighlight()
        now = 200L
        repository.setNote(row.id, "  Premier portrait.  ")
        assertThat(repository.get(row.id)!!.note).isEqualTo("Premier portrait.")
        assertThat(repository.get(row.id)!!.updatedAt).isEqualTo(200L)
        repository.setNote(row.id, "   ")
        assertThat(repository.get(row.id)!!.note).isNull()
    }

    @Test
    fun deleteThenRestoreKeepsTheSameRow() = runTest {
        val row = newHighlight()
        val deleted = repository.delete(row.id)!!
        assertThat(repository.get(row.id)).isNull()
        repository.restore(deleted)
        assertThat(repository.get(row.id)).isEqualTo(row)
        assertThat(repository.delete(999L)).isNull()
    }

    @Test
    fun chapterPathColumnRoundTrips() {
        assertThat(listOf("Deuxième partie", "I").toChapterPathColumn()).isEqualTo("Deuxième partie\nI")
        assertThat(row(path = listOf("Titre\nsur deux lignes").toChapterPathColumn()).chapterPathList())
            .containsExactly("Titre sur deux lignes")
        assertThat(row(path = "").chapterPathList()).isEmpty()
    }
}
