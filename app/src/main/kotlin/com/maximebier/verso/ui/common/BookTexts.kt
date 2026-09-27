package com.maximebier.verso.ui.common

import android.content.res.Resources
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.maximebier.verso.R
import com.maximebier.verso.core.text.LocationTexts
import com.maximebier.verso.core.text.durationOfMinutes
import com.maximebier.verso.ui.components.DangerButton
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoTextButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

/**
 * Pourcentage lu, entier arrondi vers le bas, borné à 0..100 (« 31 % »), protégé contre l’erreur d’arrondi des
 * doubles (0,29 × 100 = 28,999…). Seul calcul de pourcentage de l’app : bibliothèque, fiche, lecteur et journal.
 */
fun percentOf(progression: Double): Int = floor(progression * 100 + 1e-9).toInt().coerceIn(0, 100)

/** Date au format français, par exemple « 12 septembre 2026 » avec le motif common_date_pattern_full. */
fun formatDate(epochMillis: Long, pattern: String, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern(pattern, Locale.FRENCH).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** « 5 h 30 », « 5 h » ou « 45 min ». */
@Composable
fun durationText(minutes: Int): String {
    val parts = durationOfMinutes(minutes)
    return when {
        parts.hours == 0 -> stringResource(R.string.common_duration_minutes, parts.minutes)
        parts.minutes == 0 -> stringResource(R.string.common_duration_hours, parts.hours)
        else -> stringResource(R.string.common_duration_hours_minutes, parts.hours, parts.minutes)
    }
}

/** « Environ 5 h 30 restantes », « Environ 1 min restante », ou « Moins d’une minute restante ». */
@Composable
fun remainingTimeText(minutes: Int): String = when {
    minutes < 1 -> stringResource(R.string.common_time_remaining_less_than_minute)
    // « 1 min » ou « 1 h » : singulier.
    minutes == 1 || minutes == 60 -> stringResource(R.string.common_time_remaining_one, durationText(minutes))
    else -> stringResource(R.string.common_time_remaining, durationText(minutes))
}

/** Confirmation de suppression (écran 1.08), depuis la fiche ou depuis le menu ⋮ de la bibliothèque. */
@Composable
fun DeleteBookDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    VersoDialog(
        title = stringResource(R.string.details_delete_dialog_title, title),
        onDismissRequest = onDismiss,
        buttons = {
            VersoTextButton(text = stringResource(R.string.details_delete_dialog_cancel), onClick = onDismiss)
            DangerButton(text = stringResource(R.string.details_delete_dialog_confirm), onClick = onConfirm)
        },
    ) {
        Text(stringResource(R.string.details_delete_dialog_body))
    }
}

/** Gabarits des emplacements (« Partie II, chap. I », « Chap. IV », « A → B ») tirés de `strings.xml`. */
fun Resources.locationTexts(): LocationTexts = LocationTexts(
    part = { getString(R.string.common_location_part, it) },
    chapter = { getString(R.string.common_location_chapter, it) },
    chapterOnly = { getString(R.string.common_location_short_no_part, it) },
    join = { part, chapter -> getString(R.string.common_location_long, part, chapter) },
    passage = { from, to -> getString(R.string.journal_passage, from, to) },
)
