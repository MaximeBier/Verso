package com.maximebier.verso.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.maximebier.verso.ui.components.BookCover
import com.maximebier.verso.ui.components.CoverSize
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoProgressBar
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

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
    val typography = VersoTheme.typography
    /** 16 sp, interligne 1,4 (auteur et chapitre de la carte). */
    val cardSecondaryStyle = remember(typography) { typography.body.copy(lineHeight = 22.4.sp) }
    /** 14 sp, interligne 1,35 (pied de la carte). */
    val cardFooterStyle = remember(typography) { typography.caption.copy(lineHeight = 18.9.sp) }
    val cardFooterStrongStyle = remember(typography) { typography.captionBold.copy(lineHeight = 18.9.sp) }
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
                Text(stringResource(R.string.resume_card_label), style = VersoTheme.typography.captionBold, color = colors.accent)
                Text(
                    text = title,
                    style = VersoTheme.typography.screenTitle,
                    color = colors.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (author.isNotEmpty()) {
                    Text(author, style = cardSecondaryStyle, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (chapter != null) {
                    Text(chapter, style = cardSecondaryStyle, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (excerpt != null) {
            Text(excerpt, style = VersoTheme.typography.body, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VersoProgressBar(fraction = progression, current = true)
        // FlowRow plutôt que Row : à grande taille de texte système, le pseudo-bouton passe sous le pourcentage et
        // le temps restant au lieu de compresser leur colonne sous la largeur d'un mot (contrainte « texte à 200 %
        // sans coupure »).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier.weight(1f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(percentText, style = cardFooterStrongStyle, color = colors.text)
                Text(remainingText, style = cardFooterStyle, color = colors.textSecondary)
            }
            // Pseudo-bouton : décor du bouton unique qu'est la carte (padding 18 / 22 de la maquette, flèche à droite).
            Row(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .heightIn(min = VersoDimens.controlMin)
                    .clip(VersoShapes.pill)
                    .background(colors.accent)
                    .padding(start = 18.dp, end = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.resume_card_button), style = VersoTheme.typography.button, color = colors.onAccent)
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
                style = VersoTheme.typography.captionSemiBold.copy(fontFeatureSettings = "tnum"),
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
            Text(stringResource(R.string.library_state_finished), style = VersoTheme.typography.captionSemiBold, color = colors.textSecondary)
        }
        BookStatus.TO_READ -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VersoIcons.Bookmark, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.library_state_to_read), style = VersoTheme.typography.captionSemiBold, color = colors.textSecondary)
        }
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
        text = { Text(text, style = VersoTheme.typography.segment, color = color) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(VersoDimens.iconSmall)) },
        modifier = Modifier.heightIn(min = VersoDimens.controlMin),
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

/**
 * Pastilles de filtre (2.07) : 40 dp de haut dans une cible de 48 dp ; choisie = fond selection, gras et coche,
 * jamais la couleur seule ; les autres ont un contour. La rangée défile horizontalement (« Terminés » dépasse).
 */
@Composable
fun LibraryFilterChips(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(
        R.string.library_filter_all,
        R.string.library_filter_in_progress,
        R.string.library_filter_to_read,
        R.string.library_filter_finished,
    )
    val groupLabel = stringResource(R.string.library_filter_group)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = groupLabel }
            .selectableGroup()
            .padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryFilter.entries.forEachIndexed { index, filter ->
            FilterChip(label = stringResource(labels[index]), selected = filter == selected, onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = VersoTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = VersoDimens.controlMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shape = VersoShapes.small
        val chip = if (selected) {
            Modifier.clip(shape).background(colors.selection).padding(start = 10.dp, end = 14.dp)
        } else {
            Modifier.clip(shape).border(1.dp, colors.outline, shape).padding(horizontal = 14.dp)
        }
        Row(
            modifier = Modifier.heightIn(min = 40.dp).then(chip),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(VersoIcons.Check, contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(18.dp))
            }
            Text(
                text = label,
                style = if (selected) VersoTheme.typography.segmentSelected else VersoTheme.typography.segment,
                color = if (selected) colors.onSelection else colors.text,
            )
        }
    }
}
