package com.maximebier.verso.reader

/** Seuils techniques des gestes sur la surface de lecture (les seuils de lecture sont dans `ReadingThresholds`). */
object ReaderGestures {
    /** Zone du tap qui affiche ou masque la barre de lecture, en fraction de la largeur. */
    val CENTER_TAP_RANGE: ClosedFloatingPointRange<Float> = 0.3f..0.7f

    /** Le défilement est stabilisé après ce délai sans mouvement. */
    const val SETTLE_QUIET_MS = 250L

    /** Au-delà, le défilement est considéré terminé quoi qu’il arrive. */
    const val SETTLE_MAX_MS = 4_000L

    const val SETTLE_POLL_MS = 40L

    /** Longueur maximale de l’extrait enregistré avec la position. */
    const val EXCERPT_MAX_CHARS = 200

    /** Un appui sans glissement plus court que ceci est un tap. */
    const val TAP_MAX_DURATION_MS = 300L

    /**
     * Tap au centre que le navigateur n’a pas signalé dans ce délai : l’app le traite elle-même
     * (prototype : le premier tap après une restauration peut être absorbé par un pré-défilement interne).
     */
    const val TAP_FALLBACK_DELAY_MS = 400L}
