package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.position.ProgressionScreenDistance
import com.maximebier.verso.core.position.estimateBookScreens
import com.maximebier.verso.readium.ReadingStyle

/** Ce qui détermine la taille d’un « écran » de texte. */
data class ReaderMetrics(val viewportHeightPx: Int, val fontScale: Float, val density: Float)

/** Nombre d’écrans du livre, pour que la machine à états raisonne en écrans et non en pourcentage. */
object ReaderScreenDistance {
    /** Environ 35 caractères par ligne en Atkinson 19 sp sur 390 dp (spec, « Lecture »). */
    const val BASE_WORDS_PER_LINE = 6.0

    /** Téléphone de référence (390 × 844 dp à 2,625) tant que la surface n’est pas mesurée. */
    const val FALLBACK_VIEWPORT_HEIGHT_PX = 2_000
    const val FALLBACK_DENSITY = 2.625f

    /** Un livre sans texte (images seules) compte au moins un écran. */
    const val MIN_BOOK_SCREENS = 1.0

    fun lineHeightPx(
        fontScale: Float,
        density: Float,
        fontSizeSp: Double = ReadingStyle.READING_FONT_SIZE_SP,
        lineHeight: Double = ReadingStyle.LINE_HEIGHT,
    ): Float = (fontSizeSp * lineHeight * fontScale * density).toFloat()

    fun wordsPerLine(fontScale: Float): Double = BASE_WORDS_PER_LINE / fontScale.coerceAtLeast(0.1f)

    fun bookScreens(totalWords: Long, metrics: ReaderMetrics): Double {
        require(metrics.viewportHeightPx > 0) { "La surface de lecture n’est pas mesurée" }
        val screens = estimateBookScreens(
            totalWords = totalWords,
            viewportHeightPx = metrics.viewportHeightPx,
            lineHeightPx = lineHeightPx(metrics.fontScale, metrics.density),
            wordsPerLine = wordsPerLine(metrics.fontScale),
        )
        return screens.coerceAtLeast(MIN_BOOK_SCREENS)
    }

    fun distance(totalWords: Long, metrics: ReaderMetrics): ProgressionScreenDistance =
        ProgressionScreenDistance(bookScreens(totalWords, metrics))

    fun fallbackDistance(totalWords: Long): ProgressionScreenDistance =
        distance(totalWords, ReaderMetrics(FALLBACK_VIEWPORT_HEIGHT_PX, fontScale = 1f, density = FALLBACK_DENSITY))
}
