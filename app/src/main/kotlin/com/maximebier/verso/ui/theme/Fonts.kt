@file:OptIn(ExperimentalTextApi::class)

package com.maximebier.verso.ui.theme

import androidx.annotation.FontRes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.ReadingFont

/** Graisses utilisées par l'interface (380 = lecture en thème sombre). */
private val WEIGHTS = listOf(380, 400, 500, 600, 700)

private fun variable(@FontRes resId: Int, weight: Int, style: FontStyle): Font = Font(
    resId = resId,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun variableFamily(@FontRes regular: Int, @FontRes italic: Int): FontFamily = FontFamily(
    WEIGHTS.map { variable(regular, it, FontStyle.Normal) } + variable(italic, 400, FontStyle.Italic),
)

/** Literata, police variable (axes opsz et wght ; la taille optique reste celle par défaut dans l'interface). */
val LiterataFamily: FontFamily = variableFamily(R.font.literata, R.font.literata_italic)

/** Atkinson Hyperlegible Next, police variable (axe wght). */
val AtkinsonFamily: FontFamily =
    variableFamily(R.font.atkinson_hyperlegible_next, R.font.atkinson_hyperlegible_next_italic)

/** Police de toute l'app ; « Police du système » = celle du téléphone. */
fun fontFamilyFor(font: ReadingFont): FontFamily = when (font) {
    ReadingFont.LITERATA -> LiterataFamily
    ReadingFont.ATKINSON -> AtkinsonFamily
    ReadingFont.SYSTEM -> FontFamily.Default
}
