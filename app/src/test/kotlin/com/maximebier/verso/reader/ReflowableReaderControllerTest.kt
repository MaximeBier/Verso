@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.readium.ReadingOrderPositions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.navigator.common.CssSelector
import org.readium.navigator.common.HtmlId
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

// Robolectric : Locator et Url s’appuient sur org.json et android.net.Uri.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReflowableReaderControllerTest {

    private val positions = ReadingOrderPositions.from(
        hrefs = listOf("ch1.xhtml", "ch2.xhtml"),
        counts = listOf(50, 50),
    )!!

    private fun at(href: String, progression: Double, total: Double? = null) = Locator(
        href = Url(href)!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(progression = progression, totalProgression = total),
    )

    private class Harness(scope: TestScope) {
        var centerTaps = 0
        val navigated = mutableListOf<ReflowableWebGoLocation>()
        val controller = ReflowableReaderController(
            scope = scope.backgroundScope,
            readChapterHtml = { "<html><body><p>un deux trois quatre cinq six sept huit neuf dix</p></body></html>" },
            onCenterTap = { centerTaps++ },
            thresholds = ReadingThresholds(flingScreensPerSecond = 1.0),
            uptimeMs = { scope.testScheduler.currentTime },
            wallClockMs = { scope.testScheduler.currentTime },
        ).apply {
            viewportHeightPx = 2_000
            bind { navigated += it }
        }
    }

    @Test
    fun displayedIsNullBeforeFirstLocationThenCarriesFineTotalProgression() = runTest {
        val h = Harness(this)
        assertThat(h.controller.displayed.value).isNull()

        h.controller.setPositions(positions)
        h.controller.onDisplayed(at("ch2.xhtml", 0.5, total = 0.74))

        assertThat(h.controller.displayed.value!!.locations.totalProgression!!).isWithin(1e-9).of(0.75)
    }

    @Test
    fun positionsLoadedLaterRefillTheDisplayedLocator() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.5, total = 0.24))
        assertThat(h.controller.displayed.value!!.locations.totalProgression!!).isWithin(1e-9).of(0.24)

        h.controller.setPositions(positions)

        assertThat(h.controller.displayed.value!!.locations.totalProgression!!).isWithin(1e-9).of(0.25)
    }

    @Test
    fun gestureSignalComesOnceScrollingHasSettled() = runTest {
        val h = Harness(this)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -500f)
            advanceTimeBy(100)
            h.controller.onDisplayed(at("ch1.xhtml", 0.1))
            advanceTimeBy(100)
            h.controller.onDisplayed(at("ch1.xhtml", 0.2))
            advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS - 1)
            expectNoEvents()

            advanceTimeBy(ReaderGestures.SETTLE_POLL_MS + 1)
            val signal = awaitItem()
            assertThat(signal.isFling).isFalse()
            assertThat(signal.timeMs).isAtLeast(200 + ReaderGestures.SETTLE_QUIET_MS)
            assertThat(h.controller.displayed.value!!.locations.progression).isEqualTo(0.2)
        }
    }

    @Test
    fun flingIsDecidedByReleaseSpeedInScreensPerSecond() = runTest {
        val h = Harness(this)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -2_500f) // 1,25 écran/s
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            assertThat(awaitItem().isFling).isTrue()

            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = 1_500f) // 0,75 écran/s
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            assertThat(awaitItem().isFling).isFalse()
        }
    }

    @Test
    fun releaseAtOneAndAHalfScreensPerSecondIsAFlingWithDefaultThresholds() = runTest {
        val controller = ReflowableReaderController(
            scope = backgroundScope,
            readChapterHtml = { null },
            onCenterTap = {},
            thresholds = ReadingThresholds(), // flingScreensPerSecond = 1,0 (vitesse au relâchement)
            uptimeMs = { testScheduler.currentTime },
            wallClockMs = { testScheduler.currentTime },
        ).apply { viewportHeightPx = 2_000 }
        controller.gestures.test {
            controller.onPointerDown()
            controller.onGestureReleased(velocityYPxPerSecond = -3_000f) // 1,5 écran/s
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            assertThat(awaitItem().isFling).isTrue()
        }
    }

    @Test
    fun aNewTouchEndsThePreviousScrollAndEmitsItsSignalAtOnce() = runTest {
        val h = Harness(this)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -4_000f)
            advanceTimeBy(50)
            h.controller.onDisplayed(at("ch1.xhtml", 0.3))
            h.controller.onPointerDown()
            runCurrent()

            assertThat(awaitItem().isFling).isTrue()
            expectNoEvents()
        }
    }

    @Test
    fun goJumpsWithoutGestureSignalAndFlushesAPendingOne() = runTest {
        val h = Harness(this)
        h.controller.gestures.test {
            h.controller.go(at("ch2.xhtml", 0.4))
            runCurrent()
            expectNoEvents()
            assertThat(h.navigated.single().progression?.value).isEqualTo(0.4)

            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f)
            h.controller.go(at("ch1.xhtml", 0.0))
            runCurrent()
            assertThat(awaitItem().isFling).isFalse()
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            expectNoEvents()
        }
    }

    /** Un tap physique : vu par l’observateur de l’app au lâcher, puis (sauf s’il est absorbé) par Readium. */
    private fun Harness.tap(xFraction: Float, seenByReadium: Boolean = true) {
        controller.onPointerDown()
        controller.onTapLikeGesture(xFraction)
        if (seenByReadium) controller.onReadiumTap(xFraction)
    }

    @Test
    fun sameTapSeenByBothPathsTogglesOnce() = runTest {
        val h = Harness(this)
        h.tap(0.5f)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.centerTaps).isEqualTo(1)
    }

    @Test
    fun twoDistinctCenterTaps300MsApartToggleTwice() = runTest {
        val h = Harness(this)
        h.tap(0.5f)
        advanceTimeBy(300)
        h.tap(0.5f)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.centerTaps).isEqualTo(2)
    }

    @Test
    fun twoDistinctSwallowedCenterTaps300MsApartToggleTwice() = runTest {
        val h = Harness(this)
        h.tap(0.5f, seenByReadium = false)
        advanceTimeBy(300)
        h.tap(0.5f, seenByReadium = false)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.centerTaps).isEqualTo(2)
    }

    @Test
    fun edgeTapThenCenterTapTogglesOnce() = runTest {
        val h = Harness(this)
        h.tap(0.1f)
        advanceTimeBy(300)
        h.tap(0.5f)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.centerTaps).isEqualTo(1)
    }

    @Test
    fun readiumTapTheAppDidNotSeeAsATapStillCounts() = runTest {
        val h = Harness(this)
        h.controller.onPointerDown()
        h.controller.onReadiumTap(xFraction = 0.5f)

        assertThat(h.centerTaps).isEqualTo(1)
    }

    @Test
    fun swallowedCenterTapIsRecoveredOnceWithoutDoubleToggle() = runTest {
        val h = Harness(this)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture(xFraction = 0.5f)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS - 1)
        assertThat(h.centerTaps).isEqualTo(0)
        advanceTimeBy(2)
        assertThat(h.centerTaps).isEqualTo(1)

        // Le tap de Readium arrive quand même, en retard : déjà traité.
        h.controller.onReadiumTap(xFraction = 0.5f)
        assertThat(h.centerTaps).isEqualTo(1)
    }

    @Test
    fun touchThatStopsAScrollOrFollowsALinkIsNotATap() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.3))
        advanceTimeBy(50)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture(xFraction = 0.5f)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)
        assertThat(h.centerTaps).isEqualTo(0)

        h.controller.onPointerDown()
        h.controller.onTapLikeGesture(xFraction = 0.5f)
        h.controller.onLinkActivated()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)
        assertThat(h.centerTaps).isEqualTo(0)
    }

    @Test
    fun excerptIsApproximatedIntoTextAfter() = runTest {
        val h = Harness(this)
        assertThat(h.controller.excerptLocator()).isNull()

        h.controller.onDisplayed(at("ch1.xhtml", 0.0))
        val excerpt = h.controller.excerptLocator()!!

        assertThat(excerpt.text.after).startsWith("un deux trois")
        assertThat(excerpt.text.highlight).isNull()
        assertThat(excerpt.locations.progression).isEqualTo(0.0)
    }

    @Test
    fun goLocationDropsFragmentFromHrefAndKeepsItAsHtmlId() {
        val fromToc = Locator(
            href = Url("ch2.xhtml")!!,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(fragments = listOf("sec-3")),
        )
        val fromHref = fromToc.copy(href = Url("ch2.xhtml#note-1")!!, locations = Locator.Locations())

        assertThat(goLocationOf(fromToc)).isEqualTo(
            ReflowableWebGoLocation(href = Url("ch2.xhtml")!!, htmlId = HtmlId("sec-3")),
        )
        assertThat(goLocationOf(fromHref).href.toString()).isEqualTo("ch2.xhtml")
        assertThat(goLocationOf(fromHref).htmlId).isEqualTo(HtmlId("note-1"))
    }

    @Test
    fun goLocationKeepsProgressionAndSelectorButNeverAnchorsOnApproximateExcerpt() {
        val saved = at("ch1.xhtml", 0.42).copy(
            locations = Locator.Locations(progression = 0.42, otherLocations = mapOf("cssSelector" to "#p12")),
            text = Locator.Text(after = "Charles entra"),
        )

        val go = goLocationOf(saved)

        assertThat(go.progression?.value).isEqualTo(0.42)
        assertThat(go.cssSelector).isEqualTo(CssSelector("#p12"))
        assertThat(go.textAnchor).isNull()
        assertThat(go.htmlId).isNull()
    }

    // --- Anomalie F : saut vers un autre fichier, remise en page de la WebView ------------------------

    private fun TestScope.engine(relayoutShift: Double): FakeReflowEngine {
        val engine = FakeReflowEngine(backgroundScope, files = 4, relayoutShift = relayoutShift)
        engine.controller = ReflowableReaderController(
            scope = backgroundScope,
            readChapterHtml = { null },
            onCenterTap = {},
            uptimeMs = { testScheduler.currentTime },
            wallClockMs = { testScheduler.currentTime },
        ).apply {
            viewportHeightPx = 2_000
            bind { engine.navigate(it) }
        }
        return engine
    }

    /** Positions publiées par le contrôleur à partir de maintenant (progression dans le fichier, par fichier). */
    private fun TestScope.published(controller: ReflowableReaderController): List<Pair<String, Double?>> {
        val seen = mutableListOf<Pair<String, Double?>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.displayed.drop(1).filterNotNull().collect { seen += it.href.toString() to it.locations.progression }
        }
        return seen
    }

    @Test
    fun jumpIntoAnotherFileIsRealignedOnceAfterRelayoutAndOnlyTheFinalPositionIsPublished() = runTest {
        val e = engine(relayoutShift = 0.0201) // F1 : 2,75 écrans plus haut
        e.open(file = 0, progression = 0.0162)
        val seen = published(e.controller)

        val job = launch { e.controller.go(e.at(3, 0.8759)) }
        advanceTimeBy(ReaderGestures.JUMP_SETTLE_QUIET_MS - 1)
        runCurrent()
        // Pendant la remise en page : rien n’est publié (ni le début du fichier, ni la position décalée).
        assertThat(seen).isEmpty()
        advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
        job.join()

        assertThat(e.navigated.map { it.progression?.value }).containsExactly(0.8759, 0.8759).inOrder()
        assertThat(seen).containsExactly("f3.xhtml" to 0.8759)
        assertThat(e.controller.displayed.value!!.locations.progression).isEqualTo(0.8759)
    }

    @Test
    fun jumpIntoAnotherFileThatStaysPutIsNotRepeated() = runTest {
        val e = engine(relayoutShift = 0.0)
        e.open(file = 0, progression = 0.1)
        val seen = published(e.controller)

        e.controller.go(e.at(3, 0.8759))

        assertThat(e.navigated).hasSize(1)
        assertThat(seen).containsExactly("f3.xhtml" to 0.8759)
    }

    @Test
    fun jumpWithinTheSameFileIsNeitherHeldNorRepeated() = runTest {
        val e = engine(relayoutShift = 0.0)
        e.open(file = 3, progression = 0.5)
        val seen = published(e.controller)
        val before = testScheduler.currentTime

        e.controller.go(e.at(3, 0.8759))

        assertThat(testScheduler.currentTime).isEqualTo(before)
        assertThat(e.navigated).hasSize(1)
        assertThat(seen).containsExactly("f3.xhtml" to 0.8759)
    }

    @Test
    fun anchoredTargetIntoAnotherFileIsNeverHeldNorRealigned() = runTest {
        val e = engine(relayoutShift = 0.0201)
        e.open(file = 3, progression = 0.8759)
        val seen = published(e.controller)
        val anchored = Locator(
            href = Url("f0.xhtml#id00012")!!,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(totalProgression = 0.003),
        )

        e.controller.go(anchored)
        advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
        runCurrent()

        assertThat(e.navigated).hasSize(1)
        assertThat(seen.first()).isEqualTo("f0.xhtml" to 0.0)
    }

    @Test
    fun dragDuringTheHoldPublishesWhatIsShownAndCancelsTheRealignment() = runTest {
        val e = engine(relayoutShift = 0.0201)
        e.open(file = 0, progression = 0.0162)
        val seen = published(e.controller)

        val job = launch { e.controller.go(e.at(3, 0.8759)) }
        advanceTimeBy(100) // remise en page faite, page pas encore déclarée stable
        runCurrent()
        e.controller.onPointerDown()
        e.controller.onDragStarted()
        runCurrent()

        assertThat(seen.last().second!!).isWithin(1e-9).of(0.8759 - 0.0201)
        advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
        job.join()
        assertThat(e.navigated).hasSize(1)
    }

    @Test
    fun tapDuringTheHoldSuspendsItAndTheJumpIsStillRealignedExactly() = runTest {
        val e = engine(relayoutShift = 0.0073) // F2 : un écran plus haut
        e.open(file = 0, progression = 0.0162)
        val seen = published(e.controller)

        val job = launch { e.controller.go(e.at(3, 0.8759)) }
        advanceTimeBy(100) // remise en page faite
        runCurrent()
        // Tap au centre (barre) : appui, puis lever 80 ms plus tard, sans glissé.
        e.controller.onPointerDown()
        advanceTimeBy(ReaderGestures.JUMP_SETTLE_QUIET_MS) // doigt posé : la page n’est pas déclarée stable
        runCurrent()
        assertThat(seen).isEmpty()
        assertThat(e.navigated).hasSize(1)
        e.controller.onPointerUp()
        e.controller.onTapLikeGesture(xFraction = 0.5f)
        advanceTimeBy(ReaderGestures.JUMP_SETTLE_QUIET_MS - 1) // compté depuis le lever
        runCurrent()
        assertThat(e.navigated).hasSize(1)
        advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
        job.join()

        assertThat(e.navigated).hasSize(2)
        assertThat(seen).containsExactly("f3.xhtml" to 0.8759)
    }

    @Test
    fun holdEndsAtTheSettleCapEvenIfThePageNeverStopsMoving() = runTest {
        val e = engine(relayoutShift = 0.0)
        e.open(file = 0, progression = 0.1)
        val seen = published(e.controller)
        // Page qui bouge sans cesse (toutes les 100 ms, sous le délai de stabilité) après le saut.
        val restless = launch {
            var step = 0
            while (true) {
                delay(100)
                e.controller.onDisplayed(e.at(3, 0.5 + ++step * 1e-3))
            }
        }

        val job = launch { e.controller.go(e.at(3, 0.8759)) }
        advanceTimeBy(ReaderGestures.SETTLE_MAX_MS - 1)
        runCurrent()
        assertThat(seen).isEmpty()
        assertThat(e.navigated).hasSize(1)
        advanceTimeBy(2)
        runCurrent()
        // Plafond atteint : recalage (la page s’est écartée), puis second plafond au plus.
        assertThat(e.navigated).hasSize(2)
        advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
        runCurrent()
        assertThat(job.isCompleted).isTrue()
        assertThat(seen).hasSize(1)
        restless.cancel()
    }
}
