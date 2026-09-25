package com.maximebier.verso.core.position

/**
 * Seuils de la distinction lecture / navigation (spec, « Marque-page et progression »).
 * Valeurs de départ, à ajuster à l'usage : tout seuil passe par ici, jamais en dur.
 */
data class ReadingThresholds(
    /** Au-delà de cette vitesse (écrans par seconde) entre deux positions affichées : fling, donc navigation. */
    val flingScreensPerSecond: Double = 4.0,
    /** Durée de la fenêtre glissante de « plus de 3 écrans en moins de 5 s ». */
    val navigationWindowMs: Long = 5_000,
    /** Déplacement net, en écrans, qui fait d'un mouvement une navigation dans la fenêtre glissante. */
    val navigationWindowScreens: Double = 3.0,
    /** La carte « Revenir » apparaît si la position affichée est à plus de ce nombre d'écrans de la lecture. */
    val returnCardMinScreens: Double = 1.0,
    /** Durée de mouvement de lecture au nouvel endroit avant que la lecture y passe d'elle-même. */
    val confirmReadingMs: Long = 25_000,
    /** Dérive maximale, en écrans, autour du point d'arrivée pendant la confirmation. */
    val confirmMaxDriftScreens: Double = 1.0,
    /**
     * Délai de repos : sans nouvelle position affichée pendant ce délai, le défilement est arrêté.
     * Le tracker n'émet `SaveReading` qu'au repos ; l'app peut l'écrire sans attendre davantage.
     */
    val saveDebounceMs: Long = 500,
)
