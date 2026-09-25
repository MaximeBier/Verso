package com.maximebier.verso.core.journal

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Test

class JournalGroupingTest {

    private data class Item(val name: String, val startedAt: Long)

    private val paris = ZoneId.of("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = paris): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun group(items: List<Item>, now: Long, zone: ZoneId = paris) =
        groupByDay(items, { it.startedAt }, now, zone)

    @Test
    fun groupsTodayYesterdayAndOlderDatesKeepingOrder() {
        val a = Item("a", at(2026, 9, 25, 9, 0))
        val b = Item("b", at(2026, 9, 25, 0, 30))
        val c = Item("c", at(2026, 9, 24, 23, 59))
        val d = Item("d", at(2026, 9, 21, 20, 0))
        val e = Item("e", at(2026, 9, 21, 8, 0))
        val f = Item("f", at(2025, 12, 31, 22, 0))
        val groups = group(listOf(a, b, c, d, e, f), now = at(2026, 9, 25, 10, 0))
        assertThat(groups).containsExactly(
            DayGroup.Today to listOf(a, b),
            DayGroup.Yesterday to listOf(c),
            DayGroup.Date(LocalDate.of(2026, 9, 21)) to listOf(d, e),
            DayGroup.Date(LocalDate.of(2025, 12, 31)) to listOf(f),
        ).inOrder()
    }

    @Test
    fun dayDependsOnTheZone() {
        val item = Item("nuit", at(2026, 9, 25, 0, 30)) // 22 h 30 UTC la veille
        val now = at(2026, 9, 25, 10, 0)
        assertThat(group(listOf(item), now, paris).single().first).isEqualTo(DayGroup.Today)
        assertThat(group(listOf(item), now, ZoneOffset.UTC).single().first).isEqualTo(DayGroup.Yesterday)
    }

    @Test
    fun midnightIsTheBoundary() {
        val now = at(2026, 9, 25, 0, 0)
        val justBefore = Item("avant", now - 1)
        val atMidnight = Item("minuit", now)
        val groups = group(listOf(atMidnight, justBefore), now)
        assertThat(groups).containsExactly(
            DayGroup.Today to listOf(atMidnight),
            DayGroup.Yesterday to listOf(justBefore),
        ).inOrder()
    }

    @Test
    fun daylightSavingChangeDayIsStillYesterday() {
        // Passage à l'heure d'hiver le dimanche 25 octobre 2026 (journée de 25 h).
        val now = at(2026, 10, 26, 8, 0)
        val early = Item("tôt", at(2026, 10, 25, 1, 30))
        val late = Item("tard", at(2026, 10, 25, 23, 30))
        val before = Item("avant", at(2026, 10, 24, 23, 30))
        val groups = group(listOf(late, early, before), now)
        assertThat(groups).containsExactly(
            DayGroup.Yesterday to listOf(late, early),
            DayGroup.Date(LocalDate.of(2026, 10, 24)) to listOf(before),
        ).inOrder()
    }

    @Test
    fun emptyListGivesNoGroup() {
        assertThat(group(emptyList(), now = at(2026, 9, 25, 10, 0))).isEmpty()
    }
}
