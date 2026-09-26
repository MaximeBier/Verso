package com.maximebier.verso.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.maximebier.verso.R
import com.maximebier.verso.core.text.durationOfMinutes
import com.maximebier.verso.ui.components.DangerButton
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoTextButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

/** Pourcentage lu, entier arrondi vers le bas, borné à 0..100 (« 31 % »). */
fun percentOf(progression: Double): Int = floor(progression * 100).toInt().coerceIn(0, 100)

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

/** « Environ 5 h 30 restantes », ou « Moins d’une minute restante ». */
@Composable
fun remainingTimeText(minutes: Int): String =
    if (minutes < 1) {
        stringResource(R.string.common_time_remaining_less_than_minute)
    } else {
        stringResource(R.string.common_time_remaining, durationText(minutes))
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
