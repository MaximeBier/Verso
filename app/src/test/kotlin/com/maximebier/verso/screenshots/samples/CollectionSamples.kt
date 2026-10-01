package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.core.collections.CollectionSummary
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.ui.collections.CollectionActions
import com.maximebier.verso.ui.collections.CollectionCard
import com.maximebier.verso.ui.collections.CollectionContent
import com.maximebier.verso.ui.collections.CollectionDetail
import com.maximebier.verso.ui.collections.CollectionUiState
import com.maximebier.verso.ui.collections.CollectionsUiState
import com.maximebier.verso.ui.collections.CoverRef
import com.maximebier.verso.ui.collections.NewCollectionContent
import com.maximebier.verso.ui.collections.NewCollectionActions
import com.maximebier.verso.ui.collections.NewCollectionUiState
import com.maximebier.verso.ui.collections.SelectableBook
import com.maximebier.verso.ui.library.LibraryActions
import com.maximebier.verso.ui.library.LibraryBook
import com.maximebier.verso.ui.library.LibraryContent
import com.maximebier.verso.ui.library.LibrarySamples
import com.maximebier.verso.ui.library.LibraryTab

/** Données des maquettes 4.01 à 4.04. */
internal object CollectionData {
    private fun cover(title: String) = CoverRef(title, null, title)

    val zola = CollectionCard(
        id = 1, name = "Les Rougon-Macquart", author = "Émile Zola", bookCount = 5,
        covers = listOf(cover("La Fortune des Rougon"), cover("La Curée"), cover("L’Assommoir")),
        fraction = 0.44f, percent = 44, finished = 2,
    )
    val dumas = CollectionCard(
        id = 2, name = "Les Mousquetaires", author = "Alexandre Dumas", bookCount = 3,
        covers = listOf(cover("Les Trois Mousquetaires"), cover("Vingt Ans après"), cover("Le Vicomte de Bragelonne")),
        fraction = 0.29f, percent = 29, finished = 1,
    )

    fun book(id: Long, title: String, author: String, status: BookStatus = BookStatus.TO_READ, progression: Double = 0.0) =
        LibraryBook(id, title, author, null, title, status, progression, (progression * 100).toInt())
}

@Composable
internal fun CollectionsTabSample() {
    LibraryContent(
        state = LibrarySamples.list,
        actions = LibraryActions(),
        collections = CollectionsUiState(loading = false, cards = listOf(CollectionData.zola, CollectionData.dumas)),
        initialTab = LibraryTab.COLLECTIONS,
    )
}

@Composable
internal fun CollectionsEmptySample() {
    LibraryContent(
        state = LibrarySamples.list,
        actions = LibraryActions(),
        collections = CollectionsUiState(loading = false),
        initialTab = LibraryTab.COLLECTIONS,
    )
}

@Composable
internal fun NewCollectionSample() {
    val dumas = "Alexandre Dumas"
    NewCollectionContent(
        state = NewCollectionUiState(
            loading = false,
            name = "Les Mousquetaires",
            query = "Dumas",
            books = listOf(
                SelectableBook(CollectionData.book(1, "Les Trois Mousquetaires", dumas), selected = true),
                SelectableBook(CollectionData.book(2, "Vingt Ans après", dumas), selected = true),
                SelectableBook(CollectionData.book(3, "Le Vicomte de Bragelonne", dumas), selected = true),
                SelectableBook(CollectionData.book(4, "Le Comte de Monte-Cristo", dumas), selected = false),
            ),
            selectedCount = 3,
        ),
        actions = NewCollectionActions(),
    )
}

private val zolaBooks = listOf(
    CollectionData.book(1, "La Fortune des Rougon", "Émile Zola", BookStatus.FINISHED, 1.0),
    CollectionData.book(2, "La Curée", "Émile Zola", BookStatus.FINISHED, 1.0),
    CollectionData.book(3, "L’Assommoir", "Émile Zola", BookStatus.IN_PROGRESS, 0.4),
    CollectionData.book(4, "Germinal", "Émile Zola", BookStatus.IN_PROGRESS, 0.12),
    CollectionData.book(5, "La Bête humaine", "Émile Zola", BookStatus.TO_READ),
)

private val zolaSummary = CollectionSummary(
    fraction = 0.44, percent = 44, finished = 2, inProgress = 2, toRead = 1,
    remainingMinutes = 26 * 60, resumeIndex = 2, author = "Émile Zola",
)

private val zolaState = CollectionUiState(loading = false, detail = CollectionDetail(1, "Les Rougon-Macquart", zolaBooks, zolaSummary))

@Composable
internal fun CollectionSample() {
    CollectionContent(zolaState, CollectionActions())
}

@Composable
internal fun CollectionReorderSample() {
    CollectionContent(zolaState.copy(reordering = true), CollectionActions())
}

@Composable
internal fun CollectionEmptySample() {
    CollectionContent(
        CollectionUiState(loading = false, detail = CollectionDetail(1, "Les Mousquetaires", emptyList(), CollectionSummary(0.0, 0, 0, 0, 0, 0, null, null))),
        CollectionActions(),
    )
}

@Composable
internal fun CollectionFinishedSample() {
    val done = zolaBooks.map { it.copy(status = BookStatus.FINISHED, progression = 1.0, percent = 100) }
    val summary = zolaSummary.copy(fraction = 1.0, percent = 100, finished = 5, inProgress = 0, toRead = 0, remainingMinutes = 0, resumeIndex = null)
    CollectionContent(CollectionUiState(loading = false, detail = CollectionDetail(1, "Les Rougon-Macquart", done, summary)), CollectionActions())
}
