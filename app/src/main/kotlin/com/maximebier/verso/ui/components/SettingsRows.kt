package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/** Section de paramètres : titre 14 sp gras à l'encre, trait bas sauf la dernière. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VersoTheme.colors
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = VersoTheme.typography.captionBold,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 4.dp).semantics { heading() },
            )
            content()
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        }
    }
}

/** Phrase d'explication non cliquable (Affichage, Confidentialité). */
@Composable
fun SettingsParagraph(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = VersoTheme.typography.body,
        color = VersoTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun RowScope.RowTexts(title: String, summary: String?) {
    val colors = VersoTheme.colors
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = VersoTheme.typography.rowTitle, color = colors.text)
        if (summary != null) {
            Text(text = summary, style = VersoTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

private val rowModifier = Modifier
    .fillMaxWidth()
    .heightIn(min = 64.dp)

/** Ligne à interrupteur : toute la ligne bascule (rôle Switch) ; accent sur l'interrupteur seul. */
@Composable
fun SwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .then(rowModifier)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 24.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, summary)
        Box(Modifier.size(width = 60.dp, height = VersoDimens.controlMin), contentAlignment = Alignment.Center) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    { Icon(VersoIcons.CheckSwitch, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = colors.accent,
                    checkedIconColor = colors.accent,
                    uncheckedThumbColor = colors.outline,
                    uncheckedTrackColor = colors.surfaceHigh,
                    uncheckedBorderColor = colors.outline,
                ),
            )
        }
    }
}

/** Ligne d'action : icône 20 dp à droite (corbeille en danger, lien externe ou chevron en textSecondary). */
@Composable
fun ActionRow(
    title: String,
    summary: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = VersoTheme.colors.textSecondary,
) {
    Row(
        modifier = modifier
            .then(rowModifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 24.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, summary)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.padding(end = 12.dp).size(VersoDimens.iconSmall),
        )
    }
}

/** Ligne de valeur non cliquable (« Version … 1.0.0 »). */
@Composable
fun ValueRow(title: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .then(rowModifier)
            .semantics(mergeDescendants = true) {}
            .padding(start = 24.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, summary = null)
        Text(
            text = value,
            style = VersoTheme.typography.body,
            color = VersoTheme.colors.textSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}
