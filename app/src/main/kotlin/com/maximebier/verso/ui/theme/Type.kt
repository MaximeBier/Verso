package com.maximebier.verso.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.maximebier.verso.core.settings.ReadingFont

private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Échelle de v1-ui-reference §3.19 (14 / 16 / 18 / 22 / 28 sp), dans la police choisie (spec, « Police V2 » : la
 * police s'applique à toute l'app). Interlignes en sp (suivent l'agrandissement du texte système).
 */
class VersoTypography(private val family: FontFamily) {

    private fun verso(sizeSp: Float, weight: Int, lineHeightSp: Float? = null): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(weight),
        fontSize = sizeSp.sp,
        lineHeight = lineHeightSp?.sp ?: TextUnit.Unspecified,
        lineHeightStyle = CenteredLineHeight,
    )

    val display: TextStyle = verso(28f, 700, 35f)
    val screenTitle: TextStyle = verso(22f, 700, 27.5f)
    val sheetTitle: TextStyle = verso(22f, 700, 28.6f)
    val bookTitle: TextStyle = verso(18f, 600, 23.4f)
    val bookTitleStrong: TextStyle = verso(18f, 700, 23.4f)
    val emptyBody: TextStyle = verso(18f, 400, 27.9f)
    val emptyHint: TextStyle = verso(16f, 400, 24.8f)
    val body: TextStyle = verso(16f, 400, 24f)
    val bodyStrong: TextStyle = verso(16f, 700, 24f)
    val rowTitle: TextStyle = verso(16f, 600, 21.6f)
    val button: TextStyle = verso(16f, 700, 24f)
    val buttonOutlined: TextStyle = verso(16f, 600, 24f)
    val segment: TextStyle = verso(16f, 500, 21.6f)
    val segmentSelected: TextStyle = verso(16f, 700, 21.6f)
    val caption: TextStyle = verso(14f, 400, 19.6f)
    val captionSemiBold: TextStyle = verso(14f, 600, 19.6f)
    val captionBold: TextStyle = verso(14f, 700, 19.6f)

    /** Correspondance Material 3, pour les composants M3 utilisés tels quels. */
    val material: Typography = Typography(
        displaySmall = display,
        headlineSmall = display,
        titleLarge = screenTitle,
        titleMedium = bookTitle,
        titleSmall = rowTitle,
        bodyLarge = body,
        bodyMedium = caption,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = captionBold,
        labelSmall = caption,
    )

    companion object {
        private val Literata = VersoTypography(LiterataFamily)
        private val Atkinson = VersoTypography(AtkinsonFamily)
        private val Libron = VersoTypography(LibronFamily)
        private val System = VersoTypography(FontFamily.Default)

        /** Une instance par police (les styles ne sont pas recréés à chaque composition). */
        fun of(font: ReadingFont): VersoTypography = when (font) {
            ReadingFont.LITERATA -> Literata
            ReadingFont.ATKINSON -> Atkinson
            ReadingFont.LIBRON -> Libron
            ReadingFont.SYSTEM -> System
        }
    }
}

/** Typographie du thème courant (police choisie dans les réglages). */
val LocalVersoTypography = staticCompositionLocalOf { VersoTypography.of(ReadingFont.LITERATA) }
