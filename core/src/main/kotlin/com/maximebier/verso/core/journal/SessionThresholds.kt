package com.maximebier.verso.core.journal

import com.maximebier.verso.core.stats.StatsThresholds
import kotlin.math.abs

/** Seuils du journal de lecture. */
data class SessionThresholds(
    /** Fin après plus de 5 min sans interaction ; une reprise au-delà ouvre une nouvelle session. */
    val inactivityEndMs: Long = 5 * 60_000,
    /** Les intervalles de plus de 2 min sans scroll ni toucher sont exclus du temps actif. */
    val activeGapMs: Long = 2 * 60_000,
    /** Une session de moins de 30 s de temps actif (la durée affichée au journal) n’est pas gardée. */
    val minActiveMs: Long = 30_000,
    /** Moins d’un écran de texte lu : la session n’est pas gardée (livres dont les mots sont comptés). */
    val minWords: Long = 150,
    /** Au-delà, c’est du survol : la session n’est pas gardée au journal ni comptée dans la vitesse. */
    val maxWordsPerMinute: Int = StatsThresholds.MAX_WORDS_PER_MINUTE,
    /** Livre sans mots comptés : écart de progression au-delà duquel la session compte comme lecture. */
    val minProgressionMove: Double = 0.001,
) {
    /**
     * Session gardée au journal : assez de temps actif et, pour un livre dont les mots sont comptés ([totalWords] > 0),
     * assez de mots lus sans dépasser la vitesse de survol ; sinon, une progression qui a bougé. Même règle que le
     * nettoyage des sessions écartées en base, sauf ce dernier cas, que la base ne vérifie pas (ces sessions-là ne sont
     * jamais écrites). Le rapport mots / temps varie : une session gardée peut devenir du survol, puis redevenir gardée.
     */
    fun keeps(record: SessionRecord, totalWords: Long): Boolean {
        if (record.activeMs < minActiveMs) return false
        if (totalWords == 0L) return abs(record.end.totalProgression - record.start.totalProgression) > minProgressionMove
        return record.wordsRead >= minWords && record.wordsRead * 60_000 <= maxWordsPerMinute.toLong() * record.activeMs
    }
}
