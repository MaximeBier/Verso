package com.maximebier.verso.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.model.LibraryRules
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.importer.ImportResult
import com.maximebier.verso.importer.IncomingImports
import com.maximebier.verso.importer.RejectReason
import com.maximebier.verso.ui.common.percentOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

data class LibraryBook(
    val id: Long,
    val title: String,
    val author: String,
    val coverPath: String?,
    val colorSeed: String,
    val status: BookStatus,
    val progression: Double,
    val percent: Int,
)

data class ResumeInfo(val book: LibraryBook, val chapter: String?, val excerpt: String?, val remainingMinutes: Int)

sealed interface ImportDialog {
    data class Duplicate(val result: ImportResult.Duplicate) : ImportDialog
    data class Rejected(val reason: RejectReason, val fileName: String) : ImportDialog
}

data class ImportSnackbar(val bookId: Long, val title: String)

data class LibraryUiState(
    val loading: Boolean = true,
    val books: List<LibraryBook> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENT,
    val viewMode: LibraryViewMode = LibraryViewMode.LIST,
    val resume: ResumeInfo? = null,
    val importing: Boolean = false,
    val dialog: ImportDialog? = null,
    val snackbar: ImportSnackbar? = null,
    val pendingDelete: LibraryBook? = null,
)

/** Opérations d'import, injectées pour tester sans Readium (EpubImporter au cycle 5). */
data class ImportActions(
    val importBook: suspend (Uri) -> ImportResult,
    val replace: suspend (ImportResult.Duplicate) -> ImportResult.Added,
    val ignore: suspend (ImportResult.Duplicate) -> Unit,
    val displayName: (Uri) -> String,
)

/** Longueur maximale de l'extrait de la carte « Reprendre » (affiché sur 2 lignes au plus). */
const val EXCERPT_MAX_CHARS = 160

/** Espaces normalisés ; coupe sur une frontière de mot et ajoute « … » au-delà de [EXCERPT_MAX_CHARS]. */
fun excerptOf(highlight: String): String {
    val clean = highlight.replace(Regex("\\s+"), " ").trim()
    if (clean.length <= EXCERPT_MAX_CHARS) return clean
    val cut = clean.take(EXCERPT_MAX_CHARS)
    val lastSpace = cut.lastIndexOf(' ')
    val base = if (lastSpace > EXCERPT_MAX_CHARS / 2) cut.take(lastSpace) else cut
    return base.trimEnd(' ', ',', ';', ':') + "…"
}

class LibraryViewModel(
    private val books: BookRepository,
    private val settings: SettingsRepository,
    private val importActions: ImportActions,
    incomingPending: StateFlow<List<Uri>> = MutableStateFlow(emptyList()),
    private val takeIncoming: () -> Uri? = { null },
) : ViewModel() {

    private data class Transient(
        val importing: Boolean = false,
        val dialog: ImportDialog? = null,
        val snackbar: ImportSnackbar? = null,
        val pendingDelete: LibraryBook? = null,
    )

    private val transient = MutableStateFlow(Transient())

    /** Un import à la fois ; le suivant attend la fermeture du dialogue du précédent. */
    private val importLock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LibraryUiState> = combine(
        settings.librarySort.flatMapLatest { sort -> books.observeBooks(sort).map { list -> sort to list } },
        settings.libraryViewMode,
        books.observeLastOpened(),
        transient,
    ) { (sort, list), viewMode, lastOpened, t ->
        LibraryUiState(
            loading = false,
            books = list.map(::toLibraryBook),
            sort = sort,
            viewMode = viewMode,
            resume = lastOpened?.let(::toResume),
            importing = t.importing,
            dialog = t.dialog,
            snackbar = t.snackbar,
            pendingDelete = t.pendingDelete,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    init {
        viewModelScope.launch {
            incomingPending.collect { pending ->
                if (pending.isNotEmpty()) {
                    while (true) {
                        val uri = takeIncoming() ?: break
                        runImport(uri)
                    }
                }
            }
        }
    }

    fun onImport(uri: Uri) {
        viewModelScope.launch { runImport(uri) }
    }

    private suspend fun runImport(uri: Uri) {
        importLock.withLock {
            transient.first { it.dialog == null }
            transient.update { it.copy(importing = true) }
            val result = try {
                importActions.importBook(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportResult.Rejected(RejectReason.UNREADABLE)
            } finally {
                transient.update { it.copy(importing = false) }
            }
            when (result) {
                is ImportResult.Added ->
                    transient.update { it.copy(snackbar = ImportSnackbar(result.bookId, result.title)) }
                is ImportResult.Duplicate ->
                    transient.update { it.copy(dialog = ImportDialog.Duplicate(result)) }
                is ImportResult.Rejected ->
                    transient.update { it.copy(dialog = ImportDialog.Rejected(result.reason, importActions.displayName(uri))) }
            }
        }
    }

    fun onSortChange(sort: LibrarySort) {
        viewModelScope.launch { settings.setLibrarySort(sort) }
    }

    fun onViewModeChange(mode: LibraryViewMode) {
        viewModelScope.launch { settings.setLibraryViewMode(mode) }
    }

    fun onSnackbarShown() {
        transient.update { it.copy(snackbar = null) }
    }

    fun onDuplicateReplace() {
        val dialog = transient.value.dialog as? ImportDialog.Duplicate ?: return
        transient.update { it.copy(dialog = null) }
        viewModelScope.launch {
            try {
                importActions.replace(dialog.result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transient.update {
                    it.copy(dialog = ImportDialog.Rejected(RejectReason.UNREADABLE, dialog.result.pending.originalFileName))
                }
            }
        }
    }

    fun onDuplicateIgnore() {
        val dialog = transient.value.dialog as? ImportDialog.Duplicate ?: return
        transient.update { it.copy(dialog = null) }
        viewModelScope.launch { importActions.ignore(dialog.result) }
    }

    fun onRejectedDismiss() {
        transient.update { if (it.dialog is ImportDialog.Rejected) it.copy(dialog = null) else it }
    }

    fun onDeleteRequest(book: LibraryBook) {
        transient.update { it.copy(pendingDelete = book) }
    }

    fun onDeleteConfirm() {
        val book = transient.value.pendingDelete ?: return
        transient.update { it.copy(pendingDelete = null) }
        viewModelScope.launch { books.delete(book.id) }
    }

    fun onDeleteDismiss() {
        transient.update { it.copy(pendingDelete = null) }
    }

    private fun toLibraryBook(entity: BookEntity) = LibraryBook(
        id = entity.id,
        title = entity.title,
        author = entity.author,
        coverPath = entity.coverPath,
        colorSeed = entity.sha256,
        status = LibraryRules.status(hasReadingLocator = entity.readingLocatorJson != null, progression = entity.progression),
        progression = entity.progression,
        percent = percentOf(entity.progression),
    )

    private fun toResume(entity: BookEntity): ResumeInfo {
        val locator = entity.readingLocatorJson?.let { json ->
            try {
                Locator.fromJSON(JSONObject(json))
            } catch (e: Exception) {
                null
            }
        }
        return ResumeInfo(
            book = toLibraryBook(entity),
            chapter = locator?.title?.trim()?.takeIf { it.isNotEmpty() },
            excerpt = locator?.text?.highlight?.let(::excerptOf)?.takeIf { it.isNotEmpty() },
            remainingMinutes = remainingMinutes(entity.totalWords, entity.progression),
        )
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication
                val container = app.container
                LibraryViewModel(
                    books = container.books,
                    settings = container.settings,
                    importActions = ImportActions(
                        importBook = { uri -> container.importer.import(uri) },
                        replace = { duplicate -> container.importer.replace(duplicate) },
                        ignore = { duplicate -> container.importer.ignore(duplicate) },
                        displayName = { uri -> container.importer.displayName(uri) },
                    ),
                    incomingPending = IncomingImports.pending,
                    takeIncoming = IncomingImports::take,
                )
            }
        }
    }
}
