package com.maximebier.verso.reader

/** Seuils techniques des gestes sur la surface de lecture (les seuils de lecture sont dans `ReadingThresholds`). */
object ReaderGestures {
    /** Sans images de défilement natif : le défilement est stabilisé après ce délai sans nouvelle position. */
    const val SETTLE_QUIET_MS = 250L

    /** Au-delà, le défilement est considéré terminé quoi qu’il arrive. */
    const val SETTLE_MAX_MS = 4_000L

    const val SETTLE_POLL_MS = 40L

    /** Défilement natif arrêté : aucune image de défilement depuis ce délai (le navigateur attend 100 ms). */
    const val SCROLL_QUIET_MS = 150L

    /**
     * Après la dernière image de défilement, la position arrive en général vers 260 ms (anti-rebond de 100 ms du
     * navigateur, puis calcul JavaScript) ; au-delà de ce délai, le signal part sans elle (position inchangée).
     */
    const val POSITION_WAIT_MS = 600L

    /**
     * Remise en page (réglage changé) : attente maximale d’une position rapportée par le navigateur, après
     * l’application des préférences puis après le retour au locator (aucune si la position ne change pas).
     */
    const val RELAYOUT_REPORT_MAX_MS = 1_000L

    /** Un toucher moins de ce délai après une image de défilement arrête un défilement en cours. */
    const val SCROLL_ACTIVE_MS = 100L

    /** Glissé minimal, commencé au bord d’un chapitre, qui ouvre le chapitre voisin (dp). */
    const val CHAPTER_CHAIN_DRAG_DP = 40f

    /** Longueur maximale de l’extrait enregistré avec la position. */
    const val EXCERPT_MAX_CHARS = 200

    /** Un appui sans glissement plus court que ceci est un tap. */
    const val TAP_MAX_DURATION_MS = 300L

    /**
     * Tap que le navigateur n’a pas signalé dans ce délai : l’app le traite elle-même
     * (prototype : le premier tap après une restauration peut être absorbé par un pré-défilement interne).
     */
    const val TAP_FALLBACK_DELAY_MS = 400L

    /**
     * Tap traité par l’app sans le signal du navigateur : un signal arrivé dans ce délai est le sien, en retard
     * (taps rapprochés), et n’est pas compté une seconde fois ; au-delà, il n’est plus attendu.
     */
    const val TAP_ECHO_MAX_MS = 1_000L

    /**
     * Bascule continu ↔ pages : délai laissé à Readium pour remettre le chapitre en page avant de revenir au texte
     * qui était affiché. À revoir sur le téléphone si le retour tombe à côté.
     */
    const val MODE_SWITCH_SETTLE_MS = 300L

    /**
     * Pendant une sélection, intervalle de relecture du passage sélectionné (barre 3.04) : les poignées d’Android sont
     * des fenêtres à part, que la surface ne voit pas bouger.
     */
    const val SELECTION_POLL_MS = 300L

    /**
     * Après la fin d’une sélection, un tap signalé par Readium dans ce délai est celui qui l’a annulée (le JavaScript
     * peut le voir une fois la sélection déjà vide) : il n’affiche pas la barre de lecture.
     */
    const val SELECTION_DISMISS_TAP_MS = 600L
}
