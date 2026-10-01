package com.maximebier.verso.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoCheckbox
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/** Feuille 4.04, reliée à son ViewModel ; [onNewCollection] reçoit le livre à présélectionner dans 4.03. */
@Composable
fun AddToCollectionSheet(bookId: Long, onNewCollection: (Long) -> Unit, onDismiss: () -> Unit) {
    val viewModel: AddToCollectionViewModel = viewModel(key = "ajout-$bookId", factory = AddToCollectionViewModel.factory(bookId))
    val state by viewModel.state.collectAsState()
    if (state.loading) return
    AddToCollectionSheetContent(
        state = state,
        onToggle = viewModel::onToggle,
        onNewCollection = { onNewCollection(bookId) },
        onDismiss = onDismiss,
    )
}

/** « Ajouter à une collection » (4.04) : une case par collection, « Nouvelle collection », « Terminé ». */
@Composable
fun AddToCollectionSheetContent(
    state: AddToCollectionUiState,
    onToggle: (Long) -> Unit,
    onNewCollection: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    VersoBottomSheet(
        title = stringResource(R.string.add_to_collection_title),
        subtitle = stringResource(R.string.common_quoted, state.bookTitle),
        onDismissRequest = onDismiss,
        fitContent = true,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            state.choices.forEach { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = choice.checked, role = Role.Checkbox, onValueChange = { onToggle(choice.id) })
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VersoCheckbox(checked = choice.checked)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(choice.name, style = typography.rowTitle, color = colors.text)
                        Text(
                            text = pluralStringResource(R.plurals.library_book_count, choice.bookCount, choice.bookCount),
                            style = typography.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onNewCollection)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(VersoIcons.Plus, contentDescription = null, tint = colors.text, modifier = Modifier.size(VersoDimens.icon))
                Text(stringResource(R.string.add_to_collection_new), style = typography.bodyStrong, color = colors.text)
            }
            PrimaryButton(
                text = stringResource(R.string.add_to_collection_done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
            )
        }
    }
}
