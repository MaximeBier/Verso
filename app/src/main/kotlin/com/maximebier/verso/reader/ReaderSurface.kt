@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadingOrderPositions
import com.maximebier.verso.readium.ReadingStyle
import com.maximebier.verso.readium.VersoReadingPreferences
import com.maximebier.verso.readium.VersoReadingPreferences.applyVerso
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url

private const val LOG_TAG = "VersoReader"

/** Bords du document affiché, lus dans la WebView (marge de 4 px pour l’arrondi du défilement). */
private const val EDGES_SCRIPT =
    "JSON.stringify([window.scrollY <= 4, " +
        "window.scrollY + window.innerHeight >= document.scrollingElement.scrollHeight - 4])"

/**
 * Navigateur EPUB classique de Readium (Fragment) : un chapitre à la fois, défilé nativement par la WebView ; un
 * glissé commencé au bord ouvre le chapitre voisin ([chapterChain]). `initialLocator` n’est lu qu’à la création
 * (clé : la publication). `onInternalLink` : lien interne touché (ordre de lecture), appelé juste avant que Readium
 * ne le suive. `fontScale` : échelle de la taille de police d’Android appliquée au texte. `onFailed` : le moteur
 * refuse le livre (mise en page fixe, hors V1) ; appelé une fois, surface unie.
 */
@Composable
fun ReaderSurface(
    publication: Publication,
    initialLocator: Locator?,
    dark: Boolean,
    fontScale: Double = 1.0,
    onReady: (ReaderController) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
    onInternalLink: (Url) -> Unit = {},
    onFailed: () -> Unit = {},
) {
    val activity = LocalActivity.current as? FragmentActivity
    val density = LocalDensity.current.density
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)
    val currentOnInternalLink by rememberUpdatedState(onInternalLink)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val background = Color(ReadingStyle.colors(dark).background)

    if (publication.metadata.layout == Layout.FIXED || activity == null) {
        // Livre à mise en page fixe : hors du périmètre V1. Jamais d’écran vide sans issue.
        LaunchedEffect(publication) {
            Log.w(LOG_TAG, "Livre refusé (mise en page fixe) ou hôte sans FragmentActivity")
            currentOnFailed()
        }
        Box(modifier.fillMaxSize().background(background))
        return
    }

    val scope = rememberCoroutineScope()
    val controller = remember(publication) {
        FragmentReaderController(
            scope = scope,
            readChapterHtml = { href -> readChapterHtml(publication, href) },
            onCenterTap = { currentOnCenterTap() },
            adjacentChapter = { current, next -> adjacentChapter(publication, current, next) },
        )
    }
    controller.chainThresholdPx = ReaderGestures.CHAPTER_CHAIN_DRAG_DP * density

    val fragmentFactory = remember(publication) {
        val listener = object : EpubNavigatorFragment.Listener {
            override fun shouldFollowInternalLink(link: Link, context: HyperlinkNavigator.LinkContext?): Boolean {
                controller.onLinkActivated()
                val url = link.url()
                // Saut explicite (spec) : annoncé avant que Readium ne lance le déplacement.
                if (publication.readingOrder.any { sameResource(it.url(), url) }) currentOnInternalLink(url)
                return true
            }

            // Liens externes ignorés : aucun réseau.
            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                controller.onLinkActivated()
            }
        }
        EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            listener = listener,
            initialPreferences = VersoReadingPreferences.epub(dark, fontScale = fontScale),
            configuration = EpubNavigatorFragment.Configuration { applyVerso() },
        )
    }
    // Posée à chaque composition, donc avant que AndroidFragment instancie le fragment.
    activity.supportFragmentManager.fragmentFactory = fragmentFactory
    LaunchedEffect(controller) {
        controller.setPositions(ReadingOrderPositions.load(publication))
    }

    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    DisposableEffect(navigator) {
        val nav = navigator ?: return@DisposableEffect onDispose {}
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val width = nav.publicationView.width
                if (width > 0) controller.onReadiumTap(xFraction = event.point.x / width)
                return false
            }
        }
        nav.addInputListener(listener)
        onDispose { nav.removeInputListener(listener) }
    }
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        controller.bind(
            navigate = { nav.go(it, animated = false) },
            probeEdges = { edgesOf(nav.evaluateJavascript(EDGES_SCRIPT)) },
            visibleText = { nav.firstVisibleElementLocator()?.text?.highlight },
        )
        currentOnReady(controller)
        nav.currentLocator.collect(controller::onDisplayed)
    }
    LaunchedEffect(navigator, dark, fontScale) {
        navigator?.submitPreferences(VersoReadingPreferences.epub(dark, fontScale = fontScale))
    }

    Box(modifier.fillMaxSize().background(background)) {
        AndroidFragment<EpubNavigatorFragment>(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(readerContentInsets)
                .onSizeChanged { controller.viewportHeightPx = it.height }
                .observeGestures(controller),
        ) { fragment ->
            if (navigator !== fragment) navigator = fragment
        }
    }
}

/**
 * Insets donnés au texte : barres système **même masquées** et découpe de l’écran, donc constants. La barre de
 * lecture et les barres système sont une surcouche : les afficher ou les masquer ne change ni la mise en page du
 * texte ni la position de lecture (anomalie G).
 */
@OptIn(ExperimentalLayoutApi::class)
internal val readerContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)

/** Réponse de [EDGES_SCRIPT] (`"[true,false]"`, parfois entre guillemets) ; null si illisible. */
internal fun edgesOf(json: String?): ChapterEdges? {
    val values = json?.trim('"')?.removePrefix("[")?.removeSuffix("]")?.split(",")?.map { it.trim() } ?: return null
    if (values.size != 2 || values.any { it != "true" && it != "false" }) return null
    return ChapterEdges(atTop = values[0] == "true", atBottom = values[1] == "true")
}

/** Début du chapitre suivant ou fin du précédent dans l’ordre de lecture ; null au premier ou au dernier. */
internal fun adjacentChapter(publication: Publication, current: Locator, next: Boolean): Locator? {
    val index = publication.readingOrder.indexOfFirst { sameResource(it.url(), current.href) }
    if (index < 0) return null
    val link = publication.readingOrder.getOrNull(if (next) index + 1 else index - 1) ?: return null
    return publication.locatorFromLink(link)?.copyWithLocations(progression = if (next) 0.0 else 1.0)
}

internal fun sameResource(a: Url, b: Url): Boolean =
    a.removeFragment().toString() == b.removeFragment().toString()

/**
 * Observe chaque geste en passe `Initial` sans rien consommer : la WebView reçoit tous les événements. La vitesse
 * et le déplacement au lâcher décident du fling et du changement de chapitre ; un appui bref sans glissement est
 * un tap.
 */
private fun Modifier.observeGestures(controller: FragmentReaderController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            controller.onPointerDown()
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var moved = false
            var upAt: Long? = null
            var upY = down.position.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                upY = change.position.y
                if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!change.pressed) {
                    upAt = change.uptimeMillis
                    break
                }
            }
            val releasedAt = upAt ?: return@awaitEachGesture
            when {
                moved -> controller.onGestureReleased(
                    velocityYPxPerSecond = velocityTracker.calculateVelocity().y,
                    dragDyPx = upY - down.position.y,
                )
                releasedAt - down.uptimeMillis <= ReaderGestures.TAP_MAX_DURATION_MS && size.width > 0 ->
                    controller.onTapLikeGesture(xFraction = down.position.x / size.width)
            }
        }
    }

internal suspend fun readChapterHtml(publication: Publication, href: Url): String? =
    withContext(Dispatchers.IO) {
        val resource = publication.get(href) ?: return@withContext null
        try {
            resource.read().getOrNull()?.toString(Charsets.UTF_8)
        } finally {
            resource.close()
        }
    }

internal class FragmentReaderController(
    private val scope: CoroutineScope,
    private val readChapterHtml: suspend (Url) -> String?,
    private val onCenterTap: () -> Unit,
    private val adjacentChapter: (current: Locator, next: Boolean) -> Locator?,
    private val thresholds: ReadingThresholds = ReadingThresholds(),
    private val uptimeMs: () -> Long = SystemClock::uptimeMillis,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    /** Extraction du texte brut d’un chapitre (regex sur tout le fichier) : jamais sur le fil principal. */
    private val textDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReaderController {

    private val displayedState = MutableStateFlow<Locator?>(null)
    private val gestureFlow = MutableSharedFlow<GestureSignal>(extraBufferCapacity = 16)
    private val chapterTexts = HashMap<String, String>()
    private var navigate: (suspend (Locator) -> Unit)? = null
    private var probeEdges: (suspend () -> ChapterEdges?)? = null
    private var visibleText: (suspend () -> String?)? = null
    private var positions: ReadingOrderPositions? = null
    private var lastDisplayedChangeAt: Long? = null

    private var settleJob: Job? = null
    private var pendingIsFling = false
    private var pendingChapterTurn = false

    private var touchStoppedScroll = false
    private var tapToken: TapToken? = null

    /** Bords du chapitre à l’appui du doigt en cours ; null tant qu’ils ne sont pas lus. */
    private var edgesAtDown: ChapterEdges? = null
    private var edgesJob: Job? = null

    /** Glissé minimal (px) qui, commencé au bord, ouvre le chapitre voisin. */
    var chainThresholdPx: Float = ReaderGestures.CHAPTER_CHAIN_DRAG_DP

    override val displayed: StateFlow<Locator?> = displayedState.asStateFlow()
    override val gestures: Flow<GestureSignal> = gestureFlow.asSharedFlow()
    override var viewportHeightPx: Int = 0
        internal set

    fun bind(
        navigate: suspend (Locator) -> Unit,
        probeEdges: suspend () -> ChapterEdges?,
        visibleText: suspend () -> String?,
    ) {
        this.navigate = navigate
        this.probeEdges = probeEdges
        this.visibleText = visibleText
    }

    /** Progression totale fine ; recalcule le locator affiché s’il est déjà là (ce n’est pas un mouvement). */
    fun setPositions(positions: ReadingOrderPositions?) {
        this.positions = positions
        displayedState.value = displayedState.value?.let(::withTotalProgression)
    }

    fun onDisplayed(locator: Locator) {
        val filled = withTotalProgression(locator)
        if (filled == displayedState.value) return
        lastDisplayedChangeAt = uptimeMs()
        displayedState.value = filled
    }

    private fun withTotalProgression(locator: Locator): Locator =
        positions?.withTotalProgression(locator) ?: locator

    /** Doigt posé : il arrête tout défilement en cours, dont le signal part aussitôt ; les bords sont relus. */
    fun onPointerDown() {
        val now = uptimeMs()
        touchStoppedScroll = lastDisplayedChangeAt?.let { now - it < ReaderGestures.SETTLE_QUIET_MS } ?: false
        flushPendingGesture()
        edgesJob?.cancel()
        edgesAtDown = null
        // Un doigt qui arrête un défilement en cours ne part pas d’un bord : pas de changement de chapitre.
        if (!touchStoppedScroll) edgesJob = scope.launch { edgesAtDown = probeEdges?.invoke() }
        // Le tap précédent est clos : rattrapé tout de suite s’il attendait encore (le navigateur signale
        // un tap quelques millisecondes après le lâcher, jamais après l’appui suivant), écho oublié sinon.
        val previous = tapToken
        tapToken = null
        if (previous is TapToken.Pending) {
            previous.fallback.cancel()
            handleTap(previous.xFraction)
        }
    }

    /**
     * Fin d’un glissé. Commencé au bord et assez long ([chapterChain]) : ouvre le chapitre voisin et le signale
     * (`chapterTurn`). Sinon : fling si la vitesse au lâcher dépasse `flingScreensPerSecond` écrans par seconde.
     */
    fun onGestureReleased(velocityYPxPerSecond: Float, dragDyPx: Float) {
        val height = viewportHeightPx
        val chain = chapterChain(edgesAtDown, dragDyPx, chainThresholdPx)
        val target = displayedState.value?.takeIf { chain != ChapterChain.NONE }
            ?.let { adjacentChapter(it, chain == ChapterChain.NEXT) }
        val chapterTurn = target != null
        val isFling = !chapterTurn && height > 0 &&
            abs(velocityYPxPerSecond) / height >= thresholds.flingScreensPerSecond
        val releasedAt = uptimeMs()
        settleJob?.cancel()
        pendingIsFling = isFling
        pendingChapterTurn = chapterTurn
        settleJob = scope.launch {
            if (target != null) {
                navigate?.invoke(target)
                // Chapitre pas encore chargé : le signal attend qu’il soit affiché, sinon le tracker verrait son
                // arrivée comme une navigation (bond d’un écran), après la fin du geste.
                awaitDisplayedIn(target, releasedAt)
            }
            awaitSettled(releasedAt)
            settleJob = null
            gestureFlow.emit(GestureSignal(timeMs = wallClockMs(), isFling = isFling, chapterTurn = chapterTurn))
        }
    }

    private fun flushPendingGesture() {
        val pending = settleJob ?: return
        pending.cancel()
        settleJob = null
        gestureFlow.tryEmit(
            GestureSignal(timeMs = wallClockMs(), isFling = pendingIsFling, chapterTurn = pendingChapterTurn),
        )
    }

    private suspend fun awaitDisplayedIn(target: Locator, releasedAt: Long) {
        while (displayedState.value?.let { sameResource(it.href, target.href) } != true) {
            if (uptimeMs() - releasedAt >= ReaderGestures.SETTLE_MAX_MS) return
            delay(ReaderGestures.SETTLE_POLL_MS)
        }
    }

    private suspend fun awaitSettled(releasedAt: Long) {
        while (true) {
            val now = uptimeMs()
            val quietFor = now - max(lastDisplayedChangeAt ?: releasedAt, releasedAt)
            if (quietFor >= ReaderGestures.SETTLE_QUIET_MS || now - releasedAt >= ReaderGestures.SETTLE_MAX_MS) return
            delay(ReaderGestures.SETTLE_POLL_MS)
        }
    }

    /**
     * Appui bref sans glissement vu par l’app, au lâcher (toujours avant le signal du navigateur).
     * Normalement, Readium signale aussi ce tap ([onReadiumTap]) ; s’il ne le fait pas à temps, l’app le
     * traite elle-même. Un jeton par tap physique évite de le compter deux fois. Un appui qui arrête un
     * défilement n’est pas un tap.
     */
    fun onTapLikeGesture(xFraction: Float) {
        if (touchStoppedScroll) return
        (tapToken as? TapToken.Pending)?.fallback?.cancel()
        val fallback = scope.launch {
            delay(ReaderGestures.TAP_FALLBACK_DELAY_MS)
            tapToken = TapToken.HandledByFallback
            handleTap(xFraction)
        }
        tapToken = TapToken.Pending(xFraction, fallback)
    }

    fun onReadiumTap(xFraction: Float) {
        val token = tapToken
        tapToken = null
        when (token) {
            is TapToken.Pending -> {
                token.fallback.cancel()
                handleTap(xFraction)
            }
            // Écho tardif d’un tap déjà rattrapé par l’app.
            TapToken.HandledByFallback -> Unit
            // Tap que l’app n’a pas pris pour un tap (appui long, appui qui arrêtait un défilement) : Readium décide.
            null -> handleTap(xFraction)
        }
    }

    fun onLinkActivated() {
        (tapToken as? TapToken.Pending)?.fallback?.cancel()
        tapToken = null
    }

    private fun handleTap(xFraction: Float) {
        if (xFraction in ReaderGestures.CENTER_TAP_RANGE) onCenterTap()
    }

    /** Suivi d’un tap physique entre l’observateur de l’app et le signal du navigateur. */
    private sealed interface TapToken {
        class Pending(val xFraction: Float, val fallback: Job) : TapToken
        data object HandledByFallback : TapToken
    }

    /** Saut sans animation ; le geste en cours, interrompu, envoie son signal avant le saut. */
    override suspend fun go(locator: Locator) {
        flushPendingGesture()
        navigate?.invoke(locator)
    }

    /**
     * Texte réellement visible en haut de l’écran (premier élément visible), rangé dans `text.after` : il sert à
     * la carte Reprendre mais ne devient jamais une ancre de restauration. À défaut, extrait approché par la
     * progression dans le texte brut du chapitre.
     */
    override suspend fun excerptLocator(): Locator? {
        val current = displayedState.value ?: return null
        val visible = visibleText?.invoke()
            ?.replace(Regex("\\s+"), " ")?.trim()
            ?.take(ReaderGestures.EXCERPT_MAX_CHARS)
            ?.takeIf { it.isNotEmpty() }
        if (visible != null) return current.copy(text = Locator.Text(after = visible))
        val key = Locators.hrefKey(current)
        val text = chapterTexts[key]
            ?: readChapterHtml(current.href.removeFragment())
                ?.let { html -> withContext(textDispatcher) { ChapterText.plainText(html) } }
                ?.also { chapterTexts[key] = it }
            ?: return current
        val excerpt = ChapterText.excerptAt(
            text = text,
            progression = current.locations.progression ?: 0.0,
            maxChars = ReaderGestures.EXCERPT_MAX_CHARS,
        ) ?: return current
        return current.copy(text = Locator.Text(after = excerpt))
    }
}
