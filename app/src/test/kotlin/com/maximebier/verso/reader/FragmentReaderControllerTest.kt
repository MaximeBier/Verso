@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
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
        var taps = 0
        val navigated = mutableListOf<Locator>()
        var edges: ChapterEdges? = null
        var visible: String? = null
        var adjacent: Locator? = null
        val turns = mutableListOf<Boolean>()
        var pageCount: Int? = null

        /** Pages où commencent les chapitres ancrés du fichier affiché (vide : un seul chapitre). */
        var anchors: List<AnchorPage> = emptyList()

        /** Page affichée lue dans la WebView ; null : la progression du locator sert. */
        var currentPage: Int? = null

        /** Réponse de Readium au tour de page (false : première page du livre, tour arrière sans effet). */
        var turnAccepted = true

        /** Durée d’un déplacement du navigateur (0 : immédiat). */
        var navigateDelayMs = 0L
        val controller = FragmentReaderController(
            scope = scope.backgroundScope,
            readChapterHtml = { "<html><body><p>un deux trois quatre cinq six sept huit neuf dix</p></body></html>" },
            onTap = { taps++ },
            adjacentChapter = { _, _ -> adjacent },
            thresholds = ReadingThresholds(flingScreensPerSecond = 1.0),
            uptimeMs = { scope.testScheduler.currentTime },
            wallClockMs = { scope.testScheduler.currentTime },
        ).apply {
            viewportHeightPx = 2_000
            bind(
                navigate = {
                    navigated += it
                    delay(navigateDelayMs)
                },
                probeEdges = { edges },
                visibleText = { visible },
                turnPage = { forward -> turns += forward; turnAccepted },
                pageLayout = { pageCount?.let { PageLayout(it, anchors, currentPage) } },
            )
        }
    }

    @Test
    fun submitPublishesTheLatestStyle() = runTest {
        val controller = Harness(this).controller
        assertThat(controller.style.value).isNull()
        controller.submit(ReadingSettings(fontSizeSp = 22), AppTheme.SEPIA, ScrollMode.CONTINUOUS)
        assertThat(controller.style.value)
            .isEqualTo(ReaderStyle(ReadingSettings(fontSizeSp = 22), AppTheme.SEPIA, ScrollMode.CONTINUOUS))
    }

    /** Laisse passer le délai de calme qui suit une position rapportée pendant une remise en page. */
    private fun TestScope.passRelayoutQuiet() {
        advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS + ReaderGestures.SETTLE_POLL_MS)
        runCurrent()
    }

    @Test
    fun relayoutBringsTheTextBackWithoutPublishingTheShiftedPositions() = runTest {
        val h = Harness(this)
        val anchor = at("ch1.xhtml", 0.40)
        h.controller.onDisplayed(anchor)
        val published = mutableListOf<Locator?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.controller.displayed.collect { published += it } }
        var applied = 0

        h.controller.relayout { applied++ }
        runCurrent()
        assertThat(applied).isEqualTo(1)
        // Readium garde le défilement en pixels : la nouvelle mise en page décale la progression.
        h.controller.onDisplayed(at("ch1.xhtml", 0.47))
        passRelayoutQuiet()
        assertThat(h.navigated).containsExactly(anchor)
        h.controller.onDisplayed(at("ch1.xhtml", 0.4002))
        passRelayoutQuiet()

        assertThat(published).containsExactly(anchor)
        assertThat(h.controller.displayed.value).isEqualTo(anchor)
        // Remise en page terminée : un vrai défilement est de nouveau publié.
        h.controller.onDisplayed(at("ch1.xhtml", 0.45))
        assertThat(h.controller.displayed.value).isEqualTo(at("ch1.xhtml", 0.45))
    }

    @Test
    fun rapidRelayoutsReturnToTheLocatorShownBeforeTheFirst() = runTest {
        val h = Harness(this)
        val anchor = at("ch1.xhtml", 0.40)
        h.controller.onDisplayed(anchor)
        var applied = 0

        h.controller.relayout { applied++ }
        runCurrent()
        h.controller.onDisplayed(at("ch1.xhtml", 0.43))
        h.controller.relayout { applied++ }
        runCurrent()
        h.controller.onDisplayed(at("ch1.xhtml", 0.46))
        passRelayoutQuiet()

        assertThat(applied).isEqualTo(2)
        assertThat(h.navigated).containsExactly(anchor)
        assertThat(h.controller.displayed.value).isEqualTo(anchor)
    }

    @Test
    fun relayoutWithoutReportedPositionStillReturnsAfterTheWait() = runTest {
        val h = Harness(this)
        val anchor = at("ch1.xhtml", 0.40)
        h.controller.onDisplayed(anchor)

        h.controller.relayout {}
        advanceTimeBy(ReaderGestures.RELAYOUT_REPORT_MAX_MS + ReaderGestures.SETTLE_POLL_MS)
        runCurrent()

        assertThat(h.navigated).containsExactly(anchor)
    }

    @Test
    fun relayoutBeforeAnyPositionOnlyApplies() = runTest {
        val h = Harness(this)
        var applied = 0

        h.controller.relayout { applied++ }
        advanceTimeBy(2 * ReaderGestures.RELAYOUT_REPORT_MAX_MS)
        runCurrent()

        assertThat(applied).isEqualTo(1)
        assertThat(h.navigated).isEmpty()
        h.controller.onDisplayed(at("ch1.xhtml", 0.2))
        assertThat(h.controller.displayed.value).isEqualTo(at("ch1.xhtml", 0.2))
    }

    @Test
    fun jumpDuringRelayoutWinsOverTheReturn() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.40))
        val target = at("ch2.xhtml", 0.0)

        h.controller.relayout {}
        runCurrent()
        h.controller.go(target)
        advanceTimeBy(2 * ReaderGestures.RELAYOUT_REPORT_MAX_MS)
        runCurrent()

        assertThat(h.navigated).containsExactly(target)
        h.controller.onDisplayed(target)
        assertThat(h.controller.displayed.value).isEqualTo(target)
    }

    @Test
    fun touchDuringRelayoutKeepsTheUsersScrollAndPublishesTheScreen() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.40))

        h.controller.relayout {}
        runCurrent()
        val shifted = at("ch1.xhtml", 0.47)
        h.controller.onDisplayed(shifted)
        // Feuille « Aa » sans voile : l’utilisateur fait défiler le texte aussitôt.
        h.controller.onPointerDown()
        advanceTimeBy(2 * ReaderGestures.RELAYOUT_REPORT_MAX_MS)
        runCurrent()

        assertThat(h.navigated).isEmpty()
        assertThat(h.controller.displayed.value).isEqualTo(shifted)
        h.controller.onDisplayed(at("ch1.xhtml", 0.52))
        assertThat(h.controller.displayed.value).isEqualTo(at("ch1.xhtml", 0.52))
    }

    @Test
    fun touchBeforeTheNewLayoutIsReportedLetsItsPositionThrough() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.40))

        h.controller.relayout {}
        runCurrent()
        h.controller.onPointerDown()
        h.controller.onDisplayed(at("ch1.xhtml", 0.47))
        advanceTimeBy(2 * ReaderGestures.RELAYOUT_REPORT_MAX_MS)
        runCurrent()

        assertThat(h.navigated).isEmpty()
        assertThat(h.controller.displayed.value).isEqualTo(at("ch1.xhtml", 0.47))
    }

    @Test
    fun jumpWhileReturningToTheLocatorWins() = runTest {
        val h = Harness(this)
        val anchor = at("ch1.xhtml", 0.40)
        h.controller.onDisplayed(anchor)
        h.navigateDelayMs = 500
        val target = at("ch2.xhtml", 0.0)

        h.controller.relayout {}
        runCurrent()
        h.controller.onDisplayed(at("ch1.xhtml", 0.47))
        passRelayoutQuiet()
        // Phase 2 : le retour au locator est en cours dans le navigateur quand le saut arrive.
        assertThat(h.navigated).containsExactly(anchor)
        h.controller.go(target)
        advanceTimeBy(2 * ReaderGestures.RELAYOUT_REPORT_MAX_MS)
        runCurrent()

        assertThat(h.navigated).containsExactly(anchor, target).inOrder()
        h.controller.onDisplayed(target)
        assertThat(h.controller.displayed.value).isEqualTo(target)
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

    /** Défilement natif de la WebView pendant `durationMs`, une image toutes les 16 ms. */
    private suspend fun TestScope.scrollFor(h: Harness, durationMs: Long) {
        var elapsed = 0L
        while (elapsed < durationMs) {
            h.controller.onScrolled()
            advanceTimeBy(16)
            elapsed += 16
        }
    }

    @Test
    fun flingSignalWaitsUntilTheInertiaEndsAndItsPositionArrives() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.1))
        advanceTimeBy(1_000)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -4_000f, dragDyPx = -300f)
            // Readium ne rapporte aucune position pendant l’inertie (anti-rebond de 100 ms).
            scrollFor(h, 1_200)
            expectNoEvents()
            advanceTimeBy(150)
            expectNoEvents()
            h.controller.onDisplayed(at("ch1.xhtml", 0.3))
            advanceTimeBy(ReaderGestures.SETTLE_POLL_MS * 2)
            assertThat(awaitItem().isFling).isTrue()
            assertThat(h.controller.displayed.value!!.locations.progression).isEqualTo(0.3)
        }
    }

    @Test
    fun slowDragSignalWaitsForItsLatePosition() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.1))
        advanceTimeBy(1_000)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            scrollFor(h, 400)
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = -200f)
            // Position rapportée 260 ms après le lâcher : le signal l’attend.
            advanceTimeBy(255)
            expectNoEvents()
            advanceTimeBy(5)
            h.controller.onDisplayed(at("ch1.xhtml", 0.12))
            advanceTimeBy(ReaderGestures.SETTLE_POLL_MS * 2)
            assertThat(awaitItem().isFling).isFalse()
        }
    }

    @Test
    fun signalIsSentEvenIfTheNavigatorNeverReportsTheNewPosition() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.1))
        advanceTimeBy(1_000)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            scrollFor(h, 100)
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = -20f)
            advanceTimeBy(ReaderGestures.POSITION_WAIT_MS + ReaderGestures.SETTLE_POLL_MS)
            assertThat(awaitItem().isFling).isFalse()
        }
    }

    @Test
    fun touchDuringFlingInertiaIsNotATap() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.1))
        advanceTimeBy(1_000)
        scrollFor(h, 300)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.taps).isEqualTo(0)
    }

    @Test
    fun dragRightAfterTheInertiaEndsCanTurnTheChapter() = runTest {
        val h = Harness(this)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.adjacent = at("ch2.xhtml", 0.0)
        scrollFor(h, 300)
        advanceTimeBy(120)
        h.controller.onDisplayed(at("ch1.xhtml", 0.99))
        advanceTimeBy(60) // 60 ms après la position, 180 ms après la dernière image : le défilement est fini
        h.controller.onPointerDown()
        runCurrent()
        h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = -h.controller.chainThresholdPx * 2)
        runCurrent()

        assertThat(h.navigated.single().href.toString()).isEqualTo("ch2.xhtml")
    }

    @Test
    fun touchBeforeTheNextChapterIsDisplayedDoesNotSendTheChapterTurnEarly() = runTest {
        val h = Harness(this)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.adjacent = at("ch2.xhtml", 0.0)
        h.controller.onDisplayed(at("ch1.xhtml", 0.97))
        advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            runCurrent()
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = -h.controller.chainThresholdPx)
            advanceTimeBy(100)
            h.controller.onPointerDown() // nouveau toucher avant l’affichage du chapitre suivant
            runCurrent()
            expectNoEvents()
            h.controller.onDisplayed(at("ch2.xhtml", 0.0))
            advanceTimeBy(ReaderGestures.SETTLE_POLL_MS * 2)
            val signal = awaitItem()
            assertThat(signal.chapterTurn).isTrue()
            assertThat(h.controller.displayed.value!!.href.toString()).isEqualTo("ch2.xhtml")
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
            onTap = {},
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
    fun goJumpsWithoutGestureSignalAndDropsAPendingOne() = runTest {
        val h = Harness(this)
        h.controller.gestures.test {
            h.controller.go(at("ch2.xhtml", 0.4))
            runCurrent()
            expectNoEvents()
            assertThat(h.navigated.single().locations.progression).isEqualTo(0.4)

            h.controller.onPointerDown()
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = 0f)
            // Le geste interrompu par le saut n’est pas signalé : il arriverait après le saut et le brouillerait.
            h.controller.go(at("ch1.xhtml", 0.0))
            runCurrent()
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            expectNoEvents()
        }
    }

    /** Un tap physique : vu par l’observateur de l’app au lâcher, puis (sauf s’il est absorbé) par Readium. */
    private fun Harness.tap(seenByReadium: Boolean = true) {
        controller.onPointerDown()
        controller.onTapLikeGesture()
        if (seenByReadium) controller.onReadiumTap()
    }

    @Test
    fun sameTapSeenByBothPathsTogglesOnce() = runTest {
        val h = Harness(this)
        h.tap()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.taps).isEqualTo(1)
    }

    @Test
    fun twoDistinctTaps300MsApartToggleTwice() = runTest {
        val h = Harness(this)
        h.tap()
        advanceTimeBy(300)
        h.tap()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.taps).isEqualTo(2)
    }

    @Test
    fun twoDistinctSwallowedTaps300MsApartToggleTwice() = runTest {
        val h = Harness(this)
        h.tap(seenByReadium = false)
        advanceTimeBy(300)
        h.tap(seenByReadium = false)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.taps).isEqualTo(2)
    }

    @Test
    fun readiumEchoArrivingAfterTheNextTouchIsNotCountedAgain() = runTest {
        // Taps rapprochés (téléphone, 2026-09-29) : Readium signale le premier après l’appui suivant.
        val h = Harness(this)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture()
        advanceTimeBy(150)
        h.controller.onPointerDown() // rattrape le premier tap
        assertThat(h.taps).isEqualTo(1)
        h.controller.onReadiumTap() // écho du premier, en retard : déjà compté
        h.controller.onTapLikeGesture()
        h.controller.onReadiumTap() // écho du second
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.taps).isEqualTo(2)
    }

    @Test
    fun lateEchoOfAFallbackTapDoesNotSwallowTheNextTap() = runTest {
        val h = Harness(this)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS + 1) // rattrapé par l’app
        h.controller.onPointerDown()
        h.controller.onReadiumTap() // écho du premier, après l’appui suivant
        h.controller.onTapLikeGesture()
        h.controller.onReadiumTap()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)

        assertThat(h.taps).isEqualTo(2)
    }

    @Test
    fun anEchoNeverSentDoesNotSwallowAReadiumTapMuchLater() = runTest {
        val h = Harness(this)
        h.tap(seenByReadium = false)
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS + 1)
        advanceTimeBy(ReaderGestures.TAP_ECHO_MAX_MS)
        // Appui long : l’app n’y voit pas un tap, Readium si.
        h.controller.onPointerDown()
        h.controller.onReadiumTap()

        assertThat(h.taps).isEqualTo(2)
    }

    @Test
    fun readiumTapTheAppDidNotSeeAsATapStillCounts() = runTest {
        val h = Harness(this)
        h.controller.onPointerDown()
        h.controller.onReadiumTap()

        assertThat(h.taps).isEqualTo(1)
    }

    @Test
    fun swallowedCenterTapIsRecoveredOnceWithoutDoubleToggle() = runTest {
        val h = Harness(this)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS - 1)
        assertThat(h.taps).isEqualTo(0)
        advanceTimeBy(2)
        assertThat(h.taps).isEqualTo(1)

        // Le tap de Readium arrive quand même, en retard : déjà traité.
        h.controller.onReadiumTap()
        assertThat(h.taps).isEqualTo(1)
    }

    @Test
    fun touchThatStopsAScrollOrFollowsALinkIsNotATap() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.3))
        advanceTimeBy(50)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)
        assertThat(h.taps).isEqualTo(0)

        h.controller.onPointerDown()
        h.controller.onTapLikeGesture()
        h.controller.onLinkActivated()
        advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 3)
        assertThat(h.taps).isEqualTo(0)
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
            onTap = {},
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
    fun chapterTurnSignalWaitsUntilTheSlowNextChapterIsDisplayed() = runTest {
        val h = Harness(this)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.adjacent = at("ch2.xhtml", 0.0)
        h.controller.onDisplayed(at("ch1.xhtml", 0.97))
        advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            runCurrent()
            h.controller.onGestureReleased(velocityYPxPerSecond = -300f, dragDyPx = -h.controller.chainThresholdPx)
            // Chapitre non préchargé : la WebView ne l’affiche que 800 ms après le saut.
            advanceTimeBy(800)
            expectNoEvents()
            h.controller.onDisplayed(at("ch2.xhtml", 0.0))
            advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS + ReaderGestures.SETTLE_POLL_MS)
            assertThat(awaitItem().chapterTurn).isTrue()
            assertThat(h.controller.displayed.value!!.href.toString()).isEqualTo("ch2.xhtml")
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

    @Test
    fun topTextIsDecodedFromTheWebViewAnswer() {
        assertThat(jsString("\"Emma descendit \\\"au\\\" jardin.\\n\"")).isEqualTo("Emma descendit \"au\" jardin.\n")
        assertThat(jsString("null")).isNull()
        assertThat(jsString(null)).isNull()
        assertThat(jsString("{oops")).isNull()
    }

    // ---------- Mode pages ----------

    @Test
    fun inPagesModeTapsToggleTheBarsAndNeverTurnPages() = runTest {
        val h = Harness(this)
        h.controller.setScrollMode(ScrollMode.PAGES)

        h.tap()
        h.tap()

        assertThat(h.turns).isEmpty()
        assertThat(h.taps).isEqualTo(2)
    }

    @Test
    fun inContinuousModeTurnDoesNothing() = runTest {
        val h = Harness(this)
        h.controller.turn(forward = true)
        assertThat(h.turns).isEmpty()
    }

    @Test
    fun pageTurnSignalsAReadingGestureOnceTheNewPageIsShown() = runTest {
        val h = Harness(this)
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.gestures.test {
            h.controller.turn(forward = true)
            advanceTimeBy(100)
            h.controller.onDisplayed(at("ch1.xhtml", 0.2))
            expectNoEvents()

            advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS + ReaderGestures.SETTLE_POLL_MS + 1)
            val signal = awaitItem()
            assertThat(signal.isFling).isFalse()
            assertThat(signal.chapterTurn).isFalse()
        }
    }

    @Test
    fun pageTurnSignalWaitsForALateNewPagePosition() = runTest {
        val h = Harness(this)
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.onDisplayed(at("ch1.xhtml", 0.9))
        h.controller.gestures.test {
            // Dernière page du chapitre : le suivant se charge, sa position arrive tard.
            h.controller.turn(forward = true)
            advanceTimeBy(ReaderGestures.POSITION_WAIT_MS + ReaderGestures.SETTLE_POLL_MS)
            runCurrent()
            expectNoEvents()

            h.controller.onDisplayed(at("ch2.xhtml", 0.0))
            advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS - ReaderGestures.SETTLE_POLL_MS)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(2 * ReaderGestures.SETTLE_POLL_MS + 1)
            val signal = awaitItem()
            assertThat(signal.isFling).isFalse()
            assertThat(signal.chapterTurn).isFalse()
        }
    }

    @Test
    fun backwardTurnOnTheFirstPageOfTheBookSendsNoSignal() = runTest {
        val h = Harness(this)
        h.turnAccepted = false
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.onDisplayed(at("ch1.xhtml", 0.0))
        h.controller.gestures.test {
            h.controller.turn(forward = false)
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS + ReaderGestures.SETTLE_POLL_MS)
            runCurrent()
            expectNoEvents()
        }
        assertThat(h.turns).containsExactly(false)
    }

    @Test
    fun touchThatAbandonsTheModeSwitchRelayoutStillCountsThePages() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.5))
        h.pageCount = 10
        h.controller.submit(ReadingSettings(), AppTheme.LIGHT, ScrollMode.PAGES)
        h.controller.relayout(settleMs = ReaderGestures.MODE_SWITCH_SETTLE_MS) {}
        runCurrent()

        // Toucher avant toute position rapportée : rien à publier, mais le compte doit suivre le mode pages.
        h.controller.onPointerDown()
        runCurrent()

        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 6, pageCount = 10))
    }

    @Test
    fun inPagesModeAFastSwipeIsNeitherAFlingNorAChapterTurn() = runTest {
        val h = Harness(this)
        h.adjacent = at("ch2.xhtml", 0.0)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            runCurrent()
            h.controller.onGestureReleased(velocityYPxPerSecond = -20_000f, dragDyPx = -400f)
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            val signal = awaitItem()
            assertThat(signal.isFling).isFalse()
            assertThat(signal.chapterTurn).isFalse()
        }
        assertThat(h.navigated).isEmpty()
    }

    @Test
    fun pageInfoCountsThePagesOfTheDisplayedChapterAndDisappearsInContinuousMode() = runTest {
        val h = Harness(this)
        h.pageCount = 9
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.onDisplayed(at("ch1.xhtml", 1.0 / 9))
        runCurrent()
        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 2, pageCount = 9))

        h.controller.setScrollMode(ScrollMode.CONTINUOUS)
        assertThat(h.controller.pageInfo.value).isNull()
    }

    @Test
    fun pageInfoCountsInsideTheChapterWhenTheFileHoldsSeveralChapters() = runTest {
        val h = Harness(this)
        // Une partie de 20 pages dans un seul fichier, chapitres aux pages 1, 5 et 12 (Madame Bovary de Gutenberg).
        h.pageCount = 20
        h.anchors = listOf(AnchorPage("c1", 1), AnchorPage("c2", 5), AnchorPage("c3", 12))
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.onDisplayed(at("partie2.xhtml", 6.0 / 20))
        runCurrent()
        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 3, pageCount = 7, chapterAnchor = "c2"))

        h.controller.onDisplayed(at("partie2.xhtml", 11.0 / 20))
        runCurrent()
        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 1, pageCount = 9, chapterAnchor = "c3"))
    }

    @Test
    fun pageReadInTheWebViewWinsOverAProgressionJustBelowThePageStart() = runTest {
        val h = Harness(this)
        h.pageCount = 19
        h.currentPage = 10
        h.controller.setScrollMode(ScrollMode.PAGES)
        // Progression de Readium un peu sous le début de la page 10 : elle donnerait la page 9.
        h.controller.onDisplayed(at("partie2.xhtml", 9.0 / 19 - 0.001))
        runCurrent()
        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 10, pageCount = 19))
    }

    @Test
    fun pageInfoIsRecountedOnceARelayoutHasBroughtTheTextBack() = runTest {
        val h = Harness(this)
        h.pageCount = 9
        h.controller.setScrollMode(ScrollMode.PAGES)
        val anchor = at("ch1.xhtml", 1.0 / 9)
        h.controller.onDisplayed(anchor)
        runCurrent()
        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 2, pageCount = 9))

        // Taille agrandie : le chapitre compte deux fois plus de pages, les positions rapportées ne sont pas publiées.
        h.pageCount = 18
        h.controller.relayout {}
        runCurrent()
        h.controller.onDisplayed(at("ch1.xhtml", 0.3))
        passRelayoutQuiet()
        h.controller.onDisplayed(anchor)
        passRelayoutQuiet()

        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 3, pageCount = 18))
    }

    @Test
    fun modeSwitchRelayoutWaitsForTheSettleDelayBeforeReturningToTheLocator() = runTest {
        val h = Harness(this)
        val anchor = at("ch1.xhtml", 0.40)
        h.controller.onDisplayed(anchor)

        h.controller.relayout(settleMs = ReaderGestures.MODE_SWITCH_SETTLE_MS) {}
        runCurrent()
        h.controller.onDisplayed(at("ch1.xhtml", 0.05))
        advanceTimeBy(ReaderGestures.MODE_SWITCH_SETTLE_MS - 1)
        runCurrent()
        assertThat(h.navigated).isEmpty()

        advanceTimeBy(ReaderGestures.SETTLE_POLL_MS)
        runCurrent()
        assertThat(h.navigated).containsExactly(anchor)
        assertThat(h.controller.displayed.value).isEqualTo(anchor)
    }

    @Test
    fun switchingToPagesCountsThePagesOnlyOnceTheRelayoutIsDone() = runTest {
        val h = Harness(this)
        val anchor = at("ch1.xhtml", 0.5)
        h.controller.onDisplayed(anchor)
        h.pageCount = 1

        // La surface soumet les préférences du mode pages dans une remise en page (changesLayout).
        h.controller.submit(ReadingSettings(), AppTheme.LIGHT, ScrollMode.PAGES)
        runCurrent()
        assertThat(h.controller.pageInfo.value).isNull()

        h.controller.relayout(settleMs = ReaderGestures.MODE_SWITCH_SETTLE_MS) { h.pageCount = 10 }
        runCurrent()
        h.controller.onDisplayed(at("ch1.xhtml", 0.0))
        advanceTimeBy(ReaderGestures.MODE_SWITCH_SETTLE_MS)
        passRelayoutQuiet()
        h.controller.onDisplayed(anchor)
        passRelayoutQuiet()

        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 6, pageCount = 10))
    }

    @Test
    fun unreadablePageCountLeavesPageInfoEmpty() = runTest {
        val h = Harness(this)
        h.pageCount = null
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.onDisplayed(at("ch1.xhtml", 0.5))
        runCurrent()
        assertThat(h.controller.pageInfo.value).isNull()
    }
}
