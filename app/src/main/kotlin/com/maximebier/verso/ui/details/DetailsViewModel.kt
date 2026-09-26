package com.maximebier.verso.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.ui.common.percentOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DetailsUiState(
    val bookId: Long = 0,
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val titleField: String = "",
    val authorField: String = "",
    val savedTitle: String = "",
    val savedAuthor: String = "",
    val colorSeed: String = "",
    val coverPath: String? = null,
    val percent: Int = 0,
    val hasStarted: Boolean = false,
    val remainingMinutes: Int = 0,
    val importedAt: Long = 0,
    val sizeBytes: Long = 0,
    val originalFileName: String = "",
    val showDeleteDialog: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Fiche d'un livre (1.07) : titre et auteur enregistrés automatiquement, suppression (1.08).
 * `saveScope` survit au ViewModel pour l'enregistrement final dans [onCleared].
 */
class DetailsViewModel(
    private val bookId: Long,
    private val books: BookRepository,
    private val saveScope: CoroutineScope,
    private val debounceMs: Long = AUTOSAVE_DEBOUNCE_MS,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsUiState(bookId = bookId))
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    private var titleJob: Job? = null
    private var authorJob: Job? = null
    private val saveMutex = Mutex()

    @Volatile
    private var deleting = false

    init {
        viewModelScope.launch {
            books.observeBook(bookId).collect { book ->
                if (book == null) {
                    _state.update { it.copy(loaded = true, missing = true) }
                } else {
                    _state.update { current ->
                        current.copy(
                            loaded = true,
                            missing = false,
                            // Champs initialisés une seule fois : une réémission n'écrase pas la frappe en cours.
                            titleField = if (current.loaded) current.titleField else book.title,
                            authorField = if (current.loaded) current.authorField else book.author,
                            savedTitle = book.title,
                            savedAuthor = book.author,
                            colorSeed = book.sha256,
                            coverPath = book.coverPath,
                            percent = percentOf(book.progression),
                            hasStarted = book.readingLocatorJson != null,
                            remainingMinutes = remainingMinutes(book.totalWords, book.progression),
                            importedAt = book.importedAt,
                            sizeBytes = book.sizeBytes,
                            originalFileName = book.originalFileName,
                        )
                    }
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(titleField = value) }
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(debounceMs)
            saveTitle()
        }
    }

    fun onAuthorChange(value: String) {
        _state.update { it.copy(authorField = value) }
        authorJob?.cancel()
        authorJob = viewModelScope.launch {
            delay(debounceMs)
            saveAuthor()
        }
    }

    /** Enregistre tout de suite (perte de focus, retour, arrière-plan) ; un titre vide reprend l'ancienne valeur. */
    fun flush() {
        titleJob?.cancel()
        authorJob?.cancel()
        _state.update { if (it.loaded && it.titleField.isBlank()) it.copy(titleField = it.savedTitle) else it }
        saveScope.launch { saveAll() }
    }

    fun onDeleteClick() {
        _state.update { it.copy(showDeleteDialog = true) }
    }

    fun onDeleteDismiss() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun onDeleteConfirm() {
        deleting = true
        titleJob?.cancel()
        authorJob?.cancel()
        _state.update { it.copy(showDeleteDialog = false) }
        viewModelScope.launch {
            books.delete(bookId)
            _state.update { it.copy(deleted = true) }
        }
    }

    override fun onCleared() {
        if (!deleting) saveScope.launch { saveAll() }
    }

    private suspend fun saveAll() {
        saveTitle()
        saveAuthor()
    }

    private suspend fun saveTitle() {
        saveMutex.withLock {
            val current = _state.value
            val title = current.titleField.trim()
            if (deleting || !current.loaded || current.missing) return@withLock
            if (title.isEmpty() || title == current.savedTitle) return@withLock
            books.updateTitle(bookId, title)
            _state.update { it.copy(savedTitle = title) }
        }
    }

    private suspend fun saveAuthor() {
        saveMutex.withLock {
            val current = _state.value
            val author = current.authorField.trim()
            if (deleting || !current.loaded || current.missing) return@withLock
            if (author == current.savedAuthor) return@withLock
            books.updateAuthor(bookId, author)
            _state.update { it.copy(savedAuthor = author) }
        }
    }

    companion object {
        /** Délai d'enregistrement automatique après la dernière frappe (référence §2, 1.07). */
        const val AUTOSAVE_DEBOUNCE_MS = 500L

        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication
                DetailsViewModel(
                    bookId = bookId,
                    books = app.container.books,
                    saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                )
            }
        }
    }
}
