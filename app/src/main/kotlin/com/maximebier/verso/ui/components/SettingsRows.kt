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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
fun SettingsParagraph(text: String, modifier: Modifier = Modifier, bottomPadding: Dp = 12.dp) {
    Text(
        text = text,
        style = VersoTheme.typography.body,
        color = VersoTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = bottomPadding),
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

/** Ligne d'un choix unique : bouton radio et libellé, toute la ligne cliquable (≥ 48 dp), annoncée comme bouton radio. */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 24 dp dans une liste plein écran ; 0 dans un dialogue, qui a déjà sa marge. */
    horizontalPadding: Dp = 24.dp,
) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.outline),
        )
        Text(
            text = title,
            style = if (selected) VersoTheme.typography.bodyStrong else VersoTheme.typography.body,
            color = colors.text,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** Ligne « À propos » non cliquable : icône de l’app (40 dp), « Verso » et la version en dessous. */
@Composable
fun AppInfoRow(title: String, summary: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) {}
            .padding(start = 24.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(size = 40.dp)
        RowTexts(title, summary)
    }
}

/**
 * Libellé et choix segmenté sur une ligne (« Thème sombre : Sombre / Nuit »). TalkBack lit le libellé sur le
 * groupe de segments ; le texte visible ne se lit pas une seconde fois.
 */
@Composable
fun SegmentedRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = VersoTheme.typography.rowTitle,
            color = VersoTheme.colors.text,
            modifier = Modifier.weight(1f).clearAndSetSemantics {},
        )
        VersoSegmentedButton(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            groupLabel = title,
            modifier = Modifier.width(SegmentedRowControlWidth),
        )
    }
}

private val SegmentedRowControlWidth = 200.dp
