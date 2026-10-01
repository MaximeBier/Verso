package com.maximebier.verso.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDaoTest {
    private lateinit var db: VersoDatabase
    private lateinit var dao: BackupDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.backupDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun highlight(id: Long, bookId: Long) = HighlightEntity(
        id = id,
        bookId = bookId,
        locatorJson = """{"href":"c1.xhtml"}""",
        text = "Il faut cultiver notre jardin",
        note = "Fin",
        progression = 0.99,
        chapterPath = "XXX",
        createdAt = 1L,
        updatedAt = 2L,
    )

    @Test
    fun snapshotReturnsTheWholeLibraryInIdOrder() = runTest {
        val second = db.bookDao().insert(testBook("b"))
        val first = db.bookDao().insert(testBook("a"))
        db.sessionDao().upsert(testSession(first, startedAt = 10L))
        db.highlightDao().insert(highlight(0, second))

        val snapshot = dao.snapshot()

        assertThat(snapshot.books.map { it.id }).containsExactly(second, first).inOrder()
        assertThat(snapshot.sessions.single().bookId).isEqualTo(first)
        assertThat(snapshot.highlights.single().bookId).isEqualTo(second)
        assertThat(dao.bookCount()).isEqualTo(2)
    }

    @Test
    fun replaceAllKeepsTheOriginalIds() = runTest {
        db.bookDao().insert(testBook("ancien"))
        val library = LibrarySnapshot(
            books = listOf(testBook("x").copy(id = 7), testBook("y").copy(id = 9)),
            sessions = listOf(testSession(bookId = 9, startedAt = 5L, id = 42)),
            highlights = listOf(highlight(id = 13, bookId = 7)),
        )

        dao.replaceAll(library)

        assertThat(dao.snapshot()).isEqualTo(library)
    }

    @Test
    fun failedReplaceKeepsThePreviousLibrary() = runTest {
        val id = db.bookDao().insert(testBook("garde"))
        db.sessionDao().upsert(testSession(id, startedAt = 1L))
        val before = dao.snapshot()
        val broken = LibrarySnapshot(
            books = listOf(testBook("double").copy(id = 1), testBook("double").copy(id = 2)),
            sessions = emptyList(),
            highlights = emptyList(),
        )

        val failure = runCatching { dao.replaceAll(broken) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLiteConstraintException::class.java)

        assertThat(dao.snapshot()).isEqualTo(before)
    }
}
