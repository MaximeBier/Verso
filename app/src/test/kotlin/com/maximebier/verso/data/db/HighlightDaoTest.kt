package com.maximebier.verso.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlightDaoTest {
    private lateinit var db: VersoDatabase
    private lateinit var dao: HighlightDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.highlightDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun highlight(bookId: Long, progression: Double, note: String? = null) = HighlightEntity(
        bookId = bookId,
        locatorJson = """{"href":"ch.xhtml"}""",
        text = "passage $progression",
        note = note,
        progression = progression,
        chapterPath = "Deuxième partie\nI",
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun listIsInBookOrder() = runTest {
        val bookId = db.bookDao().insert(testBook("a"))
        dao.insert(highlight(bookId, 0.31, note = "c"))
        dao.insert(highlight(bookId, 0.01, note = "a"))
        dao.insert(highlight(bookId, 0.30, note = "b"))
        // Surlignage sans note d’avant le 2026-10-04 : gardé en base, jamais listé ni compté.
        dao.insert(highlight(bookId, 0.20))
        assertThat(dao.observeForBook(bookId).first().map { it.progression }).containsExactly(0.01, 0.30, 0.31).inOrder()
        assertThat(dao.observeCount(bookId).first()).isEqualTo(3)
        assertThat(dao.forBook(bookId)).hasSize(3)
        assertThat(dao.all()).hasSize(4)
    }

    @Test
    fun deletingTheBookDeletesItsHighlights() = runTest {
        val bookId = db.bookDao().insert(testBook("a"))
        dao.insert(highlight(bookId, 0.5))
        db.bookDao().deleteById(bookId)
        assertThat(dao.all()).isEmpty()
    }

    @Test
    fun replaceMergedIsAtomic() = runTest {
        val bookId = db.bookDao().insert(testBook("a"))
        val a = dao.insert(highlight(bookId, 0.1, "a"))
        val b = dao.insert(highlight(bookId, 0.2, "b"))
        val id = dao.replaceMerged(highlight(bookId, 0.1, "a\n\nb"), listOf(a, b))
        assertThat(dao.forBook(bookId).map { it.id }).containsExactly(id)
        assertThat(dao.byId(id)!!.note).isEqualTo("a\n\nb")
    }
}
