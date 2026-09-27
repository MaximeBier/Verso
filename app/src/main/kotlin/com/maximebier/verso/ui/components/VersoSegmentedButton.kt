package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

private val SegmentPadding = 6.dp
private val CheckSize = 18.dp
private val CheckGap = 4.dp
private val SeparatorWidth = 1.dp

/**
 * Choix unique (tri) : segments égaux ; sélection = fond selection + gras + coche, jamais l'accent.
 * Quand un libellé (en gras, coche comprise) ne tient plus dans son segment, texte système agrandi, les
 * segments passent l'un sous l'autre plutôt que de couper le mot.
 */
@Composable
fun VersoSegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    groupLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val segmentWidth = (maxWidth - SeparatorWidth * (options.size - 1)) / options.size
        val labelRoomPx = with(density) { (segmentWidth - SegmentPadding * 2 - CheckSize - CheckGap).toPx() }
        val sideBySide = options.all { label ->
            measurer.measure(label, VersoTheme.typography.segmentSelected, maxLines = 1).size.width <= labelRoomPx
        }
        val group = Modifier
            .fillMaxWidth()
            .clip(VersoShapes.small)
            .border(1.dp, colors.outline, VersoShapes.small)
            .semantics { contentDescription = groupLabel }
            .selectableGroup()
        if (sideBySide) {
            Row(group.heightIn(min = VersoDimens.controlMin).height(IntrinsicSize.Min)) {
                options.forEachIndexed { index, label ->
                    if (index > 0) Box(Modifier.width(SeparatorWidth).fillMaxHeight().background(colors.outline))
                    Segment(label, index == selectedIndex, { onSelect(index) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(group) {
                options.forEachIndexed { index, label ->
                    if (index > 0) Box(Modifier.height(SeparatorWidth).fillMaxWidth().background(colors.outline))
                    Segment(label, index == selectedIndex, { onSelect(index) }, Modifier.fillMaxWidth().heightIn(min = VersoDimens.controlMin))
                }
            }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .background(if (selected) colors.selection else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SegmentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(VersoIcons.Check, contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(CheckSize))
            Spacer(Modifier.width(CheckGap))
        }
        Text(
            text = label,
            style = if (selected) VersoTheme.typography.segmentSelected else VersoTheme.typography.segment,
            color = if (selected) colors.onSelection else colors.text,
            textAlign = TextAlign.Center,
        )
    }
}
