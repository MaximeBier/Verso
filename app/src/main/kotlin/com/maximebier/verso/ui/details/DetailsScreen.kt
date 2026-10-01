package com.maximebier.verso.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.R
import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.stats.ReadingStats
import com.maximebier.verso.ui.collections.AddToCollectionSheet
import com.maximebier.verso.ui.collections.STACKED_CARD_FONT_SCALE
import com.maximebier.verso.ui.common.DeleteBookDialog
import com.maximebier.verso.ui.common.durationText
import com.maximebier.verso.ui.common.formatDate
import com.maximebier.verso.ui.common.remainingTimeText
import com.maximebier.verso.ui.components.BookCover
import com.maximebier.verso.ui.components.CoverSize
import com.maximebier.verso.ui.components.DangerOutlinedButton
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.components.OutlinedPillButton
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSegmentedButton
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.theme.VersoTheme
import java.util.Locale

/** Taille affichée : `megabytes` = true → « 1,2 Mo », sinon « 850 ko ». */
data class SizeParts(val megabytes: Boolean, val value: String)

private const val BYTES_PER_KB = 1_000L
private const val BYTES_PER_MB = 1_000_000L

/** Moins de 1 Mo : ko entier arrondi (1 à 999) ; sinon Mo à une décimale, virgule française. */
fun sizeParts(bytes: Long): SizeParts =
    if (bytes >= BYTES_PER_MB) {
        SizeParts(megabytes = true, value = String.format(Locale.FRENCH, "%.1f", bytes / BYTES_PER_MB.toDouble()))
    } else {
        SizeParts(megabytes = false, value = ((bytes + BYTES_PER_KB / 2) / BYTES_PER_KB).coerceIn(1, 999).toString())
    }

/**
 * Point d'entrée de DetailsRoute (signature figée par la tâche 2.2, appelé par VersoNavHost) :
 * la fiche reliée à son ViewModel.
 */
@Composable
fun DetailsDestination(
    bookId: Long,
    onBack: () -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenJournal: (Long) -> Unit,
    onOpenNotes: (Long) -> Unit = {},
    onNewCollection: (Long) -> Unit = {},
) {
    DetailsScreen(
        bookId = bookId,
        onBack = onBack,
        onOpenReader = onOpenReader,
        onOpenJournal = onOpenJournal,
        onOpenNotes = onOpenNotes,
        onNewCollection = onNewCollection,
    )
}

/** Retour à la bibliothèque après suppression ou si le livre n'existe plus. */
@Composable
fun DetailsScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenJournal: (Long) -> Unit = {},
    onOpenNotes: (Long) -> Unit = {},
    onNewCollection: (Long) -> Unit = {},
    viewModel: DetailsViewModel = viewModel(factory = DetailsViewModel.factory(bookId)),
) {
    val state by viewModel.state.collectAsState()
    val leave = state.deleted || state.missing
    LaunchedEffect(leave) { if (leave) onBack() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    DetailsContent(
        state = state,
        actions = DetailsActions(
            onBack = {
                viewModel.flush()
                onBack()
            },
            onOpenReader = { id ->
                viewModel.flush()
                onOpenReader(id)
            },
            onTitleChange = viewModel::onTitleChange,
            onAuthorChange = viewModel::onAuthorChange,
            onFieldFocusLost = viewModel::flush,
            onDeleteClick = viewModel::onDeleteClick,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onStatusChange = viewModel::onStatusChange,
            onOpenNotes = { id ->
                viewModel.flush()
                onOpenNotes(id)
            },
            onOpenJournal = { id ->
                viewModel.flush()
                onOpenJournal(id)
            },
        ),
        addToCollectionSheet = { bookId, onDismiss ->
            AddToCollectionSheet(
                bookId = bookId,
                onNewCollection = { id ->
                    viewModel.flush()
                    onNewCollection(id)
                },
                onDismiss = onDismiss,
            )
        },
    )
}

/** Intentions de la fiche ; toutes facultatives pour les tests. */
data class DetailsActions(
    val onBack: () -> Unit = {},
    val onOpenReader: (Long) -> Unit = {},
    val onTitleChange: (String) -> Unit = {},
    val onAuthorChange: (String) -> Unit = {},
    val onFieldFocusLost: () -> Unit = {},
    val onDeleteClick: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onStatusChange: (BookStatus) -> Unit = {},
    val onOpenJournal: (Long) -> Unit = {},
    val onOpenNotes: (Long) -> Unit = {},
)

/** Écrans 1.07 (fiche) et 1.08 (confirmation de suppression), sans ViewModel. */
@Composable
fun DetailsContent(
    state: DetailsUiState,
    actions: DetailsActions,
    modifier: Modifier = Modifier,
    addToCollectionSheet: @Composable (bookId: Long, onDismiss: () -> Unit) -> Unit = { _, _ -> },
) {
    val colors = VersoTheme.colors
    // Feuille 4.04 : gardée ouverte pendant « Nouvelle collection » (entrée de navigation sauvegardée).
    var collectionsSheet by rememberSaveable { mutableStateOf(false) }
    /** 16 sp, interligne 1,45 (pourcentage et temps restant sous la couverture). */
    val metaStyle = VersoTheme.typography.body.copy(lineHeight = 23.2.sp)
    var titleFocused by remember { mutableStateOf(false) }
    var authorFocused by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { DetailTopBar(title = stringResource(R.string.details_title), onBack = actions.onBack) },
    ) { padding ->
        if (state.loaded && !state.missing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                    BookCover(
                        title = state.savedTitle,
                        coverPath = state.coverPath,
                        seed = state.colorSeed,
                        size = CoverSize.DETAILS,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(stringResource(R.string.common_percent_read, state.percent), style = metaStyle, color = colors.textSecondary)
                            Text(remainingTimeText(state.remainingMinutes), style = metaStyle, color = colors.textSecondary)
                        }
                        PrimaryButton(
                            text = stringResource(if (state.hasStarted) R.string.common_resume else R.string.common_start),
                            onClick = { actions.onOpenReader(state.bookId) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = VersoIcons.BookOpen,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.details_state_label),
                        style = VersoTheme.typography.captionBold,
                        color = colors.textSecondary,
                    )
                    val statuses = listOf(BookStatus.TO_READ, BookStatus.IN_PROGRESS, BookStatus.FINISHED)
                    VersoSegmentedButton(
                        options = listOf(
                            stringResource(R.string.details_state_to_read),
                            stringResource(R.string.details_state_in_progress),
                            stringResource(R.string.details_state_finished),
                        ),
                        selectedIndex = statuses.indexOf(state.status),
                        onSelect = { index -> actions.onStatusChange(statuses[index]) },
                        groupLabel = stringResource(R.string.details_state_group),
                    )
                }
                CollectionsRow(names = state.collectionNames, onEdit = { collectionsSheet = true })
                DetailsTextField(
                    value = state.titleField,
                    onValueChange = actions.onTitleChange,
                    label = stringResource(R.string.details_field_title),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                        if (titleFocused && !focus.hasFocus) actions.onFieldFocusLost()
                        titleFocused = focus.hasFocus
                    },
                )
                DetailsTextField(
                    value = state.authorField,
                    onValueChange = actions.onAuthorChange,
                    label = stringResource(R.string.details_field_author),
                    supportingText = stringResource(R.string.details_autosave_hint),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                        if (authorFocused && !focus.hasFocus) actions.onFieldFocusLost()
                        authorFocused = focus.hasFocus
                    },
                )
                state.stats?.takeIf { it.sessionCount > 0 }?.let { stats ->
                    StatisticsSection(
                        stats = stats,
                        remainingMinutes = state.remainingMinutes,
                        onOpenJournal = { actions.onOpenJournal(state.bookId) },
                    )
                }
                // Sous « Voir le journal de lecture » (spec V3) ; seulement si le livre a des surlignages.
                if (state.highlightCount > 0) {
                    OutlinedPillButton(
                        text = stringResource(R.string.details_open_notes, state.highlightCount),
                        onClick = { actions.onOpenNotes(state.bookId) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = VersoIcons.Note,
                    )
                }
                Column {
                    MetadataRow(
                        label = stringResource(R.string.details_imported_on),
                        value = formatDate(state.importedAt, stringResource(R.string.common_date_pattern_full)),
                    )
                    MetadataRow(label = stringResource(R.string.details_size), value = sizeText(state.sizeBytes))
                    MetadataRow(label = stringResource(R.string.details_format), value = stringResource(R.string.details_format_epub))
                    MetadataRow(label = stringResource(R.string.details_original_file), value = state.originalFileName)
                }
                DangerOutlinedButton(
                    text = stringResource(R.string.details_delete_book),
                    onClick = actions.onDeleteClick,
                    modifier = Modifier.fillMaxWidth(),
                    icon = VersoIcons.Trash,
                )
            }
        }
    }

    if (state.showDeleteDialog) {
        DeleteBookDialog(title = state.savedTitle, onConfirm = actions.onDeleteConfirm, onDismiss = actions.onDeleteDismiss)
    }
    if (collectionsSheet) addToCollectionSheet(state.bookId) { collectionsSheet = false }
}

@Composable
private fun sizeText(bytes: Long): String {
    val parts = sizeParts(bytes)
    return if (parts.megabytes) {
        stringResource(R.string.details_size_mb, parts.value)
    } else {
        stringResource(R.string.details_size_kb, parts.value)
    }
}

/** Arrondi à la minute, au moins 1 dès qu'il y a eu de la lecture. */
private fun activeMinutes(activeMs: Long): Int = ((activeMs + 30_000) / 60_000).toInt().coerceAtLeast(1)

/** Section « Statistiques » (2.08) : temps de lecture, vitesse moyenne, temps restant à votre rythme, journal. */
@Composable
private fun StatisticsSection(stats: ReadingStats, remainingMinutes: Int, onOpenJournal: () -> Unit) {
    val colors = VersoTheme.colors
    Column {
        Text(
            text = stringResource(R.string.details_stats_title),
            style = VersoTheme.typography.captionBold,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        MetadataRow(
            label = stringResource(R.string.details_stats_reading_time_label),
            value = pluralStringResource(
                R.plurals.details_stats_reading_time,
                stats.sessionCount,
                durationText(activeMinutes(stats.totalActiveMs)),
                stats.sessionCount,
            ),
        )
        stats.wordsPerMinute?.let { speed ->
            MetadataRow(
                label = stringResource(R.string.details_stats_speed_label),
                value = pluralStringResource(R.plurals.details_stats_speed, speed, speed),
            )
            if (remainingMinutes >= 1) {
                MetadataRow(
                    label = stringResource(R.string.details_stats_remaining_label),
                    value = stringResource(R.string.details_stats_remaining, durationText(remainingMinutes)),
                )
            }
        }
        OutlinedPillButton(
            text = stringResource(R.string.details_open_journal),
            onClick = onOpenJournal,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            icon = VersoIcons.History,
        )
    }
}

/** Ligne « Collections » (4.04) : noms séparés par des virgules ou « Aucune », et « Modifier », qui ouvre la feuille. */
@Composable
private fun CollectionsRow(names: List<String>, onEdit: () -> Unit) {
    val colors = VersoTheme.colors
    val editDescription = stringResource(R.string.details_collections_edit_content_description)
    val texts = @Composable { modifier: Modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.details_collections_label), style = VersoTheme.typography.captionBold, color = colors.textSecondary)
            Text(
                text = if (names.isEmpty()) stringResource(R.string.details_collections_none) else names.joinToString(stringResource(R.string.details_collections_separator)),
                style = VersoTheme.typography.rowTitle,
                color = colors.text,
            )
        }
    }
    val edit = @Composable {
        VersoTextButton(
            text = stringResource(R.string.details_collections_edit),
            onClick = onEdit,
            modifier = Modifier.semantics { contentDescription = editDescription },
        )
    }
    // Texte très agrandi : « Modifier » passe sous les noms, qui gardent toute la largeur.
    if (LocalDensity.current.fontScale >= STACKED_CARD_FONT_SCALE) {
        Column {
            texts(Modifier.fillMaxWidth())
            edit()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            texts(Modifier.weight(1f))
            edit()
        }
    }
}
