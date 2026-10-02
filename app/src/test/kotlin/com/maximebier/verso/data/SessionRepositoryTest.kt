package com.maximebier.verso.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.db.VersoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private lateinit var db: VersoDatabase
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessions = SessionRepository(db.sessionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun statsCoverOnlyTheSessionsOfTheBook() = runTest {
        val bookId = db.bookDao().insert(testBook(sha256 = "stats"))
        val otherId = db.bookDao().insert(testBook(sha256 = "autre"))
        sessions.upsert(testSession(bookId, startedAt = 0).copy(activeMs = 10 * 60_000, wordsRead = 2_000))
        sessions.upsert(testSession(bookId, startedAt = 1_000_000).copy(activeMs = 10 * 60_000, wordsRead = 2_800))
        sessions.upsert(testSession(otherId, startedAt = 0).copy(activeMs = 60 * 60_000, wordsRead = 100))

        val stats = sessions.observeStats(bookId).first()
        assertThat(stats.sessionCount).isEqualTo(2)
        assertThat(stats.totalActiveMs).isEqualTo(20 * 60_000L)
        assertThat(stats.wordsPerMinute).isEqualTo(200) // médiane pondérée de 200 et 280, à poids égal : la plus basse
    }

    @Test
    fun bookWithoutSessionHasEmptyStats() = runTest {
        val bookId = db.bookDao().insert(testBook(sha256 = "vide"))
        assertThat(sessions.observeStats(bookId).first().sessionCount).isEqualTo(0)
    }

    @Test
    fun allStatsAreGroupedByBook() = runTest {
        val a = db.bookDao().insert(testBook("a"))
        val b = db.bookDao().insert(testBook("b"))
        sessions.upsert(testSession(a, startedAt = 1))
        sessions.upsert(testSession(a, startedAt = 2))
        sessions.upsert(testSession(b, startedAt = 3))
        val stats = sessions.observeAllStats().first()
        assertThat(stats.getValue(a).sessionCount).isEqualTo(2)
        assertThat(stats.getValue(b).sessionCount).isEqualTo(1)
        assertThat(stats.getValue(a).wordsPerMinute).isEqualTo(218)    // 2 × 200 mots en 2 × 55 s
        assertThat(stats.getValue(b).wordsPerMinute).isNull()          // 55 s : moins d’une minute, pas de vitesse
    }

    @Test
    fun shortHistoryTakesTheSpeedOfAllBooks() = runTest {
        val fresh = db.bookDao().insert(testBook("nouveau"))
        val read = db.bookDao().insert(testBook("lu"))
        sessions.upsert(testSession(fresh, startedAt = 0).copy(activeMs = 5 * 60_000, wordsRead = 2_000))   // 400 mots/min, 5 min
        sessions.upsert(testSession(read, startedAt = 1).copy(activeMs = 40 * 60_000, wordsRead = 12_000))  // 300 mots/min, 40 min

        val stats = sessions.observeStats(fresh).first()
        assertThat(stats.wordsPerMinute).isEqualTo(400)
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(300)
        assertThat(sessions.observeAllStats().first().getValue(fresh).effectiveWordsPerMinute).isEqualTo(300)
        assertThat(sessions.observeStats(read).first().effectiveWordsPerMinute).isEqualTo(300)
    }
}
