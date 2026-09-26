@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
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

private const val LOG_TAG = "VersoReader"

/**
 * Variante A : navigateur Compose de Readium, chapitres enchaînés par un `VerticalPager` de WebViews.
 * `initialLocator` n’est lu qu’à la création (clé : la publication). `onInternalLink` : lien interne
 * touché (ordre de lecture), appelé juste avant que Readium ne le suive. `onFailed` : le moteur refuse le livre
 * (mise en page fixe, vide, protégé) ou ne peut pas créer sa rendition ; appelé une fois, la surface reste unie.
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
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)
    val currentOnInternalLink by rememberUpdatedState(onInternalLink)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val background = Color(ReadingStyle.colors(dark).background)

    val factory = remember(publication) {
        ReflowableWebRenditionFactory(
            application = context.applicationContext as Application,
            publication = publication,
            configuration = VersoReadingPreferences.reflowableWebConfiguration(),
        )
    }
    if (factory == null) {
        // Livre à mise en page fixe, vide ou protégé : hors du périmètre V1. Jamais d’écran vide sans issue.
        LaunchedEffect(publication) {
            Log.w(LOG_TAG, "Livre refusé par le moteur (mise en page fixe, vide ou protégé)")
            currentOnFailed()
        }
        Box(modifier.fillMaxSize().background(background))
        return
    }

    val renditionState by produceState<ReflowableWebRenditionState?>(initialValue = null, factory) {
        value = factory.createRenditionState(
            initialPreferences = VersoReadingPreferences.reflowableWeb(dark, fontScale),
            initialLocation = initialLocator?.let(::goLocationOf),
        ).onFailure { error ->
            Log.w(LOG_TAG, "Rendition impossible : $error")
            currentOnFailed()
        }.getOrNull()
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
                windowInsets = readerContentInsets,
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
 * Insets donnés au navigateur : barres système **même masquées** et découpe de l’écran, donc constants.
 * Readium en tire les marges de défilement du document ; `safeDrawing` suit la visibilité des barres
 * système, et le mode immersif les rétablit avec la barre de lecture : le document changeait alors de
 * hauteur, et la progression du haut de l’écran bougeait sans que le texte bouge (lue comme de la lecture).
 * La barre de lecture et les barres système sont une surcouche : la mise en page du texte ne dépend pas d’elles.
 */
@OptIn(ExperimentalLayoutApi::class)
internal val readerContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)

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
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                        controller.onDragStarted()
                    }
                    if (!change.pressed) {
                        upAt = change.uptimeMillis
                        break
                    }
                }
            } finally {
                controller.onPointerUp()
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
    /** Extraction du texte brut d’un chapitre (regex sur tout le fichier) : jamais sur le fil principal. */
    private val textDispatcher: CoroutineDispatcher = Dispatchers.Default,
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

    /** Saut vers un autre fichier en cours ([go]) : les positions affichées sont retenues, pas publiées. */
    private var holding = false
    private var held: Locator? = null
    private var goGeneration = 0

    /** Doigt posé (sans glissé) : la page n’est pas déclarée stable avant son lever ([pointerUpAt]). */
    private var pointerDown = false
    private var pointerUpAt: Long? = null

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
        val previous = if (holding) held ?: displayedState.value else displayedState.value
        onDisplayed(screenTop(location, viewport, pagerTop, previous = previous))
    }

    fun onDisplayed(locator: Locator) {
        val filled = withTotalProgression(locator)
        if (holding) {
            if (filled == (held ?: displayedState.value)) return
            lastDisplayedChangeAt = uptimeMs()
            held = filled
            return
        }
        if (filled == displayedState.value) return
        lastDisplayedChangeAt = uptimeMs()
        displayedState.value = filled
    }

    /** Fin de la retenue d’un saut : la dernière position retenue devient la position affichée. */
    private fun releaseHold() {
        if (!holding) return
        holding = false
        val last = held ?: return
        held = null
        onDisplayed(last)
    }

    private fun withTotalProgression(locator: Locator): Locator =
        positions?.withTotalProgression(locator) ?: locator

    /** Doigt posé : il arrête tout défilement en cours, dont le signal part aussitôt. */
    fun onPointerDown() {
        val now = uptimeMs()
        touchStoppedScroll = lastDisplayedChangeAt?.let { now - it < ReaderGestures.SETTLE_QUIET_MS } ?: false
        // Pendant un saut, un simple appui (tap pour la barre) ne lève pas la retenue : il la suspend
        // jusqu’au lever ; seul un glissé ([onDragStarted]) rend la main à l’utilisateur.
        pointerDown = true
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

    /**
     * Le doigt a glissé au-delà du seuil de toucher : l’utilisateur reprend la main pendant un saut, ce qui est
     * affiché redevient la référence (retenue levée, pas de recalage).
     */
    fun onDragStarted() {
        releaseHold()
    }

    /** Doigt levé (ou geste annulé), glissé ou non. */
    fun onPointerUp() {
        pointerDown = false
        pointerUpAt = uptimeMs()
    }

    /** Fling si la vitesse au lâcher dépasse `flingScreensPerSecond` écrans par seconde (seul usage de ce seuil). */
    fun onGestureReleased(velocityYPxPerSecond: Float) {
        releaseHold()
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

    /**
     * Saut sans animation ; le geste en cours, interrompu, envoie son signal avant le saut.
     *
     * Vers une progression précise dans un **autre fichier** (« Revenir », journal), Readium atterrit juste,
     * puis la WebView fraîchement chargée se remet en page et le haut de l’écran remonte de plusieurs écrans
     * (anomalie F ; dans un même fichier, le saut est exact). Les positions affichées sont alors retenues
     * jusqu’à ce que la page soit stable ([ReaderGestures.JUMP_SETTLE_QUIET_MS]) ; si elle s’est écartée de la
     * cible, le saut est refait une fois (même fichier, désormais en page), puis seule la position finale est
     * publiée : la machine à états ne voit jamais la position intermédiaire. Un glissé lève la retenue (sans
     * recalage) ; un simple appui (tap pour la barre) la suspend jusqu’au lever, puis le recalage a lieu.
     * Les cibles ancrées (sommaire, liens) ne sont pas concernées : leur progression n’est qu’estimée.
     */
    override suspend fun go(locator: Locator) {
        flushPendingGesture()
        releaseHold()
        val generation = ++goGeneration
        val target = goLocationOf(locator)
        val progression = locator.locations.progression
        val current = displayedState.value
        if (progression == null || target.htmlId != null || current == null || sameResource(current.href, target.href)) {
            navigate?.invoke(target)
            return
        }
        holding = true
        held = null
        try {
            navigate?.invoke(target)
            if (!awaitJumpSettled(generation)) return
            val arrived = held
            val off = arrived == null || !sameResource(arrived.href, target.href) ||
                (arrived.locations.progression?.let { abs(it - progression) > ReaderGestures.JUMP_REALIGN_TOLERANCE } ?: true)
            if (off) {
                navigate?.invoke(target)
                awaitJumpSettled(generation)
            }
        } finally {
            if (generation == goGeneration) releaseHold()
        }
    }

    /**
     * Attend que la page cible soit stable : [ReaderGestures.JUMP_SETTLE_QUIET_MS] sans nouvelle position ni doigt
     * posé (compté depuis le lever), au plus [ReaderGestures.SETTLE_MAX_MS]. Faux si la retenue a été levée
     * (glissé, autre saut).
     */
    private suspend fun awaitJumpSettled(generation: Int): Boolean {
        val start = uptimeMs()
        while (true) {
            if (generation != goGeneration || !holding) return false
            val now = uptimeMs()
            val quietSince = maxOf(lastDisplayedChangeAt ?: start, pointerUpAt ?: start, start)
            val quiet = !pointerDown && now - quietSince >= ReaderGestures.JUMP_SETTLE_QUIET_MS
            if (quiet || now - start >= ReaderGestures.SETTLE_MAX_MS) return true
            delay(ReaderGestures.SETTLE_POLL_MS)
        }
    }

    /**
     * Extrait approché (pas d’accès au DOM dans ce navigateur) rangé dans `text.after` : il sert à la
     * carte Reprendre mais ne devient jamais une ancre de restauration (Readium n’ancre que `highlight`).
     */
    override suspend fun excerptLocator(): Locator? {
        val current = displayedState.value ?: return null
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
