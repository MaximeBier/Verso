package com.maximebier.verso.ui.collections

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.stats.ReadingStats
import com.maximebier.verso.data.db.CollectionBookEntity
import com.maximebier.verso.data.db.CollectionEntity
import com.maximebier.verso.data.testBook
import org.junit.Test

class CollectionModelsTest {
    private val fortune = testBook("fr", title = "La Fortune des Rougon", author = "Émile Zola").copy(id = 1, stateOverride = "FINISHED")
    private val curee = testBook("c", title = "La Curée", author = "Émile Zola").copy(id = 2, readingLocatorJson = "{}", progression = 0.4)
    private val germinal = testBook("g", title = "Germinal", author = "Émile Zola").copy(id = 3)
    private val dumas = testBook("tm", title = "Les Trois Mousquetaires", author = "Alexandre Dumas").copy(id = 4)
    private val books = listOf(fortune, curee, germinal, dumas)

    private val zola = CollectionEntity(id = 10, name = "Les Rougon-Macquart", createdAt = 2)
    private val mixed = CollectionEntity(id = 11, name = "En vrac", createdAt = 1)
    private val memberships = listOf(
        CollectionBookEntity(10, 3, 7),   // trou dans les positions : seul l’ordre compte
        CollectionBookEntity(10, 1, 0),
        CollectionBookEntity(10, 2, 3),
        CollectionBookEntity(11, 4, 0),
        CollectionBookEntity(11, 1, 1),
    )

    @Test
    fun cardsFollowTheGivenCollectionOrderAndReadingOrder() {
        val cards = collectionCards(listOf(zola, mixed), memberships, books, emptyMap())
        assertThat(cards.map { it.id }).containsExactly(10L, 11L).inOrder()
        val card = cards.first()
        assertThat(card.covers.map { it.title }).containsExactly("La Fortune des Rougon", "La Curée", "Germinal").inOrder()
        assertThat(card.bookCount).isEqualTo(3)
        assertThat(card.finished).isEqualTo(1)
        assertThat(card.author).isEqualTo("Émile Zola")
        // (50 000 + 20 000 + 0) / 150 000 = 46,6 %
        assertThat(card.percent).isEqualTo(46)
        assertThat(cards[1].author).isNull()
    }

    @Test
    fun coversStopAtThree() {
        val five = (1..5).map { i -> testBook("b$i").copy(id = 100L + i) }
        val members = five.mapIndexed { index, book -> CollectionBookEntity(20, book.id, index) }
        val card = collectionCards(listOf(CollectionEntity(20, "Cinq", 0)), members, five, emptyMap()).single()
        assertThat(card.covers).hasSize(3)
        assertThat(card.bookCount).isEqualTo(5)
    }

    @Test
    fun measuredSpeedIsUsedForRemainingTime() {
        val stats = mapOf(3L to ReadingStats(totalActiveMs = 600_000, sessionCount = 1, wordsPerMinute = 500))
        val summary = collectionSummaryOf(listOf(germinal), stats)
        assertThat(summary.remainingMinutes).isEqualTo(100)   // 50 000 mots à 500 mots/min
        assertThat(collectionSummaryOf(listOf(germinal), emptyMap()).remainingMinutes).isEqualTo(200)
    }

    @Test
    fun detailListsBooksInOrderWithTheirStatus() {
        val detail = collectionDetail(zola, memberships, books, emptyMap())
        assertThat(detail.books.map { it.title }).containsExactly("La Fortune des Rougon", "La Curée", "Germinal").inOrder()
        assertThat(detail.books.map { it.status })
            .containsExactly(BookStatus.FINISHED, BookStatus.IN_PROGRESS, BookStatus.TO_READ).inOrder()
        assertThat(detail.summary.resumeIndex).isEqualTo(1)
    }
}
