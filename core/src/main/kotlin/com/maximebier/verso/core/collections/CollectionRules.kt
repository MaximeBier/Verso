package com.maximebier.verso.core.collections

import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.text.remainingMinutes
import java.text.Normalizer
import java.util.Locale
import kotlin.math.floor

/** Un livre de la collection, dans l’ordre de lecture : ce que la progression d’ensemble en retient. */
data class CollectionBook(
    val author: String,
    val totalWords: Long,
    val progression: Double,
    val status: BookStatus,
    val wordsPerMinute: Int,
)

/** Écrans 4.01 et 4.02 : progression pondérée par les mots, décompte par état, temps restant, livre à reprendre. */
data class CollectionSummary(
    val fraction: Double,
    val percent: Int,
    val finished: Int,
    val inProgress: Int,
    val toRead: Int,
    val remainingMinutes: Int,
    val resumeIndex: Int?,
    val author: String?,
) {
    val bookCount: Int get() = finished + inProgress + toRead
}

object CollectionRules {
    /** Tant qu’un livre n’est pas terminé, la collection n’affiche jamais 100 %. */
    const val MAX_PERCENT_UNFINISHED = 99

    /**
     * Mots lus / mots de la collection. Un livre terminé (y compris à la main) compte pour tous ses mots ; un livre
     * sans nombre de mots compte pour 0. Pourcentage arrondi vers le bas, comme `percentOf` dans l’app.
     */
    fun summarize(books: List<CollectionBook>): CollectionSummary {
        var totalWords = 0L
        var readWords = 0.0
        var remaining = 0
        for (book in books) {
            val words = book.totalWords.coerceAtLeast(0)
            totalWords += words
            if (book.status == BookStatus.FINISHED) {
                readWords += words
            } else {
                readWords += words * clamp(book.progression)
                remaining += remainingMinutes(words, clamp(book.progression), book.wordsPerMinute)
            }
        }
        val allFinished = books.isNotEmpty() && books.all { it.status == BookStatus.FINISHED }
        val fraction = when {
            allFinished -> 1.0
            totalWords == 0L -> 0.0
            else -> (readWords / totalWords).coerceIn(0.0, 1.0)
        }
        val percent = if (allFinished) 100 else floor(fraction * 100 + 1e-9).toInt().coerceIn(0, MAX_PERCENT_UNFINISHED)
        return CollectionSummary(
            fraction = fraction,
            percent = percent,
            finished = books.count { it.status == BookStatus.FINISHED },
            inProgress = books.count { it.status == BookStatus.IN_PROGRESS },
            toRead = books.count { it.status == BookStatus.TO_READ },
            remainingMinutes = remaining,
            resumeIndex = books.indexOfFirst { it.status != BookStatus.FINISHED }.takeIf { it >= 0 },
            author = commonAuthor(books.map { it.author }),
        )
    }

    /** L’auteur de tous les livres (comparé sans casse ni espaces en trop), tel qu’écrit dans le premier ; sinon null. */
    fun commonAuthor(authors: List<String>): String? {
        if (authors.isEmpty()) return null
        val keys = authors.map { collapse(it).lowercase(Locale.FRENCH) }
        if (keys.first().isEmpty() || keys.any { it != keys.first() }) return null
        return collapse(authors.first())
    }

    /** Filtre de 4.03 : le texte saisi dans le titre ou l’auteur, sans casse ni accents ; vide = tout. */
    fun matches(query: String, title: String, author: String): Boolean {
        val q = fold(query)
        if (q.isEmpty()) return true
        return fold(title).contains(q) || fold(author).contains(q)
    }

    private fun clamp(progression: Double): Double = if (progression.isNaN()) 0.0 else progression.coerceIn(0.0, 1.0)

    private fun collapse(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    private val marks = Regex("\\p{Mn}+")

    private fun fold(text: String): String =
        marks.replace(Normalizer.normalize(collapse(text), Normalizer.Form.NFD), "").replace('’', '\'').lowercase(Locale.FRENCH)
}

object CollectionOrder {
    /** [items] avec l’élément de [from] placé à [to] (borné à la liste) ; inchangé si [from] est hors de la liste. */
    fun <T> moved(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices) return items
        val target = to.coerceIn(0, items.lastIndex)
        if (from == target) return items
        return items.toMutableList().apply { add(target, removeAt(from)) }
    }
}
