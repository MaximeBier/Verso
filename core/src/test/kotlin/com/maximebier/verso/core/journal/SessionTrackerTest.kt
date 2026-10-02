package com.maximebier.verso.core.journal

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.journal.SessionEvent.Backgrounded
import com.maximebier.verso.core.journal.SessionEvent.Closed
import com.maximebier.verso.core.journal.SessionEvent.Interaction
import com.maximebier.verso.core.journal.SessionEvent.Opened
import com.maximebier.verso.core.journal.SessionEvent.PanelHidden
import com.maximebier.verso.core.journal.SessionEvent.PanelShown
import com.maximebier.verso.core.journal.SessionEvent.ReadingMoved
import com.maximebier.verso.core.journal.SessionEvent.Tick
import com.maximebier.verso.core.model.BookPosition
import org.junit.Test

class SessionTrackerTest {

    private fun pos(progression: Double) = BookPosition(locatorJson = "{\"p\":$progression}", totalProgression = progression)

    private fun record(
        startedAt: Long,
        endedAt: Long,
        activeMs: Long,
        start: Double,
        end: Double = start,
        wordsRead: Long = 0,
        id: Long = 0,
    ) = SessionRecord(
        id = id,
        bookId = 7,
        startedAt = startedAt,
        endedAt = endedAt,
        activeMs = activeMs,
        start = pos(start),
        end = pos(end),
        wordsRead = wordsRead,
    )

    private fun tracker() = SessionTracker(bookId = 7)

    @Test
    fun sessionIsEmptyUntilAWordIsRead() {
        val opened = record(startedAt = 0, endedAt = 60_000, activeMs = 60_000, start = 0.1)
        assertThat(opened.isEmpty).isTrue()
        assertThat(opened.copy(end = pos(0.2)).isEmpty).isTrue() // saut ou « Rester ici » : aucun mot lu
        assertThat(opened.copy(wordsRead = 12).isEmpty).isFalse()
    }

    @Test
    fun defaultThresholdsMatchSpec() {
        assertThat(SessionThresholds().inactivityEndMs).isEqualTo(300_000L)
        assertThat(SessionThresholds().activeGapMs).isEqualTo(120_000L)
    }

    @Test
    fun openingStartsSessionButEmitsNothingUntilFirstInteraction() {
        val tracker = tracker()
        assertThat(tracker.onEvent(Opened(1_000, pos(0.1)))).isEmpty()
        assertThat(tracker.current).isEqualTo(record(startedAt = 1_000, endedAt = 1_000, activeMs = 0, start = 0.1))
    }

    @Test
    fun eachInteractionReturnsTheUpdatedCurrentSessionForUpsert() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        val expected = record(startedAt = 0, endedAt = 30_000, activeMs = 30_000, start = 0.1)
        assertThat(tracker.onEvent(Interaction(30_000))).containsExactly(expected)
        assertThat(tracker.current).isEqualTo(expected)
    }

    @Test
    fun activeTimeExcludesGapsLongerThanTwoMinutes() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(60_000))
        tracker.onEvent(Interaction(180_000)) // intervalle de 2 min pile : compté
        val last = tracker.onEvent(Interaction(330_000)) // 2 min 30 : exclu (mais moins de 5 min)
        assertThat(last).containsExactly(record(startedAt = 0, endedAt = 330_000, activeMs = 180_000, start = 0.1))
    }

    @Test
    fun timeSpentInAPanelIsNotActive() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(30_000))
        tracker.onEvent(PanelShown(40_000))              // 10 s de lecture avant le panneau : comptées
        assertThat(tracker.onEvent(Interaction(60_000))).isEmpty()   // toucher dans le panneau : ignoré
        tracker.onEvent(PanelHidden(100_000))
        val last = tracker.onEvent(Interaction(110_000))  // le compte repart à la fermeture : 10 s
        assertThat(last.single().activeMs).isEqualTo(50_000)
        assertThat(last.single().endedAt).isEqualTo(110_000)
    }

    @Test
    fun panelEventsAreIdempotentAndIgnoredWithoutSession() {
        val tracker = tracker()
        assertThat(tracker.onEvent(PanelShown(0))).isEmpty()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(PanelHidden(5_000))             // aucun panneau ouvert : sans effet
        tracker.onEvent(Interaction(20_000))
        assertThat(tracker.current!!.activeMs).isEqualTo(20_000)
    }

    @Test
    fun wordsReadOnlyCountReadingMoves() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(1_000))
        assertThat(tracker.onEvent(ReadingMoved(2_000, pos(0.12), wordsDelta = 300)))
            .containsExactly(record(0, 2_000, 2_000, start = 0.1, end = 0.12, wordsRead = 300))
        assertThat(tracker.onEvent(Interaction(3_000)))
            .containsExactly(record(0, 3_000, 3_000, start = 0.1, end = 0.12, wordsRead = 300))
        // Un delta négatif (retour en arrière) compte pour 0 ; la fin suit la position de lecture.
        assertThat(tracker.onEvent(ReadingMoved(4_000, pos(0.11), wordsDelta = -100)))
            .containsExactly(record(0, 4_000, 4_000, start = 0.1, end = 0.11, wordsRead = 300))
    }

    @Test
    fun sessionEndsAfterMoreThanFiveMinutesWithoutInteractionDatedAtLastInteraction() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(10_000))
        assertThat(tracker.onEvent(Tick(310_000))).isEmpty() // 5 min pile : pas encore
        assertThat(tracker.onEvent(Tick(310_001)))
            .containsExactly(record(startedAt = 0, endedAt = 10_000, activeMs = 10_000, start = 0.1))
        assertThat(tracker.current).isNull()
        assertThat(tracker.onEvent(Tick(400_000))).isEmpty()
    }

    @Test
    fun interactionAfterMoreThanFiveMinutesClosesAndStartsANewSession() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(ReadingMoved(10_000, pos(0.2), wordsDelta = 500))
        assertThat(tracker.onEvent(Interaction(310_001))).containsExactly(
            record(startedAt = 0, endedAt = 10_000, activeMs = 10_000, start = 0.1, end = 0.2, wordsRead = 500),
            record(startedAt = 310_001, endedAt = 310_001, activeMs = 0, start = 0.2),
        ).inOrder()
    }

    @Test
    fun interactionAfterInactivityEndStartsNewSessionAtReadingPosition() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(ReadingMoved(5_000, pos(0.15), wordsDelta = 100))
        assertThat(tracker.onEvent(Tick(400_000))).hasSize(1)
        assertThat(tracker.onEvent(Interaction(500_000)))
            .containsExactly(record(startedAt = 500_000, endedAt = 500_000, activeMs = 0, start = 0.15))
    }

    @Test
    fun readingMoveAfterLongPauseStartsNewSessionFromReadingPosition() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(1_000))
        assertThat(tracker.onEvent(ReadingMoved(400_000, pos(0.12), wordsDelta = 50))).containsExactly(
            record(startedAt = 0, endedAt = 1_000, activeMs = 1_000, start = 0.1),
            record(startedAt = 400_000, endedAt = 400_000, activeMs = 0, start = 0.1, end = 0.12, wordsRead = 50),
        ).inOrder()
    }

    @Test
    fun backgroundingEndsSessionAtLastInteractionAndNextInteractionStartsANewOne() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(50_000))
        assertThat(tracker.onEvent(Backgrounded(90_000)))
            .containsExactly(record(startedAt = 0, endedAt = 50_000, activeMs = 50_000, start = 0.1))
        assertThat(tracker.current).isNull()
        assertThat(tracker.onEvent(Interaction(100_000)))
            .containsExactly(record(startedAt = 100_000, endedAt = 100_000, activeMs = 0, start = 0.1))
    }

    @Test
    fun closingEndsSessionAndIgnoresLaterEvents() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(20_000))
        assertThat(tracker.onEvent(Closed(25_000)))
            .containsExactly(record(startedAt = 0, endedAt = 20_000, activeMs = 20_000, start = 0.1))
        assertThat(tracker.onEvent(Interaction(30_000))).isEmpty()
        assertThat(tracker.onEvent(ReadingMoved(31_000, pos(0.2), wordsDelta = 10))).isEmpty()
        assertThat(tracker.onEvent(Tick(900_000))).isEmpty()
        assertThat(tracker.current).isNull()
    }

    @Test
    fun sessionWithoutInteractionIsDiscarded() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        assertThat(tracker.onEvent(Closed(1_000))).isEmpty()
        tracker.onEvent(Opened(2_000, pos(0.1)))
        assertThat(tracker.onEvent(Backgrounded(3_000))).isEmpty()
        assertThat(tracker.current).isNull()
    }

    @Test
    fun reopeningClosesThePreviousSession() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        tracker.onEvent(Interaction(1_000))
        assertThat(tracker.onEvent(Opened(2_000, pos(0.3))))
            .containsExactly(record(startedAt = 0, endedAt = 1_000, activeMs = 1_000, start = 0.1))
        assertThat(tracker.current).isEqualTo(record(startedAt = 2_000, endedAt = 2_000, activeMs = 0, start = 0.3))
    }

    @Test
    fun assignedIdIsKeptUntilTheSessionEnds() {
        val tracker = tracker()
        tracker.onEvent(Opened(0, pos(0.1)))
        assertThat(tracker.onEvent(Interaction(1_000)).single().id).isEqualTo(0L)
        tracker.assignId(42)
        assertThat(tracker.onEvent(Interaction(2_000)).single().id).isEqualTo(42L)
        val records = tracker.onEvent(Interaction(400_000))
        assertThat(records.map { it.id }).containsExactly(42L, 0L).inOrder()
    }

    @Test
    fun eventsBeforeOpeningAreIgnored() {
        val tracker = tracker()
        assertThat(tracker.onEvent(Interaction(1_000))).isEmpty()
        assertThat(tracker.onEvent(ReadingMoved(2_000, pos(0.2), wordsDelta = 10))).isEmpty()
        assertThat(tracker.onEvent(Backgrounded(3_000))).isEmpty()
        assertThat(tracker.current).isNull()
    }
}
