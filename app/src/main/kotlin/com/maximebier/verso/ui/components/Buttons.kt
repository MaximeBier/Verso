package com.maximebier.verso.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

private fun paddingFor(icon: ImageVector?): PaddingValues =
    if (icon != null) PaddingValues(start = 18.dp, end = 22.dp) else PaddingValues(horizontal = 24.dp)

@Composable
private fun ButtonIcon(icon: ImageVector?) {
    if (icon == null) return
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(VersoDimens.iconSmall))
    Spacer(Modifier.width(8.dp))
}

/** Bouton principal de l'écran (accent). large = 56 dp, réservé au bouton de l'écran vide. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = VersoTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (large) VersoDimens.emptyStatePrimaryButton else VersoDimens.controlMin),
        shape = VersoShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.surfaceHigh,
            disabledContentColor = colors.textSecondary,
        ),
        contentPadding = paddingFor(icon),
    ) {
        ButtonIcon(icon)
        Text(text = text, style = VersoTheme.typography.button)
    }
}

/** Bouton à contour (« Importer », « Sommaire », « Journal ») : encre, jamais d'accent. */
@Composable
fun OutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = VersoTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = VersoDimens.controlMin),
        shape = VersoShapes.pill,
        border = BorderStroke(1.dp, if (enabled) colors.outline else colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text, disabledContentColor = colors.textSecondary),
        contentPadding = if (icon != null) PaddingValues(start = 18.dp, end = 22.dp) else PaddingValues(horizontal = 22.dp),
    ) {
        ButtonIcon(icon)
        Text(text = text, style = VersoTheme.typography.buttonOutlined)
    }
}

/** « Supprimer le livre » : contour et texte danger. */
@Composable
fun DangerOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = VersoIcons.Trash,
) {
    val colors = VersoTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = VersoDimens.controlMin),
        shape = VersoShapes.pill,
        border = BorderStroke(1.dp, colors.danger),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
        contentPadding = paddingFor(icon),
    ) {
        ButtonIcon(icon)
        Text(text = text, style = VersoTheme.typography.buttonOutlined)
    }
}

/** Bouton destructif plein des dialogues (« Supprimer », « Effacer ») : fond danger, texte onDanger. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = VersoDimens.controlMin),
        shape = VersoShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.onDanger),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text = text, style = VersoTheme.typography.button)
    }
}

/** Bouton texte (dialogues, snackbar, carte de retour) ; couleur à l'encre par défaut. */
@Composable
fun VersoTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = VersoTheme.colors.text,
    horizontalPadding: Dp = 14.dp,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = VersoDimens.controlMin),
        shape = VersoShapes.pill,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
    ) {
        Text(text = text, style = VersoTheme.typography.button)
    }
}
