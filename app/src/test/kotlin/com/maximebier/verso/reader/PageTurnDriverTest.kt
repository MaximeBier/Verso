package com.maximebier.verso.reader

import android.graphics.Bitmap
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

// Robolectric : Locator s’appuie sur org.json et android.net.Uri, les photos sont des Bitmap.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PageTurnDriverTest {

    /** Horloge d’images au temps virtuel du test (les ressorts d’Animatable en demandent une). */
    private class TestFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private val page = Locator(href = Url("ch1.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(progression = 0.5))
    private val touch = Offset(WIDTH - 50f, 500f)

    private fun photo(): Bitmap = createBitmap(WIDTH.toInt(), 800)

    /**
     * Pilote de test. [web] : photos de la WebView par écart de page (null : hors du fichier) ; [screen] : photo de
     * l’écran rendue aussitôt, ou gardée dans [pendingScreen] si null (rendue plus tard par le test).
     */
    private inner class Harness(
        scope: TestScope,
        val web: (Int) -> Bitmap? = { photo() },
        val screen: Bitmap? = null,
        canLeaveChapter: Boolean = true,
    ) {
        val turns = mutableListOf<Boolean>()
        val webCalls = mutableListOf<Pair<Int, Bitmap?>>()
        val displayed = MutableStateFlow<Locator?>(page)
        var pendingScreen: ((Bitmap?) -> Unit)? = null
        val driver = PageTurnDriver(
            scope = CoroutineScope(scope.backgroundScope.coroutineContext + TestFrameClock(scope.testScheduler)),
            captureScreen = { onResult -> if (screen != null) onResult(screen) else pendingScreen = onResult },
            captureWeb = { pages, shot -> webCalls += pages to shot; web(pages) },
            turn = { forward -> turns += forward },
            displayed = displayed,
            canLeaveChapter = { canLeaveChapter },
        ).apply { width = WIDTH }
    }

    @After
    fun tearDown() {
        PageTurnState.turning = false
    }

    @Test
    fun atTheEndOfTheBookThePageComesBackFlatAtOnceWithoutTurning() = runTest {
        // Dernière page : ni page suivante dans le fichier, ni chapitre suivant.
        val h = Harness(this, web = { pages -> if (pages == 0) photo() else null }, canLeaveChapter = false)
        assertThat(h.driver.start(forward = true, touch = touch)).isEqualTo(1)
        runCurrent()
        val a = h.driver.anim!!
        assertThat(a.turnedEarly).isTrue()
        h.driver.drag(Offset(100f, 500f))
        runCurrent()
        // Même un élan franc dans le sens du tour ne fait rien tourner : la page revient.
        h.driver.release(Offset(-3000f, 0f))
        // Sans attendre la page suivante (qui n’arrivera pas) : bien avant le délai du tour anticipé.
        advanceTimeBy(PageTurnDriver.EARLY_TURN_TIMEOUT_MS / 2)
        runCurrent()
        assertThat(h.driver.anim).isNull()
        assertThat(h.turns).isEmpty()
        assertThat(a.edgeX.value).isWithin(3f).of(a.grab.x)
    }

    @Test
    fun atTheStartOfTheBookTheBackwardTurnDoesNothingEither() = runTest {
        val h = Harness(this, web = { pages -> if (pages == 0) photo() else null }, canLeaveChapter = false)
        assertThat(h.driver.start(forward = false, touch = Offset(50f, 500f))).isEqualTo(1)
        h.driver.release(Offset(3000f, 0f))
        // Le pilote tourne dans backgroundScope : on avance le temps virtuel (advanceUntilIdle ne l’avancerait pas).
        advanceTimeBy(PageTurnDriver.EARLY_TURN_TIMEOUT_MS / 2)
        runCurrent()
        assertThat(h.turns).isEmpty()
        assertThat(h.driver.anim).isNull()
    }

    @Test
    fun atAChapterEdgeTheNextChapterTurnsUnderThePageFromTheStart() = runTest {
        val h = Harness(this, web = { pages -> if (pages == 0) photo() else null }, canLeaveChapter = true)
        h.driver.start(forward = true, touch = touch)
        runCurrent()
        assertThat(h.turns).containsExactly(true)
    }

    @Test
    fun withoutPhotosOfTheWebViewThePageStillTurnsUnderneath() = runTest {
        // Photos impossibles (web null) : le bout du livre ne peut pas être reconnu, on tourne comme ailleurs.
        val h = Harness(this, web = { null }, screen = photo(), canLeaveChapter = false)
        h.driver.onPointerDown()
        assertThat(h.driver.start(forward = true, touch = touch)).isEqualTo(1)
        runCurrent()
        assertThat(h.turns).containsExactly(true)
    }

    @Test
    fun aPrefetchedPhotoIsKeptForTheNextTurn() = runTest {
        val h = Harness(this)
        h.driver.prefetch()
        val shot = photo()
        h.pendingScreen!!(shot)
        assertThat(h.webCalls.map { it.first }).containsExactly(1, -1)
        h.webCalls.clear()
        h.driver.start(forward = true, touch = touch)
        // Page voisine reprise des photos d’avance ; chapitre du pied de page repris de la photo de l’écran.
        assertThat(h.webCalls).containsExactly(0 to shot)
    }

    @Test
    fun aPhotoArrivingAfterInvalidateIsDropped() = runTest {
        val h = Harness(this)
        h.driver.prefetch()
        // Mise en page changée pendant que la photo de l’écran se fait.
        h.driver.invalidate()
        h.pendingScreen!!(photo())
        assertThat(h.webCalls).isEmpty()
        h.driver.start(forward = true, touch = touch)
        // Ni photo de l’écran ni page voisine gardées : tout est repris sur le moment.
        assertThat(h.webCalls).containsExactly(0 to null, 1 to null).inOrder()
    }

    @Test
    fun aScreenPhotoAskedOnTouchIsDroppedAfterInvalidate() = runTest {
        val h = Harness(this)
        h.driver.onPointerDown()
        h.driver.invalidate()
        h.pendingScreen!!(photo())
        h.driver.start(forward = true, touch = touch)
        assertThat(h.webCalls.first()).isEqualTo(0 to null)
    }

    @Test
    fun theScreenIsNotPhotographedAgainWhileThePageKeepsItsPhoto() = runTest {
        val h = Harness(this)
        h.driver.onPointerDown()
        h.pendingScreen!!(photo())
        h.pendingScreen = null
        h.driver.onPointerDown()
        assertThat(h.pendingScreen).isNull()
    }

    @Test
    fun anEarlyChapterTurnIsUndoneWhenTheGestureIsCancelled() = runTest {
        val h = Harness(this, web = { pages -> if (pages == 0) photo() else null }, canLeaveChapter = true)
        h.driver.start(forward = true, touch = touch)
        runCurrent()
        // Le chapitre suivant arrive sous l’animation.
        h.displayed.value = Locator(href = Url("ch2.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(progression = 0.0))
        advanceTimeBy(PageTurnDriver.RENDER_DELAY_MS + 1)
        runCurrent()
        val a = h.driver.anim!!
        assertThat(a.earlyTurnDone).isTrue()
        // Lâché sans chemin ni élan : la page revient, le lecteur revient au chapitre d’avant.
        h.driver.release(Offset.Zero)
        advanceTimeBy(PageTurnDriver.EARLY_TURN_TIMEOUT_MS)
        runCurrent()
        assertThat(h.turns).containsExactly(true, false).inOrder()
        assertThat(h.driver.anim).isNull()
    }

    @Test
    fun anEarlyChapterTurnIsKeptWhenTheGestureCommits() = runTest {
        val h = Harness(this, web = { pages -> if (pages == 0) photo() else null }, canLeaveChapter = true)
        h.driver.start(forward = true, touch = touch)
        runCurrent()
        h.displayed.value = Locator(href = Url("ch2.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(progression = 0.0))
        advanceTimeBy(PageTurnDriver.RENDER_DELAY_MS + 1)
        h.driver.drag(Offset(100f, 500f))
        h.driver.release(Offset(-3000f, 0f))
        advanceTimeBy(PageTurnDriver.EARLY_TURN_TIMEOUT_MS)
        runCurrent()
        assertThat(h.turns).containsExactly(true)
        assertThat(h.driver.anim).isNull()
    }

    @Test
    fun whenTheNextChapterNeverArrivesThePageComesBackAfterTheTimeout() = runTest {
        val h = Harness(this, web = { pages -> if (pages == 0) photo() else null }, canLeaveChapter = true)
        h.driver.start(forward = true, touch = touch)
        h.driver.drag(Offset(100f, 500f))
        h.driver.release(Offset(-3000f, 0f))
        // Le lâcher attend le tour anticipé, au plus EARLY_TURN_TIMEOUT_MS.
        advanceTimeBy(PageTurnDriver.EARLY_TURN_TIMEOUT_MS - 1)
        runCurrent()
        assertThat(h.driver.anim).isNotNull()
        advanceTimeBy(PageTurnDriver.EARLY_TURN_TIMEOUT_MS)
        runCurrent()
        assertThat(h.driver.anim).isNull()
        // Rien n’a tourné à l’écran : pas de tour en sens inverse.
        assertThat(h.turns).containsExactly(true)
    }

    @Test
    fun resetDropsTheTurnAndShowsTheRealFooterAgain() = runTest {
        val h = Harness(this)
        h.driver.start(forward = true, touch = touch)
        assertThat(PageTurnState.turning).isTrue()
        h.driver.reset()
        assertThat(h.driver.anim).isNull()
        assertThat(PageTurnState.turning).isFalse()
    }

    private companion object {
        const val WIDTH = 1000f
        const val FRAME_MS = 16L
    }
}
