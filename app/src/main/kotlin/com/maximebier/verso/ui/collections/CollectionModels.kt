package com.maximebier.verso.ui.collections

import com.maximebier.verso.core.collections.CollectionBook
import com.maximebier.verso.core.collections.CollectionRules
import com.maximebier.verso.core.collections.CollectionSummary
import com.maximebier.verso.core.stats.ReadingStats
import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.CollectionBookEntity
import com.maximebier.verso.data.db.CollectionEntity
import com.maximebier.verso.data.status
import com.maximebier.verso.ui.library.LibraryBook
import com.maximebier.verso.ui.library.toLibraryBook

/** Couverture d’une pile (4.01) : mêmes données que `BookCover`. */
data class CoverRef(val title: String, val coverPath: String?, val seed: String)

/** Carte de l’onglet « Collections » (4.01). */
data class CollectionCard(
    val id: Long,
    val name: String,
    val author: String?,
    val bookCount: Int,
    val covers: List<CoverRef>,
    val fraction: Float,
    val percent: Int,
    val finished: Int,
)

/** Écran d’une collection (4.02). */
data class CollectionDetail(val id: Long, val name: String, val books: List<LibraryBook>, val summary: CollectionSummary)

/** Nombre de couvertures de la pile. */
const val COVER_STACK_SIZE = 3

/** Livres de la collection dans l’ordre de lecture ; une appartenance sans livre (en cours de suppression) est ignorée. */
fun collectionBooks(collectionId: Long, memberships: List<CollectionBookEntity>, booksById: Map<Long, BookEntity>): List<BookEntity> =
    memberships.filter { it.collectionId == collectionId }
        .sortedWith(compareBy({ it.position }, { it.bookId }))
        .mapNotNull { booksById[it.bookId] }

/**
 * Vitesse de chaque livre comme dans sa fiche ; un livre sans session prend la vitesse de tous les livres (la même
 * dans chaque entrée de [stats]), sinon 250 mots/min.
 */
fun collectionSummaryOf(books: List<BookEntity>, stats: Map<Long, ReadingStats>): CollectionSummary {
    val overall = stats.values.firstNotNullOfOrNull { it.fallbackWordsPerMinute } ?: DEFAULT_WORDS_PER_MINUTE
    return CollectionRules.summarize(
        books.map { book ->
            CollectionBook(
                author = book.author,
                totalWords = book.totalWords,
                progression = book.progression,
                status = book.status(),
                wordsPerMinute = stats[book.id]?.effectiveWordsPerMinute ?: overall,
            )
        },
    )
}

fun collectionCards(
    collections: List<CollectionEntity>,
    memberships: List<CollectionBookEntity>,
    books: List<BookEntity>,
    stats: Map<Long, ReadingStats>,
): List<CollectionCard> {
    val byId = books.associateBy { it.id }
    return collections.map { collection ->
        val ordered = collectionBooks(collection.id, memberships, byId)
        val summary = collectionSummaryOf(ordered, stats)
        CollectionCard(
            id = collection.id,
            name = collection.name,
            author = summary.author,
            bookCount = ordered.size,
            covers = ordered.take(COVER_STACK_SIZE).map { CoverRef(it.title, it.coverPath, it.sha256) },
            fraction = summary.fraction.toFloat(),
            percent = summary.percent,
            finished = summary.finished,
        )
    }
}

fun collectionDetail(
    collection: CollectionEntity,
    memberships: List<CollectionBookEntity>,
    books: List<BookEntity>,
    stats: Map<Long, ReadingStats>,
): CollectionDetail {
    val ordered = collectionBooks(collection.id, memberships, books.associateBy { it.id })
    return CollectionDetail(collection.id, collection.name, ordered.map { it.toLibraryBook() }, collectionSummaryOf(ordered, stats))
}
