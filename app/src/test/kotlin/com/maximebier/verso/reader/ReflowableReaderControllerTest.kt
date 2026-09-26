@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.readium.ReadingOrderPositions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.navigator.common.CssSelector
import org.readium.navigator.common.HtmlId
import org.readium.navigator.common.Position
import org.readium.navigator.common.Progression
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.navigator.web.reflowable.resource.ReflowableWebViewport
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
    fun topOfViewportKeepsLocationInsideAChapter() {
        val location = at("ch1.xhtml", 0.4, total = 0.2)
        val viewport = viewport("ch1.xhtml" to (0.4..0.45))

        assertThat(topOfViewport(location, viewport)).isEqualTo(location)
    }

    @Test
    fun topOfViewportStaysOnThePreviousChapterAtABoundary() {
        // `location` a déjà basculé au début de ch2 alors que le haut de l’écran montre la fin de ch1.
        val location = at("ch2.xhtml", 0.0, total = 0.5).copy(title = "II")
        val viewport = viewport("ch1.xhtml" to (0.97..1.0), "ch2.xhtml" to (0.0..0.02))

        val top = topOfViewport(location, viewport)

        assertThat(top.href.toString()).isEqualTo("ch1.xhtml")
        assertThat(top.locations.progression).isEqualTo(0.97)
        assertThat(top.locations.position).isNull()
        assertThat(top.locations.totalProgression).isEqualTo(0.5)
        assertThat(top.title).isNull()
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

    private fun viewport(vararg pages: Pair<String, ClosedFloatingPointRange<Double>>): ReflowableWebViewport =
        ReflowableWebViewport(
            readingOrder = pages.map { Url(it.first)!! },
            progressions = pages.associate { (href, range) ->
                Url(href)!! to (Progression(range.start)!!..Progression(range.endInclusive)!!)
            },
            positions = Position(1)!!..Position(2)!!,
        )
}
