package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.notes.HighlightMerge
import com.maximebier.verso.core.notes.PlacedHighlight
import com.maximebier.verso.core.notes.TextQuote
import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.core.translation.Translation
import com.maximebier.verso.core.translation.TranslationResult
import com.maximebier.verso.core.translation.Translator
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.toChapterPathColumn
import com.maximebier.verso.reader.HighlightMark
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.reader.TextSelection
import com.maximebier.verso.readium.Locators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.publication.Locator

data class HighlightUiState(
    val selectionText: String? = null,
    val noteSheet: NoteSheetState? = null,
    val actions: HighlightActionsState? = null,
    val translation: TranslationSheetState? = null,
    /** Faux sans clé de traduction : « Traduire » n’apparaît pas. */
    val canTranslate: Boolean = false,
)

/**
 * Feuille de traduction (3.08 à 3.10) : [source] est le passage sélectionné (espaces normalisées) ; [short] = mot ou
 * expression de 3 mots au plus, montré en tête de feuille ; [result] null pendant le chargement.
 */
data class TranslationSheetState(val source: String, val short: Boolean, val result: TranslationResult?)

/** Feuille de note (3.05) : passage, texte du champ ; [editing] = note existante. */
data class NoteSheetState(val passage: String, val note: String, val editing: Boolean)

/** Feuille d’une note touchée dans le texte : passage, note, « Deuxième partie, chap. I · 30 % ». */
data class HighlightActionsState(val id: Long, val passage: String, val note: String?, val location: String?)

sealed interface HighlightEvent {
    data class Copied(val text: String) : HighlightEvent
    data class Deleted(val row: HighlightEntity) : HighlightEvent
}

/**
 * Sélection et notes du livre ouvert (V3) : barre de sélection (3.04), traduction (3.08 à 3.10), note créée et
 * fusionnée, toucher d’une note. Un passage n’est marqué que s’il porte une note : les surlignages sans note d’avant
 * le 2026-10-04 restent en base, masqués par [HighlightRepository]. Séparé de [ReaderViewModel] comme
 * `SessionCoordinator`. Rien ici ne touche la position de lecture : la machine à états voit la sélection par
 * `ReaderController.selecting` (17.3).
 */
class HighlightCoordinator(
    private val bookId: Long,
    private val highlights: HighlightRepository,
    private val scope: CoroutineScope,
    private val chapterText: suspend (Locator) -> String?,
    private val chapterPath: (Locator) -> List<String>,
    private val locationLabel: (HighlightEntity) -> String?,
    private val withTotalProgression: (Locator) -> Locator,
    private val clock: () -> Long,
    private val translator: Translator? = null,
) {
    private val _state = MutableStateFlow(HighlightUiState(canTranslate = translator != null))
    val state: StateFlow<HighlightUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HighlightEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HighlightEvent> = _events.asSharedFlow()

    private var controller: ReaderController? = null
    private var jobs: List<Job> = emptyList()
    private val writes = mutableListOf<Job>()

    /** Une écriture à la fois : deux « Enregistrer » rapides ne fusionnent pas sur une liste périmée. */
    private val writeLock = Mutex()

    fun attach(readerController: ReaderController) {
        if (controller === readerController) return
        detach()
        controller = readerController
        jobs = listOf(
            scope.launch {
                readerController.selection.collect { selection ->
                    val text = selection?.text?.takeIf(String::isNotEmpty)
                    // Tap sur le texte ou retour : la sélection s’efface, la feuille de traduction se ferme avec elle.
                    // Poignées déplacées : la traduction affichée n’est plus celle du passage, la barre revient.
                    val shown = _state.value.translation
                    val stale = shown != null && (text == null || Translation.clean(text) != shown.source)
                    if (stale) cancelTranslation()
                    _state.update { it.copy(selectionText = text?.let(TextQuotes::preview), translation = it.translation.takeUnless { stale }) }
                }
            },
            scope.launch {
                highlights.observe(bookId).collect { rows ->
                    val marks = rows.mapNotNull { row -> Locators.fromJson(row.locatorJson)?.let { HighlightMark(row.id, it) } }
                    readerController.showHighlights(marks)
                }
            },
            scope.launch { readerController.highlightTaps.collect(::openActions) },
        )
    }

    fun detach() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        controller = null
        cancelTranslation()
        _state.update { it.copy(selectionText = null, translation = null) }
    }

    private var translationJob: Job? = null

    /**
     * « Traduire » : la feuille s’ouvre aussitôt avec le passage et un indicateur, la barre de sélection s’efface, la
     * sélection reste visible. Une requête par appui, rien n’est gardé.
     */
    fun translateSelection() {
        val reader = controller ?: return
        if (translator == null) return
        scope.launch {
            val text = reader.currentSelection()?.text?.takeIf(String::isNotEmpty) ?: return@launch
            // Sélection effacée pendant la lecture JavaScript (tap, retour) : pas de feuille orpheline.
            if (reader.selection.value == null) return@launch
            request(Translation.clean(text))
        }
    }

    /** « Réessayer » : même passage, nouvelle requête. */
    fun retryTranslation() {
        val source = _state.value.translation?.source ?: return
        request(source)
    }

    /** Croix, glissé vers le bas ou retour : feuille fermée, sélection effacée. */
    fun dismissTranslation() {
        cancelTranslation()
        _state.update { it.copy(translation = null) }
        controller?.clearSelection()
    }

    private fun request(source: String) {
        val service = translator ?: return
        cancelTranslation()
        _state.update { it.copy(translation = TranslationSheetState(source, Translation.isShort(source), result = null)) }
        translationJob = scope.launch {
            val result = Translation.translateSelection(source, service)
            _state.update { state ->
                state.copy(translation = state.translation?.takeIf { it.source == source }?.copy(result = result))
            }
        }
    }

    private fun cancelTranslation() {
        translationJob?.cancel()
        translationJob = null
    }

    /** « Copier » : passage dans le presse-papiers (par l’écran), sélection effacée. */
    fun copySelection() {
        val reader = controller ?: return
        scope.launch {
            val text = reader.currentSelection()?.text
            reader.clearSelection()
            if (!text.isNullOrEmpty()) _events.emit(HighlightEvent.Copied(text))
        }
    }

    fun copyHighlight() {
        val actions = _state.value.actions ?: return
        _state.update { it.copy(actions = null) }
        _events.tryEmit(HighlightEvent.Copied(actions.passage))
    }

    fun dismissActions() = _state.update { it.copy(actions = null) }

    /** « Annuler » après une suppression : la même ligne revient. */
    fun undoDelete(row: HighlightEntity) = launchWrite { highlights.restore(row) }

    /** Cible de la feuille de note ouverte : passage sélectionné (rien n’existe encore) ou note existante. */
    private sealed interface NoteTarget {
        data class Selection(val locator: Locator) : NoteTarget
        data class Existing(val id: Long) : NoteTarget
    }

    private var noteTarget: NoteTarget? = null

    /** « Note » de la barre : feuille 3.05 avec le passage ; rien n’est créé avant « Enregistrer ». */
    fun noteForSelection() {
        val reader = controller ?: return
        launchWrite {
            val selection = reader.currentSelection() ?: return@launchWrite
            reader.clearSelection()
            noteTarget = NoteTarget.Selection(selection.locator)
            _state.update { it.copy(noteSheet = NoteSheetState(selection.text, "", editing = false)) }
        }
    }

    /** « Modifier la note » depuis la feuille d’une note touchée. */
    fun editNote() {
        val actions = _state.value.actions ?: return
        noteTarget = NoteTarget.Existing(actions.id)
        _state.update {
            it.copy(actions = null, noteSheet = NoteSheetState(actions.passage, actions.note.orEmpty(), editing = actions.note != null))
        }
    }

    fun onNoteChange(text: String) = _state.update { state -> state.copy(noteSheet = state.noteSheet?.copy(note = text)) }

    /** « Enregistrer » : note créée (ou fusionnée), ou note existante remplacée. Note vide : rien (bouton inactif). */
    fun saveNote() {
        val sheet = _state.value.noteSheet ?: return
        if (sheet.note.isBlank()) return
        val target = noteTarget ?: return
        noteTarget = null
        _state.update { it.copy(noteSheet = null) }
        launchWrite {
            when (target) {
                is NoteTarget.Selection -> save(target.locator, note = sheet.note)
                is NoteTarget.Existing -> highlights.setNote(target.id, sheet.note)
            }
        }
    }

    /** « Annuler » ou la croix : rien n’est créé ni modifié. */
    fun cancelNote() {
        noteTarget = null
        _state.update { it.copy(noteSheet = null) }
    }

    /** « Supprimer » : immédiat, l’écran propose « Annuler » ([HighlightEvent.Deleted]). */
    fun deleteHighlight() {
        val actions = _state.value.actions ?: return
        _state.update { it.copy(actions = null) }
        launchWrite {
            highlights.delete(actions.id)?.let { _events.emit(HighlightEvent.Deleted(it)) }
        }
    }

    private suspend fun openActions(id: Long) {
        val row = highlights.get(id) ?: return
        _state.update { it.copy(actions = HighlightActionsState(row.id, row.text, row.note, locationLabel(row))) }
    }

    /**
     * Enregistre le passage de [selected] avec [note], fusionné avec les notes qu’il recoupe dans son chapitre
     * (même fichier). Passage introuvable dans le texte brut, ou texte illisible : ajouté tel quel.
     */
    internal suspend fun save(selected: Locator, note: String?): Long = writeLock.withLock {
        val now = clock()
        val quote = TextQuote(
            highlight = selected.text.highlight.orEmpty(),
            before = selected.text.before.orEmpty(),
            after = selected.text.after.orEmpty(),
        )
        val href = Locators.hrefKey(selected)
        val text = chapterText(selected)
        val span = text?.let { TextQuotes.locate(it, quote) }
        if (text == null || span == null) {
            return@withLock highlights.saveMerged(newRow(withTotalProgression(selected), TextQuotes.normalize(quote.highlight), note, now, now), emptyList())
        }
        val sameChapter = highlights.forBook(bookId).filter { row ->
            Locators.fromJson(row.locatorJson)?.let(Locators::hrefKey) == href
        }
        val placed = sameChapter.map { row ->
            val locator = Locators.fromJson(row.locatorJson)
            val rowQuote = TextQuote(
                highlight = locator?.text?.highlight ?: row.text,
                before = locator?.text?.before.orEmpty(),
                after = locator?.text?.after.orEmpty(),
            )
            PlacedHighlight(row.id, TextQuotes.locate(text, rowQuote), row.note)
        }
        val plan = HighlightMerge.plan(span, note, placed)
        val merged = TextQuotes.quoteAt(text, plan.span)
        // Locator reconstruit : Readium retrouve le passage par son texte (sans sélecteur CSS, qui ne couvrirait pas
        // une plage sur plusieurs paragraphes) ; progression du début du passage dans le fichier.
        val locator = withTotalProgression(
            Locator(
                href = selected.href,
                mediaType = selected.mediaType,
                title = selected.title,
                locations = Locator.Locations(progression = plan.span.start.toDouble() / text.length),
                text = Locator.Text(before = merged.before, highlight = merged.highlight, after = merged.after),
            ),
        )
        val createdAt = sameChapter.filter { it.id in plan.absorbedIds }.minOfOrNull { it.createdAt } ?: now
        highlights.saveMerged(newRow(locator, merged.highlight, plan.note, createdAt, now), plan.absorbedIds)
    }

    private fun newRow(locator: Locator, text: String, note: String?, createdAt: Long, now: Long) = HighlightEntity(
        bookId = bookId,
        locatorJson = Locators.toJson(locator),
        text = text,
        note = note?.trim()?.takeIf(String::isNotEmpty),
        progression = (locator.locations.totalProgression ?: 0.0).coerceIn(0.0, 1.0),
        chapterPath = chapterPath(locator).toChapterPathColumn(),
        createdAt = createdAt,
        updatedAt = now,
    )

    private fun launchWrite(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        synchronized(writes) { writes += job }
        job.invokeOnCompletion { synchronized(writes) { writes -= job } }
    }

    /** Tests et fermeture : attend les écritures lancées. */
    suspend fun awaitIdle() {
        val pending = synchronized(writes) { writes.toList() }
        pending.joinAll()
    }
}

