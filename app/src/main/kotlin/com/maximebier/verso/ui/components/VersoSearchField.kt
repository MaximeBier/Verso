package com.maximebier.verso.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Champ de filtre en pilule (4.03) : loupe, texte d’une ligne, croix qui efface quand il y a du texte.
 * [hint] s’affiche quand le champ est vide ; [label] est l’intitulé TalkBack du champ.
 */
@Composable
fun VersoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    label: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(1.dp, colors.outline, VersoShapes.pill)
            .padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VersoIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(VersoDimens.iconSmall))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = VersoDimens.controlMin)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, style = typography.body, color = colors.textSecondary)
                    inner()
                }
            },
        )
        if (value.isNotEmpty()) {
            VersoIconButton(
                icon = VersoIcons.Close,
                contentDescription = clearLabel,
                onClick = { onValueChange("") },
                tint = colors.textSecondary,
            )
        }
    }
}
