package com.maximebier.verso.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.collections.CollectionOrder
import com.maximebier.verso.core.stats.ReadingStats
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.CollectionRepository
import com.maximebier.verso.data.LibrarySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface CollectionDialog {
    data object Rename : CollectionDialog
    data object Delete : CollectionDialog
}

data class CollectionUiState(
    val loading: Boolean = true,
    /** Collection supprimée ailleurs (ou introuvable) : l’écran se ferme. */
    val missing: Boolean = false,
    /** Supprimée depuis cet écran : l’écran se ferme. */
    val deleted: Boolean = false,
    val detail: CollectionDetail? = null,
    val reordering: Boolean = false,
    val dialog: CollectionDialog? = null,
)

/** Écran d’une collection (4.02) : ordre de lecture, « Réordonner », renommer, supprimer, retirer un livre. */
class CollectionViewModel(
    private val collectionId: Long,
    private val collections: CollectionRepository,
    books: BookRepository,
    allStats: Flow<Map<Long, ReadingStats>>,
) : ViewModel() {

    private data class Ui(val reordering: Boolean = false, val dialog: CollectionDialog? = null, val deleted: Boolean = false)

    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<CollectionUiState> = combine(
        collections.observeCollection(collectionId),
        collections.observeMemberships(),
        books.observeBooks(LibrarySort.RECENT),
        allStats,
        ui,
    ) { collection, memberships, bookList, stats, u ->
        CollectionUiState(
            loading = false,
            missing = collection == null && !u.deleted,
            deleted = u.deleted,
            detail = collection?.let { collectionDetail(it, memberships, bookList, stats) },
            reordering = u.reordering,
            dialog = u.dialog.takeIf { collection != null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CollectionUiState())

    fun onReorderStart() = ui.update { it.copy(reordering = true) }

    fun onReorderEnd() = ui.update { it.copy(reordering = false) }

    /** Un déplacement à la fois, sur l’ordre relu dans la base : deux touchers rapides s’appliquent l’un après l’autre. */
    private val moveLock = Mutex()

    /** Déplace le livre de [from] à [to] et l’enregistre aussitôt (positions 0..n-1). */
    fun onMove(from: Int, to: Int) {
        viewModelScope.launch {
            moveLock.withLock {
                val ids = collections.observeMemberships().first()
                    .filter { it.collectionId == collectionId }
                    .sortedWith(compareBy({ it.position }, { it.bookId }))
                    .map { it.bookId }
                val moved = CollectionOrder.moved(ids, from, to)
                if (moved != ids) collections.reorder(collectionId, moved)
            }
        }
    }

    /** Retire le livre de la collection ; il reste dans la bibliothèque. */
    fun onRemove(bookId: Long) {
        viewModelScope.launch { collections.remove(collectionId, bookId) }
    }

    fun onRenameRequest() = ui.update { it.copy(dialog = CollectionDialog.Rename) }

    /** Nom vide ou blanc : sans effet (le dépôt l’ignore). */
    fun onRename(name: String) {
        ui.update { it.copy(dialog = null) }
        viewModelScope.launch { collections.rename(collectionId, name) }
    }

    fun onDeleteRequest() = ui.update { it.copy(dialog = CollectionDialog.Delete) }

    fun onDeleteConfirm() {
        ui.update { it.copy(dialog = null, deleted = true) }
        viewModelScope.launch { collections.delete(collectionId) }
    }

    fun onDialogDismiss() = ui.update { it.copy(dialog = null) }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(collectionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication).container
                CollectionViewModel(collectionId, container.collections, container.books, container.sessions.observeAllStats())
            }
        }
    }
}
