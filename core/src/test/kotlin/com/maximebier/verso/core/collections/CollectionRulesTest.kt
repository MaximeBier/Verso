package com.maximebier.verso.core.collections

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.model.BookStatus.FINISHED
import com.maximebier.verso.core.model.BookStatus.IN_PROGRESS
import com.maximebier.verso.core.model.BookStatus.TO_READ
import org.junit.Test

class CollectionRulesTest {

    private fun book(words: Long, progression: Double, status: BookStatus, author: String = "Émile Zola", wpm: Int = 250) =
        CollectionBook(author = author, totalWords = words, progression = progression, status = status, wordsPerMinute = wpm)

    @Test
    fun progressIsWeightedByWords() {
        // 100 000 mots à moitié lus + 20 000 mots terminés = 70 000 / 120 000 = 58,3 %.
        val summary = CollectionRules.summarize(listOf(book(100_000, 0.5, IN_PROGRESS), book(20_000, 1.0, FINISHED)))
        assertThat(summary.percent).isEqualTo(58)
        assertThat(summary.fraction).isWithin(1e-9).of(70_000.0 / 120_000.0)
    }

    @Test
    fun finishedByHandCountsAllItsWords() {
        val summary = CollectionRules.summarize(listOf(book(10_000, 0.03, FINISHED), book(10_000, 0.0, TO_READ)))
        assertThat(summary.percent).isEqualTo(50)
    }

    @Test
    fun hundredPercentOnlyWhenEverythingIsFinished() {
        val almost = CollectionRules.summarize(listOf(book(1_000_000, 1.0, FINISHED), book(1, 0.0, TO_READ)))
        assertThat(almost.percent).isEqualTo(CollectionRules.MAX_PERCENT_UNFINISHED)
        val done = CollectionRules.summarize(listOf(book(10, 1.0, FINISHED), book(0, 0.2, FINISHED)))
        assertThat(done.percent).isEqualTo(100)
        assertThat(done.fraction).isEqualTo(1.0)
    }

    @Test
    fun booksWithoutWordCountWeighNothingAndNeverDivideByZero() {
        val summary = CollectionRules.summarize(listOf(book(0, 0.5, IN_PROGRESS), book(0, 0.0, TO_READ)))
        assertThat(summary.percent).isEqualTo(0)
        assertThat(summary.fraction).isEqualTo(0.0)
    }

    @Test
    fun emptyCollection() {
        val summary = CollectionRules.summarize(emptyList())
        assertThat(summary.percent).isEqualTo(0)
        assertThat(summary.bookCount).isEqualTo(0)
        assertThat(summary.resumeIndex).isNull()
        assertThat(summary.remainingMinutes).isEqualTo(0)
        assertThat(summary.author).isNull()
    }

    @Test
    fun countsResumeAndRemainingTime() {
        val summary = CollectionRules.summarize(
            listOf(
                book(50_000, 1.0, FINISHED),
                book(25_000, 0.2, IN_PROGRESS, wpm = 200),   // 20 000 mots restants / 200 = 100 min
                book(15_000, 0.0, TO_READ),                  // 15 000 / 250 = 60 min
            ),
        )
        assertThat(summary.finished).isEqualTo(1)
        assertThat(summary.inProgress).isEqualTo(1)
        assertThat(summary.toRead).isEqualTo(1)
        assertThat(summary.resumeIndex).isEqualTo(1)
        assertThat(summary.remainingMinutes).isEqualTo(160)
    }

    @Test
    fun nanAndOutOfRangeProgressionsAreClamped() {
        val summary = CollectionRules.summarize(listOf(book(100, Double.NaN, IN_PROGRESS), book(100, 1.7, IN_PROGRESS)))
        assertThat(summary.percent).isEqualTo(50)
    }

    @Test
    fun commonAuthorIgnoresCaseAndSurroundingSpaces() {
        assertThat(CollectionRules.commonAuthor(listOf("Émile Zola", " émile  zola "))).isEqualTo("Émile Zola")
        assertThat(CollectionRules.commonAuthor(listOf("Émile Zola", "Alexandre Dumas"))).isNull()
        assertThat(CollectionRules.commonAuthor(listOf("Émile Zola", ""))).isNull()
        assertThat(CollectionRules.commonAuthor(emptyList())).isNull()
    }

    @Test
    fun filterIgnoresCaseAndAccents() {
        assertThat(CollectionRules.matches("dumas", "Les Trois Mousquetaires", "Alexandre Dumas")).isTrue()
        assertThat(CollectionRules.matches("CUREE", "La Curée", "Émile Zola")).isTrue()
        assertThat(CollectionRules.matches("l'assom", "L’Assommoir", "Émile Zola")).isTrue()
        assertThat(CollectionRules.matches("  ", "Germinal", "")).isTrue()
        assertThat(CollectionRules.matches("hugo", "Germinal", "Émile Zola")).isFalse()
    }

    @Test
    fun movedKeepsEveryItem() {
        assertThat(CollectionOrder.moved(listOf("a", "b", "c", "d"), 0, 2)).containsExactly("b", "c", "a", "d").inOrder()
        assertThat(CollectionOrder.moved(listOf("a", "b", "c", "d"), 3, 0)).containsExactly("d", "a", "b", "c").inOrder()
        assertThat(CollectionOrder.moved(listOf("a", "b"), 1, 5)).containsExactly("a", "b").inOrder()
        assertThat(CollectionOrder.moved(listOf("a", "b"), 1, 1)).containsExactly("a", "b").inOrder()
    }
}
