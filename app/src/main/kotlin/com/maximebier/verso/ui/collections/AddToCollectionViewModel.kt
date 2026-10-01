package com.maximebier.verso.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.CollectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CollectionChoice(val id: Long, val name: String, val bookCount: Int, val checked: Boolean)

data class AddToCollectionUiState(
    val loading: Boolean = true,
    val bookTitle: String = "",
    /** Toutes les collections, la plus récente d’abord. */
    val choices: List<CollectionChoice> = emptyList(),
)

/** Feuille « Ajouter à une collection » (4.04) : chaque case ajoute le livre en dernier ou le retire, tout de suite. */
class AddToCollectionViewModel(
    private val bookId: Long,
    private val collections: CollectionRepository,
    books: BookRepository,
) : ViewModel() {

    val state: StateFlow<AddToCollectionUiState> = combine(
        books.observeBook(bookId),
        collections.observeCollections(),
        collections.observeMemberships(),
    ) { book, list, memberships ->
        AddToCollectionUiState(
            loading = false,
            bookTitle = book?.title.orEmpty(),
            choices = list.map { collection ->
                val members = memberships.filter { it.collectionId == collection.id }
                CollectionChoice(collection.id, collection.name, members.size, members.any { it.bookId == bookId })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddToCollectionUiState())

    /** Un toucher à la fois, l’appartenance relue dans la base : deux touchers rapides ajoutent puis retirent. */
    private val toggleLock = Mutex()

    fun onToggle(collectionId: Long) {
        viewModelScope.launch {
            toggleLock.withLock {
                val member = collections.observeMemberships().first().any { it.collectionId == collectionId && it.bookId == bookId }
                if (member) collections.remove(collectionId, bookId) else collections.add(collectionId, bookId)
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication).container
                AddToCollectionViewModel(bookId, container.collections, container.books)
            }
        }
    }
}
