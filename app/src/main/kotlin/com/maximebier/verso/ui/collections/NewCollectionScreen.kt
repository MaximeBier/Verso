package com.maximebier.verso.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.BookCover
import com.maximebier.verso.ui.components.CoverSize
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoCheckbox
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSearchField
import com.maximebier.verso.ui.details.DetailsTextField
import com.maximebier.verso.ui.theme.VersoTheme

/** Intentions de l’écran 4.03 ; toutes facultatives pour les tests. */
data class NewCollectionActions(
    val onClose: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onToggle: (Long) -> Unit = {},
    val onCreate: () -> Unit = {},
)

/** Point d’entrée de NewCollectionRoute ; [onCreated] reçoit l’id de la collection créée. */
@Composable
fun NewCollectionDestination(preselectedBookId: Long?, onClose: () -> Unit, onCreated: (Long) -> Unit) {
    val viewModel: NewCollectionViewModel = viewModel(factory = NewCollectionViewModel.factory(preselectedBookId))
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.created) { state.created?.let(onCreated) }
    NewCollectionContent(
        state = state,
        actions = NewCollectionActions(
            onClose = onClose,
            onNameChange = viewModel::onNameChange,
            onQueryChange = viewModel::onQueryChange,
            onToggle = viewModel::onToggle,
            onCreate = viewModel::onCreate,
        ),
    )
}

/** Écran « Nouvelle collection » (4.03), sans ViewModel. */
@Composable
fun NewCollectionContent(state: NewCollectionUiState, actions: NewCollectionActions, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .heightIn(min = 64.dp)
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VersoIconButton(icon = VersoIcons.Close, contentDescription = stringResource(R.string.common_close), onClick = actions.onClose)
                Text(
                    text = stringResource(R.string.new_collection_title),
                    style = VersoTheme.typography.screenTitle,
                    color = colors.text,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                PrimaryButton(text = stringResource(R.string.new_collection_create), onClick = actions.onCreate, enabled = state.canCreate)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(bottom = bottom + 16.dp),
        ) {
            item(key = "nom") {
                DetailsTextField(
                    value = state.name,
                    onValueChange = actions.onNameChange,
                    label = stringResource(R.string.new_collection_name),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item(key = "titre") {
                TitleWithCount(
                    title = stringResource(R.string.new_collection_books),
                    count = pluralStringResource(R.plurals.new_collection_selected, state.selectedCount, state.selectedCount),
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            item(key = "filtre") {
                VersoSearchField(
                    value = state.query,
                    onValueChange = actions.onQueryChange,
                    hint = stringResource(R.string.new_collection_filter),
                    label = stringResource(R.string.new_collection_filter_label),
                    clearLabel = stringResource(R.string.new_collection_filter_clear),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
            }
            items(state.books, key = { it.book.id }) { item -> SelectableBookRow(item, onToggle = { actions.onToggle(item.book.id) }) }
        }
    }
}

/** Ligne à cocher (4.03) : case, couverture 36 × 54 dp, titre et auteur ; toute la ligne bascule la case. */
@Composable
private fun SelectableBookRow(item: SelectableBook, onToggle: () -> Unit) {
    val colors = VersoTheme.colors
    val book = item.book
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = item.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VersoCheckbox(checked = item.selected)
        BookCover(title = book.title, coverPath = book.coverPath, seed = book.colorSeed, size = CoverSize.SMALL)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, style = VersoTheme.typography.rowTitle, color = colors.text)
            if (book.author.isNotEmpty()) {
                Text(book.author, style = VersoTheme.typography.caption, color = colors.textSecondary)
            }
        }
    }
}
