package com.maximebier.verso.core.position

import com.maximebier.verso.core.model.BookPosition
import kotlin.math.abs
import kotlin.math.max

/** Distance entre deux positions, exprimée en hauteurs d'écran. */
fun interface ScreenDistance {
    fun screensBetween(a: BookPosition, b: BookPosition): Double
}

/**
 * Distance déduite de la progression totale : `|Δprogression| × nombre d'écrans du livre`.
 * Un livre estimé à moins d'un écran (ou inconnu, NaN) compte pour un écran.
 */
class ProgressionScreenDistance(private val bookScreens: Double) : ScreenDistance {

    private val effectiveScreens: Double
        get() = if (bookScreens.isNaN() || bookScreens < 1.0) 1.0 else bookScreens

    override fun screensBetween(a: BookPosition, b: BookPosition): Double =
        abs(a.totalProgression - b.totalProgression) * effectiveScreens
}

/**
 * Nombre d'écrans du livre : lignes totales (`totalWords / wordsPerLine`) divisées par les lignes
 * visibles par écran (`viewportHeightPx / lineHeightPx`). L'espace entre paragraphes est ignoré.
 * 6 mots par ligne ≈ 35 caractères par ligne en français. Résultat jamais inférieur à 1 ;
 * toute entrée invalide (nulle, négative, NaN) donne 1.
 */
fun estimateBookScreens(
    totalWords: Long,
    viewportHeightPx: Int,
    lineHeightPx: Float,
    wordsPerLine: Double = 6.0,
): Double {
    if (totalWords <= 0 || viewportHeightPx <= 0 || !(lineHeightPx > 0f) || !(wordsPerLine > 0.0)) return 1.0
    val lines = totalWords / wordsPerLine
    val linesPerScreen = viewportHeightPx / lineHeightPx.toDouble()
    return max(1.0, lines / linesPerScreen)
}
