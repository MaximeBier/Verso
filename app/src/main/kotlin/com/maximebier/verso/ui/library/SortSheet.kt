package com.maximebier.verso.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSegmentedButton
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/** « Récents », « Titre », « Auteur » : libellé visible du bouton de tri. */
@Composable
fun sortLabel(sort: LibrarySort): String = stringResource(
    when (sort) {
        LibrarySort.RECENT -> R.string.library_sort_recent
        LibrarySort.TITLE -> R.string.library_sort_title
        LibrarySort.AUTHOR -> R.string.library_sort_author
    },
)

/** « Trier et afficher, actuellement : récents, en liste » (intitulé TalkBack, maquette 2.07). */
@Composable
fun sortButtonDescription(sort: LibrarySort, viewMode: LibraryViewMode): String = stringResource(
    R.string.library_sort_button_description,
    stringResource(
        when (sort) {
            LibrarySort.RECENT -> R.string.library_sort_state_recent
            LibrarySort.TITLE -> R.string.library_sort_state_title
            LibrarySort.AUTHOR -> R.string.library_sort_state_author
        },
    ),
    stringResource(if (viewMode == LibraryViewMode.LIST) R.string.library_view_state_list else R.string.library_view_state_grid),
)

/** Bouton « ⇅ Récents ▾ » (2.07) : 48 dp, texte 16 sp gras à l'encre ; TalkBack lit l'intitulé complet. */
@Composable
fun SortButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = VersoDimens.controlMin)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 14.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VersoIcons.SortArrows, contentDescription = null, tint = colors.text, modifier = Modifier.size(VersoDimens.iconSmall))
        Text(
            text = label,
            style = VersoTheme.typography.bodyStrong,
            color = colors.text,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Icon(VersoIcons.ChevronDown, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
    }
}

/** Feuille « Trier et afficher » (2.07b) : chaque choix s'applique aussitôt, « Fermer » ou le voile la ferment. */
@Composable
fun SortAndDisplaySheet(
    sort: LibrarySort,
    viewMode: LibraryViewMode,
    onSortChange: (LibrarySort) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VersoTheme.colors
    VersoBottomSheet(
        title = stringResource(R.string.library_sort_sheet_title),
        subtitle = null,
        onDismissRequest = onDismiss,
        fitContent = true,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val sortLabelText = stringResource(R.string.library_sort_group)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sortLabelText, style = VersoTheme.typography.captionBold, color = colors.textSecondary)
                val sorts = LibrarySort.entries
                VersoSegmentedButton(
                    options = sorts.map { sortLabel(it) },
                    selectedIndex = sorts.indexOf(sort),
                    onSelect = { index -> onSortChange(sorts[index]) },
                    groupLabel = sortLabelText,
                )
            }
            val displayLabel = stringResource(R.string.library_view_mode_group)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayLabel, style = VersoTheme.typography.captionBold, color = colors.textSecondary)
                val modes = LibraryViewMode.entries
                VersoSegmentedButton(
                    options = listOf(stringResource(R.string.library_view_list), stringResource(R.string.library_view_grid)),
                    selectedIndex = modes.indexOf(viewMode),
                    onSelect = { index -> onViewModeChange(modes[index]) },
                    groupLabel = displayLabel,
                )
            }
        }
    }
}
