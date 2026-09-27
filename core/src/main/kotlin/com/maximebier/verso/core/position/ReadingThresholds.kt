package com.maximebier.verso.core.position

/**
 * Seuils de la distinction lecture / navigation (spec, « Marque-page et progression »).
 * Valeurs de départ, à ajuster à l'usage : tout seuil passe par ici, jamais en dur.
 */
data class ReadingThresholds(
    /**
     * Vitesse du doigt **au relâchement** (écrans par seconde) au-delà de laquelle le geste est un fling
     * (`GestureEnded.isFling`, décidé par la surface de lecture de l'app). Calibrée par le prototype
     * (étape 1) : lecture ≤ 0,32, flicks d'un vrai doigt de 1,0 à 7,4
     * (`docs/superpowers/plans/spike-readium-conclusions.md`). Le tracker ne la lit pas.
     */
    val flingScreensPerSecond: Double = 1.0,
    /**
     * Vitesse **entre deux positions affichées consécutives** (écrans par seconde) au-delà de laquelle le
     * mouvement est une navigation, même sans fin de geste lancée. Plus haute que [flingScreensPerSecond] :
     * en plein glissé de lecture, la vitesse instantanée dépasse largement la vitesse au relâchement.
     */
    val displayedSpeedNavigationScreensPerSecond: Double = 4.0,
    /** Durée de la fenêtre glissante de « plus de 3 écrans en moins de 5 s ». */
    val navigationWindowMs: Long = 5_000,
    /** Déplacement net, en écrans, qui fait d'un mouvement une navigation dans la fenêtre glissante. */
    val navigationWindowScreens: Double = 3.0,
    /** La carte « Revenir » apparaît si la position affichée est à plus de ce nombre d'écrans de la lecture. */
    val returnCardMinScreens: Double = 1.0,
    /**
     * Durée de lecture au nouvel endroit avant que la lecture y passe d'elle-même. Mesurée depuis le
     * premier glissé de lecture qui suit l'arrivée (jamais depuis l'arrivée elle-même), jusqu'au glissé
     * qui la fait atteindre ; les pauses de plus de [confirmMaxIdleGapMs] la remettent à zéro.
     */
    val confirmReadingMs: Long = 25_000,
    /**
     * Pause maximale entre deux glissés de lecture sans que la fenêtre de confirmation reparte à zéro.
     * En défilement continu, on avance le texte de quelques lignes toutes les quelques secondes
     * (5 à 10 s) ; 15 s couvre ces pauses avec marge. Rester nettement sous [confirmReadingMs] impose au
     * moins trois glissés pour confirmer : deux gestes isolés séparés par un temps mort ne suffisent jamais.
     * Un lecteur plus lent garde la carte, ce qui est l'erreur sans conséquence (il choisit lui-même).
     */
    val confirmMaxIdleGapMs: Long = 15_000,
    /** Dérive maximale, en écrans, autour du point d'arrivée pendant la confirmation. */
    val confirmMaxDriftScreens: Double = 1.0,
    /**
     * Délai de repos : sans nouvelle position affichée pendant ce délai, le défilement est arrêté.
     * Le tracker n'émet `SaveReading` qu'au repos ; l'app peut l'écrire sans attendre davantage.
     */
    val saveDebounceMs: Long = 500,
    /**
     * Après un fling qui n’a encore rapporté aucune position (navigateur classique : aucune position pendant
     * l’inertie), la navigation reste ouverte jusqu’à la position d’arrivée, au plus ce délai.
     */
    val flingPositionMaxWaitMs: Long = 4_000,
    /**
     * Saut approximatif (ancre) sans aucune position rapportée : au-delà de ce délai, le texte n’a pas bougé
     * (cible déjà à l’écran) et l’arrivée est la position affichée avant le saut.
     */
    val jumpArrivalMaxWaitMs: Long = 2_000,
)
