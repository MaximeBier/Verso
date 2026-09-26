package com.maximebier.verso.readium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder

/**
 * Progression totale fine : début du chapitre + progression dans le chapitre, pondérés par le
 * nombre de positions Readium de chaque chapitre (tranches de 1 024 octets de l’archive).
 */
class ReadingOrderPositions private constructor(
    private val hrefs: List<String>,
    private val starts: IntArray,
    private val counts: IntArray,
    private val total: Int,
) {

    fun totalProgression(locator: Locator): Double? {
        val index = hrefs.indexOf(Locators.hrefKey(locator))
        if (index < 0) return null
        val progression = (locator.locations.progression ?: 0.0).coerceIn(0.0, 1.0)
        return ((starts[index] + progression * counts[index]) / total).coerceIn(0.0, 1.0)
    }

    fun withTotalProgression(locator: Locator): Locator =
        totalProgression(locator)?.let { locator.copyWithLocations(totalProgression = it) } ?: locator

    companion object {

        suspend fun load(publication: Publication): ReadingOrderPositions? =
            withContext(Dispatchers.IO) {
                from(
                    hrefs = publication.readingOrder.map { it.url().removeFragment().toString() },
                    counts = publication.positionsByReadingOrder().map { it.size },
                )
            }

        fun from(hrefs: List<String>, counts: List<Int>): ReadingOrderPositions? {
            if (hrefs.isEmpty() || hrefs.size != counts.size) return null
            val total = counts.sum()
            if (total <= 0) return null
            val starts = IntArray(counts.size)
            var accumulated = 0
            counts.forEachIndexed { index, count ->
                starts[index] = accumulated
                accumulated += count
            }
            return ReadingOrderPositions(hrefs, starts, counts.toIntArray(), total)
        }
    }
}
