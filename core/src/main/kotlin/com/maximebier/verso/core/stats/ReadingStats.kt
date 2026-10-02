package com.maximebier.verso.core.stats

import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import kotlin.math.roundToInt

/** Ce que les statistiques retiennent d'une session du journal. */
data class SessionStat(val activeMs: Long, val wordsRead: Long)

object StatsThresholds {
    /** En dessous d'une minute de lecture active retenue pour la vitesse, la vitesse n'est pas mesurée. */
    const val MIN_ACTIVE_MS_FOR_SPEED = 60_000L

    /** Une session plus lente (relecture, notes, téléphone posé) reste au journal mais ne compte pas dans la vitesse. */
    const val MIN_WORDS_PER_MINUTE = 60

    /** Au-delà, c’est du survol, pas de la lecture (ces sessions sont aussi retirées du journal). */
    const val MAX_WORDS_PER_MINUTE = 1_000

    /** Sous ce temps de lecture mesuré, le temps restant d’un livre prend la vitesse de tous les livres. */
    const val MIN_BOOK_ACTIVE_MS = 10 * 60_000L
}

/**
 * Statistiques d'un livre, calculées à partir de son journal (fiche 2.08).
 * [speedActiveMs] : temps actif des sessions retenues pour la vitesse ; [fallbackWordsPerMinute] : vitesse de tous
 * les livres, utilisée tant que ce livre n’a pas [StatsThresholds.MIN_BOOK_ACTIVE_MS] de lecture mesurée.
 */
data class ReadingStats(
    val totalActiveMs: Long,
    val sessionCount: Int,
    val wordsPerMinute: Int?,
    val speedActiveMs: Long = 0L,
    val fallbackWordsPerMinute: Int? = null,
) {
    /** Vitesse du temps restant : celle du livre, sinon celle de tous les livres, sinon [DEFAULT_WORDS_PER_MINUTE] (250). */
    val effectiveWordsPerMinute: Int
        get() = when {
            wordsPerMinute != null && speedActiveMs >= StatsThresholds.MIN_BOOK_ACTIVE_MS -> wordsPerMinute
            fallbackWordsPerMinute != null -> fallbackWordsPerMinute
            else -> wordsPerMinute ?: DEFAULT_WORDS_PER_MINUTE
        }
}

/**
 * Temps actif total et nombre de sessions (toutes les sessions du journal), et vitesse : médiane des vitesses des
 * sessions, pondérée par leur temps actif, parmi celles entre [StatsThresholds.MIN_WORDS_PER_MINUTE] et
 * [StatsThresholds.MAX_WORDS_PER_MINUTE]. Pas de vitesse sous [StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED] de temps
 * retenu. Valeurs négatives comptées 0.
 */
fun readingStats(sessions: List<SessionStat>, fallbackWordsPerMinute: Int? = null): ReadingStats {
    val activeMs = sessions.sumOf { it.activeMs.coerceAtLeast(0) }
    val speeds = sessions.mapNotNull { session ->
        val ms = session.activeMs
        val words = session.wordsRead
        if (ms <= 0 || words <= 0) return@mapNotNull null
        val wpm = words * 60_000.0 / ms
        if (wpm < StatsThresholds.MIN_WORDS_PER_MINUTE || wpm > StatsThresholds.MAX_WORDS_PER_MINUTE) null else wpm to ms
    }
    val speedMs = speeds.sumOf { it.second }
    val speed = if (speedMs < StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED) null else weightedMedian(speeds).roundToInt().coerceAtLeast(1)
    return ReadingStats(
        totalActiveMs = activeMs,
        sessionCount = sessions.size,
        wordsPerMinute = speed,
        speedActiveMs = speedMs,
        fallbackWordsPerMinute = fallbackWordsPerMinute,
    )
}

/** Vitesse de tous les livres : mêmes règles que [readingStats], sur toutes les sessions. */
fun overallWordsPerMinute(sessions: List<SessionStat>): Int? = readingStats(sessions).wordsPerMinute

/** Valeur où le poids cumulé atteint la moitié du total (plus petite valeur en cas d’égalité exacte). */
private fun weightedMedian(values: List<Pair<Double, Long>>): Double {
    val sorted = values.sortedBy { it.first }
    val half = sorted.sumOf { it.second } / 2.0
    var cumulated = 0L
    for ((value, weight) in sorted) {
        cumulated += weight
        if (cumulated >= half) return value
    }
    return sorted.last().first
}
