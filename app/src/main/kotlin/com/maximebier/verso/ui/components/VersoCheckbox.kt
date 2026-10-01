package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Case à cocher des maquettes 4.03 et 4.04 (22 dp) : cochée = fond accent et coche, sinon contour 2 dp. Purement
 * visuelle : la ligne qui la porte est `toggleable` (Role.Checkbox) et dit son état à TalkBack.
 */
@Composable
fun VersoCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val box = modifier.size(22.dp).clip(VersoShapes.cover).clearAndSetSemantics {}
    if (checked) {
        Box(box.background(colors.accent), contentAlignment = Alignment.Center) {
            Icon(VersoIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
        }
    } else {
        Box(box.background(colors.background).border(2.dp, colors.outline, VersoShapes.cover))
    }
}
