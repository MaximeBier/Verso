package com.maximebier.verso.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/**
 * Champ de la fiche (§3.12) : libellé au-dessus (pas de libellé flottant), 56 dp, bordure outline 1 dp,
 * accent 2 dp au focus, fond background, une ligne ; aide facultative dessous.
 */
@Composable
fun DetailsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val colors = VersoTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // Mémoire interne du buffer : un champ contrôlé par une valeur externe qui ne se met pas à jour
    // à chaque frappe (ex. tests d'écran sans ViewModel) ne doit pas revenir en arrière quand le focus
    // change de champ. On ne resynchronise depuis `value` que lorsqu'il change réellement de l'extérieur
    // (ex. après un `flush()` qui reprend le dernier titre enregistré).
    var lastExternalValue by remember { mutableStateOf(value) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length))) }
    if (value != lastExternalValue && value != fieldValue.text) {
        fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
    }
    lastExternalValue = value
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Libellé visible, porté par le champ pour TalkBack (« Titre, zone d’édition »), même vide.
        Text(
            label,
            style = VersoTypography.captionBold,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                if (newValue.text != value) onValueChange(newValue.text)
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true,
            textStyle = VersoTypography.body.copy(color = colors.text, lineHeight = 23.2.sp),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(VersoShapes.small)
                        .background(colors.background)
                        .border(
                            width = if (focused) 2.dp else 1.dp,
                            color = if (focused) colors.accent else colors.outline,
                            shape = VersoShapes.small,
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { innerTextField() }
            },
        )
        if (supportingText != null) {
            Text(supportingText, style = VersoTypography.caption, color = colors.textSecondary)
        }
    }
}

private val MetadataGap = 16.dp

/**
 * Ligne de métadonnée (§3.14) : libellé à gauche, valeur 600 alignée à droite, filet bas divider. Si la valeur
 * ne tient plus sur une ligne à côté du libellé (texte système agrandi, nom de fichier long), elle passe sous le
 * libellé sur toute la largeur, pour ne pas couper « madame-bovary.epub » au milieu d'un mot.
 */
@Composable
fun MetadataRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val valueStyle = VersoTypography.body.copy(fontWeight = FontWeight(600))
    val measurer = rememberTextMeasurer()
    val gapPx = with(LocalDensity.current) { MetadataGap.roundToPx() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                val y = size.height - stroke / 2f
                drawLine(color = colors.divider, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = stroke)
            }
            .padding(vertical = 12.dp),
    ) {
        val labelWidth = measurer.measure(label, VersoTypography.body, maxLines = 1).size.width
        val valueWidth = measurer.measure(value, valueStyle, maxLines = 1).size.width
        val sideBySide = labelWidth + gapPx + valueWidth <= constraints.maxWidth
        if (sideBySide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MetadataGap), verticalAlignment = Alignment.Top) {
                Text(label, style = VersoTypography.body, color = colors.textSecondary)
                Text(value, style = valueStyle, color = colors.text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = VersoTypography.body, color = colors.textSecondary)
                Text(value, style = valueStyle, color = colors.text)
            }
        }
    }
}
