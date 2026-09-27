package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.position.ProgressionScreenDistance
import com.maximebier.verso.core.position.estimateBookScreens

/** Ce qui détermine la taille d’un « écran » de texte. Valeurs par défaut : référence V1 (Atkinson 19 sp). */
data class ReaderMetrics(
    val viewportHeightPx: Int,
    val fontScale: Float,
    val density: Float,
    val fontSizeSp: Double = ReaderScreenDistance.REFERENCE_FONT_SIZE_SP,
    val lineHeight: Double = ReaderScreenDistance.REFERENCE_LINE_HEIGHT,
    val marginDp: Double = ReaderScreenDistance.REFERENCE_MARGIN_DP,
)

/** Nombre d’écrans du livre, pour que la machine à états raisonne en écrans et non en pourcentage. */
object ReaderScreenDistance {
    /** Environ 35 caractères par ligne en 19 sp sur 390 dp avec 24 dp de marges (spec, « Lecture »). */
    const val BASE_WORDS_PER_LINE = 6.0
    const val REFERENCE_FONT_SIZE_SP = 19.0
    const val REFERENCE_LINE_HEIGHT = 1.6
    const val REFERENCE_MARGIN_DP = 24.0
    const val REFERENCE_WIDTH_DP = 390.0

    /** Téléphone de référence (390 × 844 dp à 2,625) tant que la surface n’est pas mesurée. */
    const val FALLBACK_VIEWPORT_HEIGHT_PX = 2_000
    const val FALLBACK_DENSITY = 2.625f

    /** Un livre sans texte (images seules) compte au moins un écran. */
    const val MIN_BOOK_SCREENS = 1.0

    fun lineHeightPx(
        fontScale: Float,
        density: Float,
        fontSizeSp: Double = REFERENCE_FONT_SIZE_SP,
        lineHeight: Double = REFERENCE_LINE_HEIGHT,
    ): Float = (fontSizeSp * lineHeight * fontScale * density).toFloat()

    /** Mots par ligne : inversement proportionnels à la taille, proportionnels à la largeur du texte. */
    fun wordsPerLine(
        fontScale: Float,
        fontSizeSp: Double = REFERENCE_FONT_SIZE_SP,
        marginDp: Double = REFERENCE_MARGIN_DP,
    ): Double {
        val sizeRatio = REFERENCE_FONT_SIZE_SP / (fontSizeSp * fontScale.coerceAtLeast(0.1f))
        val widthRatio = (REFERENCE_WIDTH_DP - 2 * marginDp) / (REFERENCE_WIDTH_DP - 2 * REFERENCE_MARGIN_DP)
        return BASE_WORDS_PER_LINE * sizeRatio * widthRatio
    }

    fun bookScreens(totalWords: Long, metrics: ReaderMetrics): Double {
        require(metrics.viewportHeightPx > 0) { "La surface de lecture n’est pas mesurée" }
        val screens = estimateBookScreens(
            totalWords = totalWords,
            viewportHeightPx = metrics.viewportHeightPx,
            lineHeightPx = lineHeightPx(metrics.fontScale, metrics.density, metrics.fontSizeSp, metrics.lineHeight),
            wordsPerLine = wordsPerLine(metrics.fontScale, metrics.fontSizeSp, metrics.marginDp),
        )
        return screens.coerceAtLeast(MIN_BOOK_SCREENS)
    }

    fun distance(totalWords: Long, metrics: ReaderMetrics): ProgressionScreenDistance =
        ProgressionScreenDistance(bookScreens(totalWords, metrics))

    fun fallbackDistance(totalWords: Long): ProgressionScreenDistance =
        distance(totalWords, ReaderMetrics(FALLBACK_VIEWPORT_HEIGHT_PX, fontScale = 1f, density = FALLBACK_DENSITY))
}
