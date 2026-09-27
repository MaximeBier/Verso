package com.maximebier.verso.ui.reader

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.data.db.SessionEntity
import java.io.IOException
import kotlin.math.roundToLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {

    /** Remplace SessionRepository : même contrat d'upsert (id attribué à l'insertion, conservé ensuite). */
    private class FakeSessionStore {
        val rows = linkedMapOf<Long, SessionEntity>()
        private var nextId = 1L
        suspend fun upsert(session: SessionEntity): Long {
            val id = if (session.id == 0L) nextId++ else session.id
            rows[id] = session.copy(id = id)
            return id
        }
    }

    private val store = FakeSessionStore()

    private fun position(p: Double) =
        BookPosition("""{"href":"c.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":$p}}""", p)

    private fun moved(from: Double, to: Double) = TrackerEffect.ReadingMoved(position(from), position(to))

    private fun saved(p: Double) = TrackerEffect.SaveReading(position(p))

    /** Ce qu'émet la machine à états (5.1) pour un mouvement de lecture : SaveReading puis ReadingMoved. */
    private fun SessionCoordinator.readingMove(from: Double, to: Double) {
        onTrackerEffect(saved(to))
        onTrackerEffect(moved(from, to))
    }

    private fun TestScope.coordinator(clock: () -> Long) = SessionCoordinator(
        bookId = BOOK_ID,
        totalWords = TOTAL_WORDS,
        upsert = store::upsert,
        clock = clock,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun sessionWithoutReadingIsNeverWritten() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        now = 30_000; c.onInteraction()
        now = 60_000; c.onInteraction()
        c.close(); advanceUntilIdle()

        assertThat(store.rows).isEmpty()
    }

    @Test
    fun sessionWithOnlyJumpsIsNeverWritten() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        // Saut puis « Rester ici » : la lecture change sans mouvement de lecture, aucun mot lu.
        now = 30_000; c.onTrackerEffect(saved(0.50))
        c.close(); advanceUntilIdle()

        assertThat(store.rows).isEmpty()
    }

    @Test
    fun firstUpsertAssignsIdAndLaterUpsertsReuseIt() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        now = 30_000; c.onInteraction()
        now = 60_000; c.readingMove(0.10, 0.11)
        advanceUntilIdle()

        assertThat(store.rows.keys).containsExactly(1L)
        assertThat(c.current.value?.id).isEqualTo(1L)
        c.close(); advanceUntilIdle()
        assertThat(store.rows.keys).containsExactly(1L)
    }

    @Test
    fun twoSessionsArePersistedSeparately() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        now = 60_000; c.readingMove(0.10, 0.11)
        now = 120_000; c.onBackgrounded()               // onStop : fin de la première session
        advanceUntilIdle()
        assertThat(c.current.value).isNull()

        now = 600_000; c.onStarted()                    // retour au premier plan : nouvelle session
        now = 660_000; c.readingMove(0.11, 0.12)
        now = 700_000; c.close()                        // sortie du lecteur
        advanceUntilIdle()

        val rows = store.rows.values.sortedBy { it.startedAt }
        assertThat(rows).hasSize(2)
        assertThat(rows[0].id).isNotEqualTo(rows[1].id)
        assertThat(rows[0].startedAt).isEqualTo(0L)
        assertThat(rows[1].startedAt).isEqualTo(600_000L)
        assertThat(rows.map { it.wordsRead }).containsExactly(1_000L, 1_000L).inOrder()
        assertThat(rows[1].startProgression).isWithin(1e-9).of(0.11)
        assertThat(rows[1].endProgression).isWithin(1e-9).of(0.12)
    }

    @Test
    fun interactionAfterFiveMinutesOfSilenceStartsANewSession() = runTest {
        val c = coordinator { testScheduler.currentTime }
        c.start()                                       // Tick chaque seconde

        c.onOpened(position(0.20))
        advanceTimeBy(60_000); c.readingMove(0.20, 0.21)
        advanceTimeBy(6 * 60_000L)                      // plus de 5 min sans interaction : la session se ferme
        runCurrent()
        assertThat(c.current.value).isNull()

        c.readingMove(0.21, 0.22)                     // reprise : nouvelle session
        runCurrent()
        assertThat(c.current.value).isNotNull()

        c.close(); advanceUntilIdle()
        assertThat(store.rows).hasSize(2)
    }

    @Test
    fun noTickWhileBackgroundedAndTickingAgainAfterStart() = runTest {
        // Revue finale M5 : aucun battement d'une seconde en arrière-plan. Chaque Tick lit l'horloge.
        var clockReads = 0
        val c = coordinator { clockReads++; testScheduler.currentTime }
        c.start()
        try {
            c.onOpened(position(0.20))
            advanceTimeBy(3_500); runCurrent()
            assertThat(clockReads).isAtLeast(4)

            c.onBackgrounded(); runCurrent()
            val stopped = clockReads
            advanceTimeBy(60_000); runCurrent()
            assertThat(clockReads).isEqualTo(stopped)

            c.onStarted()
            advanceTimeBy(3_500); runCurrent()
            assertThat(clockReads).isAtLeast(stopped + 1 + 3)
        } finally {
            c.close() // sinon le battement tourne sans fin en temps virtuel si une assertion échoue
            advanceUntilIdle()
        }
    }

    @Test
    fun wordsCountOnlyForwardReadingMoves() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        now = 10_000; c.onInteraction()                         // saut (sommaire, carte) : interaction, aucun mot
        now = 20_000; c.readingMove(0.10, 0.12)                // lecture : 0,02 × 100 000 = 2 000 mots
        now = 30_000; c.readingMove(0.12, 0.11)                // relecture vers l'arrière : 0 mot
        now = 40_000; c.close()
        advanceUntilIdle()

        assertThat(store.rows.values.single().wordsRead).isEqualTo(2_000L)
    }

    @Test
    fun saveReadingAloneMovesTheEndWithoutCountingWordsAndScrollToIsIgnored() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        now = 5_000; c.readingMove(0.10, 0.12)                                // un peu de lecture : session gardée
        now = 10_000; c.onTrackerEffect(saved(0.30))                          // « Rester ici » : la lecture passe à 30 %
        now = 20_000; c.onTrackerEffect(TrackerEffect.ScrollTo(position(0.10))) // « Revenir » : déplacement de l'affiché seul
        now = 30_000; c.close()
        advanceUntilIdle()

        val row = store.rows.values.single()
        assertThat(row.endProgression).isWithin(1e-9).of(0.30)
        assertThat(row.wordsRead).isEqualTo((0.02 * TOTAL_WORDS).roundToLong())   // « Rester ici » n'ajoute aucun mot
        assertThat(row.endedAt).isEqualTo(10_000L)                            // ScrollTo n'est pas une interaction
        assertThat(row.activeMs).isEqualTo(10_000L)
    }

    @Test
    fun effectsWhileBackgroundedOpenNoSessionButMoveTheNextStart() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        now = 10_000; c.readingMove(0.10, 0.12)
        now = 20_000; c.onBackgrounded()
        now = 21_000; c.onTrackerEffect(saved(0.15))    // repos constaté après onStop : aucune session fantôme
        advanceUntilIdle()
        assertThat(c.current.value).isNull()
        assertThat(store.rows).hasSize(1)

        now = 600_000; c.onStarted()
        now = 610_000; c.readingMove(0.15, 0.16)
        now = 620_000; c.close()
        advanceUntilIdle()

        val rows = store.rows.values.sortedBy { it.startedAt }
        assertThat(rows).hasSize(2)
        assertThat(rows[1].startProgression).isWithin(1e-9).of(0.15)
    }

    @Test
    fun openingThenClosingWithoutInteractionWritesNothing() = runTest {
        var now = 0L
        val c = coordinator { now }

        c.onOpened(position(0.10))
        now = 5_000; c.close()
        advanceUntilIdle()

        assertThat(store.rows).isEmpty()
        assertThat(c.current.value).isNull()
    }

    @Test
    fun failedWriteIsReportedAndTheNextWriteRetries() = runTest {
        var now = 0L
        var failing = true
        val failures = mutableListOf<Exception>()
        val c = SessionCoordinator(
            bookId = BOOK_ID,
            totalWords = TOTAL_WORDS,
            upsert = { session -> if (failing) throw IOException("disque plein") else store.upsert(session) },
            clock = { now },
            dispatcher = StandardTestDispatcher(testScheduler),
            onWriteFailed = { failures += it },
        )

        c.onOpened(position(0.10))
        now = 10_000; c.readingMove(0.10, 0.11)         // écriture ratée : signalée, la lecture continue
        advanceUntilIdle()
        failing = false
        now = 20_000; c.readingMove(0.11, 0.12)         // l'écriture suivante crée la ligne
        now = 30_000; c.close()
        advanceUntilIdle()

        assertThat(failures.map { it.message }).containsExactly("disque plein")
        assertThat(store.rows.keys).containsExactly(1L)
        assertThat(store.rows.getValue(1L).endedAt).isEqualTo(20_000L)
    }

    private companion object {
        const val BOOK_ID = 7L
        const val TOTAL_WORDS = 100_000L
    }
}
