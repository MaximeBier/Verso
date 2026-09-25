package com.maximebier.verso.data

import com.maximebier.verso.data.db.BookEntity
import java.text.Collator
import java.util.Locale

enum class LibrarySort { RECENT, TITLE, AUTHOR }

enum class LibraryViewMode { LIST, GRID }

/** Tris Titre et Auteur : ordre alphabétique français (accents et casse secondaires), stable par id. */
object LibraryOrdering {

    private fun collator(): Collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.SECONDARY }

    fun byTitle(): Comparator<BookEntity> {
        val collator = collator()
        return Comparator { a, b ->
            val byTitle = collator.compare(a.title, b.title)
            if (byTitle != 0) byTitle else a.id.compareTo(b.id)
        }
    }

    /** Auteur inconnu ("") en dernier, puis par auteur, puis par titre. */
    fun byAuthor(): Comparator<BookEntity> {
        val collator = collator()
        return Comparator { a, b ->
            val aUnknown = a.author.isBlank()
            val bUnknown = b.author.isBlank()
            when {
                aUnknown != bUnknown -> if (aUnknown) 1 else -1
                else -> {
                    val byAuthor = collator.compare(a.author, b.author)
                    if (byAuthor != 0) byAuthor else {
                        val byTitle = collator.compare(a.title, b.title)
                        if (byTitle != 0) byTitle else a.id.compareTo(b.id)
                    }
                }
            }
        }
    }
}
