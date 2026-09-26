package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.core.journal.DayGroup
import com.maximebier.verso.core.journal.SessionRecord
import com.maximebier.verso.core.journal.groupByDay
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.core.text.durationOfMinutes
import com.maximebier.verso.core.text.passageLabel
import com.maximebier.verso.data.db.SessionEntity
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

// ---------------------------------------------------------------------------
// Modèle d'affichage
// ---------------------------------------------------------------------------

/** Heure d'horloge locale, 24 h (« 7 h 42 », « 22 h 10 »). */
data class ClockTime(val hour: Int, val minute: Int)

/** Une session telle qu'affichée dans la feuille 1.13. */
data class JournalSessionItem(
    val sessionId: Long, // 0 si la session en cours n'est pas encore écrite
    val start: ClockTime,
    val end: ClockTime,
    val durationMinutes: Int, // temps actif arrondi, au moins 1
    val passage: String?, // « Partie I, chap. VI → VIII » ; null si le sommaire ne permet pas de le dire
    val startPercent: Int,
    val endPercent: Int,
    val inProgress: Boolean, // « En cours », sans action
    val resumeTarget: BookPosition, // fin de la session : cible de « Reprendre ici »
)

/** Un groupe de jour ; l'année n'est affichée que pour une autre année que l'année courante. */
data class JournalDay(val group: DayGroup, val showYear: Boolean, val sessions: List<JournalSessionItem>)

data class JournalUiState(val days: List<JournalDay> = emptyList()) {
    val isEmpty: Boolean get() = days.isEmpty()
}

// ---------------------------------------------------------------------------
// Transformation pure (testée par JournalMappingTest)
// ---------------------------------------------------------------------------

/**
 * Construit l'état du journal : sessions enregistrées (plus récentes d'abord ou non, l'ordre est refait ici)
 * + session en cours du SessionTracker, qui remplace sa ligne éventuellement périmée en base.
 * [positionOf] extrait d'un locator JSON le fichier (sans fragment) et la progression dans ce fichier ;
 * en production : [positionOfLocatorJson].
 */
fun journalUiState(
    sessions: List<SessionEntity>,
    current: SessionRecord?,
    toc: List<TocNode>,
    now: Long,
    zone: ZoneId,
    positionOf: (String) -> ResourcePosition?,
): JournalUiState {
    val currentId = current?.id?.takeIf { it != 0L }
    val rows = buildList {
        if (current != null) {
            add(JournalRow(current.id, current.startedAt, current.endedAt, current.activeMs, current.start, current.end, inProgress = true))
        }
        sessions.filter { it.id != currentId }.forEach { entity ->
            add(
                JournalRow(
                    id = entity.id,
                    startedAt = entity.startedAt,
                    endedAt = entity.endedAt,
                    activeMs = entity.activeMs,
                    start = BookPosition(entity.startLocatorJson, entity.startProgression),
                    end = BookPosition(entity.endLocatorJson, entity.endProgression),
                    inProgress = false,
                ),
            )
        }
    }.sortedByDescending { it.startedAt }

    val currentYear = Instant.ofEpochMilli(now).atZone(zone).year
    val days = groupByDay(rows, { it.startedAt }, now, zone).map { (group, dayRows) ->
        JournalDay(
            group = group,
            showYear = group is DayGroup.Date && group.date.year != currentYear,
            sessions = dayRows.map { it.toItem(toc, zone, positionOf) },
        )
    }
    return JournalUiState(days)
}

/** Fichier d'une position (href sans fragment) et progression dans ce fichier (null si inconnue). */
data class ResourcePosition(val href: String, val progression: Double?)

/** Position du locator Readium sérialisé : href sans fragment et progression dans le fichier ; null si illisible. */
fun positionOfLocatorJson(json: String): ResourcePosition? =
    Locators.fromJson(json)?.let { ResourcePosition(Locators.hrefKey(it), it.locations.progression) }

private data class JournalRow(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val activeMs: Long,
    val start: BookPosition,
    val end: BookPosition,
    val inProgress: Boolean,
)

private fun JournalRow.toItem(toc: List<TocNode>, zone: ZoneId, positionOf: (String) -> ResourcePosition?): JournalSessionItem {
    val startPath = positionOf(start.locatorJson)?.let { chapterPathAt(toc, it.href, it.progression) }.orEmpty()
    val endPath = positionOf(end.locatorJson)?.let { chapterPathAt(toc, it.href, it.progression) }.orEmpty()
    return JournalSessionItem(
        sessionId = id,
        start = clockTimeOf(startedAt, zone),
        end = clockTimeOf(endedAt, zone),
        durationMinutes = minutesOf(activeMs),
        passage = passageLabel(startPath, endPath),
        startPercent = percentOf(start.totalProgression),
        endPercent = percentOf(end.totalProgression),
        inProgress = inProgress,
        resumeTarget = end,
    )
}

internal fun clockTimeOf(epochMs: Long, zone: ZoneId): ClockTime {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    return ClockTime(time.hour, time.minute)
}

/** Pourcentage entier arrondi vers le bas (v1-ui-reference §1), protégé contre l'erreur d'arrondi des doubles. */
internal fun percentOf(progression: Double): Int =
    floor(progression * 100 + 1e-9).toInt().coerceIn(0, 100)

/** Minutes de temps actif, arrondies à la minute la plus proche, jamais 0. */
internal fun minutesOf(activeMs: Long): Int =
    ((activeMs + 30_000) / 60_000).toInt().coerceAtLeast(1)

/** « Lundi 21 septembre » : motif java.time en français, première lettre en capitale. */
internal fun dayLabel(date: LocalDate, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.FRENCH).format(date)
        .replaceFirstChar { it.titlecase(Locale.FRENCH) }

// ---------------------------------------------------------------------------
// Interface
// ---------------------------------------------------------------------------

/**
 * Feuille « Journal de lecture » (1.13), ouverte depuis la barre de lecture. L'en-tête (titre, sous-titre)
 * et le bouton « Fermer » sont ceux de VersoBottomSheet (2.2) ; « Fermer » et le toucher sur le voile appellent [onDismiss].
 */
@Composable
fun JournalSheet(state: JournalUiState, onResume: (BookPosition) -> Unit, onDismiss: () -> Unit) {
    VersoBottomSheet(
        title = stringResource(R.string.journal_title),
        subtitle = stringResource(R.string.journal_subtitle),
        onDismissRequest = onDismiss,
    ) {
        JournalSheetContent(state = state, onResume = onResume, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

/** Corps de la feuille, sous l'en-tête : liste des sessions par jour, ou phrase du journal vide. */
@Composable
fun JournalSheetContent(
    state: JournalUiState,
    onResume: (BookPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    Column(modifier) {
        if (state.isEmpty) {
            Text(
                text = stringResource(R.string.journal_empty),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
                style = JournalBody,
                color = colors.textSecondary,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
            ) {
                state.days.forEach { day ->
                    item(key = "day-${day.group}") { DayHeader(day) }
                    items(day.sessions, key = { "session-${it.sessionId}-${it.inProgress}" }) { item ->
                        SessionCard(item, onResume)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: JournalDay) {
    val label = when (val group = day.group) {
        DayGroup.Today -> stringResource(R.string.journal_today)
        DayGroup.Yesterday -> stringResource(R.string.journal_yesterday)
        is DayGroup.Date -> dayLabel(
            group.date,
            stringResource(if (day.showYear) R.string.journal_day_pattern_other_year else R.string.journal_day_pattern),
        )
    }
    Text(
        text = label,
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)
            .semantics { heading() },
        style = VersoTypography.captionBold,
        color = VersoTheme.colors.textSecondary,
    )
}

@Composable
private fun SessionCard(item: JournalSessionItem, onResume: (BookPosition) -> Unit) {
    val colors = VersoTheme.colors
    val resumeLabel = stringResource(R.string.journal_resume_here)
    val base = Modifier.fillMaxWidth().clip(VersoShapes.small)
    val container = if (item.inProgress) {
        // Session en cours : carte en « background », lue d'un bloc par TalkBack, sans action.
        base.background(colors.background).semantics(mergeDescendants = true) {}
    } else {
        base.clickable(role = Role.Button, onClickLabel = resumeLabel) { onResume(item.resumeTarget) }
    }
    val timeLabel = if (item.inProgress) {
        stringResource(R.string.journal_since, clockLabel(item.start))
    } else {
        stringResource(R.string.journal_time_range, clockLabel(item.start), clockLabel(item.end))
    }

    Column(container.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(timeLabel, Modifier.weight(1f), style = JournalStrong, color = colors.text)
            Text(durationLabel(item.durationMinutes), style = JournalStrong, color = colors.text)
        }
        item.passage?.let { passage ->
            Text(passage, style = JournalBody, color = colors.text)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.journal_percent_range, item.startPercent, item.endPercent),
                modifier = Modifier.weight(1f),
                style = VersoTypography.caption,
                color = colors.textSecondary,
            )
            if (item.inProgress) {
                Text(
                    text = stringResource(R.string.journal_in_progress),
                    style = VersoTypography.captionBold,
                    color = colors.textSecondary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(resumeLabel, style = VersoTypography.captionBold, color = colors.textSecondary)
                    Icon(VersoIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun clockLabel(time: ClockTime): String =
    stringResource(R.string.common_clock_time, time.hour, time.minute)

@Composable
private fun durationLabel(minutes: Int): String {
    val parts = durationOfMinutes(minutes)
    return when {
        parts.hours == 0 -> stringResource(R.string.common_duration_minutes, parts.minutes)
        parts.minutes == 0 -> stringResource(R.string.common_duration_hours, parts.hours)
        else -> stringResource(R.string.common_duration_hours_minutes, parts.hours, parts.minutes)
    }
}

// Styles de v1-ui-reference 1.13 : 16 sp 700 lh 22 (heures, durée), 16 sp lh 1,4 (passage) ;
// 14 sp (pourcentages, actions) = VersoTypography.caption / captionBold tels quels.
private val JournalStrong: TextStyle = VersoTypography.bodyStrong.copy(lineHeight = 22.sp)
private val JournalBody: TextStyle = VersoTypography.body.copy(lineHeight = 22.4.sp)
