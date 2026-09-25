package com.maximebier.verso.core.journal

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** En-tête de jour du journal : « Aujourd'hui », « Hier », sinon la date (« Lundi 21 septembre »). */
sealed interface DayGroup {
    data object Today : DayGroup
    data object Yesterday : DayGroup
    data class Date(val date: LocalDate) : DayGroup
}

/**
 * Regroupe `items` par jour calendaire de début dans `zone`, relativement à `now`.
 * L'ordre d'entrée est conservé : ordre des groupes = ordre de première apparition, et ordre des
 * éléments dans chaque groupe (le dépôt fournit les sessions les plus récentes d'abord).
 */
fun <T> groupByDay(
    items: List<T>,
    startedAt: (T) -> Long,
    now: Long,
    zone: ZoneId,
): List<Pair<DayGroup, List<T>>> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val yesterday = today.minusDays(1)
    val byDate = LinkedHashMap<LocalDate, MutableList<T>>()
    for (item in items) {
        val date = Instant.ofEpochMilli(startedAt(item)).atZone(zone).toLocalDate()
        byDate.getOrPut(date) { mutableListOf() }.add(item)
    }
    return byDate.map { (date, dayItems) ->
        val group = when (date) {
            today -> DayGroup.Today
            yesterday -> DayGroup.Yesterday
            else -> DayGroup.Date(date)
        }
        group to dayItems.toList()
    }
}
