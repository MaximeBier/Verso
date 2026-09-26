@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ProgressionScreenDistance
import com.maximebier.verso.core.position.ReaderEvent
import com.maximebier.verso.core.position.ReadingPositionTracker
import com.maximebier.verso.readium.ReadingOrderPositions
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.navigator.common.Position
import org.readium.navigator.common.Progression
import org.readium.navigator.web.reflowable.resource.ReflowableWebViewport
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Haut réel de l’écran aux frontières de fichier (défaut B de la vérification sur appareil).
 * Les valeurs viennent du journal capturé sur le téléphone (Madame Bovary, frontière `…-0-2` / `…-0-3`,
 * début de III-VI) : fenêtres de WebView, `location` et décalages du pager.
 */
// Robolectric : Locator et Url s’appuient sur org.json et android.net.Uri.
@RunWith(AndroidJUnit4::class)
class ScreenTopTest {

    // Poids des fichiers tirés du journal : total(p2, 0,99422816) = 0,7778164 et total(p3, 0) = 0,7792642.
    private val positions = ReadingOrderPositions.from(
        hrefs = listOf("p01.xhtml", "p2.xhtml", "p3.xhtml"),
        counts = listOf(52_842, 25_084, 22_074),
    )!!

    /** Madame Bovary : environ 650 écrans (vérification sur appareil). */
    private val bookScreens = 650.0
    private val distance = ProgressionScreenDistance(bookScreens)

    private fun at(href: String, progression: Double, total: Double? = null) = Locator(
        href = Url(href)!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(progression = progression, totalProgression = total),
    )

    private fun viewport(vararg pages: Pair<String, ClosedFloatingPointRange<Double>>): ReflowableWebViewport =
        ReflowableWebViewport(
            readingOrder = pages.map { Url(it.first)!! },
            progressions = pages.associate { (href, range) ->
                Url(href)!! to (Progression(range.start)!!..Progression(range.endInclusive)!!)
            },
            positions = Position(1)!!..Position(2)!!,
        )

    private fun pager(href: String, hiddenFraction: Double) = PagerTop(Url(href)!!, hiddenFraction)

    private fun controller(clock: () -> Long = { 0L }) = ReflowableReaderController(
        scope = CoroutineScope(Dispatchers.Unconfined),
        readChapterHtml = { null },
        onCenterTap = {},
        uptimeMs = clock,
        wallClockMs = clock,
    ).apply {
        viewportHeightPx = 2_400
        setPositions(positions)
    }

    /** Une frame du navigateur, horodatée (ms). */
    private class Frame(
        val timeMs: Long,
        val location: Locator,
        val viewport: ReflowableWebViewport,
        val pagerTop: PagerTop?,
    )

    // Remontée lente (0,31 écran/s) depuis le début de p3 : la fin de p2 entre par le haut, calée sur sa fin
    // (fenêtre 0,9942..1,0) ; pendant le passage, seul le pager bouge et Readium ne recalcule rien.
    private val backwardFrames: List<Frame>
        get() {
            val p3Top = viewport("p3.xhtml" to (0.0..0.0072))
            val both = viewport("p2.xhtml" to (0.99422816..1.0), "p3.xhtml" to (0.0..0.0072))
            val locP3 = at("p3.xhtml", 0.0, total = 0.7792642)
            val locP2 = at("p2.xhtml", 0.99422816, total = 0.7759197)
            return listOf(
                Frame(0, at("p3.xhtml", 0.0013, total = 0.7792642), viewport("p3.xhtml" to (0.0013..0.0085)), pager("p3.xhtml", 0.0)),
                Frame(280, at("p3.xhtml", 0.0006, total = 0.7792642), viewport("p3.xhtml" to (0.0006..0.0079)), pager("p3.xhtml", 0.0)),
                Frame(564, locP3, p3Top, pager("p3.xhtml", 0.0)),
                // Le pager a déjà fait entrer p2 mais le viewport ne le connaît pas encore.
                Frame(580, locP3, p3Top, pager("p2.xhtml", 0.99)),
                // Frame fautive du journal (t + 198 ms) : viewport = [p2, p3], haut de la WebView de p2 à 0,9942.
                Frame(762, locP3, both, pager("p2.xhtml", 0.98)),
                Frame(1_100, locP3, both, pager("p2.xhtml", 0.88)),
                Frame(2_400, locP3, both, pager("p2.xhtml", 0.70)),
                // `location` bascule sur p2 au milieu du passage (page la plus visible).
                Frame(3_600, locP2, both, pager("p2.xhtml", 0.45)),
                Frame(4_300, locP2, both, pager("p2.xhtml", 0.25)),
                Frame(5_000, locP2, both, pager("p2.xhtml", 0.10)),
                Frame(5_600, locP2, viewport("p2.xhtml" to (0.99422816..1.0)), pager("p2.xhtml", 0.0)),
                // p3 est sorti : la WebView de p2 défile à son tour.
                Frame(5_900, at("p2.xhtml", 0.9935), viewport("p2.xhtml" to (0.9935..0.9993)), pager("p2.xhtml", 0.0)),
            )
        }

    private fun displayedTotals(frames: List<Frame>): List<Pair<Long, Double>> {
        var now = 0L
        val controller = controller { now }
        return frames.map { frame ->
            now = frame.timeMs
            controller.onViewport(frame.location, frame.viewport, frame.pagerTop)
            frame.timeMs to controller.displayed.value!!.locations.totalProgression!!
        }
    }

    @Test
    fun backwardAcrossFileBoundaryIsMonotonicAndContinuous() {
        val totals = displayedTotals(backwardFrames).map { it.second }

        totals.zipWithNext().forEach { (before, after) ->
            assertThat(after).isAtMost(before)
            // Jamais plus d’un quart d’écran entre deux frames (0,25 écran = 600 px en 800 ms).
            assertThat((before - after) * bookScreens).isLessThan(0.3)
        }
        // Le passage entier ne vaut qu’un écran environ (hauteur de la page de p2 entrée par le haut).
        assertThat((totals.first() - totals.last()) * bookScreens).isWithin(0.3).of(1.3)
    }

    @Test
    fun forwardAcrossFileBoundaryIsMonotonicAndContinuous() {
        val both = viewport("p2.xhtml" to (0.99422816..1.0), "p3.xhtml" to (0.0..0.0073))
        val locP2 = at("p2.xhtml", 0.99422816, total = 0.7759197)
        val locP3 = at("p3.xhtml", 0.0, total = 0.7792642)
        val frames = listOf(
            Frame(0, locP2, viewport("p2.xhtml" to (0.99422816..1.0)), pager("p2.xhtml", 0.0)),
            Frame(400, locP2, both, pager("p2.xhtml", 0.12)),
            Frame(1_500, locP2, both, pager("p2.xhtml", 0.35)),
            Frame(2_600, locP3, both, pager("p2.xhtml", 0.58)),
            Frame(3_600, locP3, both, pager("p2.xhtml", 0.80)),
            Frame(4_500, locP3, both, pager("p2.xhtml", 0.998)),
            // Le viewport est encore [p2, p3] alors que le pager ne montre plus que p3.
            Frame(4_510, locP3, both, pager("p3.xhtml", 0.0)),
            Frame(4_520, at("p3.xhtml", 0.00004), viewport("p3.xhtml" to (0.00004..0.0073)), pager("p3.xhtml", 0.0)),
            Frame(4_700, at("p3.xhtml", 0.0005), viewport("p3.xhtml" to (0.0005..0.0078)), pager("p3.xhtml", 0.0)),
        )

        val totals = displayedTotals(frames).map { it.second }

        totals.zipWithNext().forEach { (before, after) ->
            assertThat(after).isAtLeast(before)
            assertThat((after - before) * bookScreens).isLessThan(0.3)
        }
    }

    @Test
    fun slowBackwardDragAcrossBoundaryDoesNotShowReturnCard() {
        val tracker = ReadingPositionTracker(BookPosition("{}", 0.7795209), distance)
        displayedTotals(backwardFrames).forEach { (timeMs, total) ->
            tracker.onEvent(ReaderEvent.Displayed(timeMs, BookPosition("{}", total)))
        }
        tracker.onEvent(ReaderEvent.Tick(8_000))

        assertThat(tracker.state.showReturnCard).isFalse()
    }

    @Test
    fun deviceSequenceBeforeTheFixShowedTheCard() {
        // Totaux affichés relevés sur le téléphone avant correction : le haut de la WebView de p2 (0,9942)
        // pris pour le haut de l’écran recule d’environ un écran en 198 ms.
        val tracker = ReadingPositionTracker(BookPosition("{}", 0.7795209), distance)
        listOf(0L to 0.7795209, 280L to 0.7794000, 564L to 0.7792642, 762L to 0.7778164).forEach { (timeMs, total) ->
            tracker.onEvent(ReaderEvent.Displayed(timeMs, BookPosition("{}", total)))
        }
        tracker.onEvent(ReaderEvent.Tick(2_000))

        assertThat(tracker.state.showReturnCard).isTrue()
        // La même frame, avec le décalage du pager, n’avance plus que d’une fraction d’écran.
        val fixed = displayedTotals(backwardFrames.take(5)).last().second
        assertThat(abs(0.7792642 - fixed) * bookScreens).isLessThan(0.05)
    }

    @Test
    fun keepsLocationInsideAChapter() {
        val location = at("ch1.xhtml", 0.4, total = 0.2)

        assertThat(screenTop(location, viewport("ch1.xhtml" to (0.4..0.45)), pager("ch1.xhtml", 0.0), null))
            .isEqualTo(location)
        assertThat(screenTop(location, viewport("ch1.xhtml" to (0.4..0.45)), null, null)).isEqualTo(location)
    }

    @Test
    fun placesTheTopOfTheScreenInsideTheFirstVisibleWebView() {
        // `location` a déjà basculé au début de ch2 alors que le haut de l’écran montre la fin de ch1.
        val location = at("ch2.xhtml", 0.0, total = 0.5).copy(title = "II")
        val viewport = viewport("ch1.xhtml" to (0.96..1.0), "ch2.xhtml" to (0.0..0.02))

        val top = screenTop(location, viewport, pager("ch1.xhtml", 0.75), previous = null)

        assertThat(top.href.toString()).isEqualTo("ch1.xhtml")
        assertThat(top.locations.progression!!).isWithin(1e-9).of(0.99)
        assertThat(top.locations.position).isNull()
        assertThat(top.locations.totalProgression).isEqualTo(0.5)
        assertThat(top.title).isNull()
    }

    @Test
    fun withoutPagerOffsetKeepsThePreviousPositionDuringACrossing() {
        val location = at("ch2.xhtml", 0.0, total = 0.5)
        val viewport = viewport("ch1.xhtml" to (0.96..1.0), "ch2.xhtml" to (0.0..0.02))
        val previous = at("ch2.xhtml", 0.0, total = 0.5)

        assertThat(screenTop(location, viewport, pagerTop = null, previous = previous)).isEqualTo(previous)
        // Sans position précédente visible : haut de la première WebView (comportement d’origine).
        assertThat(screenTop(location, viewport, pagerTop = null, previous = null).locations.progression)
            .isEqualTo(0.96)
    }
}
