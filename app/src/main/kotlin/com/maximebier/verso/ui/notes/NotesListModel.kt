package com.maximebier.verso.ui.notes

import android.net.Uri
import com.maximebier.verso.core.notes.NotesExportItem
import com.maximebier.verso.core.notes.notesMarkdown
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.ui.common.percentOf
import com.maximebier.verso.ui.reader.NoteSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class NoteItem(val id: Long, val text: String, val note: String?, val location: String)

data class NotesUiState(
    val bookTitle: String = "",
    val items: List<NoteItem> = emptyList(),
    val loaded: Boolean = false,
    val noteSheet: NoteSheetState? = null,
    val exportFileName: String = "",
)

sealed interface NotesEvent {
    data class Deleted(val row: HighlightEntity) : NotesEvent
    data object Exported : NotesEvent
    data object ExportFailed : NotesEvent
}

class NotesTexts(
    val location: (shortLocation: String?, percent: Int) -> String,
    val longChapter: (path: List<String>) -> String?,
    val exportMeta: (author: String, count: Int, date: String) -> String,
    val exportFileName: (title: String) -> String,
    val exportDate: (epochMs: Long) -> String,
    val shortChapter: (path: List<String>) -> String?,
)

/**
 * « Notes et surlignages » d’un livre (3.06) : éléments dans l’ordre du livre, menu ⋮ (note, suppression avec
 * « Annuler »), export Markdown. Partagé par la surcouche du lecteur et l’écran ouvert depuis la fiche ; « Aller au
 * passage » reste à l’appelant (saut dans le lecteur).
 */
class NotesListModel(
    private val bookId: Long,
    private val highlights: HighlightRepository,
    books: BookRepository,
    private val scope: CoroutineScope,
    private val texts: NotesTexts,
    private val writeText: suspend (Uri, String) -> Unit,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<NotesEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<NotesEvent> = _events.asSharedFlow()

    private var rows: List<HighlightEntity> = emptyList()
    private var author: String = ""
    private var editing: Long? = null
    private val writes = mutableListOf<Job>()

    init {
        scope.launch {
            combine(books.observeBook(bookId), highlights.observe(bookId)) { book, list -> book to list }.collect { (book, list) ->
                rows = list
                author = book?.author.orEmpty()
                val title = book?.title.orEmpty()
                _state.update { state ->
                    state.copy(
                        bookTitle = title,
                        exportFileName = texts.exportFileName(title),
                        loaded = true,
                        items = list.map { row ->
                            NoteItem(row.id, row.text, row.note, texts.location(texts.shortChapter(row.chapterPathList()), percentOf(row.progression)))
                        },
                    )
                }
            }
        }
    }

    fun editNote(id: Long) {
        val row = rows.firstOrNull { it.id == id } ?: return
        editing = id
        _state.update { it.copy(noteSheet = NoteSheetState(row.text, row.note.orEmpty(), editing = row.note != null)) }
    }

    fun onNoteChange(text: String) = _state.update { it.copy(noteSheet = it.noteSheet?.copy(note = text)) }

    fun saveNote() {
        val id = editing ?: return
        val sheet = _state.value.noteSheet ?: return
        editing = null
        _state.update { it.copy(noteSheet = null) }
        launchWrite { highlights.setNote(id, sheet.note) }
    }

    fun cancelNote() {
        editing = null
        _state.update { it.copy(noteSheet = null) }
    }

    fun delete(id: Long) = launchWrite { highlights.delete(id)?.let { _events.emit(NotesEvent.Deleted(it)) } }

    fun undoDelete(row: HighlightEntity) = launchWrite { highlights.restore(row) }

    fun export(uri: Uri) {
        val title = _state.value.bookTitle
        val current = rows
        scope.launch {
            val markdown = notesMarkdown(
                title = title,
                meta = texts.exportMeta(author, current.size, texts.exportDate(clock())),
                items = current.map { NotesExportItem(texts.longChapter(it.chapterPathList()), it.text, it.note) },
            )
            val event = runCatching { writeText(uri, markdown) }.fold({ NotesEvent.Exported }, { NotesEvent.ExportFailed })
            _events.emit(event)
        }
    }

    private fun launchWrite(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        synchronized(writes) { writes += job }
        job.invokeOnCompletion { synchronized(writes) { writes -= job } }
    }

    suspend fun awaitIdle() = synchronized(writes) { writes.toList() }.joinAll()
}


/** Textes sans ressources (tests, valeur par défaut) ; l’app passe `Resources.notesTexts()`. */
fun plainNotesTexts(): NotesTexts = NotesTexts(
    location = { location, percent -> listOfNotNull(location, "$percent %").joinToString(" · ") },
    shortChapter = { path -> path.takeIf { it.isNotEmpty() }?.joinToString(", ") },
    longChapter = { path -> path.takeIf { it.isNotEmpty() }?.joinToString(", ") },
    exportMeta = { author, count, date -> listOf(author, "$count", date).filter(String::isNotEmpty).joinToString(" · ") },
    exportFileName = { title -> "$title – notes.md" },
    exportDate = { it.toString() },
)
