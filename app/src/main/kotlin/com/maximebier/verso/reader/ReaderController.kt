package com.maximebier.verso.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.publication.Locator

/**
 * Ce que le reste de l’app voit du moteur de lecture. L’implémentation (navigateur classique de Readium,
 * `EpubNavigatorFragment`) est `FragmentReaderController`.
 */
interface ReaderController {
    /** Position affichée ; suit chaque scroll. `null` avant le premier affichage. */
    val displayed: StateFlow<Locator?>

    /** Une émission par geste de défilement, une fois le défilement stabilisé. */
    val gestures: Flow<GestureSignal>

    /** Hauteur visible du texte, en pixels ; 0 tant que la vue n’est pas mesurée. */
    val viewportHeightPx: Int

    /** Saut sans animation (sommaire, journal, « Revenir »). */
    suspend fun go(locator: Locator)

    /** Locator affiché enrichi du texte visible (extrait de 2 lignes de la carte Reprendre). */
    suspend fun excerptLocator(): Locator?
}

/** `chapterTurn` : le glissé, commencé au bord, a ouvert le chapitre voisin (lecture, pas navigation). */
data class GestureSignal(val timeMs: Long, val isFling: Boolean, val chapterTurn: Boolean = false)
