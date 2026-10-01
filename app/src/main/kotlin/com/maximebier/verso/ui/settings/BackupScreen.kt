package com.maximebier.verso.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.R
import com.maximebier.verso.backup.BackupService
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.ui.common.formatDate
import com.maximebier.verso.ui.components.DangerButton
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.components.OutlinedPillButton
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSnackbarHost
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.details.SizeParts
import com.maximebier.verso.ui.details.sizeParts
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

data class BackupActions(
    val onBack: () -> Unit,
    val onCreate: () -> Unit,
    val onRestore: () -> Unit,
    val onRestoreConfirm: () -> Unit,
    val onRestoreDismiss: () -> Unit,
)

/** Point d’entrée de BackupRoute : sélecteurs Android, messages, arrivée sur la bibliothèque après restauration. */
@Composable
fun BackupDestination(onBack: () -> Unit, onRestored: () -> Unit) {
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupService.MIME_TYPE)) { uri ->
        viewModel.onCreateDocument(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onRestoreDocument(uri)
    }
    val message = state.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(message = messageText, duration = SnackbarDuration.Long)
            viewModel.onMessageShown(message)
        }
    }
    LaunchedEffect(state.restored) {
        if (state.restored) {
            viewModel.onRestoredHandled()
            onRestored()
        }
    }
    // Création et restauration vont jusqu’au bout : le retour attend la fin.
    BackHandler(enabled = state.busy != null) {}
    BackupScreen(
        state = state,
        actions = BackupActions(
            onBack = { if (state.busy == null) onBack() },
            onCreate = { createLauncher.launch(viewModel.suggestedFileName()) },
            onRestore = { restoreLauncher.launch(BackupService.RESTORE_MIME_TYPES) },
            onRestoreConfirm = viewModel::onRestoreConfirm,
            onRestoreDismiss = viewModel::onRestoreDismiss,
        ),
        snackbarHostState = snackbarHostState,
    )
}

/** Écran « Sauvegarde » (3.07), sans état propre. */
@Composable
fun BackupScreen(
    state: BackupUiState,
    actions: BackupActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val idle = state.busy == null
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            DetailTopBar(title = stringResource(R.string.backup_title), onBack = actions.onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(text = stringResource(R.string.backup_intro), style = typography.body, color = colors.text)
                LastBackupCard(state.lastBackup)
                PrimaryButton(
                    text = stringResource(R.string.backup_create),
                    onClick = actions.onCreate,
                    icon = VersoIcons.Download,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedPillButton(
                    text = stringResource(R.string.backup_restore),
                    onClick = actions.onRestore,
                    icon = VersoIcons.Upload,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.busy?.let { busy ->
                    Text(
                        text = stringResource(busy.textRes),
                        style = typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = VersoIcons.AlertTriangle,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp).size(VersoDimens.iconSmall),
                    )
                    Text(
                        text = stringResource(R.string.backup_restore_warning),
                        style = typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        VersoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    state.confirmation?.let { RestoreDialog(it, actions) }
}

/** Carte « Dernière sauvegarde » : lue d’un bloc par TalkBack, annoncée quand elle change. */
@Composable
private fun LastBackupCard(last: LastBackup?) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    Column(
        Modifier
            .fillMaxWidth()
            .clip(VersoShapes.card)
            .background(colors.surface)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.backup_last_title), style = typography.caption, color = colors.textSecondary)
        if (last == null) {
            Text(text = stringResource(R.string.backup_none), style = typography.body, color = colors.text)
        } else {
            Text(
                text = formatDate(last.at, stringResource(R.string.backup_last_date_pattern)),
                style = typography.bookTitleStrong,
                color = colors.text,
            )
            Text(
                text = stringResource(R.string.backup_last_details, backupSizeText(last.sizeBytes), breakableFileName(last.fileName)),
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun RestoreDialog(confirmation: RestoreConfirmation, actions: BackupActions) {
    val current = pluralStringResource(R.plurals.library_book_count, confirmation.currentBooks, confirmation.currentBooks)
    val backup = pluralStringResource(R.plurals.library_book_count, confirmation.backupBooks, confirmation.backupBooks)
    VersoDialog(
        title = stringResource(R.string.backup_restore_dialog_title),
        onDismissRequest = actions.onRestoreDismiss,
        buttons = {
            VersoTextButton(text = stringResource(R.string.backup_restore_dialog_cancel), onClick = actions.onRestoreDismiss)
            DangerButton(text = stringResource(R.string.backup_restore_dialog_confirm), onClick = actions.onRestoreConfirm)
        },
    ) {
        // VersoDialog fournit la couleur (textSecondary) et le style (body) du texte.
        Text(text = stringResource(R.string.backup_restore_dialog_body, current, backup))
    }
}

private const val ZERO_WIDTH_SPACE = "​"

/**
 * Nom de document coupable après chaque « - » et avant le point (espace sans chasse, muet pour TalkBack) : à 200 %,
 * « verso-sauvegarde-2026-09-20.zip » passe à la ligne sans couper un mot (aucune coupure n’est permise avant un
 * chiffre qui suit un tiret).
 */
internal fun breakableFileName(name: String): String =
    name.replace("-", "-$ZERO_WIDTH_SPACE").replace(".", "$ZERO_WIDTH_SPACE.")

private const val BYTES_PER_MB = 1_000_000L

/** Au-delà, la taille est un nombre entier de Mo. */
private const val WHOLE_MEGABYTES_FROM = 10_000_000L

/** « 48 Mo » à partir de 10 Mo (maquette 3.07) ; en dessous, comme la fiche (« 1,2 Mo », « 850 ko »). */
fun backupSizeParts(bytes: Long): SizeParts =
    if (bytes >= WHOLE_MEGABYTES_FROM) {
        SizeParts(megabytes = true, value = ((bytes + BYTES_PER_MB / 2) / BYTES_PER_MB).toString())
    } else {
        sizeParts(bytes)
    }

@Composable
private fun backupSizeText(bytes: Long): String {
    val parts = backupSizeParts(bytes)
    return stringResource(if (parts.megabytes) R.string.details_size_mb else R.string.details_size_kb, parts.value)
}

@get:StringRes
private val BackupBusy.textRes: Int
    get() = when (this) {
        BackupBusy.CREATING -> R.string.backup_creating
        BackupBusy.CHECKING -> R.string.backup_checking
        BackupBusy.RESTORING -> R.string.backup_restoring
    }

@get:StringRes
private val BackupMessage.textRes: Int
    get() = when (this) {
        BackupMessage.CREATED -> R.string.backup_created
        BackupMessage.CREATE_FAILED -> R.string.backup_create_failed
        BackupMessage.RESTORE_INVALID -> R.string.backup_restore_invalid
        BackupMessage.RESTORE_NEWER -> R.string.backup_restore_newer
        BackupMessage.RESTORE_FAILED -> R.string.backup_restore_failed
    }
