package com.maximebier.verso.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.maximebier.verso.ui.theme.VersoTheme

/** Passage surligné (3.05, 3.06) : fond `highlight` et soulignement, jamais la couleur seule. */
@Composable
fun MarkedText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(background = colors.highlight, textDecoration = TextDecoration.Underline)) { append(text) }
        },
        style = style,
        color = colors.text,
        modifier = modifier,
    )
}
