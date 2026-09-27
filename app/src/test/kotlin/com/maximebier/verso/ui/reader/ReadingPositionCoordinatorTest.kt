package com.maximebier.verso.ui.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ProgressionScreenDistance
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.GestureSignal
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.reader.testLocator
import com.maximebier.verso.readium.Locators
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReadingPositionCoordinatorTest {

    /** 1 000 écrans : un écran = 0,001 de progression totale. */
    private val bookScreens = 1_000.0

    private val saved = mutableListOf<BookPosition>()

    private fun TestScope.coordinatorAt(start: Locator) =
        ReadingPositionCoordinator(
            initial = Locators.toPosition(start),
            distance = ProgressionScreenDistance(bookScreens),
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            onSave = { saved += it },
        )

    /** Effets réémis par le coordinateur, collectés sans délai dans l’ordre d’émission. */
    private fun TestScope.effectsOf(coordinator: ReadingPositionCoordinator): List<TrackerEffect> {
        val effects = mutableListOf<TrackerEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.readingEffects.collect { effects += it }
        }
        return effects
    }

    @Test
    fun goBackWhileTheSurfaceIsRecreatedIsReplayedOnTheNewOne() = runTest {
        val start = testLocator(total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()
        for (step in 1..3) {
            advanceTimeBy(200)
            fake.displayed.value = testLocator(total = 0.300 + step * 0.010)
            runCurrent()
        }
        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = true))
        advanceTimeBy(1_500)
        runCurrent()
        assertThat(coordinator.state.value.showReturnCard).isTrue()

        // Rotation : la surface est retirée, « Revenir » est touché avant que la nouvelle soit prête.
        coordinator.detach()
        coordinator.onGoBack()
        val recreated = FakeReaderController(testLocator(total = 0.330))
        coordinator.attach(recreated)
        runCurrent()

        assertThat(recreated.goCalls.single().locations.totalProgression!!).isWithin(1e-9).of(0.300)
    }

    @Test
    fun violentScrollOfThirtyPagesKeepsReadingAndGoBackReturnsInOneTap() = runTest {
        val start = testLocator(total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()

        // Geste lancé : 30 écrans en 600 ms.
        for (step in 1..3) {
            advanceTimeBy(200)
            fake.displayed.value = testLocator(total = 0.300 + step * 0.010)
            runCurrent()
        }
        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = true))
        runCurrent()
        // La carte n’apparaît qu’au repos : constaté au Tick suivant, au moins saveDebounceMs après le dernier mouvement.
        assertThat(coordinator.state.value.showReturnCard).isFalse()
        advanceTimeBy(1_500)
        runCurrent()

        assertThat(coordinator.state.value.reading.totalProgression).isWithin(1e-9).of(0.300)
        assertThat(coordinator.state.value.showReturnCard).isTrue()
        assertThat(saved.none { it.totalProgression > 0.301 }).isTrue()

        coordinator.onGoBack()
        runCurrent()

        assertThat(fake.goCalls.last().locations.totalProgression!!).isWithin(1e-9).of(0.300)
        assertThat(coordinator.state.value.showReturnCard).isFalse()
    }

    @Test
    fun noTickWhileStoppedAndTickingAgainOnStart() = runTest {
        // Revue finale M5 : aucun battement d’une seconde tant que le lecteur est en arrière-plan. Chaque Tick lit l’horloge.
        var clockReads = 0
        val start = testLocator(total = 0.300)
        val coordinator = ReadingPositionCoordinator(
            initial = Locators.toPosition(start),
            distance = ProgressionScreenDistance(bookScreens),
            scope = backgroundScope,
            clock = { clockReads++; testScheduler.currentTime },
            onSave = { saved += it },
        )
        coordinator.attach(FakeReaderController(start))
        advanceTimeBy(3_500); runCurrent()
        assertThat(clockReads).isAtLeast(3)

        coordinator.onStopped()
        val stopped = clockReads
        advanceTimeBy(60_000); runCurrent()
        assertThat(clockReads).isEqualTo(stopped)

        coordinator.onStarted()
        advanceTimeBy(3_500); runCurrent()
        assertThat(clockReads).isAtLeast(stopped + 3)
    }

    @Test
    fun tocJumpToBookStartShowsCardAndGoBackReturnsToChapter() = runTest {
        val start = testLocator(chapter = 9, total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()

        coordinator.onJump(testLocator(chapter = 1, progression = 0.0, total = 0.0))
        runCurrent()

        assertThat(fake.goCalls.single().href.toString()).isEqualTo("chapitre-1.xhtml")
        assertThat(coordinator.state.value.showReturnCard).isTrue()
        assertThat(coordinator.state.value.reading.totalProgression).isWithin(1e-9).of(0.300)

        coordinator.onGoBack()
        runCurrent()

        assertThat(fake.goCalls.last().href.toString()).isEqualTo("chapitre-9.xhtml")
        assertThat(coordinator.state.value.showReturnCard).isFalse()
    }

    @Test
    fun readingTwentyFiveSecondsAtNewPlaceHidesCardAndMovesProgress() = runTest {
        val start = testLocator(total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()

        val effects = effectsOf(coordinator)
        coordinator.onJump(testLocator(total = 0.500))
        runCurrent()
        assertThat(coordinator.state.value.showReturnCard).isTrue()

        // Lecture réelle : petits scrolls lents toutes les 2 s, moins d’un écran au total. La fenêtre de
        // confirmation s’ouvre au premier (2 s après le saut) : 14 scrolls couvrent 26 s de lecture.
        var total = 0.500
        repeat(14) {
            advanceTimeBy(2_000)
            total += 0.000_05
            fake.displayed.value = testLocator(total = total)
            runCurrent()
            fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false))
            runCurrent()
        }

        assertThat(coordinator.state.value.showReturnCard).isFalse()
        assertThat(coordinator.state.value.reading.totalProgression).isWithin(0.001).of(total)
        assertThat(saved.last().totalProgression).isWithin(0.001).of(total)
        // Confirmation : la lecture change sans mouvement de lecture (SaveReading seul, aucun mot compté).
        assertThat(effects.filterIsInstance<TrackerEffect.ReadingMoved>()).isEmpty()
        assertThat((effects.single() as TrackerEffect.SaveReading).position.totalProgression).isWithin(0.001).of(total)
    }

    @Test
    fun stayHereMakesDisplayedTheReadingPosition() = runTest {
        val start = testLocator(total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()
        val effects = effectsOf(coordinator)

        coordinator.onJump(testLocator(total = 0.700))
        runCurrent()
        coordinator.onStayHere()
        runCurrent()

        assertThat(coordinator.state.value.showReturnCard).isFalse()
        assertThat(coordinator.state.value.reading.totalProgression).isWithin(1e-9).of(0.700)
        assertThat(saved.last().totalProgression).isWithin(1e-9).of(0.700)
        // « Rester ici » : SaveReading sans ReadingMoved.
        assertThat(effects.map { it::class }).containsExactly(TrackerEffect.SaveReading::class)
    }

    @Test
    fun readingMoveEmitsSaveReadingThenReadingMovedInTrackerOrder() = runTest {
        val start = testLocator(total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()
        val effects = effectsOf(coordinator)

        // Petit pas lent (0,2 écran en 2 s), puis doigt levé sans lancer : mouvement de lecture validé.
        advanceTimeBy(2_000)
        val next = testLocator(total = 0.300_2)
        fake.displayed.value = next
        runCurrent()
        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false))
        runCurrent()

        val from = Locators.toPosition(start)
        val to = Locators.toPosition(next)
        assertThat(effects).containsExactly(
            TrackerEffect.SaveReading(to),
            TrackerEffect.ReadingMoved(from, to),
        ).inOrder()
        assertThat(saved).containsExactly(to)
    }

    /** Cible d’un lien ou d’une entrée de sommaire avec ancre, comme la donne `locatorFromLink` : pas de progression. */
    private fun anchorTarget(chapter: Int, total: Double) = Locator(
        href = Url("chapitre-$chapter.xhtml")!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(fragments = listOf("note-3"), totalProgression = total),
    )

    @Test
    fun stayHereAfterLinkToAnchorSavesTheDisplayedPositionNotTheChapterStart() = runTest {
        val start = testLocator(chapter = 2, total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()

        coordinator.onLinkFollowed(anchorTarget(chapter = 9, total = 0.900))
        runCurrent()
        // Le moteur se pose sur la note, au milieu du chapitre 9.
        advanceTimeBy(100)
        val note = testLocator(chapter = 9, progression = 0.6, total = 0.960)
        fake.displayed.value = note
        runCurrent()
        coordinator.onStayHere()
        runCurrent()

        assertThat(saved).containsExactly(Locators.toPosition(note))
        assertThat(coordinator.state.value.reading).isEqualTo(Locators.toPosition(note))
        assertThat(coordinator.state.value.showReturnCard).isFalse()
    }

    @Test
    fun stayHereAfterTocEntryWithAnchorSavesTheDisplayedPosition() = runTest {
        val start = testLocator(chapter = 2, total = 0.300)
        // Comme Readium : go() vers une ancre se pose directement sur l’ancre, au milieu du fichier.
        val anchor = testLocator(chapter = 5, progression = 0.4, total = 0.540)
        val engine = object : ReaderController {
            override val displayed = MutableStateFlow<Locator?>(start)
            override val gestures = MutableSharedFlow<GestureSignal>()
            override val viewportHeightPx = 2_000
            override suspend fun go(locator: Locator) {
                displayed.value = anchor
            }
            override suspend fun excerptLocator(): Locator? = displayed.value
        }
        val coordinator = coordinatorAt(start)
        coordinator.attach(engine)
        runCurrent()

        coordinator.onJump(anchorTarget(chapter = 5, total = 0.500))
        advanceTimeBy(2_000) // stabilisée : arrivée constatée au Tick
        runCurrent()
        assertThat(coordinator.state.value.showReturnCard).isTrue()
        assertThat(coordinator.state.value.displayed).isEqualTo(Locators.toPosition(anchor))

        coordinator.onStayHere()
        runCurrent()

        assertThat(saved).containsExactly(Locators.toPosition(anchor))
    }

    @Test
    fun internalLinkFollowedByTheEngineIsAJumpNotReading() = runTest {
        val start = testLocator(chapter = 2, total = 0.300)
        val fake = FakeReaderController(start)
        val coordinator = coordinatorAt(start)
        coordinator.attach(fake)
        runCurrent()
        val effects = effectsOf(coordinator)

        // Lien vers une note en fin de livre : le moteur se déplace lui-même, le coordinateur l’annonce seulement.
        val note = testLocator(chapter = 9, progression = 0.0, total = 0.950)
        coordinator.onLinkFollowed(note)
        runCurrent()
        assertThat(fake.goCalls).isEmpty()
        advanceTimeBy(100)
        fake.displayed.value = note
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertThat(coordinator.state.value.showReturnCard).isTrue()
        assertThat(coordinator.state.value.reading.totalProgression).isWithin(1e-9).of(0.300)
        assertThat(effects).isEmpty()
        assertThat(saved).isEmpty()

        coordinator.onGoBack()
        runCurrent()
        assertThat(fake.goCalls.single().href.toString()).isEqualTo("chapitre-2.xhtml")
    }
}
