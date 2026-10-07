package com.maximebier.verso.core.settings

/** Police de toute l'app et du texte de lecture (spec, « Police V2 »). */
enum class ReadingFont { LITERATA, ATKINSON, LIBRON, SYSTEM }

/** Interligne (spec, « Réglages de lecture ») : Serré 1,4, Normal 1,6, Aéré 1,8. */
enum class LineSpacing(val factor: Double) { TIGHT(1.4), NORMAL(1.6), AIRY(1.8) }

/** Marges latérales du texte, en dp : Étroites, Normales (valeur V1), Larges. */
enum class Margins(val dp: Double) { NARROW(16.0), NORMAL(24.0), WIDE(32.0) }

/** Défilement d'un livre : scroll continu ou pages tournées. */
enum class ScrollMode { CONTINUOUS, PAGES }

/** Bornes de la taille du texte de lecture (sp, avant l'échelle de police d'Android). */
object ReadingSettingsLimits {
    const val MIN_FONT_SIZE_SP = 14
    const val MAX_FONT_SIZE_SP = 32
    const val DEFAULT_FONT_SIZE_SP = 20
    const val FONT_SIZE_STEP_SP = 1
}

/**
 * Réglages communs à tous les livres (feuille « Aa » et Paramètres). Le défilement propre à un livre est dans
 * `books.scrollMode` ; [defaultScrollMode] vaut pour les livres sans choix.
 */
data class ReadingSettings(
    val font: ReadingFont = ReadingFont.LITERATA,
    val fontSizeSp: Int = ReadingSettingsLimits.DEFAULT_FONT_SIZE_SP,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val margins: Margins = Margins.NORMAL,
    val defaultScrollMode: ScrollMode = ScrollMode.CONTINUOUS,
) {
    init {
        require(fontSizeSp in ReadingSettingsLimits.MIN_FONT_SIZE_SP..ReadingSettingsLimits.MAX_FONT_SIZE_SP) {
            "Taille hors bornes : $fontSizeSp sp"
        }
    }

    val canGrow: Boolean get() = fontSizeSp < ReadingSettingsLimits.MAX_FONT_SIZE_SP
    val canShrink: Boolean get() = fontSizeSp > ReadingSettingsLimits.MIN_FONT_SIZE_SP

    fun larger(): ReadingSettings = withFontSize(fontSizeSp + ReadingSettingsLimits.FONT_SIZE_STEP_SP)

    fun smaller(): ReadingSettings = withFontSize(fontSizeSp - ReadingSettingsLimits.FONT_SIZE_STEP_SP)

    fun withFontSize(sp: Int): ReadingSettings =
        copy(fontSizeSp = sp.coerceIn(ReadingSettingsLimits.MIN_FONT_SIZE_SP, ReadingSettingsLimits.MAX_FONT_SIZE_SP))
}
