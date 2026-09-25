@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.spike

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.abs
import kotlinx.collections.immutable.persistentListOf
import org.readium.navigator.common.InputListener
import org.readium.navigator.common.TapContext
import org.readium.navigator.common.TapEvent
import org.readium.navigator.web.common.FontFamilyDeclarations
import org.readium.navigator.web.common.FontStyle
import org.readium.navigator.web.reflowable.ReflowableWebConfiguration
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.navigator.web.reflowable.ReflowableWebRendition
import org.readium.navigator.web.reflowable.ReflowableWebRenditionFactory
import org.readium.navigator.web.reflowable.ReflowableWebRenditionState
import org.readium.navigator.web.reflowable.preferences.ReflowableWebPreferences
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.navigator.preferences.Color as ReadiumColor

/** Réglages V1 traduits pour le navigateur Compose (pas de publisherStyles ni de pageMargins ici : minMargins × 30 dp). */
fun spikeReflowablePreferences(): ReflowableWebPreferences = ReflowableWebPreferences(
    backgroundColor = ReadiumColor(0xFFF5F1E8.toInt()),
    textColor = ReadiumColor(0xFF1F1B16.toInt()),
    fontFamily = SpikeAtkinson,
    fontSize = 19.0 / 16.0,
    lineHeight = 1.6,
    paragraphSpacing = 0.5,
    minMargins = 0.8,
    textAlign = TextAlign.START,
    hyphens = false,
    scroll = true,
)

fun spikeReflowableConfiguration(): ReflowableWebConfiguration = ReflowableWebConfiguration(
    servedAssets = persistentListOf("fonts/.*"),
    fontFamilyDeclarations = FontFamilyDeclarations {
        addFontFamilyDeclaration(SpikeAtkinson) {
            addFontFace {
                addSource("fonts/atkinson_hyperlegible_next.ttf", preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(200..800)
            }
        }
    },
)

@Composable
fun VariantAReader(publication: Publication, book: SpikeBook, store: LocatorStore, log: SpikeLog) {
    val context = LocalContext.current
    val key = "A-${book.name}"
    val renditionState by produceState<ReflowableWebRenditionState?>(initialValue = null, publication) {
        val factory = ReflowableWebRenditionFactory(
            application = context.applicationContext as Application,
            publication = publication,
            configuration = spikeReflowableConfiguration(),
        ) ?: error("Publication refusée par ReflowableWebRenditionFactory")
        value = factory.createRenditionState(
            initialPreferences = spikeReflowablePreferences(),
            initialLocation = store.load(key)?.let { ReflowableWebGoLocation(it) },
        ).getOrElse { failure -> error(failure.message) }
    }
    val state = renditionState
    if (state == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }

    var overlayVisible by remember { mutableStateOf(true) }
    var phase by remember { mutableStateOf(SpikePhases.first()) }
    var info by remember { mutableStateOf("A · en attente du premier locator") }
    var viewportHeightPx by remember { mutableIntStateOf(1) }
    var lastLocator by remember { mutableStateOf<Locator?>(null) }
    val rolling = remember { RollingScreens() }

    val controller = state.controller
    LaunchedEffect(controller) {
        val readyController = controller ?: return@LaunchedEffect
        snapshotFlow { readyController.location }.collect { location ->
            val locator = location.toLocator()
            lastLocator = locator
            store.save(key, locator)
            val now = System.currentTimeMillis()
            log.write(
                GestureMath.csvLine(
                    now, "A", phase, "LOCATION", locator.href.toString(),
                    locator.locations.progression, locator.locations.totalProgression, null, rolling.total(now),
                ),
            )
            info = "A · ${locator.href} · chap. ${locator.locations.progression.format3()} · livre ${locator.locations.totalProgression.format3()}"
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        lastLocator?.let { store.save(key, it) }
    }

    val scrollObserver = remember {
        ScrollObserver(
            onScroll = { deltaY, _ -> rolling.add(System.currentTimeMillis(), abs(deltaY.toDouble()) / viewportHeightPx) },
            onFling = { velocityY ->
                val now = System.currentTimeMillis()
                log.write(
                    GestureMath.csvLine(
                        now, "A", phase, "PRE_FLING", lastLocator?.href?.toString().orEmpty(),
                        lastLocator?.locations?.progression, lastLocator?.locations?.totalProgression,
                        GestureMath.screensPerSecond(velocityY, viewportHeightPx), rolling.total(now),
                    ),
                )
            },
        )
    }
    val inputListener = remember {
        object : InputListener {
            override fun onTap(event: TapEvent, context: TapContext) {
                val relativeX = event.offset.x / context.viewport.width
                log.write(GestureMath.csvLine(System.currentTimeMillis(), "A", phase, "TAP", "", relativeX.toDouble(), null, null, null))
                if (relativeX in 0.3f..0.7f) overlayVisible = !overlayVisible
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height.coerceAtLeast(1) }
            .observeGestures { timeMs, velocityY ->
                log.write(
                    GestureMath.csvLine(
                        timeMs, "A", phase, "RELEASE", lastLocator?.href?.toString().orEmpty(),
                        lastLocator?.locations?.progression, lastLocator?.locations?.totalProgression,
                        GestureMath.screensPerSecond(velocityY, viewportHeightPx), rolling.total(timeMs),
                    ),
                )
            }
            .nestedScroll(scrollObserver),
    ) {
        ReflowableWebRendition(
            state = state,
            modifier = Modifier.fillMaxSize(),
            windowInsets = WindowInsets.safeDrawing,
            inputListener = inputListener,
        )
        SpikeOverlay(overlayVisible, info, phase, onPhase = { phase = it }, modifier = Modifier.align(Alignment.TopCenter))
    }
}
