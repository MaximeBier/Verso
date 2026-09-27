package com.maximebier.verso.data.db

import android.database.sqlite.SQLiteConstraintException
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
class BookDaoTest {
    private lateinit var db: VersoDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bookDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertThenFindBySha256() = runTest {
        val id = dao.insert(testBook("aaa", title = "Germinal"))
        val found = dao.bySha256("aaa")
        assertThat(found?.id).isEqualTo(id)
        assertThat(found?.title).isEqualTo("Germinal")
        assertThat(found?.progression).isEqualTo(0.0)
        assertThat(found?.readingLocatorJson).isNull()
    }

    @Test
    fun duplicateHashIsRejected() = runTest {
        dao.insert(testBook("same"))
        val second = runCatching { dao.insert(testBook("same", title = "Autre titre")) }
        assertThat(second.exceptionOrNull()).isInstanceOf(SQLiteConstraintException::class.java)
        assertThat(dao.observeAll().first()).hasSize(1)
    }

    @Test
    fun recentOrderPutsLastOpenedFirstAndNeverOpenedLast() = runTest {
        dao.insert(testBook("never-old", importedAt = 1_000))
        dao.insert(testBook("opened-early", importedAt = 2_000, lastOpenedAt = 10_000))
        dao.insert(testBook("never-new", importedAt = 3_000))
        dao.insert(testBook("opened-late", importedAt = 500, lastOpenedAt = 20_000))
        val order = dao.observeRecent().first().map { it.sha256 }
        assertThat(order).containsExactly("opened-late", "opened-early", "never-new", "never-old").inOrder()
    }

    @Test
    fun lastOpenedIgnoresNeverOpenedBooks() = runTest {
        dao.insert(testBook("never", importedAt = 9_000))
        assertThat(dao.lastOpened()).isNull()
        val id = dao.insert(testBook("opened", lastOpenedAt = 5_000))
        assertThat(dao.lastOpened()?.id).isEqualTo(id)
        assertThat(dao.observeLastOpened().first()?.id).isEqualTo(id)
    }

    @Test
    fun updatesAreApplied() = runTest {
        val id = dao.insert(testBook("x"))
        dao.updateTitle(id, "Madame Bovary")
        dao.updateAuthor(id, "Gustave Flaubert")
        dao.updateReadingPosition(id, """{"href":"ch3.xhtml"}""", 0.31)
        dao.markOpened(id, now = 42L)
        dao.replaceFile(id, "/books/y.epub", "/covers/y.png", 2_000L, "bovary.epub", 60_000L)
        val book = dao.byId(id)!!
        assertThat(book.title).isEqualTo("Madame Bovary")
        assertThat(book.author).isEqualTo("Gustave Flaubert")
        assertThat(book.readingLocatorJson).isEqualTo("""{"href":"ch3.xhtml"}""")
        assertThat(book.progression).isEqualTo(0.31)
        assertThat(book.lastOpenedAt).isEqualTo(42L)
        assertThat(book.filePath).isEqualTo("/books/y.epub")
        assertThat(book.coverPath).isEqualTo("/covers/y.png")
        assertThat(book.sizeBytes).isEqualTo(2_000L)
        assertThat(book.originalFileName).isEqualTo("bovary.epub")
        assertThat(book.totalWords).isEqualTo(60_000L)
    }

    @Test
    fun deleteByIdRemovesTheRow() = runTest {
        val id = dao.insert(testBook("gone"))
        assertThat(dao.deleteById(id)).isEqualTo(1)
        assertThat(dao.byId(id)).isNull()
        assertThat(dao.observeById(id).first()).isNull()
    }

    @Test
    fun scrollModeAndStateOverrideDefaultToNullAndAreWritable() = runTest {
        val id = dao.insert(testBook("ddd", title = "Nana"))
        assertThat(dao.byId(id)!!.scrollMode).isNull()
        assertThat(dao.byId(id)!!.stateOverride).isNull()
        dao.setScrollMode(id, "PAGES")
        dao.setStateOverride(id, "TO_READ")
        assertThat(dao.byId(id)!!.scrollMode).isEqualTo("PAGES")
        assertThat(dao.byId(id)!!.stateOverride).isEqualTo("TO_READ")
    }
}
