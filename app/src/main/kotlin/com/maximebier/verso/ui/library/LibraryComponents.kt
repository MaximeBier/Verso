package com.maximebier.verso.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.ui.components.BookCover
import com.maximebier.verso.ui.components.CoverSize
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoProgressBar
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/** 16 sp, interligne 1,4 (auteur et chapitre de la carte). */
private val CardSecondaryStyle = VersoTypography.body.copy(lineHeight = 22.4.sp)

/** 14 sp, interligne 1,35 (pied de la carte). */
private val CardFooterStyle = VersoTypography.caption.copy(lineHeight = 18.9.sp)
private val CardFooterStrongStyle = VersoTypography.captionBold.copy(lineHeight = 18.9.sp)

/**
 * Carte « Reprendre », variante A complète (§3.3), utilisée en liste **et** en grille (la spec prime sur la maquette 1.03).
 * Toute la carte est un seul bouton : le pseudo-bouton « Reprendre » n'est pas focalisable séparément.
 */
@Composable
fun ResumeCard(
    title: String,
    author: String,
    chapter: String?,
    excerpt: String?,
    coverPath: String?,
    seed: String,
    progression: Float,
    percentText: String,
    remainingText: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    val label = contentDescription
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(VersoShapes.card)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = label }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            BookCover(title = title, coverPath = coverPath, seed = seed, size = CoverSize.RESUME)
            Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.resume_card_label), style = VersoTypography.captionBold, color = colors.accent)
                Text(
                    text = title,
                    style = VersoTypography.screenTitle,
                    color = colors.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (author.isNotEmpty()) {
                    Text(author, style = CardSecondaryStyle, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (chapter != null) {
                    Text(chapter, style = CardSecondaryStyle, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (excerpt != null) {
            Text(excerpt, style = VersoTypography.body, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VersoProgressBar(fraction = progression, current = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(percentText, style = CardFooterStrongStyle, color = colors.text)
                Text(remainingText, style = CardFooterStyle, color = colors.textSecondary)
            }
            // Pseudo-bouton : décor du bouton unique qu'est la carte (padding 18 / 22 de la maquette, flèche à droite).
            Row(
                modifier = Modifier
                    .heightIn(min = VersoDimens.controlMin)
                    .clip(VersoShapes.pill)
                    .background(colors.accent)
                    .padding(start = 18.dp, end = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.resume_card_button), style = VersoTypography.button, color = colors.onAccent)
                Icon(VersoIcons.ArrowRight, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Ligne d'état d'un livre (§3.4) : écrite, jamais seulement colorée. */
@Composable
fun BookStatusLine(status: BookStatus, progression: Float, percentText: String, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    when (status) {
        BookStatus.IN_PROGRESS -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersoProgressBar(fraction = progression, current = false, modifier = Modifier.weight(1f))
            Text(
                text = percentText,
                style = VersoTypography.captionSemiBold.copy(fontFeatureSettings = "tnum"),
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 44.dp),
            )
        }
        BookStatus.FINISHED -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VersoIcons.Check, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.library_state_finished), style = VersoTypography.captionSemiBold, color = colors.textSecondary)
        }
        BookStatus.NEW -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.library_state_new), style = VersoTypography.captionSemiBold, color = colors.textSecondary)
        }
    }
}

/** Bascule Liste / Grille (§3.6) : deux segments 52 × 48, icônes seules, intitulés TalkBack, état sélectionné exposé. */
@Composable
fun ViewModeToggle(selected: LibraryViewMode, onSelect: (LibraryViewMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val groupLabel = stringResource(R.string.library_view_mode_group)
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(VersoShapes.small)
            .border(1.dp, colors.outline, VersoShapes.small)
            .semantics { contentDescription = groupLabel }
            .selectableGroup(),
    ) {
        ViewModeSegment(
            icon = VersoIcons.List,
            label = stringResource(R.string.library_view_list),
            selected = selected == LibraryViewMode.LIST,
            onClick = { onSelect(LibraryViewMode.LIST) },
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.outline))
        ViewModeSegment(
            icon = VersoIcons.Grid,
            label = stringResource(R.string.library_view_grid),
            selected = selected == LibraryViewMode.GRID,
            onClick = { onSelect(LibraryViewMode.GRID) },
        )
    }
}

@Composable
private fun ViewModeSegment(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = VersoTheme.colors
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = VersoDimens.controlMin)
            .background(if (selected) colors.selection else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.onSelection else colors.text,
            modifier = Modifier.size(VersoDimens.iconSmall),
        )
    }
}

/** Menu ⋮ d'un livre (§3.9) : 232 dp, fond surface, rayon 12, ombre sans teinte, aucun voile. */
@Composable
fun BookOptionsMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(232.dp),
        shape = VersoShapes.small,
        containerColor = VersoTheme.colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        content = content,
    )
}

/** Entrée du menu ⋮ : 48 dp, icône 20 + libellé 16 sp 500 ; `destructive` = couleur danger (icône et texte). */
@Composable
fun BookOptionsMenuItem(text: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    val color = if (destructive) VersoTheme.colors.danger else VersoTheme.colors.text
    DropdownMenuItem(
        text = { Text(text, style = VersoTypography.segment, color = color) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(VersoDimens.iconSmall)) },
        modifier = Modifier.heightIn(min = VersoDimens.controlMin),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}
