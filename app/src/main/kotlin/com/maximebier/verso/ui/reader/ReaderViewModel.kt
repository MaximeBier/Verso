package com.maximebier.verso.ui.reader

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.journal.SessionRecord
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.core.text.shortLocation
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadingOrderPositions
import java.io.File
import java.time.ZoneId
import kotlin.math.floor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

/** Carte « Revenir » : forme courte de la position de lecture (null si hors sommaire) et pourcentage lu. */
data class ReturnCardState(val location: String?, val percent: Int)

data class ReaderUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val publication: Publication? = null,
    /** Locator de création de la surface : position sauvegardée, puis dernière position affichée. */
    val initialLocator: Locator? = null,
    val bookTitle: String = "",
    val totalWords: Long = 0,
    val toc: List<TocNode> = emptyList(),
    val readingOrderHrefs: List<String> = emptyList(),
    /** Fichier (sans fragment) de la position de lecture. */
    val currentHref: String? = null,
    val chapterPath: List<String> = emptyList(),
    val shortLocation: String? = null,
    /** Progression totale de la position de LECTURE (jamais de la position affichée). */
    val readingProgression: Double = 0.0,
    val readingPercent: Int = 0,
    val remainingMinutes: Int = 0,
    val barsVisible: Boolean = false,
    val tocVisible: Boolean = false,
    val journalVisible: Boolean = false,
    /** Carte « Revenir » ; jamais persistée. */
    val returnCard: ReturnCardState? = null,
)

class ReaderViewModel(
    private val bookId: Long,
    private val books: BookRepository,
    private val sessions: SessionRepository,
    private val openPublication: suspend (File) -> Result<Publication>,
    private val clock: () -> Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val readingEffectsFlow = MutableSharedFlow<TrackerEffect>(extraBufferCapacity = 64)

    /**
     * Effets `SaveReading` et `ReadingMoved` de la machine à états, dans l’ordre où elle les produit
     * (relais de `ReadingPositionCoordinator.readingEffects`), pour le journal des sessions (6.4).
     * `SaveReading` seul : restauration, « Rester ici » ou confirmation ; jamais `ScrollTo`.
     */
    val readingEffects: SharedFlow<TrackerEffect> = readingEffectsFlow.asSharedFlow()

    private val positionSaver = PositionSaver(
        bookId = bookId,
        save = books::saveReadingPosition,
        scope = viewModelScope,
    )
    private var coordinator: ReadingPositionCoordinator? = null
    private var controller: ReaderController? = null
    private var controllerJob: Job? = null

    /** Saut demandé sans surface prête (activité recréée) : appliqué au prochain [onReaderReady]. */
    private var pendingJump: PendingJump? = null
    private var tocLinks: List<Link> = emptyList()
    private var positions: ReadingOrderPositions? = null
    private var metrics: ReaderMetrics? = null
    private var appliedMetrics: ReaderMetrics? = null

    // --- Journal de lecture (tâche 6.4) ---------------------------------------------------------
    private var sessionCoordinator: SessionCoordinator? = null
    private val sessionCurrent = MutableStateFlow<SessionRecord?>(null)
    private var lastDisplayed: Locator? = null

    /** État de la feuille « Journal de lecture » ; null tant que `uiState.journalVisible` est faux. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val journal: StateFlow<JournalUiState?> = _uiState
        .map { it.journalVisible }
        .distinctUntilChanged()
        .flatMapLatest { visible ->
            if (!visible) {
                flowOf(null)
            } else {
                combine(sessions.observeSessions(bookId), sessionCurrent) { list, current ->
                    journalUiState(list, current, _uiState.value.toc, clock(), ZoneId.systemDefault(), ::hrefOfLocatorJson)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = books.book(bookId)
        if (book == null) {
            _uiState.update { it.copy(loading = false, failed = true) }
            return
        }
        val publication = openPublication(File(book.filePath)).getOrElse {
            _uiState.update { it.copy(loading = false, failed = true) }
            return
        }
        books.markOpened(bookId, clock())
        tocLinks = publication.tableOfContents.ifEmpty { publication.readingOrder }
        positions = ReadingOrderPositions.load(publication)
        val saved = book.readingLocatorJson?.let(Locators::fromJson)
        val start = saved ?: publication.readingOrder.firstOrNull()?.let(publication::locatorFromLink)
        val initialPosition = when {
            saved != null && book.readingLocatorJson != null -> BookPosition(book.readingLocatorJson, book.progression)
            start != null -> Locators.toPosition(start)
            else -> null
        }
        if (initialPosition == null) {
            _uiState.update { it.copy(loading = false, failed = true) }
            return
        }
        val created = ReadingPositionCoordinator(
            initial = initialPosition,
            distance = ReaderScreenDistance.fallbackDistance(book.totalWords),
            scope = viewModelScope,
            clock = clock,
            onSave = positionSaver::requestSave,
        )
        coordinator = created
        _uiState.update {
            it.copy(
                publication = publication,
                initialLocator = saved,
                bookTitle = book.title,
                totalWords = book.totalWords,
                toc = tocLinks.toTocNodes(),
                readingOrderHrefs = publication.readingOrder.map { link -> link.url().removeFragment().toString() },
            )
        }
        onPositionState(created.state.value)
        // `loading = false` en dernier : l’état publié est alors complet (progression comprise).
        _uiState.update { it.copy(loading = false) }
        viewModelScope.launch { created.state.collect(::onPositionState) }
        // Relais dans l’ordre d’émission ; lancé avant attach() (onReaderReady), donc aucun effet perdu.
        viewModelScope.launch { created.readingEffects.collect { readingEffectsFlow.emit(it) } }
        startSessions(book.totalWords, initialPosition)
    }

    private fun startSessions(totalWords: Long, initial: BookPosition) {
        if (sessionCoordinator != null) return
        val coordinator = SessionCoordinator(
            bookId = bookId,
            totalWords = totalWords,
            upsert = sessions::upsert,
            clock = clock,
        )
        sessionCoordinator = coordinator
        viewModelScope.launch { coordinator.current.collect { sessionCurrent.value = it } }
        viewModelScope.launch { readingEffects.collect(coordinator::onTrackerEffect) }
        coordinator.onOpened(initial)
        coordinator.start()
    }

    fun onReaderReady(readerController: ReaderController) {
        if (controller === readerController) return
        controller = readerController
        positionSaver.attach(readerController)
        coordinator?.attach(readerController)
        controllerJob?.cancel()
        controllerJob = viewModelScope.launch {
            readerController.displayed.filterNotNull().collect { locator ->
                val previous = lastDisplayed
                lastDisplayed = locator
                if (previous != null && previous != locator) sessionCoordinator?.onInteraction()
                // Reprise au même endroit si la surface est recréée (rotation, thème).
                _uiState.update { it.copy(initialLocator = locator) }
                refreshDistance()
            }
        }
        pendingJump?.let { jump ->
            pendingJump = null
            if (jump.closesToc) closeTocAndBars()
            jumpTo(jump.target)
        }
    }

    /**
     * Surface retirée de la composition (rotation, thème : l’activité est recréée). Jusqu’au prochain
     * [onReaderReady], les sauts sont mémorisés au lieu de partir vers l’ancienne rendition.
     */
    fun onReaderGone() {
        controllerJob?.cancel()
        controllerJob = null
        controller = null
        coordinator?.detach()
        // Le premier locator de la prochaine surface est une arrivée, pas un scroll (journal, 6.4).
        lastDisplayed = null
    }

    /** Échelle de police et densité de l’écran (envoyées par ReaderScreen). */
    fun onDisplayMetrics(fontScale: Float, density: Float) {
        metrics = ReaderMetrics(viewportHeightPx = 0, fontScale = fontScale, density = density)
        refreshDistance()
    }

    /** Distance en écrans recalculée quand la surface est mesurée ou que l’échelle du texte change. */
    private fun refreshDistance() {
        val base = metrics ?: return
        val height = controller?.viewportHeightPx ?: return
        if (height <= 0) return
        val current = base.copy(viewportHeightPx = height)
        if (current == appliedMetrics) return
        appliedMetrics = current
        coordinator?.updateDistance(ReaderScreenDistance.distance(_uiState.value.totalWords, current))
    }

    /** Barre, sommaire et carte montrent toujours la position de LECTURE. */
    private fun onPositionState(position: PositionState) {
        val reading = Locators.fromJson(position.reading.locatorJson)
        val href = reading?.let(Locators::hrefKey)
        val progression = position.reading.totalProgression
        _uiState.update { state ->
            val path = href?.let { chapterPathAt(state.toc, it) } ?: emptyList()
            val location = shortLocation(path)
            val percent = readingPercent(progression)
            state.copy(
                currentHref = href,
                chapterPath = path,
                shortLocation = location,
                readingProgression = progression,
                readingPercent = percent,
                remainingMinutes = remainingMinutes(state.totalWords, progression),
                returnCard = if (position.showReturnCard) ReturnCardState(location, percent) else null,
            )
        }
    }

    fun toggleBars() {
        sessionCoordinator?.onInteraction()
        _uiState.update { it.copy(barsVisible = !it.barsVisible) }
    }

    fun showToc() = _uiState.update { it.copy(tocVisible = true) }

    fun hideToc() = _uiState.update { it.copy(tocVisible = false) }

    fun showJournal() {
        sessionCoordinator?.onInteraction()
        _uiState.update { it.copy(journalVisible = true) }
    }

    fun hideJournal() = _uiState.update { it.copy(journalVisible = false) }

    /**
     * « Reprendre ici » (journal) : saut explicite à la fin de la session, par [jumpTo] (`ReaderEvent.Jumped`) :
     * la position de lecture ne bouge pas, la carte « Revenir » apparaît si la cible est à plus d'un écran.
     */
    fun resumeFromJournal(target: BookPosition) {
        hideJournal()
        sessionCoordinator?.onInteraction()
        Locators.fromJson(target.locatorJson)?.let(::jumpTo)
    }

    /** Premier plan (`ON_START`) : après une mise en arrière-plan, une nouvelle session commence. */
    fun onStart() {
        sessionCoordinator?.onStarted()
    }

    /** Saut par le sommaire ; `index` = rang dans le préordre du sommaire (`preorder`, même parcours que `buildTocRows`). */
    fun jumpToTocEntry(index: Int) {
        val publication = _uiState.value.publication ?: return
        val link = preorder(tocLinks) { it.children }.getOrNull(index) ?: return
        val target = publication.locatorFromLink(link) ?: return
        if (controller == null) {
            // Sommaire laissé ouvert : il se ferme quand le saut part vraiment.
            pendingJump = PendingJump(target, closesToc = true)
            return
        }
        closeTocAndBars()
        jumpTo(target)
    }

    private fun closeTocAndBars() = _uiState.update { it.copy(tocVisible = false, barsVisible = false) }

    /**
     * Saut explicite (sommaire ; « Reprendre ici » du journal en 6.4) : `ReaderEvent.Jumped` puis
     * `controller.go(locator)`, via le coordinateur. Navigation : ne touche pas la position de lecture.
     * La cible reçoit sa progression totale (un lien de sommaire n’en a pas), sinon un saut vers le
     * chapitre courant paraîtrait lointain.
     */
    fun jumpTo(locator: Locator) {
        if (controller == null) {
            pendingJump = PendingJump(locator, closesToc = false)
            return
        }
        coordinator?.onJump(withTotalProgression(locator))
    }

    /**
     * Lien interne touché dans le texte, avant que le moteur ne le suive lui-même (spec : saut
     * explicite). Annoncé à la machine à états seulement ; le déplacement reste celui du moteur.
     */
    fun onInternalLinkFollowed(url: Url) {
        val publication = _uiState.value.publication ?: return
        val target = publication.locatorFromLink(Link(href = url)) ?: return
        coordinator?.onLinkFollowed(withTotalProgression(target))
    }

    private fun withTotalProgression(locator: Locator): Locator = positions?.withTotalProgression(locator) ?: locator

    fun stayHere() {
        coordinator?.onStayHere()
    }

    fun goBack() {
        coordinator?.onGoBack()
    }

    private data class PendingJump(val target: Locator, val closesToc: Boolean)

    /**
     * `ON_STOP` de l’écran. La position est toujours enregistrée. La session ne se termine que pour une vraie
     * mise en arrière-plan : pendant un changement de configuration (rotation, thème du système), l’activité
     * est recréée aussitôt et la session continue.
     */
    fun onStop(changingConfigurations: Boolean = false) {
        positionSaver.flush()
        if (!changingConfigurations) sessionCoordinator?.onBackgrounded()
    }

    override fun onCleared() {
        // Sortie du lecteur (retour à la bibliothèque = popBackStack) : dernière écriture de la session.
        sessionCoordinator?.close()
        _uiState.value.publication?.close()
    }

    /** Attend la dernière écriture de la session après [onCleared] (tests : avant de fermer la base). */
    @VisibleForTesting
    internal suspend fun awaitSessionWrites() {
        sessionCoordinator?.awaitClosed()
    }

    companion object {
        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication).container
                ReaderViewModel(
                    bookId = bookId,
                    books = container.books,
                    sessions = container.sessions,
                    openPublication = container.readiumOpener::open,
                    clock = container.clock,
                )
            }
        }
    }
}

/** Pourcentage entier arrondi vers le bas (« 31 % lu »). */
fun readingPercent(progression: Double): Int = floor(progression.coerceIn(0.0, 1.0) * 100).toInt()

internal fun List<Link>.toTocNodes(): List<TocNode> = mapIndexed { index, link ->
    TocNode(
        title = link.displayTitle(index),
        href = link.url().removeFragment().toString(),
        children = link.children.toTocNodes(),
    )
}

/** Titre du sommaire, ou nom du fichier pour un EPUB sans titres (sommaire absent : ordre de lecture). */
private fun Link.displayTitle(index: Int): String =
    title?.takeIf { it.isNotBlank() }
        ?: url().removeFragment().toString().substringAfterLast('/').ifBlank { (index + 1).toString() }
