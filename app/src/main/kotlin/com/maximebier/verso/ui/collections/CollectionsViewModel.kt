package com.maximebier.verso.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.stats.ReadingStats
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.CollectionRepository
import com.maximebier.verso.data.LibrarySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CollectionsUiState(val loading: Boolean = true, val cards: List<CollectionCard> = emptyList())

/** Onglet « Collections » de la bibliothèque (4.01). */
class CollectionsViewModel(
    collections: CollectionRepository,
    books: BookRepository,
    allStats: Flow<Map<Long, ReadingStats>>,
) : ViewModel() {
    val state: StateFlow<CollectionsUiState> = combine(
        collections.observeCollections(),
        collections.observeMemberships(),
        books.observeBooks(LibrarySort.RECENT),
        allStats,
    ) { list, memberships, bookList, stats ->
        CollectionsUiState(loading = false, cards = collectionCards(list, memberships, bookList, stats))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionsUiState())

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication).container
                CollectionsViewModel(container.collections, container.books, container.sessions.observeAllStats())
            }
        }
    }
}
