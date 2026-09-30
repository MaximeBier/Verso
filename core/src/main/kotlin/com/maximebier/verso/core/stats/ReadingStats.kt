package com.maximebier.verso.core.stats

import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import kotlin.math.roundToInt

/** Ce que les statistiques retiennent d'une session du journal. */
data class SessionStat(val activeMs: Long, val wordsRead: Long)

object StatsThresholds {
    /** En dessous d'une minute de lecture active en tout, la vitesse n'est pas mesurée. */
    const val MIN_ACTIVE_MS_FOR_SPEED = 60_000L
}

/** Statistiques d'un livre, calculées à partir de son journal (fiche 2.08). */
data class ReadingStats(val totalActiveMs: Long, val sessionCount: Int, val wordsPerMinute: Int?) {
    /** Vitesse mesurée, ou [DEFAULT_WORDS_PER_MINUTE] (250) sans mesure. */
    val effectiveWordsPerMinute: Int
        get() = wordsPerMinute ?: DEFAULT_WORDS_PER_MINUTE
}

/**
 * Temps actif total, nombre de sessions et vitesse = mots lus / temps actif, arrondie, au moins 1. Pas de vitesse
 * sous [StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED] de temps actif ou sans mot lu. Valeurs négatives comptées 0.
 */
fun readingStats(sessions: List<SessionStat>): ReadingStats {
    val activeMs = sessions.sumOf { it.activeMs.coerceAtLeast(0) }
    val words = sessions.sumOf { it.wordsRead.coerceAtLeast(0) }
    val speed = if (activeMs < StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED || words <= 0) {
        null
    } else {
        (words * 60_000.0 / activeMs).roundToInt().coerceAtLeast(1)
    }
    return ReadingStats(totalActiveMs = activeMs, sessionCount = sessions.size, wordsPerMinute = speed)
}
