package com.maximebier.verso.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

/**
 * Premier nombre de colonnes de [candidates] (du plus grand au plus petit) où chaque libellé tient sur une ligne
 * dans sa cellule. Le texte système agrandi fait passer la grille à moins de colonnes au lieu de couper un mot.
 */
@Composable
internal fun fittingColumns(
    labels: List<String>,
    style: TextStyle,
    maxWidth: Dp,
    gap: Dp,
    cellPadding: Dp,
    candidates: List<Int>,
): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return candidates.firstOrNull { count ->
        val cell = (maxWidth - gap * (count - 1)) / count - cellPadding * 2
        val roomPx = with(density) { cell.toPx() }
        labels.all { measurer.measure(it, style, maxLines = 1).size.width <= roomPx }
    } ?: candidates.last()
}

/** Grille de [columns] colonnes égales ; les cellules d’une rangée ont la même hauteur. */
@Composable
internal fun <T> AdaptiveGrid(
    items: List<T>,
    columns: Int,
    gap: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { item -> cell(item, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
