@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.spike

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.navigator.preferences.Color as ReadiumColor

fun spikeEpubPreferences(): EpubPreferences = EpubPreferences(
    backgroundColor = ReadiumColor(0xFFF5F1E8.toInt()),
    textColor = ReadiumColor(0xFF1F1B16.toInt()),
    fontFamily = SpikeAtkinson,
    fontSize = 19.0 / 16.0,
    lineHeight = 1.6,
    paragraphSpacing = 0.5,
    pageMargins = 1.0,
    publisherStyles = false,
    textAlign = TextAlign.START,
    hyphens = false,
    scroll = true,
    theme = Theme.LIGHT,
)

/** Seuil (px) d'un drag vertical qui demande de passer au chapitre voisin quand on est au bord. */
private const val CHAIN_DRAG_THRESHOLD_PX = 100f

@Composable
fun VariantBReader(
    activity: FragmentActivity,
    publication: Publication,
    book: SpikeBook,
    store: LocatorStore,
    log: SpikeLog,
) {
    val key = "B-${book.name}"
    val scope = rememberCoroutineScope()
    var overlayVisible by remember { mutableStateOf(true) }
    var phase by remember { mutableStateOf(SpikePhases.first()) }
    var info by remember { mutableStateOf("B · en attente du premier locator") }
    var viewportHeightPx by remember { mutableIntStateOf(1) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    val rolling = remember { RollingScreens() }

    // La fabrique doit être posée avant que AndroidFragment instancie le fragment.
    remember(publication) {
        activity.supportFragmentManager.fragmentFactory = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = store.load(key),
            initialPreferences = spikeEpubPreferences(),
            configuration = EpubNavigatorFragment.Configuration {
                servedAssets = listOf("fonts/.*")
                disablePageTurnsWhileScrolling = true
                addFontFamilyDeclaration(SpikeAtkinson) {
                    addFontFace {
                        addSource("fonts/atkinson_hyperlegible_next.ttf", preload = true)
                        setFontStyle(FontStyle.NORMAL)
                        setFontWeight(200..800)
                    }
                }
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { activity.supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory() }
    }

    // DisposableEffect + removeInputListener : sinon, en rouvrant la variante dans le même processus,
    // l'ancien écouteur restait attaché et chaque TAP / CHAIN_NEXT était journalisé deux fois.
    DisposableEffect(navigator) {
        val nav = navigator ?: return@DisposableEffect onDispose {}
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val relativeX = event.point.x / nav.publicationView.width.coerceAtLeast(1)
                log.write(GestureMath.csvLine(System.currentTimeMillis(), "B", phase, "TAP", "", relativeX.toDouble(), null, null, null))
                if (relativeX in 0.3f..0.7f) {
                    overlayVisible = !overlayVisible
                    return true
                }
                return false
            }

            // Toujours false : true appellerait preventDefault() et bloquerait le scroll de la WebView.
            override fun onDrag(event: DragEvent): Boolean {
                if (event.type == DragEvent.Type.End) {
                    scope.launch { chainChapters(nav, publication, event.offset.y, log, phase) }
                }
                return false
            }
        }
        nav.addInputListener(listener)
        onDispose { nav.removeInputListener(listener) }
    }

    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        launch {
            var previous: Pair<String, Double>? = null
            while (isActive) {
                delay(100)
                val href = nav.currentLocator.value.href.toString()
                val screens = nav.evaluateJavascript("window.scrollY / window.innerHeight")?.toDoubleOrNull() ?: continue
                val last = previous
                if (last != null && last.first == href) rolling.add(System.currentTimeMillis(), abs(screens - last.second))
                previous = href to screens
            }
        }
        nav.currentLocator.collect { locator ->
            store.save(key, locator)
            val now = System.currentTimeMillis()
            log.write(
                GestureMath.csvLine(
                    now, "B", phase, "LOCATION", locator.href.toString(),
                    locator.locations.progression, locator.locations.totalProgression, null, rolling.total(now),
                ),
            )
            info = "B · ${locator.href} · chap. ${locator.locations.progression.format3()} · livre ${locator.locations.totalProgression.format3()}"
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        navigator?.currentLocator?.value?.let { store.save(key, it) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height.coerceAtLeast(1) }
            .observeGestures { timeMs, velocityY ->
                val locator = navigator?.currentLocator?.value
                log.write(
                    GestureMath.csvLine(
                        timeMs, "B", phase, "RELEASE", locator?.href?.toString().orEmpty(),
                        locator?.locations?.progression, locator?.locations?.totalProgression,
                        GestureMath.screensPerSecond(velocityY, viewportHeightPx), rolling.total(timeMs),
                    ),
                )
                val nav = navigator ?: return@observeGestures
                scope.launch {
                    delay(600)
                    saveWithExcerpt(nav, store, key, log, phase)
                }
            },
    ) {
        AndroidFragment<EpubNavigatorFragment>(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        ) { fragment ->
            if (navigator !== fragment) navigator = fragment
        }
        SpikeOverlay(overlayVisible, info, phase, onPhase = { phase = it }, modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** Enchaînement simulé : au bord bas + drag vers le haut → chapitre suivant ; au bord haut + drag vers le bas → fin du précédent. */
private suspend fun chainChapters(
    nav: EpubNavigatorFragment,
    publication: Publication,
    dragOffsetY: Float,
    log: SpikeLog,
    phase: String,
) {
    val current = nav.currentLocator.value
    val index = publication.readingOrder.indexOfFirst { it.url().removeFragment() == current.href.removeFragment() }
    if (dragOffsetY < -CHAIN_DRAG_THRESHOLD_PX && nav.isAtEdge(bottom = true)) {
        // Pas goForward() : avec scroll = true et disablePageTurnsWhileScrolling = true,
        // R2BasicWebView.scrollRight() ne fait rien (vérifié dans les sources 3.4.0 et sur le téléphone).
        val nextLink = publication.readingOrder.getOrNull(index + 1) ?: return
        val target = publication.locatorFromLink(nextLink)?.copyWithLocations(progression = 0.0) ?: return
        log.write(GestureMath.csvLine(System.currentTimeMillis(), "B", phase, "CHAIN_NEXT", current.href.toString(), null, null, null, null))
        nav.go(target, animated = false)
    } else if (dragOffsetY > CHAIN_DRAG_THRESHOLD_PX && nav.isAtEdge(bottom = false)) {
        val previousLink = publication.readingOrder.getOrNull(index - 1) ?: return
        val target = publication.locatorFromLink(previousLink)?.copyWithLocations(progression = 1.0) ?: return
        log.write(GestureMath.csvLine(System.currentTimeMillis(), "B", phase, "CHAIN_PREVIOUS", current.href.toString(), null, null, null, null))
        nav.go(target, animated = false)
    }
}

private suspend fun EpubNavigatorFragment.isAtEdge(bottom: Boolean): Boolean {
    val script = if (bottom) {
        "(window.scrollY + window.innerHeight >= document.scrollingElement.scrollHeight - 4)"
    } else {
        "(window.scrollY <= 4)"
    }
    return evaluateJavascript(script) == "true"
}

/** Locator courant enrichi du texte du premier bloc visible (ce que ferait excerptLocator()). */
private suspend fun saveWithExcerpt(nav: EpubNavigatorFragment, store: LocatorStore, key: String, log: SpikeLog, phase: String) {
    val current = nav.currentLocator.value
    val first = nav.firstVisibleElementLocator() ?: return
    store.save(key, current.copy(text = first.text))
    log.write(
        GestureMath.csvLine(
            System.currentTimeMillis(), "B", phase, "EXCERPT:" + first.text.highlight.orEmpty().replace(Regex("\\s+"), " ").trim().take(40).replace(';', ','),
            current.href.toString(), current.locations.progression, current.locations.totalProgression, null, null,
        ),
    )
}
