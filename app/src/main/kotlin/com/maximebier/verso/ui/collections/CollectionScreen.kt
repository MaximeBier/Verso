package com.maximebier.verso.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.R
import com.maximebier.verso.core.collections.CollectionOrder
import com.maximebier.verso.core.collections.CollectionSummary
import com.maximebier.verso.ui.common.durationText
import com.maximebier.verso.ui.components.BookCover
import com.maximebier.verso.ui.components.CoverSize
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoProgressBar
import com.maximebier.verso.ui.library.BookOptionsMenu
import com.maximebier.verso.ui.library.BookOptionsMenuItem
import com.maximebier.verso.ui.library.BookStatusLine
import com.maximebier.verso.ui.library.LibraryBook
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/** Intentions de l’écran 4.02 ; toutes facultatives pour les tests. */
data class CollectionActions(
    val onBack: () -> Unit = {},
    val onOpenReader: (Long) -> Unit = {},
    val onOpenDetails: (Long) -> Unit = {},
    val onReorderStart: () -> Unit = {},
    val onReorderEnd: () -> Unit = {},
    val onMove: (Int, Int) -> Unit = { _, _ -> },
    val onRemove: (Long) -> Unit = {},
    val onRenameRequest: () -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onDeleteRequest: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDialogDismiss: () -> Unit = {},
)

/** Point d’entrée de CollectionRoute ; l’écran se ferme si la collection est supprimée. */
@Composable
fun CollectionDestination(collectionId: Long, onBack: () -> Unit, onOpenReader: (Long) -> Unit, onOpenDetails: (Long) -> Unit) {
    val viewModel: CollectionViewModel = viewModel(factory = CollectionViewModel.factory(collectionId))
    val state by viewModel.state.collectAsState()
    val leave = state.deleted || state.missing
    LaunchedEffect(leave) { if (leave) onBack() }
    CollectionContent(
        state = state,
        actions = CollectionActions(
            onBack = onBack,
            onOpenReader = onOpenReader,
            onOpenDetails = onOpenDetails,
            onReorderStart = viewModel::onReorderStart,
            onReorderEnd = viewModel::onReorderEnd,
            onMove = viewModel::onMove,
            onRemove = viewModel::onRemove,
            onRenameRequest = viewModel::onRenameRequest,
            onRename = viewModel::onRename,
            onDeleteRequest = viewModel::onDeleteRequest,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDialogDismiss = viewModel::onDialogDismiss,
        ),
    )
}

/** « Environ 26 h de lecture restantes », singulier pour 1 min ou 1 h, « Moins d’une minute… ». */
@Composable
fun collectionRemainingText(minutes: Int): String = when {
    minutes < 1 -> stringResource(R.string.collection_remaining_less_than_minute)
    minutes == 1 || minutes == 60 -> stringResource(R.string.collection_remaining_one, durationText(minutes))
    else -> stringResource(R.string.collection_remaining, durationText(minutes))
}

/** Écran d’une collection (4.02), sans ViewModel. */
@Composable
fun CollectionContent(state: CollectionUiState, actions: CollectionActions, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val detail = state.detail
    BackHandler(enabled = state.reordering) { actions.onReorderEnd() }
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { if (detail != null) CollectionTopBar(detail, actions) },
    ) { padding ->
        if (detail != null) CollectionBody(detail, state.reordering, actions, Modifier.padding(padding))
    }
    if (detail != null) {
        when (state.dialog) {
            CollectionDialog.Rename -> RenameCollectionDialog(detail.name, onConfirm = actions.onRename, onDismiss = actions.onDialogDismiss)
            CollectionDialog.Delete -> DeleteCollectionDialog(detail.name, onConfirm = actions.onDeleteConfirm, onDismiss = actions.onDialogDismiss)
            null -> Unit
        }
    }
}

@Composable
private fun CollectionTopBar(detail: CollectionDetail, actions: CollectionActions) {
    val colors = VersoTheme.colors
    val count = pluralStringResource(R.plurals.library_book_count, detail.books.size, detail.books.size)
    val titles = @Composable { modifier: Modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(detail.name, style = VersoTheme.typography.screenTitle, color = colors.text, modifier = Modifier.semantics { heading() })
            Text(
                text = detail.summary.author?.let { stringResource(R.string.collections_author_and_count, it, count) } ?: count,
                style = VersoTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
    // Texte très agrandi : le nom passe sous les boutons, sur toute la largeur, pour ne pas se couper en plein mot.
    val stacked = LocalDensity.current.fontScale >= STACKED_CARD_FONT_SCALE
    Column(Modifier.fillMaxWidth().background(colors.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersoIconButton(icon = VersoIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back), onClick = actions.onBack)
            if (stacked) Box(Modifier.weight(1f)) else titles(Modifier.weight(1f))
            CollectionMenu(detail, actions)
        }
        if (stacked) titles(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
    }
}

@Composable
private fun CollectionMenu(detail: CollectionDetail, actions: CollectionActions) {
    var menu by rememberSaveable { mutableStateOf(false) }
    Box {
        VersoIconButton(
            icon = VersoIcons.MoreVertical,
            contentDescription = stringResource(R.string.collection_options),
            onClick = { menu = true },
        )
        BookOptionsMenu(expanded = menu, onDismissRequest = { menu = false }) {
            BookOptionsMenuItem(stringResource(R.string.collection_menu_rename), VersoIcons.Note, onClick = {
                menu = false
                actions.onRenameRequest()
            })
            if (detail.books.size > 1) {
                BookOptionsMenuItem(stringResource(R.string.collection_menu_reorder), VersoIcons.SortArrows, onClick = {
                    menu = false
                    actions.onReorderStart()
                })
            }
            BookOptionsMenuItem(stringResource(R.string.collection_menu_delete), VersoIcons.Trash, destructive = true, onClick = {
                menu = false
                actions.onDeleteRequest()
            })
        }
    }
}

@Composable
private fun CollectionBody(detail: CollectionDetail, reordering: Boolean, actions: CollectionActions, modifier: Modifier) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val summary = detail.summary
    // Livre glissé repéré par son id (la liste peut changer pendant le geste), hauteur mesurée de chaque ligne.
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<Long, Int>() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "progression") { ProgressSection(summary, Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)) }
        summary.resumeIndex?.let { index ->
            item(key = "reprendre") {
                ResumeBookCard(detail.books[index], actions.onOpenReader, Modifier.padding(horizontal = 16.dp))
            }
        }
        if (detail.books.isEmpty()) {
            item(key = "vide") {
                Text(
                    text = stringResource(R.string.collection_empty),
                    style = VersoTheme.typography.body,
                    color = VersoTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        } else {
            item(key = "titre-liste") { OrderHeader(reordering, detail.books.size > 1, actions) }
            itemsIndexed(detail.books, key = { _, book -> book.id }) { index, book ->
                val dragged = draggedId == book.id
                Box(
                    Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                        .onSizeChanged { rowHeights[book.id] = it.height },
                ) {
                    if (reordering) {
                        ReorderRow(
                            rank = index + 1,
                            book = book,
                            canMoveUp = index > 0,
                            canMoveDown = index < detail.books.lastIndex,
                            onMove = { to -> actions.onMove(index, to) },
                            onDragStart = {
                                draggedId = book.id
                                dragOffset = 0f
                            },
                            onDragBy = { dy -> dragOffset += dy },
                            onDragStop = { cancelled ->
                                val from = detail.books.indexOfFirst { it.id == book.id }
                                val heights = detail.books.map { rowHeights[it.id] ?: 0 }
                                val target = CollectionOrder.dropIndex(from, dragOffset, heights)
                                draggedId = null
                                dragOffset = 0f
                                if (!cancelled && from >= 0 && target != from) actions.onMove(from, target)
                            },
                        )
                    } else {
                        CollectionBookRow(index + 1, book, actions)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressSection(summary: CollectionSummary, modifier: Modifier) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.common_percent, summary.percent), style = typography.display, color = colors.text, modifier = Modifier.alignByBaseline())
            Text(stringResource(R.string.collection_percent_label), style = typography.body, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
        }
        VersoProgressBar(fraction = summary.fraction.toFloat(), current = true)
        val parts = buildList {
            if (summary.finished > 0) add(pluralStringResource(R.plurals.collection_finished, summary.finished, summary.finished))
            if (summary.inProgress > 0) add(stringResource(R.string.collection_in_progress, summary.inProgress))
            if (summary.toRead > 0) add(stringResource(R.string.collection_to_read, summary.toRead))
        }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(stringResource(R.string.collection_counts_separator)), style = typography.body, color = colors.text)
        }
        if (summary.remainingKnown) {
            Text(collectionRemainingText(summary.remainingMinutes), style = typography.caption, color = colors.textSecondary)
        }
    }
}

/** Carte « Reprendre » (4.02) : toute la carte, comme le bouton rond, ouvre le livre à sa position. */
@Composable
private fun ResumeBookCard(book: LibraryBook, onOpen: (Long) -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val description = stringResource(R.string.collection_resume_content_description, book.title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(VersoShapes.card)
            .background(colors.surface)
            .clickable(role = Role.Button) { onOpen(book.id) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(title = book.title, coverPath = book.coverPath, seed = book.colorSeed, size = CoverSize.CARD)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.collection_resume), style = typography.captionBold, color = colors.accent)
            Text(book.title, style = typography.bookTitleStrong, color = colors.text)
            Text(stringResource(R.string.common_percent_read, book.percent), style = typography.caption, color = colors.textSecondary)
        }
        Box(
            Modifier
                .size(VersoDimens.controlMin)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(role = Role.Button) { onOpen(book.id) }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(VersoIcons.ArrowRight, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(VersoDimens.iconSmall))
        }
    }
}

/**
 * « Dans l’ordre de lecture » et « Réordonner » (ou « Terminé ») : côte à côte, le titre passant sur deux lignes comme
 * sur la maquette ; le bouton passe sous le titre quand le texte est très agrandi.
 */
@Composable
private fun OrderHeader(reordering: Boolean, canReorder: Boolean, actions: CollectionActions) {
    val colors = VersoTheme.colors
    val title = @Composable { modifier: Modifier ->
        Text(
            text = stringResource(R.string.collection_order_title),
            style = VersoTheme.typography.screenTitle,
            color = colors.text,
            modifier = modifier.padding(vertical = 8.dp).semantics { heading() },
        )
    }
    val button = @Composable {
        if (canReorder) {
            Row(
                modifier = Modifier
                    .heightIn(min = VersoDimens.controlMin)
                    .clip(VersoShapes.pill)
                    .clickable(role = Role.Button) { if (reordering) actions.onReorderEnd() else actions.onReorderStart() }
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (reordering) VersoIcons.Check else VersoIcons.SortArrows,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(VersoDimens.iconSmall),
                )
                Text(
                    text = stringResource(if (reordering) R.string.collection_reorder_done else R.string.collection_reorder),
                    style = VersoTheme.typography.button,
                    color = colors.text,
                )
            }
        }
    }
    val modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 16.dp)
    if (LocalDensity.current.fontScale >= STACKED_CARD_FONT_SCALE) {
        Column(modifier) {
            title(Modifier)
            button()
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            title(Modifier.weight(1f))
            button()
        }
    }
}

@Composable
private fun RankText(rank: Int) {
    Text(
        text = rank.toString(),
        style = VersoTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
        color = VersoTheme.colors.textSecondary,
        textAlign = TextAlign.End,
        modifier = Modifier.width(40.dp).clearAndSetSemantics {},
    )
}

/** Ligne de la liste (4.02) : rang, couverture, titre et état ; toucher ouvre le livre ; ⋮ Détails ou Retirer. */
@Composable
private fun CollectionBookRow(rank: Int, book: LibraryBook, actions: CollectionActions) {
    val colors = VersoTheme.colors
    var menu by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RankText(rank)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button) { actions.onOpenReader(book.id) }
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(title = book.title, coverPath = book.coverPath, seed = book.colorSeed, size = CoverSize.ROW)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.title, style = VersoTheme.typography.rowTitle, color = colors.text)
                BookStatusLine(
                    status = book.status,
                    progression = book.progression.toFloat(),
                    percentText = stringResource(R.string.common_percent, book.percent),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Box {
            VersoIconButton(
                icon = VersoIcons.MoreVertical,
                contentDescription = stringResource(R.string.library_book_options, book.title),
                onClick = { menu = true },
                tint = colors.textSecondary,
            )
            BookOptionsMenu(expanded = menu, onDismissRequest = { menu = false }) {
                BookOptionsMenuItem(stringResource(R.string.library_menu_details), VersoIcons.Info, onClick = {
                    menu = false
                    actions.onOpenDetails(book.id)
                })
                BookOptionsMenuItem(stringResource(R.string.collection_book_menu_remove), VersoIcons.Minus, destructive = true, onClick = {
                    menu = false
                    actions.onRemove(book.id)
                })
            }
        }
    }
}

/**
 * Ligne en mode « Réordonner » : poignée à glisser (avec les actions TalkBack « Monter » et « Descendre »), rang,
 * couverture, titre, puis les boutons « Monter » et « Descendre ».
 */
@Composable
private fun ReorderRow(
    rank: Int,
    book: LibraryBook,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragStop: (cancelled: Boolean) -> Unit,
) {
    val colors = VersoTheme.colors
    // La poignée garde son détecteur pendant le geste : elle appelle toujours les derniers rappels.
    val start by rememberUpdatedState(onDragStart)
    val dragBy by rememberUpdatedState(onDragBy)
    val stop by rememberUpdatedState(onDragStop)
    val index = rank - 1
    val up = stringResource(R.string.collection_move_up, book.title)
    val down = stringResource(R.string.collection_move_down, book.title)
    val drag = stringResource(R.string.collection_drag, book.title)
    val buttons = @Composable {
        VersoIconButton(icon = VersoIcons.ChevronUp, contentDescription = up, onClick = { onMove(index - 1) }, enabled = canMoveUp)
        VersoIconButton(icon = VersoIcons.ChevronDown, contentDescription = down, onClick = { onMove(index + 1) }, enabled = canMoveDown)
    }
    // Texte très agrandi : « Monter » et « Descendre » passent sous le titre, qui garde la largeur (pas de mot coupé).
    val stacked = LocalDensity.current.fontScale >= STACKED_CARD_FONT_SCALE
    Column(Modifier.fillMaxWidth().background(colors.background).padding(end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start() },
                            onDrag = { change, amount ->
                                change.consume()
                                dragBy(amount.y)
                            },
                            onDragEnd = { stop(false) },
                            onDragCancel = { stop(true) },
                        )
                    }
                    .size(VersoDimens.controlMin)
                    .semantics {
                        contentDescription = drag
                        customActions = buildList {
                            if (canMoveUp) add(CustomAccessibilityAction(up) { onMove(index - 1); true })
                            if (canMoveDown) add(CustomAccessibilityAction(down) { onMove(index + 1); true })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(VersoIcons.GripVertical, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(VersoDimens.iconSmall))
            }
            Text(
                text = rank.toString(),
                style = VersoTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
                color = colors.textSecondary,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Row(
                Modifier.weight(1f).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(title = book.title, coverPath = book.coverPath, seed = book.colorSeed, size = CoverSize.ROW)
                Text(book.title, style = VersoTheme.typography.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
            }
            if (!stacked) buttons()
        }
        if (stacked) Row(Modifier.align(Alignment.End)) { buttons() }
    }
}
