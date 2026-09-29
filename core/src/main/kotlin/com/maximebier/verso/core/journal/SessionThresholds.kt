package com.maximebier.verso.core.journal

/** Seuils du journal de lecture (spec, « Journal de lecture »). */
data class SessionThresholds(
    /** Fin après plus de 5 min sans interaction ; une reprise au-delà ouvre une nouvelle session. */
    val inactivityEndMs: Long = 5 * 60_000,
    /** Les intervalles de plus de 2 min sans scroll ni toucher sont exclus du temps actif. */
    val activeGapMs: Long = 2 * 60_000,
    /** Une session de moins de 30 s de temps actif (la durée affichée au journal) n’est pas gardée. */
    val minActiveMs: Long = 30_000,
)
