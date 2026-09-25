package com.maximebier.verso.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Arrondis de tokens.json (shape.radiusDp) ; boutons en pilule. */
object VersoShapes {
    val cover = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(28.dp)
    val sheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(percent = 50)
}
