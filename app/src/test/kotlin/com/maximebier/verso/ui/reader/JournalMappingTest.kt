package com.maximebier.verso.ui.reader

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.journal.DayGroup
import com.maximebier.verso.core.journal.SessionRecord
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.core.text.passageLabel
import com.maximebier.verso.data.db.SessionEntity
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class JournalMappingTest {

    private val zone: ZoneId = ZoneId.of("Europe/Paris")

    // Mercredi 23 septembre 2026, 22 h 41 (comme la maquette 1.13).
    private val now = at(2026, 9, 23, 22, 41)

    private val toc = listOf(
        TocNode(
            "Première partie", "p1.xhtml",
            listOf(
                TocNode("Chapitre VI", "p1c6.xhtml"),
                TocNode("Chapitre VIII", "p1c8.xhtml"),
            ),
        ),
        TocNode("Deuxième partie", "p2.xhtml", listOf(TocNode("Chapitre I", "p2c1.xhtml"))),
    )

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private fun entity(
        id: Long, start: Long, end: Long, activeMinutes: Long,
        startHref: String, endHref: String, startP: Double, endP: Double,
    ) = SessionEntity(
        id = id, bookId = 1, startedAt = start, endedAt = end, activeMs = activeMinutes * 60_000,
        startLocatorJson = startHref, endLocatorJson = endHref,
        startProgression = startP, endProgression = endP, wordsRead = 0,
    )

    // Dans ce test, le « JSON » du locator est directement le href, sans progression dans le fichier.
    private fun map(sessions: List<SessionEntity>, current: SessionRecord?) =
        journalUiState(sessions, current, toc, now, zone, positionOf = { ResourcePosition(it, null) })

    private val yesterday = entity(4, at(2026, 9, 22, 21, 5), at(2026, 9, 22, 21, 36), 31, "p1c6.xhtml", "p1c8.xhtml", 0.18, 0.25)
    private val mondayNoon = entity(3, at(2026, 9, 21, 12, 30), at(2026, 9, 21, 12, 48), 18, "p1c6.xhtml", "p1c6.xhtml", 0.15, 0.18)
    private val mondayMorning = entity(2, at(2026, 9, 21, 7, 42), at(2026, 9, 21, 8, 2), 20, "p1.xhtml", "p1c6.xhtml", 0.11, 0.15)
    private val lastYear = entity(1, at(2025, 12, 31, 10, 0), at(2025, 12, 31, 10, 30), 30, "p1.xhtml", "p1.xhtml", 0.0, 0.05)
    private val staleCurrentRow = entity(5, at(2026, 9, 23, 22, 10), at(2026, 9, 23, 22, 30), 20, "p1c8.xhtml", "p1c8.xhtml", 0.25, 0.29)
    private val current = SessionRecord(
        id = 5, bookId = 1, startedAt = at(2026, 9, 23, 22, 10), endedAt = at(2026, 9, 23, 22, 41),
        activeMs = 31 * 60_000, start = BookPosition("p1c8.xhtml", 0.25), end = BookPosition("p2c1.xhtml", 0.31), wordsRead = 1_200,
    )

    @Test
    fun groupsByDayMostRecentFirstWithYearOnlyForOtherYears() {
        val state = map(listOf(lastYear, mondayMorning, mondayNoon, yesterday, staleCurrentRow), current)

        assertThat(state.days.map { it.group }).containsExactly(
            DayGroup.Today,
            DayGroup.Yesterday,
            DayGroup.Date(LocalDate.of(2026, 9, 21)),
            DayGroup.Date(LocalDate.of(2025, 12, 31)),
        ).inOrder()
        assertThat(state.days.map { it.showYear }).containsExactly(false, false, false, true).inOrder()
        assertThat(state.days[2].sessions.map { it.sessionId }).containsExactly(3L, 2L).inOrder()
    }

    @Test
    fun currentSessionReplacesItsStaleRowAndIsInProgress() {
        val state = map(listOf(yesterday, staleCurrentRow), current)

        val all = state.days.flatMap { it.sessions }
        assertThat(all.count { it.sessionId == 5L }).isEqualTo(1)
        val today = state.days.first().sessions.single()
        assertThat(today.inProgress).isTrue()
        assertThat(today.start).isEqualTo(ClockTime(22, 10))
        assertThat(today.durationMinutes).isEqualTo(31)
        assertThat(today.startPercent).isEqualTo(25)
        assertThat(today.endPercent).isEqualTo(31)
    }

    @Test
    fun currentSessionNotYetPersistedIsShownOnce() {
        val state = map(listOf(yesterday), current.copy(id = 0))

        assertThat(state.days.first().group).isEqualTo(DayGroup.Today)
        assertThat(state.days.first().sessions.single().inProgress).isTrue()
        assertThat(state.days.flatMap { it.sessions }).hasSize(2)
    }

    @Test
    fun finishedSessionCarriesTimesPercentsPassageAndResumeTarget() {
        val item = map(listOf(yesterday), null).days.single().sessions.single()

        assertThat(item.inProgress).isFalse()
        assertThat(item.start).isEqualTo(ClockTime(21, 5))
        assertThat(item.end).isEqualTo(ClockTime(21, 36))
        assertThat(item.durationMinutes).isEqualTo(31)
        assertThat(item.startPercent).isEqualTo(18)
        assertThat(item.endPercent).isEqualTo(25)
        assertThat(item.resumeTarget).isEqualTo(BookPosition("p1c8.xhtml", 0.25))
        assertThat(item.passage).isEqualTo(
            passageLabel(chapterPathAt(toc, "p1c6.xhtml"), chapterPathAt(toc, "p1c8.xhtml")),
        )
    }

    @Test
    fun passageOfAnchoredChaptersFollowsTheProgressionInsideTheFile() {
        // Défaut C : deux chapitres ancrés dans le même fichier ; le passage ne doit pas se réduire au premier.
        val anchored = listOf(
            TocNode(
                "Première partie", "p1.xhtml", progression = 0.2,
                children = listOf(
                    TocNode("Chapitre VI", "p1.xhtml", progression = 0.2),
                    TocNode("Chapitre VIII", "p1.xhtml", progression = 0.6),
                ),
            ),
        )
        // « JSON » du locator de ce test : « href@progression ».
        val session = entity(7, at(2026, 9, 22, 21, 5), at(2026, 9, 22, 21, 36), 31, "p1.xhtml@0.3", "p1.xhtml@0.7", 0.18, 0.25)

        val state = journalUiState(listOf(session), null, anchored, now, zone) { json ->
            ResourcePosition(json.substringBefore('@'), json.substringAfter('@').toDouble())
        }

        assertThat(state.days.single().sessions.single().passage).isEqualTo("Partie I, chap. VI → VIII")
    }

    @Test
    fun unknownHrefGivesNoPassageWithoutCrashing() {
        val state = journalUiState(listOf(yesterday), null, emptyList(), now, zone, positionOf = { null })

        assertThat(state.days.single().sessions.single().passage)
            .isEqualTo(passageLabel(emptyList(), emptyList()))
    }

    @Test
    fun emptyJournal() {
        assertThat(map(emptyList(), null).isEmpty).isTrue()
    }

    @Test
    fun percentsAreFlooredAndClamped() {
        assertThat(percentOf(0.29)).isEqualTo(29) // 0,29 × 100 = 28,999… en double
        assertThat(percentOf(0.314)).isEqualTo(31)
        assertThat(percentOf(1.0)).isEqualTo(100)
        assertThat(percentOf(-0.1)).isEqualTo(0)
    }

    @Test
    fun durationsAreRoundedToAtLeastOneMinute() {
        assertThat(minutesOf(0)).isEqualTo(1)
        assertThat(minutesOf(89_000)).isEqualTo(1)
        assertThat(minutesOf(90_000)).isEqualTo(2)
        assertThat(minutesOf(31 * 60_000L)).isEqualTo(31)
    }

    @Test
    fun dayLabelsAreFrenchWithCapitalInitial() {
        assertThat(dayLabel(LocalDate.of(2026, 9, 21), "EEEE d MMMM")).isEqualTo("Lundi 21 septembre")
        assertThat(dayLabel(LocalDate.of(2025, 9, 21), "EEEE d MMMM yyyy")).isEqualTo("Dimanche 21 septembre 2025")
    }

    @Test
    fun clockTimeUsesTheGivenZone() {
        assertThat(clockTimeOf(at(2026, 9, 21, 7, 42), zone)).isEqualTo(ClockTime(7, 42))
    }
}
