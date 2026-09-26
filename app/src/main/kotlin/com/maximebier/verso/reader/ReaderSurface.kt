@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import android.app.Application
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadingOrderPositions
import com.maximebier.verso.readium.ReadingStyle
import com.maximebier.verso.readium.VersoReadingPreferences
import kotlin.math.abs
import kotlin.math.max
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
import org.readium.navigator.common.HtmlId
import org.readium.navigator.common.HyperlinkListener
import org.readium.navigator.common.InputListener
import org.readium.navigator.common.LinkContext
import org.readium.navigator.common.TapContext
import org.readium.navigator.common.TapEvent
import org.readium.navigator.common.defaultHyperlinkListener
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.navigator.web.reflowable.ReflowableWebRendition
import org.readium.navigator.web.reflowable.ReflowableWebRenditionFactory
import org.readium.navigator.web.reflowable.ReflowableWebRenditionState
import org.readium.navigator.web.reflowable.resource.ReflowableWebViewport
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url

/**
 * Variante A : navigateur Compose de Readium, chapitres enchaînés par un `VerticalPager` de WebViews.
 * `initialLocator` n’est lu qu’à la création (clé : la publication). `onInternalLink` : lien interne
 * touché (ordre de lecture), appelé juste avant que Readium ne le suive.
 */
@Composable
fun ReaderSurface(
    publication: Publication,
    initialLocator: Locator?,
    dark: Boolean,
    onReady: (ReaderController) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
    onInternalLink: (Url) -> Unit = {},
) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)
    val currentOnInternalLink by rememberUpdatedState(onInternalLink)
    val background = Color(ReadingStyle.colors(dark).background)

    val factory = remember(publication) {
        ReflowableWebRenditionFactory(
            application = context.applicationContext as Application,
            publication = publication,
            configuration = VersoReadingPreferences.reflowableWebConfiguration(),
        )
    }
    if (factory == null) {
        // Livre à mise en page fixe, vide ou protégé : hors du périmètre V1.
        Box(modifier.fillMaxSize().background(background))
        return
    }

    val renditionState by produceState<ReflowableWebRenditionState?>(initialValue = null, factory) {
        value = factory.createRenditionState(
            initialPreferences = VersoReadingPreferences.reflowableWeb(dark, fontScale),
            initialLocation = initialLocator?.let(::goLocationOf),
        ).getOrNull()
    }
    val state = renditionState
    if (state == null) {
        Box(modifier.fillMaxSize().background(background))
        return
    }

    val scope = rememberCoroutineScope()
    val controller = remember(state) {
        ReflowableReaderController(
            scope = scope,
            readChapterHtml = { href -> readChapterHtml(publication, href) },
            onCenterTap = { currentOnCenterTap() },
        )
    }
    val rendition = state.controller

    LaunchedEffect(controller) {
        controller.setPositions(ReadingOrderPositions.load(publication))
    }
    LaunchedEffect(rendition) {
        val ready = rendition ?: return@LaunchedEffect
        controller.bind { ready.goTo(it) }
        val pager = pagerStateOf(state)
        val readingOrder = publication.readingOrder.map { it.url() }
        fun pagerTop(): PagerTop? = pager?.let { pagerTopOf(it, readingOrder) }
        controller.onViewport(ready.location.toLocator(), ready.viewport, pagerTop())
        currentOnReady(controller)
        // Le décalage du pager est lu ici : pendant un passage de fichier, lui seul bouge.
        snapshotFlow { Triple(ready.location.toLocator(), ready.viewport, pagerTop()) }
            .collect { (location, viewport, top) -> controller.onViewport(location, viewport, top) }
    }
    LaunchedEffect(rendition, dark, fontScale) {
        rendition?.preferences = VersoReadingPreferences.reflowableWeb(dark, fontScale)
    }

    val inputListener = remember(controller) {
        object : InputListener {
            override fun onTap(event: TapEvent, context: TapContext) {
                if (context.viewport.width.value <= 0f) return
                controller.onReadiumTap(xFraction = event.offset.x / context.viewport.width)
            }
        }
    }
    // Liens internes suivis par Readium (liens externes ignorés : aucun réseau) ; on retient qu’un lien
    // a été touché, pour ne pas prendre ce tap pour un tap au centre, et on signale les liens internes.
    val readiumLinks = defaultHyperlinkListener(rendition)
    val hyperlinkListener = remember(controller, readiumLinks) {
        object : HyperlinkListener {
            override fun onReadingOrderLinkActivated(url: Url, context: LinkContext?) {
                controller.onLinkActivated()
                // Saut explicite (spec) : annoncé avant que Readium ne lance le déplacement.
                currentOnInternalLink(url)
                readiumLinks.onReadingOrderLinkActivated(url, context)
            }

            override fun onNonLinearLinkActivated(url: Url, context: LinkContext?) {
                controller.onLinkActivated()
                readiumLinks.onNonLinearLinkActivated(url, context)
            }

            override fun onExternalLinkActivated(url: AbsoluteUrl, context: LinkContext?) {
                controller.onLinkActivated()
            }
        }
    }

    Box(modifier.fillMaxSize().background(background)) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { controller.viewportHeightPx = it.height }
                .observeGestures(controller),
        ) {
            ReflowableWebRendition(
                state = state,
                modifier = Modifier.fillMaxSize(),
                windowInsets = WindowInsets.safeDrawing,
                inputListener = inputListener,
                hyperlinkListener = hyperlinkListener,
            )
        }
        // Le navigateur ne décale pas le texte sous la barre d’état en mode défilement : quand elle
        // réapparaît (mode immersif, balayage du bord), un bandeau du fond de lecture garde ses icônes lisibles.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(background))
    }
}

/**
 * Observe chaque geste en passe `Initial` sans rien consommer : le navigateur reçoit tous les
 * événements. La vitesse au lâcher décide du fling ; un appui bref sans glissement est un tap.
 */
private fun Modifier.observeGestures(controller: ReflowableReaderController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            controller.onPointerDown()
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var moved = false
            var upAt: Long? = null
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                }
                if (!change.pressed) {
                    upAt = change.uptimeMillis
                    break
                }
            }
            val releasedAt = upAt ?: return@awaitEachGesture
            when {
                moved -> controller.onGestureReleased(velocityTracker.calculateVelocity().y)
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

/**
 * `PagerState` du navigateur. Readium 3.4.0 ne l’expose pas (`internal`), or c’est le seul endroit
 * où lire le décalage de la page pendant un passage de fichier ([screenTop]). Lu une fois par
 * réflexion ; null si une version de Readium le renomme : on retombe alors sur le repli de [screenTop].
 */
private fun pagerStateOf(state: ReflowableWebRenditionState): PagerState? =
    runCatching {
        state.javaClass.methods
            .firstOrNull { it.name.startsWith("getPagerState") && it.parameterCount == 0 }
            ?.invoke(state) as? PagerState
    }.getOrNull()

/** Première page visible et part de cette page cachée au-dessus de l’écran (lecture observable). */
private fun pagerTopOf(pager: PagerState, readingOrder: List<Url>): PagerTop? {
    val layout = pager.layoutInfo
    val first = layout.visiblePagesInfo.firstOrNull() ?: return null
    val href = readingOrder.getOrNull(first.index) ?: return null
    if (layout.pageSize <= 0) return null
    return PagerTop(href = href, hiddenFraction = -first.offset.toDouble() / layout.pageSize)
}

/**
 * Cible Readium d’un locator. Readium ne cherche le chapitre que par `href` exact et ignore les
 * fragments : le fragment (lien du sommaire, `locatorFromLink`) devient une ancre `htmlId`.
 * L’extrait approché de [ReflowableReaderController.excerptLocator] est dans `text.after` : sans
 * `highlight`, Readium n’en fait jamais une ancre texte.
 */
internal fun goLocationOf(locator: Locator): ReflowableWebGoLocation {
    val fromLocator = ReflowableWebGoLocation(locator)
    val fragment = locator.href.fragment ?: locator.locations.fragments.firstOrNull()
    return fromLocator.copy(
        href = locator.href.removeFragment(),
        htmlId = fragment?.takeIf { it.isNotEmpty() }?.let(::HtmlId),
    )
}

internal class ReflowableReaderController(
    private val scope: CoroutineScope,
    private val readChapterHtml: suspend (Url) -> String?,
    private val onCenterTap: () -> Unit,
    private val thresholds: ReadingThresholds = ReadingThresholds(),
    private val uptimeMs: () -> Long = SystemClock::uptimeMillis,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) : ReaderController {

    private val displayedState = MutableStateFlow<Locator?>(null)
    private val gestureFlow = MutableSharedFlow<GestureSignal>(extraBufferCapacity = 16)
    private val chapterTexts = HashMap<String, String>()
    private var navigate: (suspend (ReflowableWebGoLocation) -> Unit)? = null
    private var positions: ReadingOrderPositions? = null
    private var lastDisplayedChangeAt: Long? = null

    private var settleJob: Job? = null
    private var pendingIsFling = false

    private var touchStoppedScroll = false
    private var tapToken: TapToken? = null

    override val displayed: StateFlow<Locator?> = displayedState.asStateFlow()
    override val gestures: Flow<GestureSignal> = gestureFlow.asSharedFlow()
    override var viewportHeightPx: Int = 0
        internal set

    fun bind(navigate: suspend (ReflowableWebGoLocation) -> Unit) {
        this.navigate = navigate
    }

    /** Progression totale fine ; recalcule le locator affiché s’il est déjà là (ce n’est pas un mouvement). */
    fun setPositions(positions: ReadingOrderPositions?) {
        this.positions = positions
        displayedState.value = displayedState.value?.let(::withTotalProgression)
    }

    /** Nouvel état du navigateur : la position affichée est le haut réel de l’écran ([screenTop]). */
    fun onViewport(location: Locator, viewport: ReflowableWebViewport, pagerTop: PagerTop?) {
        onDisplayed(screenTop(location, viewport, pagerTop, previous = displayedState.value))
    }

    fun onDisplayed(locator: Locator) {
        val filled = withTotalProgression(locator)
        if (filled == displayedState.value) return
        lastDisplayedChangeAt = uptimeMs()
        displayedState.value = filled
    }

    private fun withTotalProgression(locator: Locator): Locator =
        positions?.withTotalProgression(locator) ?: locator

    /** Doigt posé : il arrête tout défilement en cours, dont le signal part aussitôt. */
    fun onPointerDown() {
        val now = uptimeMs()
        touchStoppedScroll = lastDisplayedChangeAt?.let { now - it < ReaderGestures.SETTLE_QUIET_MS } ?: false
        flushPendingGesture()
        // Le tap précédent est clos : rattrapé tout de suite s’il attendait encore (le navigateur signale
        // un tap quelques millisecondes après le lâcher, jamais après l’appui suivant), écho oublié sinon.
        val previous = tapToken
        tapToken = null
        if (previous is TapToken.Pending) {
            previous.fallback.cancel()
            handleTap(previous.xFraction)
        }
    }

    /** Fling si la vitesse au lâcher dépasse `flingScreensPerSecond` écrans par seconde (seul usage de ce seuil). */
    fun onGestureReleased(velocityYPxPerSecond: Float) {
        val height = viewportHeightPx
        val isFling = height > 0 &&
            abs(velocityYPxPerSecond) / height >= thresholds.flingScreensPerSecond
        val releasedAt = uptimeMs()
        settleJob?.cancel()
        pendingIsFling = isFling
        settleJob = scope.launch {
            awaitSettled(releasedAt)
            settleJob = null
            gestureFlow.emit(GestureSignal(timeMs = wallClockMs(), isFling = isFling))
        }
    }

    private fun flushPendingGesture() {
        val pending = settleJob ?: return
        pending.cancel()
        settleJob = null
        gestureFlow.tryEmit(GestureSignal(timeMs = wallClockMs(), isFling = pendingIsFling))
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
        navigate?.invoke(goLocationOf(locator))
    }

    /**
     * Extrait approché (pas d’accès au DOM dans ce navigateur) rangé dans `text.after` : il sert à la
     * carte Reprendre mais ne devient jamais une ancre de restauration (Readium n’ancre que `highlight`).
     */
    override suspend fun excerptLocator(): Locator? {
        val current = displayedState.value ?: return null
        val key = Locators.hrefKey(current)
        val text = chapterTexts[key]
            ?: readChapterHtml(current.href.removeFragment())?.let(ChapterText::plainText)?.also { chapterTexts[key] = it }
            ?: return current
        val excerpt = ChapterText.excerptAt(
            text = text,
            progression = current.locations.progression ?: 0.0,
            maxChars = ReaderGestures.EXCERPT_MAX_CHARS,
        ) ?: return current
        return current.copy(text = Locator.Text(after = excerpt))
    }
}
