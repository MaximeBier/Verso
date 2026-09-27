package com.maximebier.verso.reader

import android.os.SystemClock
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadingOrderPositions
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
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

/**
 * Pont entre le navigateur classique de Readium et l’app : positions affichées, signaux de fin de geste (après la
 * fin réelle du défilement natif), taps, changement de chapitre au bord et extrait de la carte Reprendre.
 */
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

    private val styleState = MutableStateFlow<ReaderStyle?>(null)

    /** Derniers réglages demandés ; la surface les passe à `submitPreferences`. */
    val style: StateFlow<ReaderStyle?> = styleState.asStateFlow()

    override fun submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode) {
        styleState.value = ReaderStyle(settings, theme, scrollMode)
    }

    /** Remise en page en cours : locator affiché avant le premier changement, rétabli ensuite ; null sinon. */
    private var relayoutAnchor: Locator? = null
    private var relayoutJob: Job? = null

    /** Instant de la dernière position rapportée pendant l’étape en cours de la remise en page ; null si aucune. */
    private var relayoutReportAt: Long? = null

    /** Dernière position rapportée pendant la remise en page (non publiée) : celle de l’écran si elle est abandonnée. */
    private var relayoutLastReport: Locator? = null

    /**
     * Préférences qui changent la mise en page (police, taille, interligne, marges, et largeur de la surface, dont
     * dépend la gouttière) : Readium applique le CSS sans repositionner le texte (défilement gardé en pixels).
     * [apply] soumet les préférences ; une fois la nouvelle mise en page rapportée, le texte revient au locator
     * affiché avant le changement. Entre-temps, les positions rapportées ne sont pas publiées : la machine à états
     * ne voit ni mouvement, ni fling, ni navigation, et la position affichée reste ce locator. Des changements
     * rapprochés (taille +/− répétés) gardent celui du premier. Un toucher sur le texte ([onPointerDown]) ou un saut
     * ([go]) abandonne la remise en page : l’utilisateur ou le saut l’emporte sur le retour au locator.
     */
    fun relayout(apply: () -> Unit) {
        relayoutJob?.cancel()
        val anchor = relayoutAnchor ?: displayedState.value
        if (anchor == null) {
            // Rien d’affiché encore : le navigateur ouvrira directement la position initiale avec ces préférences.
            apply()
            return
        }
        relayoutAnchor = anchor
        relayoutJob = scope.launch {
            relayoutReportAt = null
            apply()
            awaitRelayoutReport()
            relayoutReportAt = null
            navigate?.invoke(anchor)
            // Un saut ou un toucher arrivé pendant ce déplacement l’a abandonnée : plus rien à faire.
            ensureActive()
            awaitRelayoutReport()
            relayoutAnchor = null
            relayoutReportAt = null
            relayoutLastReport = null
            relayoutJob = null
        }
    }

    /**
     * Position rapportée puis calme depuis [ReaderGestures.SETTLE_QUIET_MS] (une police qui se charge peut
     * décaler le texte une seconde fois), ou [ReaderGestures.RELAYOUT_REPORT_MAX_MS] sans position.
     */
    private suspend fun awaitRelayoutReport() {
        val startedAt = uptimeMs()
        while (true) {
            val now = uptimeMs()
            val reportAt = relayoutReportAt
            if (reportAt != null && now - reportAt >= ReaderGestures.SETTLE_QUIET_MS) return
            if (reportAt == null && now - startedAt >= ReaderGestures.RELAYOUT_REPORT_MAX_MS) return
            if (now - startedAt >= ReaderGestures.SETTLE_MAX_MS) return
            delay(ReaderGestures.SETTLE_POLL_MS)
        }
    }

    /**
     * Abandonne la remise en page en cours. `publishScreen` : la dernière position rapportée pendant celle-ci est
     * publiée, pour que [displayed] corresponde à l’écran (le navigateur ne la réémettra pas). Pas pour un saut :
     * sa cible est la prochaine position.
     */
    private fun cancelRelayout(publishScreen: Boolean) {
        if (relayoutAnchor == null) return
        relayoutJob?.cancel()
        relayoutJob = null
        relayoutAnchor = null
        relayoutReportAt = null
        val screen = relayoutLastReport
        relayoutLastReport = null
        if (publishScreen && screen != null) onDisplayed(screen)
    }

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
        if (relayoutAnchor != null) {
            relayoutReportAt = uptimeMs()
            relayoutLastReport = locator
            return
        }
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
        // L’utilisateur reprend le texte en main (feuille « Aa » sans voile) : pas de retour au locator sous son doigt.
        cancelRelayout(publishScreen = true)
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
        // Remise en page commencée pendant le glissé : le chapitre voisin se calcule sur la position réelle.
        cancelRelayout(publishScreen = true)
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
        // Le saut l’emporte sur le retour au locator d’une remise en page en cours.
        cancelRelayout(publishScreen = false)
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
