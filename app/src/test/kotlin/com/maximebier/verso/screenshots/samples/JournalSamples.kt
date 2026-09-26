package com.maximebier.verso.screenshots.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maximebier.verso.core.journal.DayGroup
import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.ui.reader.ClockTime
import com.maximebier.verso.ui.reader.JournalDay
import com.maximebier.verso.ui.reader.JournalSessionItem
import com.maximebier.verso.ui.reader.JournalSheet
import com.maximebier.verso.ui.reader.JournalUiState
import com.maximebier.verso.ui.theme.VersoTheme
import java.time.LocalDate

/** Contenu de la maquette 1.13 (les passages sont des données, produites par :core en production). */
internal val journalSampleState = JournalUiState(
    days = listOf(
        JournalDay(DayGroup.Today, showYear = false, sessions = listOf(
            sampleItem(5, 22, 10, 22, 41, 31, "Partie I, chap. VIII → Partie II, chap. I", 25, 31, inProgress = true),
        )),
        JournalDay(DayGroup.Yesterday, showYear = false, sessions = listOf(
            sampleItem(4, 21, 5, 21, 36, 31, "Partie I, chap. VI → VIII", 18, 25),
        )),
        JournalDay(DayGroup.Date(LocalDate.of(2026, 9, 21)), showYear = false, sessions = listOf(
            sampleItem(3, 12, 30, 12, 48, 18, "Partie I, chap. V → VI", 15, 18),
            sampleItem(2, 7, 42, 8, 2, 20, "Partie I, chap. IV → V", 11, 15),
        )),
        JournalDay(DayGroup.Date(LocalDate.of(2026, 9, 19)), showYear = false, sessions = listOf(
            sampleItem(1, 15, 2, 15, 50, 48, "Partie I, chap. I → IV", 0, 11),
        )),
    ),
)

internal fun sampleItem(
    id: Long, startH: Int, startM: Int, endH: Int, endM: Int, minutes: Int,
    passage: String?, startPercent: Int, endPercent: Int, inProgress: Boolean = false,
) = JournalSessionItem(
    sessionId = id,
    start = ClockTime(startH, startM),
    end = ClockTime(endH, endM),
    durationMinutes = minutes,
    passage = passage,
    startPercent = startPercent,
    endPercent = endPercent,
    inProgress = inProgress,
    resumeTarget = BookPosition("""{"href":"s$id.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":${endPercent / 100.0}}}""", endPercent / 100.0),
)

/** Écran de lecture (fond seul, la WebView de Readium n'existe pas sous Robolectric) avec la feuille 1.13 ouverte. */
@Composable
internal fun JournalSample(
    state: JournalUiState = journalSampleState,
    onResume: (BookPosition) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        JournalSheet(state = state, onResume = onResume, onDismiss = onDismiss)
    }
}

@Composable
internal fun JournalEmptySample() = JournalSample(state = JournalUiState())
