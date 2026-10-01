package com.maximebier.verso.core.position

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ReaderEvent.Displayed
import com.maximebier.verso.core.position.ReaderEvent.GestureEnded
import com.maximebier.verso.core.position.ReaderEvent.GoBack
import com.maximebier.verso.core.position.ReaderEvent.Jumped
import com.maximebier.verso.core.position.ReaderEvent.SelectionEnded
import com.maximebier.verso.core.position.ReaderEvent.SelectionStarted
import com.maximebier.verso.core.position.ReaderEvent.StayHere
import com.maximebier.verso.core.position.ReaderEvent.Tick
import com.maximebier.verso.core.position.TrackerEffect.ReadingMoved
import com.maximebier.verso.core.position.TrackerEffect.SaveReading
import com.maximebier.verso.core.position.TrackerEffect.ScrollTo
import com.maximebier.verso.core.settings.ScrollMode
import kotlin.math.abs
import org.junit.Test

class ReadingPositionTrackerTest {

    /** Distance factice : 1 écran = 0,01 de progression, donc pos(12.5) est à 2,5 écrans de pos(10.0). */
    private val distance = ScreenDistance { a, b -> abs(a.totalProgression - b.totalProgression) * 100.0 }

    /** Position située à `screens` écrans du début du livre. */
    private fun pos(screens: Double) =
        BookPosition(locatorJson = "{\"screens\":$screens}", totalProgression = screens / 100.0)

    /**
     * Seuils des scénarios, explicites pour ne pas dépendre des valeurs par défaut (calibrées à l'usage) :
     * navigation au-delà de 4 écrans/s entre deux Displayed, pour que les vitesses des scénarios (3,5 et
     * 5 écrans/s) restent de part et d'autre. La vitesse au relâchement n'est pas lue par le tracker.
     */
    private val thresholds = ReadingThresholds(
        flingScreensPerSecond = 1.0,
        displayedSpeedNavigationScreensPerSecond = 4.0,
    )

    /** Lecture à 10 écrans, déjà affichée : le premier Displayed (arrivée) est reçu à t = 0. */
    private fun tracker(thresholds: ReadingThresholds = this.thresholds): ReadingPositionTracker =
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

    // ---------- Changement de chapitre (glissé au bord) ----------

    @Test
    fun chapterTurnWhileFollowingMovesReadingWithoutReturnCard() {
        val tracker = tracker()
        // Le nouveau chapitre s’affiche 30 ms après la fin du glissé, 1,2 écran plus loin : vitesse de navigation.
        val effects = tracker.feed(
            Displayed(1_000, pos(10.2)),
            Displayed(1_030, pos(11.4)),
            GestureEnded(1_300, pos(11.4), isFling = false, chapterTurn = true),
            Tick(5_000),
        )
        assertThat(tracker.state).isEqualTo(following(11.4))
        assertThat(effects).contains(SaveReading(pos(11.4)))
    }

    @Test
    fun chapterTurnRightAfterAFlingStaysANavigation() {
        val tracker = tracker()
        // Fling accidentel jusqu’au bout du chapitre, puis glissé au bord : chapitre suivant, sans repos entre les deux.
        val effects = tracker.feed(
            GestureEnded(1_000, pos(10.3), isFling = true),
            Displayed(1_100, pos(13.0)),
            Displayed(1_300, pos(15.0)),
            Displayed(1_450, pos(16.2)),
            GestureEnded(1_700, pos(16.2), isFling = false, chapterTurn = true),
            Tick(5_000),
        )
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 16.2))
        assertThat(effects.filterIsInstance<SaveReading>()).isEmpty()
    }

    @Test
    fun chapterTurnWhileAwayKeepsTheCardAndTheReading() {
        val tracker = awayAt15()
        val effects = tracker.feed(
            Displayed(3_000, pos(16.2)),
            GestureEnded(3_300, pos(16.2), isFling = false, chapterTurn = true),
            Tick(6_000),
        )
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 16.2))
        assertThat(effects.filterIsInstance<SaveReading>()).isEmpty()
    }

    // ---------- Suivi (FOLLOWING) ----------

    @Test
    fun initialStateFollowsInitialPosition() {
        val tracker = ReadingPositionTracker(pos(10.0), distance, thresholds)
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun firstDisplayedIsTheArrivalAndNeverMovesReading() {
        val tracker = ReadingPositionTracker(pos(10.0), distance, thresholds)
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
    fun flingWhosePositionArrivesAfterTheRestDelayIsANavigation() {
        val tracker = tracker()
        // Navigateur classique : aucune position pendant l’inertie ; celle d’arrivée vient 1,3 s après la fin du geste.
        val effects = tracker.feed(
            GestureEnded(1_000, pos(10.0), isFling = true),
            Tick(1_650),
            Tick(2_000),
            Displayed(2_300, pos(12.0)),
            Tick(3_000),
        )
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 12.0))
        assertThat(effects).isEmpty()
    }

    @Test
    fun flingWhosePositionNeverArrivesEndsAfterTheMaximumWait() {
        val tracker = tracker()
        tracker.feed(GestureEnded(1_000, pos(10.0), isFling = true))
        tracker.onEvent(Tick(1_000 + ReadingThresholds().flingPositionMaxWaitMs))
        // Navigation close ; la position suivante, lente, est de la lecture ordinaire.
        tracker.feed(Displayed(6_000, pos(10.3)), Tick(7_000))
        assertThat(tracker.state).isEqualTo(following(10.3))
    }

    @Test
    fun restEventSettlesAPendingReadingMove() {
        val tracker = tracker()
        tracker.onEvent(Displayed(1_000, pos(10.3)))
        val effects = tracker.onEvent(ReaderEvent.Rest(1_100))
        assertThat(effects).containsExactly(SaveReading(pos(10.3)), ReadingMoved(pos(10.0), pos(10.3))).inOrder()
    }

    @Test
    fun positionReportedJustAfterTheGestureEndIsStillReading() {
        val tracker = tracker()
        // Navigateur classique : le moteur rapporte la position ~260 ms après le lâcher, soit juste après la fin
        // du geste (250 ms), qui porte encore l’ancienne position. Glissés de 0,33 écran toutes les 1,33 s.
        val effects = tracker.feed(
            Displayed(1_000, pos(10.33)),
            GestureEnded(1_250, pos(10.33), isFling = false),
            GestureEnded(2_580, pos(10.33), isFling = false),
            Displayed(2_590, pos(10.66)),
            GestureEnded(3_910, pos(10.66), isFling = false),
            Displayed(3_920, pos(10.99)),
            GestureEnded(5_240, pos(10.99), isFling = false),
            Displayed(5_250, pos(11.32)),
            Tick(6_000),
        )
        assertThat(tracker.state).isEqualTo(following(11.32))
        assertThat(effects.last()).isEqualTo(ReadingMoved(pos(10.99), pos(11.32)))
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
    fun briskReadingDragAtTwoScreensPerSecondBetweenDisplayedStaysReading() {
        // Seuils par défaut : le seuil de fling au relâchement (1,0) ne s'applique pas entre deux Displayed.
        val tracker = tracker(ReadingThresholds())
        tracker.onEvent(Displayed(100, pos(10.2))) // 0,2 écran en 100 ms : 2 écrans/s
        assertThat(tracker.onEvent(GestureEnded(400, pos(10.2), isFling = false)))
            .containsExactly(SaveReading(pos(10.2)), ReadingMoved(pos(10.0), pos(10.2))).inOrder()
        assertThat(tracker.state).isEqualTo(following(10.2))
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
    fun approximateJumpThatNeverMovesArrivesWhereTheTextStands() {
        val tracker = tracker()
        // Lien vers une note déjà à l’écran : le moteur ne rapporte aucune nouvelle position.
        tracker.onEvent(Jumped(1_000, pos(50.0), approximate = true))
        assertThat(tracker.state.showReturnCard).isTrue()
        tracker.onEvent(Tick(1_000 + ReadingThresholds().jumpArrivalMaxWaitMs))
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun stayHereBeforeAnApproximateJumpMovedKeepsTheReading() {
        val tracker = tracker()
        tracker.onEvent(Jumped(1_000, pos(50.0), approximate = true))
        val effects = tracker.onEvent(StayHere(1_200))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

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
    fun linkToAnchorThenStayHereSavesTheActuallyDisplayedPositionNotTheFileStart() {
        val tracker = tracker()
        // Lien vers une note : cible approximative (début du fichier à 50), le moteur affiche l'ancre à 57.
        assertThat(tracker.onEvent(Jumped(1_000, pos(50.0), approximate = true))).isEmpty()
        tracker.onEvent(Displayed(1_100, pos(57.0)))
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 57.0))

        // « Rester ici » avant même la stabilisation : la position réellement affichée.
        assertThat(tracker.onEvent(StayHere(1_200))).containsExactly(SaveReading(pos(57.0)))
        assertThat(tracker.state).isEqualTo(following(57.0))
    }

    @Test
    fun tocEntryWithAnchorArrivesAtTheStabilizedDisplayedPositionAndStayHereSavesIt() {
        val tracker = tracker()
        tracker.onEvent(Jumped(1_000, pos(50.0), approximate = true))
        // Le moteur passe par le début du fichier puis se pose sur l'ancre.
        tracker.feed(Displayed(1_050, pos(50.0)), Displayed(1_150, pos(57.0)))
        assertThat(tracker.feed(Tick(1_700))).isEmpty() // stabilisée : arrivée à 57
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 57.0))

        // Après l'arrivée, l'affiché suit de nouveau les petits mouvements de lecture.
        tracker.onEvent(Displayed(3_700, pos(57.2)))
        assertThat(tracker.onEvent(StayHere(3_800))).containsExactly(SaveReading(pos(57.2)))
        assertThat(tracker.state).isEqualTo(following(57.2))
    }

    @Test
    fun approximateJumpLandingNearReadingHidesTheCardAtArrival() {
        val tracker = tracker()
        // Ancre dans le chapitre courant : la cible (début du chapitre, à 4) paraît lointaine…
        tracker.onEvent(Jumped(1_000, pos(4.0), approximate = true))
        assertThat(tracker.state.showReturnCard).isTrue()
        // … mais le moteur se pose à 0,5 écran de la lecture.
        tracker.onEvent(Displayed(1_100, pos(10.5)))
        assertThat(tracker.feed(Tick(1_700))).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.5))
    }

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

    /**
     * Petit glissé de lecture : l'affiché passe à `to` à `timeMs`, le doigt est levé sans fling et le
     * signal de fin de geste arrive 200 ms plus tard (après le repos du défilement, comme dans l'app).
     */
    private fun drag(timeMs: Long, to: Double): Array<ReaderEvent> =
        arrayOf(Displayed(timeMs, pos(to)), GestureEnded(timeMs + 200, pos(to), isFling = false))

    /** Glissés de lecture de 0,05 écran chacun à partir de `from`, aux instants donnés. */
    private fun readingDrags(from: Double, times: List<Long>): Array<ReaderEvent> =
        times.flatMapIndexed { i, t -> drag(t, from + 0.05 * (i + 1)).toList() }.toTypedArray()

    private fun ticks(fromS: Int, toS: Int): Array<ReaderEvent> =
        (fromS..toS).map { s -> Tick(s * 1_000L) }.toTypedArray()

    @Test
    fun confirmationAfter25SecondsOfReadingAtTheNewPlace() {
        val tracker = awayAt15()
        // Petits glissés toutes les 3 s à partir de 5 000 : la fenêtre de lecture s'ouvre à la fin du premier
        // (5 200), pas à l'arrivée (1 300).
        val before = tracker.feed(*readingDrags(15.0, (0..8).map { 5_000L + 3_000L * it }))
        assertThat(before).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)
        assertThat(tracker.state.reading).isEqualTo(pos(10.0))

        // 10ᵉ glissé, fini à 32 200 : 27 s de lecture depuis 5 200, la lecture passe au nouvel endroit.
        assertThat(tracker.feed(*drag(32_000, 15.5))).containsExactly(SaveReading(pos(15.5)))
        assertThat(tracker.state).isEqualTo(following(15.5))
    }

    @Test
    fun noConfirmationOneMillisecondBefore25SecondsOfReading() {
        val tracker = awayAt15()
        val effects = tracker.feed(
            *drag(6_300, 15.1),
            *drag(16_300, 15.3),
            *drag(26_300, 15.4),
            Displayed(31_299, pos(15.5)),
            GestureEnded(31_499, pos(15.5), isFling = false), // 24 999 ms après la fin du premier glissé
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 15.5))
        assertThat(tracker.feed(*drag(32_000, 15.6))).containsExactly(SaveReading(pos(15.6)))
    }

    @Test
    fun noConfirmationWithoutReadingMovement() {
        val tracker = awayAt15()
        val effects = tracker.feed(*ticks(2, 600))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 15.0))
    }

    /** Défaut A du contrôle sur téléphone : carte laissée 30 s, puis un seul glissé de 150 px. */
    @Test
    fun singleSmallDragAfterCardIdleForThirtySecondsDoesNotConfirm() {
        val tracker = awayAt15()
        tracker.feed(*ticks(2, 31))
        val effects = tracker.feed(
            Displayed(31_300, pos(15.05)),
            Displayed(31_600, pos(15.1)),
            Displayed(31_900, pos(15.12)),
            GestureEnded(32_150, pos(15.12), isFling = false),
            Tick(33_000),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 15.12))
    }

    @Test
    fun firstFrameOfAFlingAfterTwentyFiveSecondsDoesNotConfirm() {
        val tracker = awayAt15()
        tracker.feed(*ticks(2, 30))
        // Premier Displayed du fling : lent vu depuis l'arrivée (30 s plus tôt), il n'est pas encore une navigation.
        assertThat(tracker.onEvent(Displayed(30_500, pos(15.3)))).isEmpty()
        assertThat(tracker.state.reading).isEqualTo(pos(10.0))
        val effects = tracker.feed(
            GestureEnded(30_600, pos(15.3), isFling = true),
            Displayed(30_700, pos(18.0)),
            Displayed(30_900, pos(20.0)),
            Tick(31_500),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 20.0))
    }

    /** Un tap (barres masquées) peut décaler l'affiché sans geste de défilement : ce n'est pas de la lecture. */
    @Test
    fun displayedShiftWithoutGestureAfterTwentyFiveSecondsDoesNotConfirm() {
        val tracker = awayAt15()
        tracker.feed(*ticks(2, 30))
        val effects = tracker.feed(Displayed(30_500, pos(15.02)), Tick(31_000), Tick(32_000))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 15.02))
    }

    @Test
    fun longIdleGapInsideTheReadingWindowRestartsIt() {
        val tracker = awayAt15()
        // 10 s de lecture, 60 s sans rien, 10 s de lecture : jamais 25 s de lecture continue.
        val first = tracker.feed(*readingDrags(15.0, (0..5).map { 5_000L + 2_000L * it }))
        tracker.feed(*ticks(16, 74))
        val second = tracker.feed(*readingDrags(15.1, (0..5).map { 75_000L + 2_000L * it }))
        assertThat(first + second).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)

        // La lecture continue : 25 s après le début de la seconde fenêtre (75 200), elle confirme.
        val more = tracker.feed(*readingDrags(15.4, (0..5).map { 87_000L + 2_000L * it }))
        assertThat(more).isEmpty()
        assertThat(tracker.feed(*drag(100_000, 15.8))).containsExactly(SaveReading(pos(15.8)))
    }

    @Test
    fun pausesUpToTheIdleGapKeepTheReadingWindowOpen() {
        val gap = ReadingThresholds().confirmMaxIdleGapMs
        val tracker = awayAt15()
        val effects = tracker.feed(*drag(5_000, 15.1), *drag(5_000 + gap, 15.2))
        assertThat(effects).isEmpty()
        // Fenêtre ouverte à 5 200 ; ce glissé finit 25 s plus tard, chaque pause ≤ confirmMaxIdleGapMs.
        assertThat(tracker.feed(*drag(30_000, 15.3))).containsExactly(SaveReading(pos(15.3)))
    }

    @Test
    fun pauseLongerThanTheIdleGapRestartsTheReadingWindow() {
        val gap = ReadingThresholds().confirmMaxIdleGapMs
        val tracker = awayAt15()
        val effects = tracker.feed(
            *drag(5_000, 15.1),
            *drag(5_000 + gap + 1, 15.2), // pause trop longue : la fenêtre repart ici
            *drag(30_000, 15.3),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)
    }

    @Test
    fun driftBeyondOneScreenMovesArrivalPointAndRestartsClock() {
        val tracker = awayAt15()
        assertThat(tracker.feed(*drag(6_300, 15.5))).isEmpty()
        // 1,2 écran du point d'arrivée : le point se déplace à 16,2 et la fenêtre repart à la fin de ce glissé (11 500).
        assertThat(tracker.feed(*drag(11_300, 16.2))).isEmpty()
        assertThat(tracker.feed(*drag(21_300, 16.3))).isEmpty()
        // 25 s après la première fenêtre (6 500) mais 20 s après la nouvelle : pas encore.
        assertThat(tracker.feed(*drag(31_300, 16.4))).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)
        assertThat(tracker.feed(*drag(36_300, 16.5))).containsExactly(SaveReading(pos(16.5)))
        assertThat(tracker.state).isEqualTo(following(16.5))
    }

    @Test
    fun newNavigationWhileAwayRestartsFromTheNewPlaceAndKeepsReading() {
        val tracker = awayAt15()
        tracker.feed(*drag(11_300, 15.1), *drag(21_300, 15.2))
        val effects = tracker.feed(
            GestureEnded(22_000, pos(15.3), isFling = true),
            Displayed(22_100, pos(18.0)),
            Displayed(22_300, pos(20.0)),
            Tick(22_800),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 20.0))

        // La navigation ne compte pas et remet la fenêtre à zéro : 25 s après le premier glissé (11 500), rien.
        assertThat(tracker.feed(*drag(32_300, 20.1), *drag(36_300, 20.2))).isEmpty()
        assertThat(tracker.state.mode).isEqualTo(TrackerMode.AWAY)

        // La carte ramène toujours à la lecture d'origine.
        assertThat(tracker.onEvent(GoBack(37_000))).containsExactly(ScrollTo(pos(10.0)))
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

    // ---------- Mode pages : un tour de page est un geste de lecture ----------

    /** Tour de page vers [page] écrans : position affichée à [atMs], fin de geste 100 ms plus tard. */
    private fun pageTurn(atMs: Long, page: Double): Array<ReaderEvent> =
        arrayOf(Displayed(atMs, pos(page)), GestureEnded(atMs + 100, pos(page), isFling = false))

    @Test
    fun slowPageTurnsAreReadingAndReadingFollows() {
        val tracker = tracker(ReadingThresholds.forPages())
        val effects = (1..5).flatMap { i -> tracker.feed(*pageTurn(atMs = 30_000L * i, page = 10.0 + i)) } +
            tracker.onEvent(Tick(160_000))

        assertThat(tracker.state).isEqualTo(following(15.0))
        assertThat(effects).contains(SaveReading(pos(15.0)))
        assertThat(effects.filterIsInstance<ScrollTo>()).isEmpty()
    }

    @Test
    fun turningPagesFastIsANavigationAndGoBackReturnsInOneTap() {
        val tracker = tracker(ReadingThresholds.forPages())
        // Cinq pages en deux secondes : plus de 3 écrans en moins de 5 s.
        (1..5).forEach { i -> tracker.feed(*pageTurn(atMs = 600L + 400L * i, page = 10.0 + i)) }
        tracker.onEvent(Tick(4_000))

        assertThat(tracker.state.reading).isEqualTo(pos(10.0))
        assertThat(tracker.state.showReturnCard).isTrue()
        assertThat(tracker.onEvent(GoBack(5_000))).containsExactly(ScrollTo(pos(10.0)))
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun afterALongRestFourFastPagesAreANavigationBackToTheRestingPage() {
        val tracker = tracker(ReadingThresholds.forPages())
        // Page 10 lue 30 s, puis quatre pages en deux secondes : le déplacement se compte depuis la page affichée au
        // début de la fenêtre (la page de repos), pas depuis le premier tour, qui n’est pas de la lecture.
        (1..4).forEach { i -> tracker.feed(*pageTurn(atMs = 30_000L + 500L * i, page = 10.0 + i)) }
        tracker.onEvent(Tick(34_000))

        assertThat(tracker.state.reading).isEqualTo(pos(10.0))
        assertThat(tracker.state.showReturnCard).isTrue()
        assertThat(tracker.onEvent(GoBack(35_000))).containsExactly(ScrollTo(pos(10.0)))
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun afterALongRestThreePagesInAFewSecondsAreStillReading() {
        val tracker = tracker(ReadingThresholds.forPages())
        // Trois écrans en moins de 5 s ne dépassent pas navigationWindowScreens : c’est encore de la lecture.
        (1..3).forEach { i -> tracker.feed(*pageTurn(atMs = 30_000L + 1_500L * i, page = 10.0 + i)) }
        tracker.onEvent(Tick(40_000))

        assertThat(tracker.state).isEqualTo(following(13.0))
    }

    @Test
    fun inPagesModeTwoPagesReadAtTheNewPlaceConfirmIt() {
        val tracker = tracker()
        tracker.updateThresholds(ReadingThresholds.forPages())
        tracker.feed(Jumped(1_000, pos(30.0)), Displayed(1_100, pos(30.0)), Tick(2_000))
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 30.0))

        // Une page lue en une minute environ, puis la suivante.
        tracker.feed(*pageTurn(atMs = 40_000, page = 31.0))
        assertThat(tracker.state.showReturnCard).isTrue()
        val effects = tracker.feed(*pageTurn(atMs = 100_000, page = 32.0))

        assertThat(tracker.state).isEqualTo(following(32.0))
        assertThat(effects).contains(SaveReading(pos(32.0)))
    }

    @Test
    fun withContinuousThresholdsPageTurnsWouldNeverConfirm() {
        // Justifie ReadingThresholds.forPages() : 60 s entre deux tours dépassent la pause de 15 s, et deux pages
        // dépassent la dérive d’un écran ; la carte ne partirait jamais en lisant.
        val tracker = tracker()
        tracker.feed(Jumped(1_000, pos(30.0)), Displayed(1_100, pos(30.0)), Tick(2_000))
        tracker.feed(*pageTurn(atMs = 40_000, page = 31.0))
        tracker.feed(*pageTurn(atMs = 100_000, page = 32.0))
        tracker.feed(*pageTurn(atMs = 160_000, page = 33.0))

        assertThat(tracker.state.showReturnCard).isTrue()
        assertThat(tracker.state.reading).isEqualTo(pos(10.0))
    }

    @Test
    fun pagesThresholdsOnlyRelaxTheConfirmationWindow() {
        val pages = ReadingThresholds.forPages()
        assertThat(pages).isEqualTo(
            ReadingThresholds().copy(
                confirmMaxIdleGapMs = ReadingThresholds.PAGES_CONFIRM_MAX_IDLE_GAP_MS,
                confirmMaxDriftScreens = ReadingThresholds.PAGES_CONFIRM_MAX_DRIFT_SCREENS,
            ),
        )
        assertThat(ReadingThresholds.forScrollMode(ScrollMode.PAGES)).isEqualTo(pages)
        assertThat(ReadingThresholds.forScrollMode(ScrollMode.CONTINUOUS)).isEqualTo(ReadingThresholds())
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
        val tracker = tracker(thresholds.copy(returnCardMinScreens = 3.0))
        tracker.feed(
            GestureEnded(1_000, pos(10.2), isFling = true),
            Displayed(1_100, pos(11.5)),
            Displayed(1_200, pos(12.5)),
            Tick(1_700),
        )
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 12.5))
    }

    // ---------- Sélection de texte (V3) ----------

    @Test
    fun selectionWithAutoScrollNeverMovesReading() {
        val tracker = tracker()
        val effects = tracker.feed(
            SelectionStarted(1_000),
            // Poignée tirée en bas de l’écran : le texte défile tout seul de 3 écrans.
            Displayed(1_200, pos(11.0)),
            Displayed(1_400, pos(13.0)),
            GestureEnded(1_500, pos(13.0), isFling = false),
            Tick(3_000),
            SelectionEnded(3_200, pos(13.0)),
            Tick(6_000),
        )
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 13.0))
    }

    @Test
    fun readingResumesFromWhereTheSelectionLeftTheText() {
        val tracker = tracker()
        tracker.feed(SelectionStarted(1_000), Displayed(1_200, pos(10.5)), SelectionEnded(2_000, pos(10.5)))
        val effects = tracker.feed(
            Displayed(3_000, pos(10.8)),
            GestureEnded(3_100, pos(10.8), isFling = false),
            Tick(5_000),
        )
        assertThat(effects).contains(SaveReading(pos(10.8)))
        assertThat(tracker.state).isEqualTo(following(10.8))
    }

    @Test
    fun selectionKeepsTheReturnCardAsItWas() {
        val tracker = awayAt15()
        val effects = tracker.feed(SelectionStarted(5_000), Displayed(5_100, pos(15.4)), SelectionEnded(6_000, pos(15.4)), Tick(9_000))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 15.4))
    }

    @Test
    fun pendingReadingIsSettledWhenTheSelectionStarts() {
        val tracker = tracker()
        // Petit mouvement pas encore validé (repos pas atteint), puis appui long.
        val effects = tracker.feed(Displayed(1_000, pos(10.4)), SelectionStarted(1_200))
        assertThat(effects).contains(SaveReading(pos(10.4)))
        assertThat(tracker.state).isEqualTo(following(10.4))
    }

    @Test
    fun selectionEndedWithoutStartIsHarmless() {
        val tracker = tracker()
        val effects = tracker.feed(SelectionEnded(1_000, pos(10.2)), Tick(5_000))
        assertThat(effects).isEmpty()
        assertThat(tracker.state).isEqualTo(following(reading = 10.0, displayed = 10.2))
    }

    @Test
    fun selectionDuringAJumpWithoutArrivalDoesNotSwallowTheNextMove() {
        val tracker = tracker()
        // Saut vers une cible déjà à l’écran : aucune position ne vient, puis appui long.
        tracker.feed(Jumped(1_000, pos(10.0)), SelectionStarted(1_100), SelectionEnded(1_500, pos(10.0)))
        val effects = tracker.feed(
            Displayed(2_000, pos(10.4)),
            GestureEnded(2_100, pos(10.4), isFling = false),
            Tick(4_000),
        )
        assertThat(effects).contains(SaveReading(pos(10.4)))
        assertThat(tracker.state).isEqualTo(following(10.4))
    }
}
