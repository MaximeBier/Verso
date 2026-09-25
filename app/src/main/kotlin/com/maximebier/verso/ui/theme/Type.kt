@file:OptIn(ExperimentalTextApi::class)

package com.maximebier.verso.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R

private fun atkinson(weight: Int, style: FontStyle = FontStyle.Normal): Font = Font(
    resId = if (style == FontStyle.Italic) R.font.atkinson_hyperlegible_next_italic else R.font.atkinson_hyperlegible_next,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Atkinson Hyperlegible Next, police variable : une instance par graisse utilisée (380 = lecture en thème sombre). */
val AtkinsonFamily: FontFamily = FontFamily(
    atkinson(380),
    atkinson(400),
    atkinson(500),
    atkinson(600),
    atkinson(700),
    atkinson(400, FontStyle.Italic),
)

private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** Style Atkinson ; interligne en sp (suit l'agrandissement du texte système). */
private fun verso(sizeSp: Float, weight: Int, lineHeightSp: Float? = null): TextStyle = TextStyle(
    fontFamily = AtkinsonFamily,
    fontWeight = FontWeight(weight),
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeightStyle = CenteredLineHeight,
)

/** Échelle de v1-ui-reference §3.19 : 14 / 16 / 18 / 22 / 28 sp. */
object VersoTypography {
    val logo: TextStyle = verso(28f, 700).copy(letterSpacing = (-0.01).em)
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
}
