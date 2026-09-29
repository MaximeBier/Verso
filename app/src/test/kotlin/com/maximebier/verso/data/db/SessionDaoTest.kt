package com.maximebier.verso.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionDaoTest {
    private lateinit var db: VersoDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingABookCascadesToItsSessions() = runTest {
        val kept = db.bookDao().insert(testBook("kept"))
        val deleted = db.bookDao().insert(testBook("deleted"))
        db.sessionDao().upsert(testSession(kept, startedAt = 1_000))
        db.sessionDao().upsert(testSession(deleted, startedAt = 2_000))
        db.sessionDao().upsert(testSession(deleted, startedAt = 3_000))

        db.bookDao().deleteById(deleted)

        assertThat(db.sessionDao().countForBook(deleted)).isEqualTo(0)
        assertThat(db.sessionDao().countForBook(kept)).isEqualTo(1)
    }

    @Test
    fun deleteDiscardedRemovesSessionsWithoutReadingOrShorterThanTheMinimum() = runTest {
        val bookId = db.bookDao().insert(testBook("b"))
        val other = db.bookDao().insert(testBook("o"))
        val empty = testSession(bookId, startedAt = 1_000).copy(wordsRead = 0)
        db.sessionDao().upsert(empty)
        db.sessionDao().upsert(testSession(bookId, startedAt = 2_000))
        db.sessionDao().upsert(testSession(bookId, startedAt = 3_000).copy(activeMs = 29_999))
        db.sessionDao().upsert(testSession(bookId, startedAt = 4_000).copy(activeMs = 30_000))
        db.sessionDao().upsert(empty.copy(bookId = other))

        db.sessionDao().deleteDiscarded(minActiveMs = 30_000)

        assertThat(db.sessionDao().observeForBook(bookId).first().map { it.startedAt }).containsExactly(4_000L, 2_000L)
        assertThat(db.sessionDao().countForBook(other)).isEqualTo(0)
    }

    @Test
    fun upsertUpdatesAnExistingSession() = runTest {
        val bookId = db.bookDao().insert(testBook("b"))
        val id = db.sessionDao().upsert(testSession(bookId, startedAt = 1_000))
        db.sessionDao().upsert(testSession(bookId, startedAt = 1_000, id = id).copy(endedAt = 99_000, wordsRead = 5_000))
        val sessions = db.sessionDao().observeForBook(bookId).first()
        assertThat(sessions).hasSize(1)
        assertThat(sessions.single().endedAt).isEqualTo(99_000)
        assertThat(sessions.single().wordsRead).isEqualTo(5_000)
    }

    @Test
    fun sessionsAreNewestFirstAndClearAllEmptiesTheJournal() = runTest {
        val bookId = db.bookDao().insert(testBook("b"))
        db.sessionDao().upsert(testSession(bookId, startedAt = 1_000))
        db.sessionDao().upsert(testSession(bookId, startedAt = 3_000))
        db.sessionDao().upsert(testSession(bookId, startedAt = 2_000))
        assertThat(db.sessionDao().observeForBook(bookId).first().map { it.startedAt })
            .containsExactly(3_000L, 2_000L, 1_000L).inOrder()

        db.sessionDao().clearAll()

        assertThat(db.sessionDao().observeForBook(bookId).first()).isEmpty()
        assertThat(db.bookDao().byId(bookId)).isNotNull()
    }
}
