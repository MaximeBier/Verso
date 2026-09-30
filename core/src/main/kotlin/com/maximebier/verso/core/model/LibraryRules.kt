package com.maximebier.verso.core.model

/** État de lecture affiché dans la bibliothèque : « À lire », barre + %, « Terminé ». */
enum class BookStatus { TO_READ, IN_PROGRESS, FINISHED }

/** Règles du catalogue, seuils nommés. */
object LibraryRules {
    /** À partir de 99 % de progression, le livre est terminé. */
    const val FINISHED_PROGRESSION = 0.99

    /** Rouvrir le dernier livre s'il a été lu il y a moins de 24 h. */
    const val REOPEN_WINDOW_MS = 24L * 60 * 60 * 1000

    /**
     * Choix manuel de la fiche ([override]) s'il existe ; sinon jamais ouvert (pas de locator de lecture) → TO_READ,
     * progression ≥ 0,99 → FINISHED, sinon IN_PROGRESS. Le choix manuel tient jusqu'au choix manuel suivant.
     */
    fun status(hasReadingLocator: Boolean, progression: Double, override: BookStatus? = null): BookStatus = override ?: when {
        !hasReadingLocator -> BookStatus.TO_READ
        progression >= FINISHED_PROGRESSION -> BookStatus.FINISHED
        else -> BookStatus.IN_PROGRESS
    }

    /**
     * Vrai si le réglage est actif et que le livre a été ouvert il y a strictement moins de 24 h.
     * Une date d'ouverture dans le futur (horloge reculée) compte comme récente.
     */
    fun shouldReopen(enabled: Boolean, lastOpenedAt: Long?, now: Long): Boolean {
        if (!enabled || lastOpenedAt == null) return false
        return now - lastOpenedAt < REOPEN_WINDOW_MS
    }
}
