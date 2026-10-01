package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.notes.HighlightMerge
import com.maximebier.verso.core.notes.PlacedHighlight
import com.maximebier.verso.core.notes.TextQuote
import com.maximebier.verso.core.notes.TextQuotes
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
)

/** Feuille de note (3.05) : passage, texte du champ ; [editing] = note d’un surlignage existant. */
data class NoteSheetState(val passage: String, val note: String, val editing: Boolean)

/** Feuille d’un surlignage touché : passage, note, « Deuxième partie, chap. I · 30 % ». */
data class HighlightActionsState(val id: Long, val passage: String, val note: String?, val location: String?)

sealed interface HighlightEvent {
    data class Copied(val text: String) : HighlightEvent
    data class Deleted(val row: HighlightEntity) : HighlightEvent
}

/**
 * Surlignages et notes du livre ouvert (V3) : barre de sélection (3.04), création et fusion, toucher d’un
 * surlignage. Séparé de [ReaderViewModel] comme `SessionCoordinator`. Rien ici ne touche la position de lecture :
 * la machine à états voit la sélection par `ReaderController.selecting` (17.3).
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
) {
    private val _state = MutableStateFlow(HighlightUiState())
    val state: StateFlow<HighlightUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HighlightEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HighlightEvent> = _events.asSharedFlow()

    private var controller: ReaderController? = null
    private var jobs: List<Job> = emptyList()
    private val writes = mutableListOf<Job>()

    /** Une écriture à la fois : deux « Surligner » rapides ne fusionnent pas sur une liste périmée. */
    private val writeLock = Mutex()

    fun attach(readerController: ReaderController) {
        if (controller === readerController) return
        detach()
        controller = readerController
        jobs = listOf(
            scope.launch {
                readerController.selection.collect { selection ->
                    _state.update { it.copy(selectionText = selection?.text?.takeIf(String::isNotEmpty)?.let(TextQuotes::preview)) }
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
        _state.update { it.copy(selectionText = null) }
    }

    /** « Surligner » : surlignage créé (ou fusionné) aussitôt, sélection effacée, sans snackbar. */
    fun highlightSelection() {
        val reader = controller ?: return
        launchWrite {
            val selection = reader.currentSelection()
            reader.clearSelection()
            if (selection != null) save(selection.locator, note = null)
        }
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

    /** Cible de la feuille de note ouverte : passage sélectionné (rien n’existe encore) ou surlignage existant. */
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

    /** « Modifier la note » ou « Ajouter une note » depuis la feuille d’un surlignage touché. */
    fun editNote() {
        val actions = _state.value.actions ?: return
        noteTarget = NoteTarget.Existing(actions.id)
        _state.update {
            it.copy(actions = null, noteSheet = NoteSheetState(actions.passage, actions.note.orEmpty(), editing = actions.note != null))
        }
    }

    fun onNoteChange(text: String) = _state.update { state -> state.copy(noteSheet = state.noteSheet?.copy(note = text)) }

    /** « Enregistrer » : surlignage créé avec sa note, ou note du surlignage remplacée ; une note vide n’en est pas une. */
    fun saveNote() {
        val sheet = _state.value.noteSheet ?: return
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
     * Enregistre le passage de [selected] avec [note], fusionné avec les surlignages qu’il recoupe dans son chapitre
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

