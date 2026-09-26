package com.maximebier.verso.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.R
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.importer.IncomingIntent
import com.maximebier.verso.ui.common.DeleteBookDialog
import com.maximebier.verso.ui.common.remainingTimeText
import com.maximebier.verso.ui.components.BookCover
import com.maximebier.verso.ui.components.CoverSize
import com.maximebier.verso.ui.components.GridBookCover
import com.maximebier.verso.ui.components.LibraryTopBar
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSegmentedButton
import com.maximebier.verso.ui.components.VersoSnackbarHost
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/** 16 sp, interligne 1,35 (auteur d'une ligne ou d'une cellule). */
private val AuthorStyle = VersoTypography.body.copy(lineHeight = 21.6.sp)

/** 16 sp, interligne 22 sp (compteur « 7 livres »). */
private val CountStyle = VersoTypography.body.copy(lineHeight = 22.sp)

/**
 * Point d'entrée de LibraryRoute (signature figée par la tâche 2.2, appelé par VersoNavHost) :
 * la bibliothèque complète, reliée à son ViewModel.
 */
@Composable
fun LibraryDestination(onOpenSettings: () -> Unit, onOpenDetails: (Long) -> Unit, onOpenReader: (Long) -> Unit) {
    LibraryScreen(onOpenReader = onOpenReader, onOpenDetails = onOpenDetails, onOpenSettings = onOpenSettings)
}

/** Relie l'écran au ViewModel et au sélecteur de fichiers Android (SAF). */
@Composable
fun LibraryScreen(
    onOpenReader: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImport(uri)
    }
    LibraryContent(
        state = state,
        actions = LibraryActions(
            onImport = { picker.launch(arrayOf(IncomingIntent.EPUB_MIME_TYPE)) },
            onOpenSettings = onOpenSettings,
            onOpenBook = onOpenReader,
            onOpenDetails = onOpenDetails,
            onDeleteRequest = viewModel::onDeleteRequest,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onSortChange = viewModel::onSortChange,
            onViewModeChange = viewModel::onViewModeChange,
            onDuplicateReplace = viewModel::onDuplicateReplace,
            onDuplicateIgnore = viewModel::onDuplicateIgnore,
            onRejectedDismiss = viewModel::onRejectedDismiss,
            onSnackbarAction = onOpenReader,
            onSnackbarShown = viewModel::onSnackbarShown,
            onOpenFailedShown = viewModel::onOpenFailedShown,
        ),
    )
}

/** Intentions de l'écran bibliothèque ; toutes facultatives pour les tests. */
data class LibraryActions(
    val onImport: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenBook: (Long) -> Unit = {},
    val onOpenDetails: (Long) -> Unit = {},
    val onDeleteRequest: (LibraryBook) -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onSortChange: (LibrarySort) -> Unit = {},
    val onViewModeChange: (LibraryViewMode) -> Unit = {},
    val onDuplicateReplace: () -> Unit = {},
    val onDuplicateIgnore: () -> Unit = {},
    val onRejectedDismiss: () -> Unit = {},
    val onSnackbarAction: (Long) -> Unit = {},
    val onSnackbarShown: () -> Unit = {},
    val onOpenFailedShown: () -> Unit = {},
)

/** Écrans 1.01 à 1.06 (et 1.08 depuis le menu ⋮), sans ViewModel. */
@Composable
fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbar = state.snackbar
    if (snackbar != null) {
        val message = stringResource(R.string.import_success, snackbar.title)
        val actionLabel = stringResource(R.string.import_success_action)
        LaunchedEffect(snackbar) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) actions.onSnackbarAction(snackbar.bookId)
            actions.onSnackbarShown()
        }
    }
    val openFailed = state.openFailed
    if (openFailed != null) {
        val message = stringResource(R.string.library_open_failed, openFailed)
        LaunchedEffect(openFailed) {
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
            actions.onOpenFailedShown()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LibraryTopBar(
                showImport = !state.loading && state.books.isNotEmpty(),
                onOpenSettings = actions.onOpenSettings,
                onImport = actions.onImport,
            )
        },
        snackbarHost = { VersoSnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Unit
                // Corps de l'écran 1.01 (tâche 2.2) : la barre du haut, déjà affichée, n'a pas de bouton « Importer ».
                state.books.isEmpty() -> EmptyLibraryContent(onImport = actions.onImport, modifier = Modifier.fillMaxSize())
                state.viewMode == LibraryViewMode.LIST -> LibraryList(state, actions)
                else -> LibraryGrid(state, actions)
            }
            if (state.importing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = colors.text,
                    trackColor = colors.progressTrack,
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        is ImportDialog.Duplicate -> DuplicateImportDialog(
            fileName = dialog.result.pending.originalFileName,
            existingTitle = dialog.result.existing.title,
            importedAt = dialog.result.existing.importedAt,
            onReplace = actions.onDuplicateReplace,
            onIgnore = actions.onDuplicateIgnore,
        )
        is ImportDialog.Rejected -> RejectedImportDialog(dialog.reason, dialog.fileName, actions.onRejectedDismiss)
        null -> Unit
    }
    state.pendingDelete?.let { book ->
        DeleteBookDialog(title = book.title, onConfirm = actions.onDeleteConfirm, onDismiss = actions.onDeleteDismiss)
    }
}

@Composable
private fun LibraryList(state: LibraryUiState, actions: LibraryActions) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        state.resume?.let { resume ->
            item(key = "resume") {
                ResumeCardItem(resume, actions, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            }
        }
        item(key = "header") {
            LibraryHeader(state, actions, Modifier.padding(start = 24.dp, top = 20.dp, end = 16.dp, bottom = 4.dp))
        }
        item(key = "sort") {
            SortSelector(
                selected = state.sort,
                onSelect = actions.onSortChange,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        items(state.books, key = { it.id }) { book -> BookRow(book, actions) }
    }
}

@Composable
private fun LibraryGrid(state: LibraryUiState, actions: LibraryActions) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        state.resume?.let { resume ->
            item(key = "resume", span = { GridItemSpan(maxLineSpan) }) {
                ResumeCardItem(resume, actions, Modifier.padding(top = 8.dp))
            }
        }
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(state, actions, Modifier.padding(start = 8.dp))
        }
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            SortSelector(selected = state.sort, onSelect = actions.onSortChange)
        }
        itemsIndexed(state.books, key = { _, book -> book.id }) { index, book ->
            // Marge extérieure de 20 dp comme la maquette : 16 dp de la grille + 4 dp.
            val edge = if (index % 2 == 0) Modifier.padding(start = 4.dp) else Modifier.padding(end = 4.dp)
            BookGridCell(book, actions, edge)
        }
    }
}

/** Tri Récents / Titre / Auteur (§3.5) : le segmenté partagé de 2.2, dans l'ordre de LibrarySort.entries. */
@Composable
private fun SortSelector(selected: LibrarySort, onSelect: (LibrarySort) -> Unit, modifier: Modifier = Modifier) {
    val sorts = LibrarySort.entries
    VersoSegmentedButton(
        options = listOf(
            stringResource(R.string.library_sort_recent),
            stringResource(R.string.library_sort_title),
            stringResource(R.string.library_sort_author),
        ),
        selectedIndex = sorts.indexOf(selected),
        onSelect = { index -> onSelect(sorts[index]) },
        groupLabel = stringResource(R.string.library_sort_group),
        modifier = modifier,
    )
}

@Composable
private fun ResumeCardItem(resume: ResumeInfo, actions: LibraryActions, modifier: Modifier) {
    val book = resume.book
    ResumeCard(
        title = book.title,
        author = book.author,
        chapter = resume.chapter,
        excerpt = resume.excerpt?.let { stringResource(R.string.common_quoted, it) },
        coverPath = book.coverPath,
        seed = book.colorSeed,
        progression = book.progression.toFloat(),
        percentText = stringResource(R.string.common_percent_read, book.percent),
        remainingText = remainingTimeText(resume.remainingMinutes),
        contentDescription = stringResource(R.string.resume_card_content_description, book.title, book.percent),
        onClick = { actions.onOpenBook(book.id) },
        modifier = modifier,
    )
}

/** Largeur intrinsèque de ViewModeToggle : deux segments de 52 dp + le séparateur de 1 dp (la bordure ne prend pas de place de mise en page). */
private val ToggleWidth = 105.dp

@Composable
private fun HeaderTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, style = VersoTypography.screenTitle, color = color, modifier = modifier)
}

@Composable
private fun HeaderCount(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, style = CountStyle, color = color, modifier = modifier)
}

/**
 * Titre, compteur et bascule liste/grille. À 100 %, les trois tiennent sur une ligne (mesuré ci-dessous
 * et inchangé par rapport au rendu d'origine). À grande taille de texte système (jusqu'à 200 %, cf.
 * contrainte « texte à 200 % sans coupure »), la mise en page se réduit d'abord en faisant passer la
 * bascule sous le titre et le compteur, puis, si le titre et le compteur eux-mêmes ne tiennent plus
 * côte à côte, en passant le titre seul sur sa propre ligne.
 */
@Composable
private fun LibraryHeader(state: LibraryUiState, actions: LibraryActions, modifier: Modifier) {
    val colors = VersoTheme.colors
    val titleText = stringResource(R.string.library_title)
    val countText = pluralStringResource(R.plurals.library_book_count, state.books.size, state.books.size)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val titleWidthPx = measurer.measure(titleText, VersoTypography.screenTitle).size.width
        val countWidthPx = measurer.measure(countText, CountStyle).size.width
        val toggleWidthPx = with(density) { ToggleWidth.roundToPx() }
        val smallGapPx = with(density) { 10.dp.roundToPx() }
        val bigGapPx = with(density) { 8.dp.roundToPx() }

        val titleAndCountFit = titleWidthPx + smallGapPx + countWidthPx <= maxWidthPx
        val everythingFits = titleAndCountFit && titleWidthPx + smallGapPx + countWidthPx + bigGapPx + toggleWidthPx <= maxWidthPx

        when {
            everythingFits -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderTitle(titleText, colors.text, Modifier.alignByBaseline())
                    HeaderCount(countText, colors.textSecondary, Modifier.alignByBaseline())
                }
                ViewModeToggle(selected = state.viewMode, onSelect = actions.onViewModeChange)
            }
            titleAndCountFit -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderTitle(titleText, colors.text, Modifier.alignByBaseline())
                    HeaderCount(countText, colors.textSecondary, Modifier.alignByBaseline())
                }
                ViewModeToggle(selected = state.viewMode, onSelect = actions.onViewModeChange)
            }
            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderTitle(titleText, colors.text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderCount(countText, colors.textSecondary, Modifier.weight(1f))
                    ViewModeToggle(selected = state.viewMode, onSelect = actions.onViewModeChange)
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: LibraryBook, actions: LibraryActions) {
    val colors = VersoTheme.colors
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { actions.onOpenBook(book.id) }
                .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(title = book.title, coverPath = book.coverPath, seed = book.colorSeed, size = CoverSize.LIST)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, style = VersoTypography.bookTitle, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotEmpty()) {
                    Text(book.author, style = AuthorStyle, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BookStatusLine(
                    status = book.status,
                    progression = book.progression.toFloat(),
                    percentText = stringResource(R.string.common_percent, book.percent),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        BookOptions(book, actions)
    }
}

@Composable
private fun BookGridCell(book: LibraryBook, actions: LibraryActions, modifier: Modifier) {
    val colors = VersoTheme.colors
    Column(modifier) {
        Column(
            modifier = Modifier.clickable { actions.onOpenBook(book.id) },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GridBookCover(title = book.title, author = book.author, coverPath = book.coverPath, seed = book.colorSeed)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, style = VersoTypography.bookTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotEmpty()) {
                    Text(book.author, style = AuthorStyle, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BookStatusLine(
                status = book.status,
                progression = book.progression.toFloat(),
                percentText = stringResource(R.string.common_percent, book.percent),
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
            BookOptions(book, actions)
        }
    }
}

/** Bouton ⋮ « Options pour « Titre » » et son menu (1.02b). */
@Composable
private fun BookOptions(book: LibraryBook, actions: LibraryActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        VersoIconButton(
            icon = VersoIcons.MoreVertical,
            contentDescription = stringResource(R.string.library_book_options, book.title),
            onClick = { expanded = true },
            tint = VersoTheme.colors.textSecondary,
        )
        BookOptionsMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookOptionsMenuItem(
                text = stringResource(R.string.library_menu_details),
                icon = VersoIcons.Info,
                onClick = {
                    expanded = false
                    actions.onOpenDetails(book.id)
                },
            )
            BookOptionsMenuItem(
                text = stringResource(R.string.library_menu_delete),
                icon = VersoIcons.Trash,
                destructive = true,
                onClick = {
                    expanded = false
                    actions.onDeleteRequest(book)
                },
            )
        }
    }
}
