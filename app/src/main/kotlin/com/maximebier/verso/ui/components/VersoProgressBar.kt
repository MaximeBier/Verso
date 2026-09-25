package com.maximebier.verso.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Barre de progression : current = livre en cours (6 dp, accent), sinon 4 dp à l'encre (progressInk).
 * Dessinée sur Canvas : pas d'espace ni de point d'arrêt M3.
 */
@Composable
fun VersoProgressBar(fraction: Float, current: Boolean, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val clamped = fraction.coerceIn(0f, 1f)
    val fill = if (current) colors.accent else colors.progressInk
    val track = colors.progressTrack
    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (current) VersoDimens.progressCurrent else VersoDimens.progressOther)
            .progressSemantics(clamped),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        if (clamped > 0f) {
            drawRoundRect(color = fill, size = Size(size.width * clamped, size.height), cornerRadius = radius)
        }
    }
}
