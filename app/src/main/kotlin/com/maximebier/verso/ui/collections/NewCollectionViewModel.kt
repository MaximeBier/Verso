package com.maximebier.verso.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.collections.CollectionRules
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.CollectionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.ui.library.LibraryBook
import com.maximebier.verso.ui.library.toLibraryBook
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectableBook(val book: LibraryBook, val selected: Boolean)

data class NewCollectionUiState(
    val loading: Boolean = true,
    val name: String = "",
    val query: String = "",
    /** Livres du filtre, dans l’ordre de la bibliothèque. */
    val books: List<SelectableBook> = emptyList(),
    /** Tous les livres cochés, filtre ignoré. */
    val selectedCount: Int = 0,
    val created: Long? = null,
) {
    val canCreate: Boolean get() = name.isNotBlank() && created == null
}

/** Écran 4.03 : nom, filtre, cases ; les livres cochés entrent dans l’ordre de la bibliothèque, pas dans l’ordre des clics. */
class NewCollectionViewModel(
    preselectedBookId: Long?,
    books: BookRepository,
    settings: SettingsRepository,
    private val collections: CollectionRepository,
) : ViewModel() {

    private data class Form(val name: String = "", val query: String = "", val selected: Set<Long>, val created: Long? = null)

    private val form = MutableStateFlow(Form(selected = setOfNotNull(preselectedBookId)))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val library = settings.librarySort.flatMapLatest { sort -> books.observeBooks(sort) }

    val state: StateFlow<NewCollectionUiState> = combine(library, form) { list, f ->
        val all = list.map { it.toLibraryBook() }
        NewCollectionUiState(
            loading = false,
            name = f.name,
            query = f.query,
            books = all.filter { CollectionRules.matches(f.query, it.title, it.author) }.map { SelectableBook(it, it.id in f.selected) },
            selectedCount = all.count { it.id in f.selected },
            created = f.created,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NewCollectionUiState())

    private var creating = false

    fun onNameChange(value: String) = form.update { it.copy(name = value) }

    fun onQueryChange(value: String) = form.update { it.copy(query = value) }

    fun onToggle(bookId: Long) = form.update {
        it.copy(selected = if (bookId in it.selected) it.selected - bookId else it.selected + bookId)
    }

    /** Une seule création, même si « Créer » est touché deux fois. */
    fun onCreate() {
        val current = form.value
        if (current.name.isBlank() || current.created != null || creating) return
        creating = true
        viewModelScope.launch {
            val ordered = library.first().map { it.id }.filter { it in current.selected }
            val id = collections.create(current.name, ordered)
            form.update { it.copy(created = id) }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(preselectedBookId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication).container
                NewCollectionViewModel(preselectedBookId, container.books, container.settings, container.collections)
            }
        }
    }
}
