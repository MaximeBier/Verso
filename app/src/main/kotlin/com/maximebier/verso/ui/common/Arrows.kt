package com.maximebier.verso.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.maximebier.verso.ui.theme.LiterataFamily

private const val ARROW = '→'

/**
 * « → » toujours dessiné en Literata, quelle que soit la police de l’app : la flèche de Libron est minuscule et posée
 * trop bas (journal « 18 → 25 % », traduction « Anglais → Français »). Le reste du texte garde son style.
 */
fun literataArrows(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    var index = text.indexOf(ARROW)
    while (index >= 0) {
        addStyle(SpanStyle(fontFamily = LiterataFamily), index, index + 1)
        index = text.indexOf(ARROW, index + 1)
    }
}
