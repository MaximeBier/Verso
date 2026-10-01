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
import androidx.compose.ui.semantics.Role
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
import com.maximebier.verso.ui.collections.AddToCollectionSheet
import com.maximebier.verso.ui.collections.CollectionsActions
import com.maximebier.verso.ui.collections.CollectionsTab
import com.maximebier.verso.ui.collections.CollectionsUiState
import com.maximebier.verso.ui.collections.CollectionsViewModel
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

/** 16 sp, interligne 1,35 (auteur d'une ligne ou d'une cellule). */
@Composable
private fun authorStyle() = VersoTheme.typography.body.copy(lineHeight = 21.6.sp)

/** 16 sp, interligne 22 sp (compteur « 7 livres »). */
@Composable
private fun countStyle() = VersoTheme.typography.body.copy(lineHeight = 22.sp)

/**
 * Point d'entrée de LibraryRoute (signature figée par la tâche 2.2, appelé par VersoNavHost) :
 * la bibliothèque complète, reliée à son ViewModel.
 */
@Composable
fun LibraryDestination(
    onOpenSettings: () -> Unit,
    onOpenDetails: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onNewCollection: () -> Unit = {},
    onOpenCollection: (Long) -> Unit = {},
    onNewCollectionFor: (Long) -> Unit = {},
) {
    LibraryScreen(
        onOpenReader = onOpenReader,
        onOpenDetails = onOpenDetails,
        onOpenSettings = onOpenSettings,
        onNewCollectionFor = onNewCollectionFor,
        collectionActions = CollectionsActions(onOpenCollection = onOpenCollection, onNewCollection = onNewCollection),
    )
}

/** Relie l'écran au ViewModel et au sélecteur de fichiers Android (SAF). */
@Composable
fun LibraryScreen(
    onOpenReader: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onNewCollectionFor: (Long) -> Unit = {},
    collectionActions: CollectionsActions = CollectionsActions(),
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    collectionsViewModel: CollectionsViewModel = viewModel(factory = CollectionsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val collections by collectionsViewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImport(uri)
    }
    LibraryContent(
        state = state,
        actions = LibraryActions(
            onImport = { picker.launch(IncomingIntent.PICKER_MIME_TYPES) },
            onOpenSettings = onOpenSettings,
            onOpenBook = onOpenReader,
            onOpenDetails = onOpenDetails,
            onDeleteRequest = viewModel::onDeleteRequest,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onSortChange = viewModel::onSortChange,
            onViewModeChange = viewModel::onViewModeChange,
            onFilterChange = viewModel::onFilterChange,
            onDuplicateReplace = viewModel::onDuplicateReplace,
            onDuplicateIgnore = viewModel::onDuplicateIgnore,
            onRejectedDismiss = viewModel::onRejectedDismiss,
            onSnackbarAction = onOpenReader,
            onSnackbarShown = viewModel::onSnackbarShown,
            onOpenFailedShown = viewModel::onOpenFailedShown,
        ),
        collections = collections,
        collectionActions = collectionActions,
        addToCollectionSheet = { bookId, onDismiss ->
            AddToCollectionSheet(bookId = bookId, onNewCollection = onNewCollectionFor, onDismiss = onDismiss)
        },
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
    val onFilterChange: (LibraryFilter) -> Unit = {},
    val onDuplicateReplace: () -> Unit = {},
    val onDuplicateIgnore: () -> Unit = {},
    val onRejectedDismiss: () -> Unit = {},
    val onSnackbarAction: (Long) -> Unit = {},
    val onSnackbarShown: (ImportSnackbar) -> Unit = {},
    val onOpenFailedShown: () -> Unit = {},
    /** Ouvre la feuille « Ajouter à une collection » (4.04) ; câblé par LibraryContent. */
    val onAddToCollection: (Long) -> Unit = {},
)

/** Onglet de la bibliothèque (4.01). */
enum class LibraryTab { BOOKS, COLLECTIONS }

/** Écrans 1.01 à 1.06 (et 1.08 depuis le menu ⋮), onglet « Collections » (4.01), sans ViewModel. */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    collections: CollectionsUiState = CollectionsUiState(),
    collectionActions: CollectionsActions = CollectionsActions(),
    initialTab: LibraryTab = LibraryTab.BOOKS,
    addToCollectionSheet: @Composable (bookId: Long, onDismiss: () -> Unit) -> Unit = { _, _ -> },
) {
    val colors = VersoTheme.colors
    // Feuille 4.04 : gardée ouverte pendant « Nouvelle collection » (entrée de navigation sauvegardée).
    var addingBookId by rememberSaveable { mutableStateOf<Long?>(null) }
    @Suppress("NAME_SHADOWING")
    val actions = actions.copy(onAddToCollection = { addingBookId = it })
    var sortSheetVisible by rememberSaveable { mutableStateOf(false) }
    // Gardé dans l’entrée de navigation : un retour depuis une collection revient sur l’onglet « Collections ».
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbar = state.snackbar
    if (snackbar != null) {
        val message = stringResource(R.string.import_success, snackbar.title)
        val actionLabel = stringResource(R.string.import_success_action)
        // Consommé même si l’écran est quitté pendant l’affichage : il ne réapparaît pas au retour.
        LaunchedEffect(snackbar) {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) actions.onSnackbarAction(snackbar.bookId)
            } finally {
                actions.onSnackbarShown(snackbar)
            }
        }
    }
    val openFailed = state.openFailed
    if (openFailed != null) {
        val message = stringResource(R.string.library_open_failed, openFailed)
        LaunchedEffect(openFailed) {
            try {
                snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
            } finally {
                actions.onOpenFailedShown()
            }
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
                else -> Column(Modifier.fillMaxSize()) {
                    VersoSegmentedButton(
                        options = listOf(stringResource(R.string.library_tab_books), stringResource(R.string.library_tab_collections)),
                        selectedIndex = tab.ordinal,
                        onSelect = { tab = LibraryTab.entries[it] },
                        groupLabel = stringResource(R.string.library_tabs_group),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        tabs = true,
                    )
                    Box(Modifier.weight(1f)) {
                        when {
                            tab == LibraryTab.COLLECTIONS -> CollectionsTab(collections, collectionActions)
                            state.viewMode == LibraryViewMode.LIST -> LibraryList(state, actions) { sortSheetVisible = true }
                            else -> LibraryGrid(state, actions) { sortSheetVisible = true }
                        }
                    }
                }
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
    if (sortSheetVisible) {
        SortAndDisplaySheet(
            sort = state.sort,
            viewMode = state.viewMode,
            onSortChange = actions.onSortChange,
            onViewModeChange = actions.onViewModeChange,
            onDismiss = { sortSheetVisible = false },
        )
    }
    addingBookId?.let { bookId -> addToCollectionSheet(bookId) { addingBookId = null } }
}

@Composable
private fun LibraryList(state: LibraryUiState, actions: LibraryActions, onOpenSortSheet: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        state.resume?.let { resume ->
            item(key = "resume") {
                ResumeCardItem(resume, actions, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            }
        }
        item(key = "header") {
            LibraryHeader(state, onOpenSortSheet = onOpenSortSheet, modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 8.dp, bottom = 4.dp))
        }
        item(key = "filters") {
            LibraryFilterChips(selected = state.filter, onSelect = actions.onFilterChange)
        }
        if (state.filteredBooks.isEmpty()) {
            item(key = "filter-empty") { FilterEmptyText(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) }
        }
        items(state.listedBooks, key = { it.id }) { book -> BookRow(book, actions) }
    }
}

@Composable
private fun LibraryGrid(state: LibraryUiState, actions: LibraryActions, onOpenSortSheet: () -> Unit) {
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
            LibraryHeader(state, onOpenSortSheet = onOpenSortSheet, modifier = Modifier.padding(start = 8.dp))
        }
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            LibraryFilterChips(selected = state.filter, onSelect = actions.onFilterChange, modifier = Modifier.padding(start = 4.dp))
        }
        if (state.filteredBooks.isEmpty()) {
            item(key = "filter-empty", span = { GridItemSpan(maxLineSpan) }) {
                FilterEmptyText(Modifier.padding(start = 8.dp, top = 16.dp))
            }
        }
        itemsIndexed(state.listedBooks, key = { _, book -> book.id }) { index, book ->
            // Marge extérieure de 20 dp comme la maquette : 16 dp de la grille + 4 dp.
            val edge = if (index % 2 == 0) Modifier.padding(start = 4.dp) else Modifier.padding(end = 4.dp)
            BookGridCell(book, actions, edge)
        }
    }
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
        options = { BookOptions(book, actions) },
    )
}

@Composable
private fun HeaderTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, style = VersoTheme.typography.screenTitle, color = color, modifier = modifier)
}

@Composable
private fun HeaderCount(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, style = countStyle(), color = color, modifier = modifier)
}

/**
 * Titre, compteur et bascule liste/grille. À 100 %, les trois tiennent sur une ligne (mesuré ci-dessous
 * et inchangé par rapport au rendu d'origine). À grande taille de texte système (jusqu'à 200 %, cf.
 * contrainte « texte à 200 % sans coupure »), la mise en page se réduit d'abord en faisant passer la
 * bascule sous le titre et le compteur, puis, si le titre et le compteur eux-mêmes ne tiennent plus
 * côte à côte, en passant le titre seul sur sa propre ligne.
 */
@Composable
private fun LibraryHeader(state: LibraryUiState, onOpenSortSheet: () -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    val titleText = stringResource(R.string.library_title)
    val countText = pluralStringResource(R.plurals.library_book_count, state.filteredBooks.size, state.filteredBooks.size)
    val buttonLabel = sortLabel(state.sort)
    val buttonDescription = sortButtonDescription(state.sort, state.viewMode)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val sortButton = @Composable {
        SortButton(label = buttonLabel, description = buttonDescription, onClick = onOpenSortSheet)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val titleWidthPx = measurer.measure(titleText, VersoTheme.typography.screenTitle).size.width
        val countWidthPx = measurer.measure(countText, countStyle()).size.width
        // Bouton : texte + icônes (20 + 18 dp) + écarts (2 × 6 dp) + marges (14 + 12 dp).
        val buttonWidthPx = measurer.measure(buttonLabel, VersoTheme.typography.bodyStrong).size.width +
            with(density) { 76.dp.roundToPx() }
        val smallGapPx = with(density) { 10.dp.roundToPx() }
        val bigGapPx = with(density) { 8.dp.roundToPx() }

        val titleAndCountFit = titleWidthPx + smallGapPx + countWidthPx <= maxWidthPx
        val everythingFits = titleAndCountFit && titleWidthPx + smallGapPx + countWidthPx + bigGapPx + buttonWidthPx <= maxWidthPx

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
                sortButton()
            }
            titleAndCountFit -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderTitle(titleText, colors.text, Modifier.alignByBaseline())
                    HeaderCount(countText, colors.textSecondary, Modifier.alignByBaseline())
                }
                sortButton()
            }
            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderTitle(titleText, colors.text)
                HeaderCount(countText, colors.textSecondary)
                sortButton()
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
                .clickable(role = Role.Button) { actions.onOpenBook(book.id) }
                .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(title = book.title, coverPath = book.coverPath, seed = book.colorSeed, size = CoverSize.LIST)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, style = VersoTheme.typography.bookTitle, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotEmpty()) {
                    Text(book.author, style = authorStyle(), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            modifier = Modifier.clickable(role = Role.Button) { actions.onOpenBook(book.id) },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GridBookCover(title = book.title, author = book.author, coverPath = book.coverPath, seed = book.colorSeed)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, style = VersoTheme.typography.bookTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotEmpty()) {
                    Text(book.author, style = authorStyle(), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun FilterEmptyText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.library_filter_empty),
        style = VersoTheme.typography.body,
        color = VersoTheme.colors.textSecondary,
        modifier = modifier,
    )
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
                text = stringResource(R.string.library_menu_add_to_collection),
                icon = VersoIcons.ListPlus,
                onClick = {
                    expanded = false
                    actions.onAddToCollection(book.id)
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
