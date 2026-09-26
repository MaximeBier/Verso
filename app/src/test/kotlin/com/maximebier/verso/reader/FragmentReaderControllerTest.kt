@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.readium.ReadingOrderPositions
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

// Robolectric : Locator et Url s’appuient sur org.json et android.net.Uri.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FragmentReaderControllerTest {

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
        val navigated = mutableListOf<Locator>()
        var edges: ChapterEdges? = null
        var visible: String? = null
        var adjacent: Locator? = null
        val controller = FragmentReaderController(
            scope = scope.backgroundScope,
            readChapterHtml = { "<html><body><p>un deux trois quatre cinq six sept huit neuf dix</p></body></html>" },
            onCenterTap = { centerTaps++ },
            adjacentChapter = { _, _ -> adjacent },
            thresholds = ReadingThresholds(flingScreensPerSecond = 1.0),
            uptimeMs = { scope.testScheduler.currentTime },
            wallClockMs = { scope.testScheduler.currentTime },
        ).apply {
            viewportHeightPx = 2_000
            bind(navigate = { navigated += it }, probeEdges = { edges }, visibleText = { visible })
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
            h.controller.onGestureReleased(velocityYPxPerSecond = -500f, dragDyPx = 0f)
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
            h.controller.onGestureReleased(velocityYPxPerSecond = -2_500f, dragDyPx = 0f) // 1,25 écran/s
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            assertThat(awaitItem().isFling).isTrue()

            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = 1_500f, dragDyPx = 0f) // 0,75 écran/s
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            assertThat(awaitItem().isFling).isFalse()
        }
    }

    @Test
    fun releaseAtOneAndAHalfScreensPerSecondIsAFlingWithDefaultThresholds() = runTest {
        val controller = FragmentReaderController(
            scope = backgroundScope,
            readChapterHtml = { null },
            onCenterTap = {},
            adjacentChapter = { _, _ -> null },
            thresholds = ReadingThresholds(), // flingScreensPerSecond = 1,0 (vitesse au relâchement)
            uptimeMs = { testScheduler.currentTime },
            wallClockMs = { testScheduler.currentTime },
        ).apply { viewportHeightPx = 2_000 }
        controller.gestures.test {
            controller.onPointerDown()
            controller.onGestureReleased(velocityYPxPerSecond = -3_000f, dragDyPx = 0f) // 1,5 écran/s
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            assertThat(awaitItem().isFling).isTrue()
        }
    }

    @Test
    fun aNewTouchEndsThePreviousScrollAndEmitsItsSignalAtOnce() = runTest {
        val h = Harness(this)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -4_000f, dragDyPx = 0f)
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
            assertThat(h.navigated.single().locations.progression).isEqualTo(0.4)

            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = 0f)
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

    /** Répartiteur qui compte ce qu’on lui confie (revue finale M8). */
    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    @Test
    fun chapterPlainTextIsComputedOnTheTextDispatcherNotTheCaller() = runTest {
        // Revue finale M8 : 4 regex DOTALL sur tout un chapitre, jamais sur le fil principal.
        val text = CountingDispatcher(StandardTestDispatcher(testScheduler))
        val controller = FragmentReaderController(
            scope = backgroundScope,
            readChapterHtml = { "<html><body><p>un deux trois quatre cinq</p></body></html>" },
            onCenterTap = {},
            adjacentChapter = { _, _ -> null },
            uptimeMs = { testScheduler.currentTime },
            wallClockMs = { testScheduler.currentTime },
            textDispatcher = text,
        )
        controller.onDisplayed(at("ch1.xhtml", 0.0))

        val excerpt = controller.excerptLocator()!!

        assertThat(excerpt.text.after).startsWith("un deux trois")
        assertThat(text.dispatches).isAtLeast(1)
    }


    // ---------- Changement de chapitre ----------

    @Test
    fun dragStartedAtTheBottomEdgeOpensTheNextChapterAndSignalsAChapterTurn() = runTest {
        val h = Harness(this)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.adjacent = at("ch2.xhtml", 0.0)
        h.controller.onDisplayed(at("ch1.xhtml", 0.97))
        advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            runCurrent()
            h.controller.onGestureReleased(velocityYPxPerSecond = -5_000f, dragDyPx = -h.controller.chainThresholdPx)
            runCurrent()
            assertThat(h.navigated.single().href.toString()).isEqualTo("ch2.xhtml")
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            val signal = awaitItem()
            assertThat(signal.chapterTurn).isTrue()
            assertThat(signal.isFling).isFalse()
        }
    }

    @Test
    fun flingThatReachesTheEdgeWithoutStartingThereStaysInTheChapter() = runTest {
        val h = Harness(this)
        h.edges = ChapterEdges(atTop = false, atBottom = false)
        h.adjacent = at("ch2.xhtml", 0.0)
        h.controller.onDisplayed(at("ch1.xhtml", 0.5))
        advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            runCurrent()
            h.controller.onGestureReleased(velocityYPxPerSecond = -8_000f, dragDyPx = -600f)
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            val signal = awaitItem()
            assertThat(signal.chapterTurn).isFalse()
            assertThat(signal.isFling).isTrue()
            assertThat(h.navigated).isEmpty()
        }
    }

    @Test
    fun lastChapterEdgeDoesNothingAndSendsAnOrdinarySignal() = runTest {
        val h = Harness(this)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.adjacent = null
        h.controller.onDisplayed(at("ch2.xhtml", 0.97))
        advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            runCurrent()
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = -h.controller.chainThresholdPx * 2)
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            assertThat(awaitItem().chapterTurn).isFalse()
            assertThat(h.navigated).isEmpty()
        }
    }

    @Test
    fun touchThatStopsAScrollNeverTurnsTheChapter() = runTest {
        val h = Harness(this)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.adjacent = at("ch2.xhtml", 0.0)
        h.controller.onDisplayed(at("ch1.xhtml", 0.97))
        advanceTimeBy(50) // défilement encore en cours à l’appui
        h.controller.onPointerDown()
        runCurrent()
        h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = -h.controller.chainThresholdPx * 2)
        advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
        assertThat(h.navigated).isEmpty()
    }

    @Test
    fun visibleTextIsTheExcerptWhenTheNavigatorGivesIt() = runTest {
        val h = Harness(this)
        h.visible = "  Le texte   visible\nen haut  "
        h.controller.onDisplayed(at("ch1.xhtml", 0.4))

        assertThat(h.controller.excerptLocator()!!.text.after).isEqualTo("Le texte visible en haut")
    }

    @Test
    fun edgesAreParsedFromTheWebViewAnswer() {
        assertThat(edgesOf("[true,false]")).isEqualTo(ChapterEdges(atTop = true, atBottom = false))
        assertThat(edgesOf("\"[false, true]\"")).isEqualTo(ChapterEdges(atTop = false, atBottom = true))
        assertThat(edgesOf(null)).isNull()
        assertThat(edgesOf("null")).isNull()
    }
}
