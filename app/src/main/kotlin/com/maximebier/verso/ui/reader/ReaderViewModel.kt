package com.maximebier.verso.ui.reader

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.R
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.journal.SessionRecord
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.core.position.TrackerEffect
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.core.text.LocationTexts
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.TocProgress
import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import com.maximebier.verso.core.text.calibrateAnchor
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.core.text.chapterPathOfEntry
import com.maximebier.verso.core.text.longLocation
import com.maximebier.verso.core.text.preorder
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.core.text.shortLocation
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.reader.ChapterText
import com.maximebier.verso.reader.PageInfo
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.reader.ReaderGestures
import com.maximebier.verso.reader.readChapterHtml
import com.maximebier.verso.reader.sameResource
import com.maximebier.verso.readium.BookSearch
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadingOrderPositions
import com.maximebier.verso.readium.ReadingStyle
import com.maximebier.verso.readium.SearchHit
import com.maximebier.verso.readium.TocAnchors
import com.maximebier.verso.ui.common.highlightLocation
import com.maximebier.verso.ui.common.notesTexts
import com.maximebier.verso.ui.notes.NotesEvent
import com.maximebier.verso.ui.notes.NotesListModel
import com.maximebier.verso.ui.notes.NotesTexts
import com.maximebier.verso.ui.notes.NotesUiState
import com.maximebier.verso.ui.notes.plainNotesTexts
import com.maximebier.verso.ui.common.locationTexts
import com.maximebier.verso.ui.common.percentOf
import com.maximebier.verso.ui.library.OpenFailures
import com.maximebier.verso.ui.reader.search.BookSearchModel
import com.maximebier.verso.ui.reader.search.SearchUiState
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.shared.publication.Layout
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
    /** Positions de l’ordre de lecture (progression totale fine), calculées une fois et partagées avec la surface. */
    val readingPositions: ReadingOrderPositions? = null,
    /** Locator de création de la surface : position sauvegardée, puis dernière position affichée. */
    val initialLocator: Locator? = null,
    val bookTitle: String = "",
    val totalWords: Long = 0,
    val toc: List<TocNode> = emptyList(),
    val readingOrderHrefs: List<String> = emptyList(),
    /** Fichier (sans fragment) de la position de lecture. */
    val currentHref: String? = null,
    /** Progression dans ce fichier de la position de lecture (chapitres ancrés, voir `chapterPathAt`). */
    val currentProgression: Double? = null,
    val chapterPath: List<String> = emptyList(),
    val shortLocation: String? = null,
    /** Progression totale de la position de LECTURE (jamais de la position affichée). */
    val readingProgression: Double = 0.0,
    val readingPercent: Int = 0,
    val remainingMinutes: Int = 0,
    val wordsPerMinute: Int = DEFAULT_WORDS_PER_MINUTE,
    val barsVisible: Boolean = false,
    val tocVisible: Boolean = false,
    val journalVisible: Boolean = false,
    /** Feuille « Réglages de lecture » (2.02) ouverte. */
    val settingsVisible: Boolean = false,
    /** Écran « Recherche dans le livre » (2.06) affiché par-dessus le texte. */
    val searchVisible: Boolean = false,
    /** Défilement de ce livre (`books.scrollMode`), à défaut celui des Paramètres (`defaultScrollMode`). */
    val scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
    /** Mode pages : « Page 2 sur 9 » du chapitre affiché ; null en continu ou avant le premier compte. */
    val pageInfo: PageInfo? = null,
    /** Chapitre de la position AFFICHÉE (pied de page du mode pages) ; la barre montre celui de la lecture. */
    val displayedChapterPath: List<String> = emptyList(),
    /** Carte « Revenir » ; jamais persistée. */
    val returnCard: ReturnCardState? = null,
    /** Surcouche « Notes et surlignages » (3.06) ouverte depuis la barre. */
    val notesVisible: Boolean = false,
    /** Réglages de lecture courants (police, taille, interligne, marges). */
    val readingSettings: ReadingSettings = ReadingSettings(),
)

class ReaderViewModel(
    private val bookId: Long,
    private val books: BookRepository,
    private val sessions: SessionRepository,
    private val openPublication: suspend (File) -> Result<Publication>,
    private val clock: () -> Long,
    /** Gabarits des emplacements (« Partie II, chap. I », « Deuxième partie, chapitre I »), lus dans les ressources. */
    private val locationTexts: LocationTexts,
    /** Livre impossible à ouvrir (fichier illisible, moteur qui le refuse) : titre signalé à la bibliothèque. */
    private val reportOpenFailure: (title: String) -> Unit = {},
    /** Réglages de lecture (appliqués au lecteur à chaque changement) et thème, lus et écrits depuis la feuille « Aa ». */
    private val settings: SettingsRepository,
    /** Ouvert par « Voir le journal de lecture » de la fiche : feuille du journal affichée au chargement. */
    private val openJournalOnLoad: Boolean = false,
    /** Recherche plein texte d’une publication ouverte ([BookSearch]) ; les tests la remplacent par une recherche pilotée. */
    private val searchIn: (Publication) -> (String) -> Flow<List<SearchHit>> = { publication -> BookSearch(publication)::search },
    /** Surlignages et notes (V3). */
    private val highlightRepository: HighlightRepository,
    /** « Deuxième partie, chap. I · 30 % » d’un surlignage, lu dans les ressources. */
    private val highlightLocationText: (location: String?, percent: Int) -> String =
        { location, percent -> listOfNotNull(location, "$percent %").joinToString(" · ") },
    /** Textes de la liste des notes et de l’export (3.06). */
    private val notesTexts: NotesTexts = plainNotesTexts(),
    /** Écriture du fichier choisi par le sélecteur d’Android (export Markdown). */
    private val writeDocument: suspend (Uri, String) -> Unit = { _, _ -> },
    /** Ouvert depuis « Notes et surlignages » de la fiche : saut vers ce surlignage au chargement. */
    private val openHighlightOnLoad: Long? = null,
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
        chapterTitle = ::chapterTitleOf,
    )
    private var coordinator: ReadingPositionCoordinator? = null
    private var controller: ReaderController? = null
    private var controllerJob: Job? = null
    private var calibrationJob: Job? = null

    /** Entre `ON_STOP` et `ON_START` : aucun battement d’horloge (coordinateur de position). */
    private var stopped = false

    /** Saut demandé sans surface prête (activité recréée) : appliqué au prochain [onReaderReady]. */
    private var pendingJump: PendingJump? = null
    private var tocLinks: List<Link> = emptyList()
    private var positions: ReadingOrderPositions? = null
    private var metrics: ReaderMetrics? = null
    private var appliedMetrics: ReaderMetrics? = null

    /** Recherche plein texte du livre ouvert (2.06) ; créée avec la publication ([BookSearch]). */
    private var bookSearch: ((String) -> Flow<List<SearchHit>>)? = null

    private val searchModel = BookSearchModel(
        scope = viewModelScope,
        search = { query -> bookSearch?.invoke(query) ?: emptyFlow() },
        readingOrderHrefs = { _uiState.value.readingOrderHrefs },
        chapterLabel = { hit -> chapterTitleOf(hit.locator) },
    )

    /** Requête et résultats de la recherche ; gardés tant que le lecteur est ouvert. */
    val search: StateFlow<SearchUiState> = searchModel.state

    // --- Surlignages et notes (V3) ---------------------------------------------------------------
    private val chapterTexts = HashMap<String, String>()
    private var highlightCoordinator: HighlightCoordinator? = null
    private val highlightState = MutableStateFlow(HighlightUiState())

    /** Barre de sélection, feuilles de note et de surlignage. */
    val highlights: StateFlow<HighlightUiState> = highlightState.asStateFlow()
    private val highlightEventsFlow = MutableSharedFlow<HighlightEvent>(extraBufferCapacity = 8)
    val highlightEvents: SharedFlow<HighlightEvent> = highlightEventsFlow.asSharedFlow()

    /** Palette affichée, reçue de ReaderScreen ; null tant qu’elle n’est pas connue (rien n’est envoyé au lecteur). */
    private var theme: AppTheme? = null

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
                    journalUiState(list, current, _uiState.value.toc, clock(), ZoneId.systemDefault(), locationTexts, ::positionOfLocatorJson)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Thème choisi (Automatique, Clair, Sépia, Sombre, Nuit), pour la feuille « Aa ». */
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTO)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            sessions.observeStats(bookId).collect { stats ->
                val rate = stats.effectiveWordsPerMinute
                _uiState.update { it.copy(wordsPerMinute = rate, remainingMinutes = remainingMinutes(it.totalWords, it.readingProgression, rate)) }
            }
        }
        viewModelScope.launch {
            settings.readingSettings.collect { reading ->
                _uiState.update { it.copy(readingSettings = reading) }
                submitStyle()
                refreshDistance()
            }
        }
    }

    private suspend fun load() {
        val book = books.book(bookId)
        if (book == null) {
            _uiState.update { it.copy(loading = false, failed = true) }
            return
        }
        val publication = openPublication(File(book.filePath)).getOrElse {
            fail(book.title)
            return
        }
        try {
            start(book, publication)
        } catch (t: Throwable) {
            // Ouverture annulée (retour pendant le chargement) ou ratée : l’archive est refermée tout de suite.
            if (_uiState.value.publication !== publication) publication.close()
            throw t
        }
    }

    private suspend fun start(book: BookEntity, publication: Publication) {
        if (publication.metadata.layout == Layout.FIXED) {
            // Mise en page fixe, hors V1 : refusée avant de devenir le dernier livre ouvert (rouvert en boucle sinon).
            publication.close()
            fail(book.title)
            return
        }
        tocLinks = publication.tableOfContents.ifEmpty { publication.readingOrder }
        positions = ReadingOrderPositions.load(publication)
        bookSearch = searchIn(publication)
        startHighlights(publication)
        val anchors = TocAnchors.load(publication, tocLinks)
        val saved = book.readingLocatorJson?.let(Locators::fromJson)
        val start = saved ?: publication.readingOrder.firstOrNull()?.let(publication::locatorFromLink)
        val initialPosition = when {
            saved != null && book.readingLocatorJson != null -> BookPosition(book.readingLocatorJson, book.progression)
            start != null -> Locators.toPosition(start)
            else -> null
        }
        if (initialPosition == null) {
            publication.close()
            fail(book.title)
            return
        }
        // Réglages lus avant de publier `publication` : la surface naît avec eux, sans relayout à la première image.
        val reading = settings.readingSettings.first()
        // Lu une fois à l’ouverture ; ensuite, seul setScrollMode le change (même moment que l’écriture en base).
        val scrollMode = book.scrollMode?.let { stored -> ScrollMode.entries.firstOrNull { it.name == stored } }
            ?: reading.defaultScrollMode
        val created = ReadingPositionCoordinator(
            initial = initialPosition,
            distance = ReaderScreenDistance.fallbackDistance(book.totalWords),
            scope = viewModelScope,
            clock = clock,
            onSave = positionSaver::requestSave,
            thresholds = ReadingThresholds.forScrollMode(scrollMode),
        )
        coordinator = created
        if (stopped) created.onStopped()
        _uiState.update {
            it.copy(
                publication = publication,
                readingSettings = reading,
                scrollMode = scrollMode,
                readingPositions = positions,
                initialLocator = saved,
                bookTitle = book.title,
                totalWords = book.totalWords,
                toc = tocLinks.toTocNodes(anchors),
                readingOrderHrefs = publication.readingOrder.map { link -> link.url().removeFragment().toString() },
            )
        }
        onPositionState(created.state.value)
        // `loading = false` en dernier : l’état publié est alors complet (progression comprise).
        _uiState.update { it.copy(loading = false) }
        if (openJournalOnLoad) showJournal()
        openHighlightOnLoad?.let(::openHighlight)
        viewModelScope.launch { created.state.collect(::onPositionState) }
        // Relais dans l’ordre d’émission ; lancé avant attach() (onReaderReady), donc aucun effet perdu.
        viewModelScope.launch { created.readingEffects.collect { readingEffectsFlow.emit(it) } }
        startSessions(book.totalWords, initialPosition)
        // Livre réellement ouvert : il devient le dernier lu (carte « Reprendre », réouverture au lancement).
        books.markOpened(bookId, clock())
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
        viewModelScope.launch { sessions.deleteDiscarded() }
        viewModelScope.launch { coordinator.current.collect { sessionCurrent.value = it } }
        viewModelScope.launch { readingEffects.collect(coordinator::onTrackerEffect) }
        coordinator.onOpened(initial)
        // Arrière-plan pendant le chargement : pas de session ni de battement avant le retour au premier plan.
        if (stopped) coordinator.onBackgrounded()
        coordinator.start()
    }

    private fun startHighlights(publication: Publication) {
        if (highlightCoordinator != null) return
        val created = HighlightCoordinator(
            bookId = bookId,
            highlights = highlightRepository,
            scope = viewModelScope,
            chapterText = { locator -> chapterPlainText(publication, locator) },
            chapterPath = { locator -> chapterPathAt(_uiState.value.toc, Locators.hrefKey(locator), locator.locations.progression) },
            locationLabel = { row -> highlightLocationText(shortLocation(row.chapterPathList(), locationTexts), percentOf(row.progression)) },
            withTotalProgression = ::withTotalProgression,
            clock = clock,
        )
        highlightCoordinator = created
        viewModelScope.launch {
            created.state.collect { state ->
                highlightState.value = state
                // La barre de sélection et la feuille d’un surlignage remplacent la barre de lecture.
                if (state.selectionText != null || state.actions != null) _uiState.update { it.copy(barsVisible = false) }
            }
        }
        viewModelScope.launch { created.events.collect { highlightEventsFlow.emit(it) } }
        controller?.let(created::attach)
    }

    /** Texte brut normalisé du fichier d’un locator, lu une fois par fichier. */
    private suspend fun chapterPlainText(publication: Publication, locator: Locator): String? {
        val key = Locators.hrefKey(locator)
        chapterTexts[key]?.let { return it }
        val html = readChapterHtml(publication, locator.href.removeFragment()) ?: return null
        val text = withContext(Dispatchers.Default) { TextQuotes.normalize(ChapterText.plainText(html)) }
        chapterTexts[key] = text
        return text
    }

    fun highlightSelection() {
        sessionCoordinator?.onInteraction()
        highlightCoordinator?.highlightSelection()
    }

    fun copySelection() {
        sessionCoordinator?.onInteraction()
        highlightCoordinator?.copySelection()
    }

    fun copyHighlight() {
        highlightCoordinator?.copyHighlight()
    }

    fun dismissHighlightActions() {
        highlightCoordinator?.dismissActions()
    }

    /** « Note » de la barre de sélection : feuille 3.05. */
    fun noteForSelection() {
        sessionCoordinator?.onInteraction()
        highlightCoordinator?.noteForSelection()
    }

    fun editNote() {
        highlightCoordinator?.editNote()
    }

    fun onNoteChange(text: String) {
        highlightCoordinator?.onNoteChange(text)
    }

    fun saveNote() {
        sessionCoordinator?.onInteraction()
        highlightCoordinator?.saveNote()
    }

    fun cancelNote() {
        highlightCoordinator?.cancelNote()
    }

    fun deleteHighlight() {
        sessionCoordinator?.onInteraction()
        highlightCoordinator?.deleteHighlight()
    }

    /** « Annuler » de la snackbar « Surlignage supprimé ». */
    fun undoDeleteHighlight(row: HighlightEntity) {
        highlightCoordinator?.undoDelete(row)
    }

    // --- « Notes et surlignages » (3.06) ----------------------------------------------------------
    private val notesModel = NotesListModel(
        bookId = bookId,
        highlights = highlightRepository,
        books = books,
        scope = viewModelScope,
        texts = notesTexts,
        writeText = { uri, text -> writeDocument(uri, text) },
        clock = clock,
    )
    val notes: StateFlow<NotesUiState> = notesModel.state
    val notesEvents: SharedFlow<NotesEvent> = notesModel.events

    /** « Notes » de la barre : surcouche 3.06, barre de lecture fermée. */
    fun showNotes() {
        sessionCoordinator?.onInteraction()
        _uiState.update { it.copy(notesVisible = true, barsVisible = false) }
    }

    fun hideNotes() = _uiState.update { it.copy(notesVisible = false) }

    /** Élément touché (ou « Aller au passage ») : surcouche fermée, saut explicite (carte « Revenir »). */
    fun openHighlight(id: Long) {
        viewModelScope.launch {
            val row = highlightRepository.get(id) ?: return@launch
            val target = Locators.fromJson(row.locatorJson) ?: return@launch
            _uiState.update { it.copy(notesVisible = false, barsVisible = false) }
            sessionCoordinator?.onInteraction()
            jumpTo(target)
        }
    }

    fun exportNotes(uri: Uri) = notesModel.export(uri)
    fun editNoteFromList(id: Long) = notesModel.editNote(id)
    fun onListNoteChange(text: String) = notesModel.onNoteChange(text)
    fun saveListNote() = notesModel.saveNote()
    fun cancelListNote() = notesModel.cancelNote()
    fun deleteFromList(id: Long) = notesModel.delete(id)
    fun undoDeleteFromList(row: HighlightEntity) = notesModel.undoDelete(row)

    /** Retour système pendant une sélection : elle s’efface, le livre reste ouvert. */
    fun clearSelection() {
        controller?.clearSelection()
    }

    fun onReaderReady(readerController: ReaderController) {
        if (controller === readerController) return
        controller = readerController
        highlightCoordinator?.attach(readerController)
        submitStyle()
        positionSaver.attach(readerController)
        coordinator?.attach(readerController)
        controllerJob?.cancel()
        controllerJob = viewModelScope.launch {
            // Un geste de l’utilisateur : la position affichée n’est plus celle d’une ancre visée par un saut,
            // et le mot trouvé par la recherche n’est plus marqué (glissé, fling ou tour de page).
            launch {
                readerController.gestures.collect {
                    cancelCalibration()
                    clearSearchMatch()
                }
            }
            launch {
                readerController.pageInfo.collect { info ->
                    _uiState.update { state ->
                        val shown = lastDisplayed
                        state.copy(
                            pageInfo = info,
                            displayedChapterPath = if (shown != null) displayedChapterPath(state.toc, shown, info) else state.displayedChapterPath,
                        )
                    }
                }
            }
            readerController.displayed.filterNotNull().collect { locator ->
                val previous = lastDisplayed
                lastDisplayed = locator
                if (previous != null && previous != locator) sessionCoordinator?.onInteraction()
                // Reprise au même endroit si la surface est recréée (rotation, thème) ; chapitre du pied de page.
                _uiState.update { state ->
                    state.copy(
                        initialLocator = locator,
                        displayedChapterPath = displayedChapterPath(state.toc, locator, readerController.pageInfo.value),
                    )
                }
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
        cancelCalibration()
        controller = null
        highlightCoordinator?.detach()
        // La prochaine surface n’a pas la décoration.
        searchMatchShown = false
        coordinator?.detach()
        // Le premier locator de la prochaine surface est une arrivée, pas un scroll (journal, 6.4).
        lastDisplayed = null
    }

    /** Échelle de police et densité de l’écran (envoyées par ReaderScreen). */
    fun onDisplayMetrics(fontScale: Float, density: Float) {
        metrics = ReaderMetrics(viewportHeightPx = 0, fontScale = fontScale, density = density)
        refreshDistance()
    }

    /** Palette affichée (réglage Thème de Verso, ou celle du téléphone en automatique), envoyée par ReaderScreen. */
    fun onThemeChanged(theme: AppTheme) {
        if (this.theme == theme) return
        this.theme = theme
        submitStyle()
        // Les thèmes foncés espacent davantage les lignes (ReadingStyle.lineHeight).
        refreshDistance()
    }

    /** Réglages courants vers le moteur : jamais un saut ni un geste pour la machine à états. */
    private fun submitStyle() {
        val reader = controller ?: return
        val current = theme ?: return
        reader.submit(_uiState.value.readingSettings, current, _uiState.value.scrollMode)
    }

    /** Distance en écrans recalculée quand la surface est mesurée, que l’échelle ou les réglages du texte changent. */
    private fun refreshDistance() {
        val base = metrics ?: return
        val height = controller?.viewportHeightPx ?: return
        if (height <= 0) return
        val settings = _uiState.value.readingSettings
        val current = base.copy(
            viewportHeightPx = height,
            fontSizeSp = settings.fontSizeSp.toDouble(),
            lineHeight = ReadingStyle.lineHeight(settings.lineSpacing.factor, dark = theme?.isDark == true),
            marginDp = settings.margins.dp,
        )
        if (current == appliedMetrics) return
        appliedMetrics = current
        coordinator?.updateDistance(ReaderScreenDistance.distance(_uiState.value.totalWords, current))
    }

    /** Barre, sommaire et carte montrent toujours la position de LECTURE. */
    private fun onPositionState(position: PositionState) {
        val reading = Locators.fromJson(position.reading.locatorJson)
        val href = reading?.let(Locators::hrefKey)
        val inFile = reading?.locations?.progression
        val progression = position.reading.totalProgression
        _uiState.update { state ->
            val path = href?.let { chapterPathAt(state.toc, it, inFile) } ?: emptyList()
            val location = shortLocation(path, locationTexts)
            val percent = percentOf(progression)
            state.copy(
                currentHref = href,
                currentProgression = inFile,
                chapterPath = path,
                shortLocation = location,
                readingProgression = progression,
                readingPercent = percent,
                remainingMinutes = remainingMinutes(state.totalWords, progression, state.wordsPerMinute),
                returnCard = if (position.showReturnCard) ReturnCardState(location, percent) else null,
            )
        }
    }

    /**
     * Chapitre du pied de page : en mode pages, celui dont l’ancre a été mesurée dans la page ([PageInfo.chapterAnchor],
     * le même découpage que « Page x sur y ») ; sinon, ou sans ancre, l’estimation par la progression ([chapterPathAt]).
     */
    private fun displayedChapterPath(toc: List<TocNode>, locator: Locator, info: PageInfo?): List<String> {
        val anchor = info?.chapterAnchor
        if (anchor != null) {
            val index = preorder(tocLinks) { it.children }
                .indexOfFirst { it.url().fragment == anchor && sameResource(it.url(), locator.href) }
            if (index >= 0) return chapterPathOfEntry(toc, index)
        }
        return chapterPathAt(toc, Locators.hrefKey(locator), locator.locations.progression)
    }

    /** Titre de chapitre d’une position, même règle que la barre de lecture ([chapterPathAt], ancres comprises). */
    private fun chapterTitleOf(locator: Locator): String? =
        longLocation(chapterPathAt(_uiState.value.toc, Locators.hrefKey(locator), locator.locations.progression), locationTexts.join)

    /** Le moteur refuse le livre (mise en page fixe…) : retour à la bibliothèque, avec un message. */
    fun onEngineFailed() {
        if (_uiState.value.failed) return
        fail(_uiState.value.bookTitle)
    }

    /** Retour à la bibliothèque (ReaderScreen suit `failed`) ; la bibliothèque affiche `library_open_failed`. */
    private fun fail(title: String) {
        reportOpenFailure(title)
        _uiState.update { it.copy(loading = false, failed = true) }
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

    /** « Aa » : la feuille s’ouvre, la barre se referme (maquette 2.02 : texte seul derrière la feuille). */
    fun showReadingSettings() {
        sessionCoordinator?.onInteraction()
        _uiState.update { it.copy(settingsVisible = true, barsVisible = false) }
    }

    fun hideReadingSettings() = _uiState.update { it.copy(settingsVisible = false) }

    /** Écran « Recherche » (2.06) : la barre et la feuille de réglages se referment. */
    fun showSearch() {
        sessionCoordinator?.onInteraction()
        _uiState.update { it.copy(searchVisible = true, barsVisible = false, tocVisible = false, settingsVisible = false) }
        // Recherche arrêtée à la fermeture avant sa fin : elle reprend.
        searchModel.resume()
    }

    /** Recherche fermée (retour, retour système) : elle ne parcourt plus le livre ; requête et résultats restent. */
    fun hideSearch() {
        searchModel.cancel()
        _uiState.update { it.copy(searchVisible = false) }
    }

    fun onSearchQueryChange(query: String) = searchModel.onQueryChange(query)

    fun clearSearch() = searchModel.clear()

    /** Mot marqué dans le texte par [openSearchResult] ; effacé au premier geste ou au prochain saut. */
    private var searchMatchShown = false

    /**
     * Résultat de recherche touché : recherche (arrêtée, comme [hideSearch]) et barre fermées, saut explicite ([jumpTo] : carte « Revenir »,
     * position de lecture inchangée), puis le mot est marqué dans le texte.
     */
    fun openSearchResult(hit: SearchHit) {
        searchModel.cancel()
        _uiState.update { it.copy(searchVisible = false, barsVisible = false) }
        sessionCoordinator?.onInteraction()
        jumpTo(hit.locator)
        val readerController = controller ?: return
        // Même locator que le saut (progression totale comprise).
        val target = withTotalProgression(hit.locator)
        searchMatchShown = true
        viewModelScope.launch { readerController.showSearchMatch(target) }
    }

    private fun clearSearchMatch() {
        if (!searchMatchShown) return
        searchMatchShown = false
        val readerController = controller ?: return
        viewModelScope.launch { readerController.showSearchMatch(null) }
    }

    /** Police, taille, interligne, marges : communs à tous les livres (spec) ; appliqués en direct par la collecte des réglages. */
    fun updateReadingSettings(transform: (ReadingSettings) -> ReadingSettings) {
        sessionCoordinator?.onInteraction()
        viewModelScope.launch { settings.updateReadingSettings(transform) }
    }

    /** Thème de toute l’app (même réglage que les Paramètres) ; revient par `onThemeChanged` via `VersoTheme.theme`. */
    fun setThemeMode(mode: ThemeMode) {
        sessionCoordinator?.onInteraction()
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /**
     * Défilement de ce livre (feuille « Aa ») : mémorisé pour ce livre seulement, et `uiState.scrollMode` changé au
     * même moment. Les seuils de la machine à états suivent le mode ; le passage au moteur reste [submitStyle], dont
     * la remise en page ramène le texte affiché (`FragmentReaderController.relayout`, [ReaderGestures.MODE_SWITCH_SETTLE_MS]) :
     * ni saut ni geste pour la machine à états, la position de lecture ne bouge pas.
     */
    fun setScrollMode(mode: ScrollMode) {
        if (_uiState.value.scrollMode == mode) return
        sessionCoordinator?.onInteraction()
        coordinator?.updateThresholds(ReadingThresholds.forScrollMode(mode))
        _uiState.update { it.copy(scrollMode = mode, pageInfo = if (mode == ScrollMode.PAGES) it.pageInfo else null) }
        viewModelScope.launch { books.setScrollMode(bookId, mode) }
        submitStyle()
    }

    /** Page suivante ou précédente (actions TalkBack du pied de page) ; sans effet en continu. */
    fun turnPage(forward: Boolean) {
        controller?.turn(forward)
    }

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
        stopped = false
        coordinator?.onStarted()
        sessionCoordinator?.onStarted()
    }

    /** Saut par le sommaire ; `index` = rang dans le préordre du sommaire (`preorder`, même parcours que `buildTocRows`). */
    fun jumpToTocEntry(index: Int) {
        val publication = _uiState.value.publication ?: return
        val link = preorder(tocLinks) { it.children }.getOrNull(index) ?: return
        // Entrée hors de l’ordre de lecture (linear="no") : le moteur ne l’afficherait pas.
        if (publication.readingOrder.none { sameResource(it.url(), link.url()) }) return
        val target = publication.locatorFromLink(link) ?: return
        if (controller == null) {
            // Sommaire laissé ouvert : il se ferme quand le saut part vraiment.
            pendingJump = PendingJump(target, closesToc = true)
            return
        }
        closeTocAndBars()
        jump(target, calibrateEntry = index)
    }

    private fun closeTocAndBars() = _uiState.update { it.copy(tocVisible = false, barsVisible = false) }

    /**
     * Saut explicite (sommaire ; « Reprendre ici » du journal en 6.4) : `ReaderEvent.Jumped` puis
     * `controller.go(locator)`, via le coordinateur. Navigation : ne touche pas la position de lecture.
     * La cible reçoit sa progression totale (un lien de sommaire n’en a pas), sinon un saut vers le
     * chapitre courant paraîtrait lointain.
     */
    fun jumpTo(locator: Locator) = jump(locator, calibrateEntry = null)

    /** [jumpTo], avec calibrage de l’entrée de sommaire n° [calibrateEntry] (préordre) si elle est ancrée. */
    private fun jump(locator: Locator, calibrateEntry: Int?) {
        // Tout saut efface l’ancienne marque ; openSearchResult pose la sienne après.
        clearSearchMatch()
        val readerController = controller
        if (readerController == null) {
            pendingJump = PendingJump(locator, closesToc = false)
            return
        }
        cancelCalibration()
        // Avant le saut : la position affichée au moment du saut n’est pas celle de l’ancre.
        calibrateEntry?.let { startCalibration(it, readerController) }
        coordinator?.onJump(withTotalProgression(locator))
    }

    /**
     * Calibrage d’une ancre du sommaire (sa position n’est qu’estimée sur le texte, voir `TocAnchors`) : après un
     * saut vers elle, la première progression affichée dans son fichier qui reste immobile
     * [TocProgress.CALIBRATION_SETTLE_MS] devient son début pour la session, si [calibrateAnchor] l’accepte
     * (écart plausible, ordre des ancres gardé). Abandonné au premier geste, à un autre saut, au retour,
     * ou après [TocProgress.CALIBRATION_WINDOW_MS].
     */
    @OptIn(FlowPreview::class)
    private fun startCalibration(index: Int, readerController: ReaderController) {
        val entry = preorder(_uiState.value.toc) { it.children }.getOrNull(index) ?: return
        if (entry.progression == null) return
        calibrationJob = viewModelScope.launch {
            val calibrated = withTimeoutOrNull(TocProgress.CALIBRATION_WINDOW_MS) {
                readerController.displayed
                    .drop(1)
                    .filterNotNull()
                    .debounce(TocProgress.CALIBRATION_SETTLE_MS)
                    .filter { Locators.hrefKey(it) == entry.href }
                    .mapNotNull { locator ->
                        locator.locations.progression?.let { calibrateAnchor(_uiState.value.toc, index, it) }
                    }
                    .first()
            } ?: return@launch
            _uiState.update { it.copy(toc = calibrated) }
            coordinator?.let { onPositionState(it.state.value) }
        }
    }

    private fun cancelCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
    }

    /**
     * Lien interne touché dans le texte, avant que le moteur ne le suive lui-même (spec : saut
     * explicite). Annoncé à la machine à états seulement ; le déplacement reste celui du moteur.
     */
    fun onInternalLinkFollowed(url: Url) {
        clearSearchMatch()
        val publication = _uiState.value.publication ?: return
        val target = publication.locatorFromLink(Link(href = url)) ?: return
        cancelCalibration()
        coordinator?.onLinkFollowed(withTotalProgression(target))
    }

    private fun withTotalProgression(locator: Locator): Locator = positions?.withTotalProgression(locator) ?: locator

    fun stayHere() {
        coordinator?.onStayHere()
    }

    fun goBack() {
        clearSearchMatch()
        cancelCalibration()
        coordinator?.onGoBack()
    }

    private data class PendingJump(val target: Locator, val closesToc: Boolean)

    /**
     * `ON_STOP` de l’écran. La position est toujours enregistrée. La session ne se termine que pour une vraie
     * mise en arrière-plan : pendant un changement de configuration (rotation, thème du système), l’activité
     * est recréée aussitôt et la session continue.
     */
    fun onStop(changingConfigurations: Boolean = false) {
        // Repos forcé d’abord : le dernier glissé, pas encore validé, est enregistré par le flush qui suit.
        // Aucun battement d’horloge en arrière-plan (revue finale M5) ; il reprend à onStart.
        stopped = true
        coordinator?.onStopped()
        positionSaver.flush()
        if (!changingConfigurations) sessionCoordinator?.onBackgrounded()
    }

    override fun onCleared() {
        searchModel.cancel()
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
        fun factory(bookId: Long, openJournal: Boolean = false, highlightId: Long? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication
                val container = app.container
                ReaderViewModel(
                    bookId = bookId,
                    books = container.books,
                    sessions = container.sessions,
                    openPublication = container.readiumOpener::open,
                    clock = container.clock,
                    locationTexts = app.resources.locationTexts(),
                    reportOpenFailure = OpenFailures::report,
                    settings = container.settings,
                    openJournalOnLoad = openJournal,
                    highlightRepository = container.highlights,
                    highlightLocationText = { location, percent -> app.resources.highlightLocation(location, percent) },
                    notesTexts = app.resources.notesTexts(),
                    writeDocument = container.documents::writeText,
                    openHighlightOnLoad = highlightId,
                )
            }
        }
    }
}

/**
 * Sommaire Readium → [TocNode]. `anchors` ([TocAnchors.load]) : début estimé de chaque lien ancré dans son
 * fichier et longueur du texte de ce fichier, par href complet ; un lien sans ancre (ou ancre introuvable) reste
 * au début du fichier.
 */
internal fun List<Link>.toTocNodes(anchors: Map<String, TocAnchors.Anchor> = emptyMap()): List<TocNode> =
    mapIndexed { index, link ->
        TocNode(
            title = link.displayTitle(index),
            href = link.url().removeFragment().toString(),
            children = link.children.toTocNodes(anchors),
            progression = anchors[link.url().toString()]?.progression,
            fileChars = anchors[link.url().toString()]?.fileChars,
        )
    }

/** Titre du sommaire, ou nom du fichier pour un EPUB sans titres (sommaire absent : ordre de lecture). */
private fun Link.displayTitle(index: Int): String =
    title?.takeIf { it.isNotBlank() }
        ?: url().removeFragment().toString().substringAfterLast('/').ifBlank { (index + 1).toString() }
