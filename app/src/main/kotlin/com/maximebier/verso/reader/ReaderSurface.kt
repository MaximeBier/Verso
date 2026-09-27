@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.platform.LocalView
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener
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
 * Texte qui commence à la première ligne visible (premier caractère à 12 px sous le haut de l’écran, en partant de la gauche), sur environ
 * 400 caractères : l’extrait de la carte Reprendre est le passage où l’on s’est arrêté, pas le début du paragraphe.
 * Réponse : chaîne JSON, ou `null` si aucun texte sous ce point.
 */
private const val TOP_TEXT_SCRIPT =
    "(function(){var r=null;for(var x=2;x<window.innerWidth;x+=8){" +
        "r=document.caretRangeFromPoint(x,12);if(r&&r.startContainer.nodeType===3)break;r=null;}" +
        "if(!r)return null;" +
        "var n=r.startContainer,s=n.data.substring(r.startOffset);" +
        "var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);w.currentNode=n;" +
        "while(s.length<400&&w.nextNode())s+=' '+w.currentNode.data;" +
        "return s;})()"

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
    // Défilement natif de la WebView, image par image : Readium ne rapporte la position que 100 ms après l’arrêt.
    val hostView = LocalView.current
    DisposableEffect(hostView, controller) {
        val observer = hostView.viewTreeObserver
        val scrollListener = ViewTreeObserver.OnScrollChangedListener { controller.onScrolled() }
        observer.addOnScrollChangedListener(scrollListener)
        onDispose { if (observer.isAlive) observer.removeOnScrollChangedListener(scrollListener) }
    }
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        controller.bind(
            navigate = { nav.go(it, animated = false) },
            probeEdges = { edgesOf(nav.evaluateJavascript(EDGES_SCRIPT)) },
            visibleText = {
                jsString(nav.evaluateJavascript(TOP_TEXT_SCRIPT))?.takeIf { it.isNotBlank() }
                    ?: nav.firstVisibleElementLocator()?.text?.highlight
            },
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
 * Insets donnés au texte : barre d’état et côtés des barres système **même masquées**, et découpe de l’écran, donc
 * constants. Rien en bas : le texte va jusqu’au bas de l’écran (plein écran), sous la barre de navigation masquée.
 * La barre de lecture et les barres système sont une surcouche : les afficher ou les masquer ne change ni la mise en
 * page du texte ni la position de lecture (anomalie G).
 */
@OptIn(ExperimentalLayoutApi::class)
internal val readerContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBarsIgnoringVisibility
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        .union(WindowInsets.displayCutout)

/** Chaîne rendue par `evaluateJavascript` (encodée en JSON : `"\"texte\""`) ; null si `null` ou illisible. */
internal fun jsString(json: String?): String? =
    json?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }

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

    /** Dernière image de défilement natif de la WebView ([onScrolled]) ; null si la surface ne la signale pas. */
    private var lastScrollAt: Long? = null
    private var gestureDownAt: Long = Long.MIN_VALUE

    /** Geste lâché dont le signal attend la fin du défilement. */
    private var pending: PendingGesture? = null

    /** Changement de chapitre interrompu par un toucher : son signal part dès que le chapitre visé s’affiche. */
    private var detachedTurn: PendingGesture? = null

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

    /** Image de défilement natif (le navigateur ne rapporte la position qu’après 100 ms sans défilement). */
    fun onScrolled() {
        lastScrollAt = uptimeMs()
    }

    /** Un défilement est en cours : dernière image de défilement il y a moins de [ReaderGestures.SCROLL_ACTIVE_MS]. */
    private fun scrolling(now: Long): Boolean {
        val lastScroll = lastScrollAt
            ?: return lastDisplayedChangeAt?.let { now - it < ReaderGestures.SETTLE_QUIET_MS } ?: false
        return now - lastScroll < ReaderGestures.SCROLL_ACTIVE_MS
    }

    /** Doigt posé : il arrête tout défilement en cours, dont le signal part aussitôt ; les bords sont relus. */
    fun onPointerDown() {
        val now = uptimeMs()
        touchStoppedScroll = scrolling(now)
        gestureDownAt = now
        flushPendingGesture()
        edgesJob?.cancel()
        edgesAtDown = null
        // Un doigt qui arrête un défilement en cours ne part pas d’un bord : pas de changement de chapitre.
        if (!touchStoppedScroll) {
            edgesJob = scope.launch {
                val edges = probeEdges?.invoke()
                // La lecture JavaScript ne s’annule pas : un appui plus récent a pu la remplacer entre-temps.
                ensureActive()
                edgesAtDown = edges
            }
        }
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
        pending?.job?.cancel()
        val gesture = PendingGesture(isFling = isFling, chapterTurn = chapterTurn, target = target)
        pending = gesture
        gesture.job = scope.launch {
            if (target != null) {
                navigate?.invoke(target)
                // Chapitre pas encore chargé : le signal attend qu’il soit affiché, sinon le tracker verrait son
                // arrivée comme une navigation (bond d’un écran), après la fin du geste.
                awaitDisplayedIn(target, releasedAt)
            }
            if (!gesture.detached) awaitSettled(releasedAt)
            if (pending === gesture) pending = null
            if (detachedTurn === gesture) detachedTurn = null
            gestureFlow.emit(gesture.signal())
        }
    }

    /**
     * Nouveau toucher : le geste précédent envoie son signal tout de suite. Sauf un changement de chapitre dont le
     * chapitre visé n’est pas encore affiché : son arrivée serait prise pour une navigation ; il signale à l’affichage.
     */
    private fun flushPendingGesture() {
        val gesture = pending ?: return
        pending = null
        val target = gesture.target
        if (target != null && !isDisplayedIn(target)) {
            gesture.detached = true
            detachedTurn = gesture
            return
        }
        gesture.job?.cancel()
        gestureFlow.tryEmit(gesture.signal())
    }

    /** Saut : le geste en attente n’est pas signalé (il arriverait après le saut et le brouillerait). */
    private fun dropPendingGesture() {
        pending?.job?.cancel()
        pending = null
        detachedTurn?.job?.cancel()
        detachedTurn = null
    }

    private fun isDisplayedIn(target: Locator): Boolean =
        displayedState.value?.let { sameResource(it.href, target.href) } == true

    private fun PendingGesture.signal() =
        GestureSignal(timeMs = wallClockMs(), isFling = isFling, chapterTurn = chapterTurn)

    private class PendingGesture(val isFling: Boolean, val chapterTurn: Boolean, val target: Locator?) {
        var job: Job? = null
        var detached = false
    }

    private suspend fun awaitDisplayedIn(target: Locator, releasedAt: Long) {
        while (!isDisplayedIn(target)) {
            if (uptimeMs() - releasedAt >= ReaderGestures.SETTLE_MAX_MS) return
            delay(ReaderGestures.SETTLE_POLL_MS)
        }
    }

    /**
     * Fin réelle du défilement. Avec les images de défilement natif : plus d’image depuis
     * [ReaderGestures.SCROLL_QUIET_MS], puis une position rapportée après la dernière image (au plus
     * [ReaderGestures.POSITION_WAIT_MS] après elle). Sans elles : aucune nouvelle position depuis
     * [ReaderGestures.SETTLE_QUIET_MS]. Jamais plus de [ReaderGestures.SETTLE_MAX_MS] après le lâcher.
     */
    private suspend fun awaitSettled(releasedAt: Long) {
        while (true) {
            val now = uptimeMs()
            if (now - releasedAt >= ReaderGestures.SETTLE_MAX_MS) return
            val lastScroll = lastScrollAt?.takeIf { it >= gestureDownAt }
            val settled = if (lastScroll == null) {
                now - max(lastDisplayedChangeAt ?: releasedAt, releasedAt) >= ReaderGestures.SETTLE_QUIET_MS
            } else {
                val positionAfterScroll = (lastDisplayedChangeAt ?: Long.MIN_VALUE) > lastScroll
                now - lastScroll >= ReaderGestures.SCROLL_QUIET_MS &&
                    (positionAfterScroll || now - lastScroll >= ReaderGestures.POSITION_WAIT_MS)
            }
            if (settled) return
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

    /** Saut sans animation ; le geste en attente est abandonné sans signal ([dropPendingGesture]). */
    override suspend fun go(locator: Locator) {
        dropPendingGesture()
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
