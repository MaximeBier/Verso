package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/** Choix unique (tri) : segments égaux ; sélection = fond selection + gras + coche, jamais l'accent. */
@Composable
fun VersoSegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    groupLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = VersoDimens.controlMin)
            .height(IntrinsicSize.Min)
            .clip(VersoShapes.small)
            .border(1.dp, colors.outline, VersoShapes.small)
            .semantics { contentDescription = groupLabel }
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            if (index > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outline))
            }
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) colors.selection else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Icon(VersoIcons.Check, contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    style = if (selected) VersoTypography.segmentSelected else VersoTypography.segment,
                    color = if (selected) colors.onSelection else colors.text,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
