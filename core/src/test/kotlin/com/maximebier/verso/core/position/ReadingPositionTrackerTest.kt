package com.maximebier.verso.core.position

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ReaderEvent.Displayed
import com.maximebier.verso.core.position.ReaderEvent.GestureEnded
import com.maximebier.verso.core.position.ReaderEvent.GoBack
import com.maximebier.verso.core.position.ReaderEvent.Jumped
import com.maximebier.verso.core.position.ReaderEvent.StayHere
import com.maximebier.verso.core.position.ReaderEvent.Tick
import com.maximebier.verso.core.position.TrackerEffect.ReadingMoved
import com.maximebier.verso.core.position.TrackerEffect.SaveReading
import com.maximebier.verso.core.position.TrackerEffect.ScrollTo
import kotlin.math.abs
import org.junit.Test

class ReadingPositionTrackerTest {

    /** Distance factice : 1 écran = 0,01 de progression, donc pos(12.5) est à 2,5 écrans de pos(10.0). */
    private val distance = ScreenDistance { a, b -> abs(a.totalProgression - b.totalProgression) * 100.0 }

    /** Position située à `screens` écrans du début du livre. */
    private fun pos(screens: Double) =
        BookPosition(locatorJson = "{\"screens\":$screens}", totalProgression = screens / 100.0)

    /** Lecture à 10 écrans, déjà affichée : le premier Displayed (arrivée) est reçu à t = 0. */
    private fun tracker(thresholds: ReadingThresholds = ReadingThresholds()): ReadingPositionTracker =
        ReadingPositionTracker(pos(10.0), distance, thresholds).also { it.onEvent(Displayed(0, pos(10.0))) }

    private fun ReadingPositionTracker.feed(vararg events: ReaderEvent): List<TrackerEffect> =
        events.flatMap { onEvent(it) }

    /** Fling depuis la lecture (10) jusqu'à 15 écrans, immobile depuis t = 1 300 : AWAY, arrivée datée de 1 300. */
    private fun awayAt15(): ReadingPositionTracker = tracker().also {
        val effects = it.feed(
            GestureEnded(1_000, pos(10.3), isFling = true),
            Displayed(1_100, pos(13.0)),
            Displayed(1_300, pos(15.0)),
            Tick(1_800),
        )
        assertThat(effects).isEmpty()
        assertThat(it.state).isEqualTo(away(reading = 10.0, displayed = 15.0))
    }

    private fun following(reading: Double, displayed: Double = reading) =
        TrackerState(pos(reading), pos(displayed), TrackerMode.FOLLOWING, showReturnCard = false)

    private fun away(reading: Double, displayed: Double) =
        TrackerState(pos(reading), pos(displayed), TrackerMode.AWAY, showReturnCard = true)

    // ---------- Suivi (FOLLOWING) ----------

    @Test
    fun initialStateFollowsInitialPosition() {
        val tracker = ReadingPositionTracker(pos(10.0), distance)
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun firstDisplayedIsTheArrivalAndNeverMovesReading() {
        val tracker = ReadingPositionTracker(pos(10.0), distance)
        val effects = tracker.feed(Displayed(0, pos(10.2)), Tick(5_000), Tick(60_000))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.2))
    }

    @Test
    fun readingIsNotUpdatedWhileMoving() {
        val tracker = tracker()
        assertThat(tracker.onEvent(Displayed(1_000, pos(10.3)))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.3))
    }

    @Test
    fun readingFollowsAtGestureEndWithoutFling() {
        val tracker = tracker()
        tracker.onEvent(Displayed(1_000, pos(10.3)))
        val effects = tracker.onEvent(GestureEnded(1_100, pos(10.3), isFling = false))
        assertThat(effects).containsExactly(SaveReading(pos(10.3)), ReadingMoved(pos(10.0), pos(10.3))).inOrder()
        assertThat(tracker.state).isEqualTo(following(10.3))
    }

    @Test
    fun readingFollowsWhenDisplayedIsStableForTheRestDelay() {
        val tracker = tracker()
        tracker.onEvent(Displayed(1_000, pos(10.3)))
        assertThat(tracker.onEvent(Tick(1_499))).isEmpty()
        assertThat(tracker.onEvent(Tick(1_500)))
            .containsExactly(SaveReading(pos(10.3)), ReadingMoved(pos(10.0), pos(10.3))).inOrder()
    }

    @Test
    fun repeatedDisplayedAtSamePositionIsNotAMovement() {
        val tracker = tracker()
        val effects = tracker.feed(Displayed(2_000, pos(10.0)), Tick(5_000))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun smallScrollsInBothDirectionsAreReading() {
        val tracker = tracker()
        tracker.onEvent(Displayed(1_000, pos(10.4)))
        assertThat(tracker.onEvent(GestureEnded(1_100, pos(10.4), isFling = false)))
            .containsExactly(SaveReading(pos(10.4)), ReadingMoved(pos(10.0), pos(10.4))).inOrder()
        tracker.onEvent(Displayed(3_000, pos(10.1)))
        assertThat(tracker.onEvent(GestureEnded(3_100, pos(10.1), isFling = false)))
            .containsExactly(SaveReading(pos(10.1)), ReadingMoved(pos(10.4), pos(10.1))).inOrder()
    }

    @Test
    fun speedBelowFlingThresholdIsReading() {
        val tracker = tracker()
        tracker.onEvent(Displayed(100, pos(10.35))) // 3,5 écrans/s
        assertThat(tracker.onEvent(GestureEnded(200, pos(10.35), isFling = false)))
            .containsExactly(SaveReading(pos(10.35)), ReadingMoved(pos(10.0), pos(10.35))).inOrder()
    }

    @Test
    fun fastDisplayedMovementIsNavigationAndShortOneKeepsReadingWithoutCard() {
        val tracker = tracker()
        val effects = tracker.feed(
            Displayed(100, pos(10.5)), // 5 écrans/s : fling
            Displayed(200, pos(10.8)),
            Tick(800), // repos : fin de la navigation, à 0,8 écran de la lecture
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.8))

        // La lecture reprend ensuite normalement depuis l'endroit affiché.
        tracker.onEvent(Displayed(3_000, pos(10.9)))
        assertThat(tracker.onEvent(GestureEnded(3_100, pos(10.9), isFling = false)))
            .containsExactly(SaveReading(pos(10.9)), ReadingMoved(pos(10.0), pos(10.9))).inOrder()
    }

    @Test
    fun shortFlingGestureEndingWithinOneScreenKeepsReadingAndShowsNoCard() {
        val tracker = tracker()
        val effects = tracker.feed(
            GestureEnded(1_000, pos(10.2), isFling = true),
            Displayed(1_100, pos(10.5)),
            Displayed(1_200, pos(10.7)),
            Tick(1_700),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.7))
    }

    @Test
    fun flingFarAwayShowsReturnCardWithoutTouchingReading() {
        awayAt15() // les assertions sont dans le helper
    }

    @Test
    fun cardIsShownOnlyAtRestAfterTheNavigation() {
        val tracker = tracker()
        tracker.feed(GestureEnded(1_000, pos(10.3), isFling = true), Displayed(1_100, pos(13.0)), Displayed(1_300, pos(15.0)))
        assertThat(tracker.state.showReturnCard).isFalse()
        tracker.onEvent(Tick(1_799))
        assertThat(tracker.state.showReturnCard).isFalse()
        tracker.onEvent(Tick(1_800))
        assertThat(tracker.state.showReturnCard).isTrue()
    }

    @Test
    fun slowButLongScrollOverThreeScreensInFiveSecondsIsNavigation() {
        val tracker = tracker()
        val positions = listOf(10.35, 10.7, 11.05, 11.4, 11.75, 12.1, 12.45, 12.8, 13.15, 13.5)
        val events = positions.mapIndexed { i, p -> Displayed(400L * (i + 1), pos(p)) } +
            GestureEnded(4_100, pos(13.5), isFling = false)
        val effects = tracker.feed(*events.toTypedArray())
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 13.5))
    }

    @Test
    fun slowScrollUnderThreeScreensInFiveSecondsIsReading() {
        val tracker = tracker()
        val positions = listOf(10.35, 10.7, 11.05, 11.4, 11.75, 12.1, 12.45, 12.8)
        val events = positions.mapIndexed { i, p -> Displayed(400L * (i + 1), pos(p)) } +
            GestureEnded(3_300, pos(12.8), isFling = false)
        val effects = tracker.feed(*events.toTypedArray())
        assertThat(effects).containsExactly(SaveReading(pos(12.8)), ReadingMoved(pos(10.0), pos(12.8))).inOrder()
        assertThat(tracker.state).isEqualTo(following(12.8))
    }

    @Test
    fun lateWindowDetectionRestoresReadingValidatedBeforeTheWindow() {
        val tracker = tracker()
        tracker.onEvent(Displayed(1_000, pos(10.8)))
        assertThat(tracker.onEvent(GestureEnded(1_100, pos(10.8), isFling = false)))
            .containsExactly(SaveReading(pos(10.8)), ReadingMoved(pos(10.0), pos(10.8))).inOrder()
        tracker.onEvent(Displayed(2_000, pos(11.6)))
        assertThat(tracker.onEvent(GestureEnded(2_100, pos(11.6), isFling = false)))
            .containsExactly(SaveReading(pos(11.6)), ReadingMoved(pos(10.8), pos(11.6))).inOrder()
        tracker.onEvent(Displayed(3_000, pos(12.4)))
        assertThat(tracker.onEvent(GestureEnded(3_100, pos(12.4), isFling = false)))
            .containsExactly(SaveReading(pos(12.4)), ReadingMoved(pos(11.6), pos(12.4))).inOrder()
        assertThat(tracker.state.reading).isEqualTo(pos(12.4))

        // 3,2 écrans depuis t = 0 en 4 s : c'était une navigation depuis le début.
        assertThat(tracker.onEvent(Displayed(4_000, pos(13.2)))).containsExactly(SaveReading(pos(10.0)))
        assertThat(tracker.state.reading).isEqualTo(pos(10.0))

        assertThat(tracker.onEvent(GestureEnded(4_100, pos(13.2), isFling = false))).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 13.2))
    }

    @Test
    fun navigationWindowSlidesAndForgetsOlderMovements() {
        val tracker = tracker()
        val steps = listOf(2_000L to 10.8, 4_000L to 11.6, 6_000L to 12.4, 8_000L to 13.2, 10_000L to 14.0)
        val effects = steps.flatMap { (t, p) ->
            tracker.feed(Displayed(t, pos(p)), GestureEnded(t + 100, pos(p), isFling = false))
        }
        assertThat(effects.filterIsInstance<SaveReading>().map { it.position })
            .containsExactly(pos(10.8), pos(11.6), pos(12.4), pos(13.2), pos(14.0)).inOrder()
        assertThat(tracker.state).isEqualTo(following(14.0))
    }

    // ---------- Sauts (sommaire, journal) ----------

    @Test
    fun jumpToCurrentChapterWithinOneScreenKeepsReadingAndShowsNoCard() {
        val tracker = tracker()
        assertThat(tracker.onEvent(Jumped(1_000, pos(9.5)))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 9.5))
        val effects = tracker.feed(Displayed(1_200, pos(9.5)), Tick(3_000), Tick(10_000))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 9.5))
    }

    @Test
    fun jumpToChapterStartMoreThanOneScreenBackShowsCard() {
        val tracker = tracker()
        assertThat(tracker.onEvent(Jumped(1_000, pos(4.0)))).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 4.0))
    }

    @Test
    fun jumpToBeginningThenGoBackReturnsToReading() {
        val tracker = tracker()
        tracker.feed(Jumped(1_000, pos(0.0)), Displayed(1_100, pos(0.0)))
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 0.0))
        assertThat(tracker.onEvent(GoBack(5_000))).containsExactly(ScrollTo(pos(10.0)))
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun jumpNearReadingWhileAwayHidesCard() {
        val tracker = awayAt15()
        assertThat(tracker.onEvent(Jumped(3_000, pos(10.2)))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.2))
    }

    @Test
    fun gestureDuringPendingJumpEndsTheWait() {
        val tracker = tracker()
        tracker.onEvent(Jumped(1_000, pos(0.0)))
        assertThat(tracker.onEvent(GestureEnded(2_000, pos(0.3), isFling = false))).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 0.3))
    }

    // ---------- Carte « Revenir » (AWAY) ----------

    @Test
    fun goBackScrollsToReadingIgnoresStaleDisplayedAndArrivalDoesNotMoveReading() {
        val tracker = awayAt15()
        assertThat(tracker.onEvent(GoBack(3_000))).containsExactly(ScrollTo(pos(10.0)))
        assertThat(tracker.state).isEqualTo(following(10.0))

        // Position encore affichée avant le saut : ignorée.
        assertThat(tracker.onEvent(Displayed(3_050, pos(15.1)))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(10.0))

        // Arrivée près de la cible : ni navigation ni lecture.
        val arrival = tracker.feed(Displayed(3_100, pos(10.05)), Tick(4_000), Tick(9_000))
        assertThat(arrival).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.05))

        // La lecture reprend ensuite normalement.
        tracker.onEvent(Displayed(5_000, pos(10.3)))
        assertThat(tracker.onEvent(GestureEnded(5_100, pos(10.3), isFling = false)))
            .containsExactly(SaveReading(pos(10.3)), ReadingMoved(pos(10.0), pos(10.3))).inOrder()
    }

    @Test
    fun goBackIsIgnoredWhenFollowing() {
        val tracker = tracker()
        assertThat(tracker.onEvent(GoBack(1_000))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun stayHereMakesDisplayedTheReadingPosition() {
        val tracker = awayAt15()
        assertThat(tracker.onEvent(StayHere(3_000))).containsExactly(SaveReading(pos(15.0)))
        assertThat(tracker.state).isEqualTo(following(15.0))

        tracker.onEvent(Displayed(4_000, pos(15.3)))
        assertThat(tracker.onEvent(GestureEnded(4_100, pos(15.3), isFling = false)))
            .containsExactly(SaveReading(pos(15.3)), ReadingMoved(pos(15.0), pos(15.3))).inOrder()
    }

    @Test
    fun stayHereIsIgnoredWhenFollowing() {
        val tracker = tracker()
        assertThat(tracker.onEvent(StayHere(1_000))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun manualReturnBySmallScrollsHidesCard() {
        val tracker = tracker()
        tracker.feed(
            GestureEnded(1_000, pos(10.2), isFling = true),
            Displayed(1_100, pos(11.5)),
            Displayed(1_200, pos(12.5)),
            Tick(1_700),
        )
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 12.5))

        assertThat(tracker.onEvent(Displayed(3_000, pos(12.1)))).isEmpty()
        assertThat(tracker.onEvent(Displayed(4_000, pos(11.7)))).isEmpty()
        assertThat(tracker.onEvent(Displayed(5_000, pos(11.3)))).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 11.3))

        // À moins d'un écran de la lecture : carte masquée, la lecture suit de nouveau.
        assertThat(tracker.onEvent(Displayed(6_000, pos(10.9)))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.9))
        assertThat(tracker.onEvent(GestureEnded(6_100, pos(10.9), isFling = false)))
            .containsExactly(SaveReading(pos(10.9)), ReadingMoved(pos(10.0), pos(10.9))).inOrder()
    }

    @Test
    fun confirmationAfter25SecondsOfReadingAtTheNewPlace() {
        val tracker = awayAt15()
        val before = tracker.feed(
            Displayed(6_300, pos(15.1)),
            Displayed(11_300, pos(15.2)),
            Displayed(16_300, pos(15.3)),
            Displayed(21_300, pos(15.4)),
        )
        assertThat(before).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)

        // 25 000 ms après l'arrivée (1 300) : la lecture passe au nouvel endroit.
        assertThat(tracker.onEvent(Displayed(26_300, pos(15.5)))).containsExactly(SaveReading(pos(15.5)))
        assertThat(tracker.state).isEqualTo(following(15.5))
    }

    @Test
    fun noConfirmationOneMillisecondBefore25Seconds() {
        val tracker = awayAt15()
        val effects = tracker.feed(
            Displayed(6_300, pos(15.1)),
            Displayed(16_300, pos(15.3)),
            Displayed(26_299, pos(15.5)),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 15.5))
    }

    @Test
    fun noConfirmationWithoutReadingMovement() {
        val tracker = awayAt15()
        val ticks = (2..600).map { s -> Tick(s * 1_000L) }
        val effects = tracker.feed(*ticks.toTypedArray())
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 15.0))
    }

    @Test
    fun driftBeyondOneScreenMovesArrivalPointAndRestartsClock() {
        val tracker = awayAt15()
        assertThat(tracker.onEvent(Displayed(6_300, pos(15.5)))).isEmpty()
        // 1,2 écran du point d'arrivée : le point se déplace à 16,2 et le chrono repart à 11 300.
        assertThat(tracker.onEvent(Displayed(11_300, pos(16.2)))).isEmpty()
        // 30 s après la première arrivée mais 20 s après la nouvelle : pas encore.
        assertThat(tracker.onEvent(Displayed(31_300, pos(16.4)))).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)
        assertThat(tracker.onEvent(Displayed(36_300, pos(16.5)))).containsExactly(SaveReading(pos(16.5)))
        assertThat(tracker.state).isEqualTo(following(16.5))
    }

    @Test
    fun newNavigationWhileAwayRestartsFromTheNewPlaceAndKeepsReading() {
        val tracker = awayAt15()
        tracker.feed(Displayed(11_300, pos(15.1)), Displayed(21_300, pos(15.2)))
        val effects = tracker.feed(
            GestureEnded(22_000, pos(15.3), isFling = true),
            Displayed(22_100, pos(18.0)),
            Displayed(22_300, pos(20.0)),
            Tick(22_800),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 20.0))

        // Le chrono repart de la nouvelle arrivée (22 300) : 31 s après la première, rien.
        assertThat(tracker.onEvent(Displayed(32_300, pos(20.1)))).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)

        // La carte ramène toujours à la lecture d'origine.
        assertThat(tracker.onEvent(GoBack(33_000))).containsExactly(ScrollTo(pos(10.0)))
    }

    @Test
    fun navigationWhileAwayBackNearReadingHidesCard() {
        val tracker = awayAt15()
        val effects = tracker.feed(
            GestureEnded(3_000, pos(14.8), isFling = true),
            Displayed(3_100, pos(12.0)),
            Displayed(3_300, pos(10.4)),
            Tick(3_800),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.4))
    }

    // ---------- Scénarios des critères d'acceptation ----------

    @Test
    fun violentThirtyPageScrollNeverMovesReadingAndGoBackReturnsInOneTap() {
        val tracker = tracker()
        val scroll = listOf<ReaderEvent>(GestureEnded(1_000, pos(10.4), isFling = true)) +
            (1..30).map { i -> Displayed(1_000L + 50L * i, pos(10.4 + i)) } +
            Tick(3_000)
        val effects = tracker.feed(*scroll.toTypedArray())

        // La progression ne bouge pas : rien à sauvegarder, fermer l'app ici rouvre à la lecture.
        assertThat(effects.filterIsInstance<SaveReading>()).isEmpty()
        assertThat(tracker.state.reading).isEqualTo(pos(10.0))
        assertThat(tracker.state.showReturnCard).isTrue()

        assertThat(tracker.onEvent(GoBack(4_000))).containsExactly(ScrollTo(pos(10.0)))
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    // ---------- Configuration ----------

    @Test
    fun updateDistanceChangesTheScreenScale() {
        val tracker = tracker()
        tracker.updateDistance { a, b -> abs(a.totalProgression - b.totalProgression) * 1_000.0 }
        tracker.onEvent(Jumped(1_000, pos(9.5))) // 0,005 de progression = 5 écrans à la nouvelle échelle
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 9.5))
    }

    @Test
    fun thresholdsAreConfigurable() {
        val tracker = tracker(ReadingThresholds(returnCardMinScreens = 3.0))
        tracker.feed(
            GestureEnded(1_000, pos(10.2), isFling = true),
            Displayed(1_100, pos(11.5)),
            Displayed(1_200, pos(12.5)),
            Tick(1_700),
        )
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 12.5))
    }
}
