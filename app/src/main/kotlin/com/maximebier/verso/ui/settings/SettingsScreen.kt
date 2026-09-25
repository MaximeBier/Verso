package com.maximebier.verso.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.BuildConfig
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.ActionRow
import com.maximebier.verso.ui.components.DangerButton
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.components.SettingsParagraph
import com.maximebier.verso.ui.components.SettingsSection
import com.maximebier.verso.ui.components.SwitchRow
import com.maximebier.verso.ui.components.ValueRow
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Point d'entrée de SettingsRoute (signature figée par 2.2, appelé par VersoNavHost) :
 * relie le ViewModel, la version de BuildConfig et l'ouverture du lien.
 */
@Composable
fun SettingsDestination(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.settings_source_code_url)
    SettingsScreen(
        state = state,
        versionName = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onReopenLastBookChange = viewModel::onReopenLastBookChange,
        onClearJournalClick = viewModel::onClearJournalClick,
        onClearJournalConfirm = viewModel::onClearJournalConfirm,
        onClearJournalDismiss = viewModel::onClearJournalDismiss,
        onOpenSourceCode = { openUrl(context, sourceUrl) },
        onOpenLicenses = onOpenLicenses,
    )
}

/**
 * Ouvre le lien dans le navigateur (ACTION_VIEW). Verso n'a pas la permission INTERNET :
 * c'est le navigateur qui charge la page. Sans navigateur installé, il ne se passe rien.
 */
internal fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Aucun navigateur : rien à faire.
    }
}

/** Écran Paramètres (1.09), sans état propre. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    versionName: String,
    onBack: () -> Unit,
    onReopenLastBookChange: (Boolean) -> Unit,
    onClearJournalClick: () -> Unit,
    onClearJournalConfirm: () -> Unit,
    onClearJournalDismiss: () -> Unit,
    onOpenSourceCode: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    Column(modifier.fillMaxSize().background(colors.background)) {
        DetailTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_startup)) {
                SwitchRow(
                    title = stringResource(R.string.settings_reopen_last_book),
                    summary = stringResource(R.string.settings_reopen_last_book_summary),
                    checked = state.reopenLastBook,
                    onCheckedChange = onReopenLastBookChange,
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_display)) {
                SettingsParagraph(text = stringResource(R.string.settings_display_body))
            }
            SettingsSection(title = stringResource(R.string.settings_section_privacy)) {
                SettingsParagraph(text = stringResource(R.string.settings_privacy_body))
                ActionRow(
                    title = stringResource(R.string.settings_clear_journal),
                    summary = stringResource(R.string.settings_clear_journal_summary),
                    icon = VersoIcons.Trash,
                    onClick = onClearJournalClick,
                    iconTint = colors.danger,
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_about), showDivider = false) {
                ValueRow(title = stringResource(R.string.settings_version), value = versionName)
                ActionRow(
                    title = stringResource(R.string.settings_source_code),
                    summary = stringResource(R.string.settings_source_code_url_label),
                    icon = VersoIcons.ExternalLink,
                    onClick = onOpenSourceCode,
                )
                ActionRow(
                    title = stringResource(R.string.settings_licenses),
                    summary = null,
                    icon = VersoIcons.ChevronRight,
                    onClick = onOpenLicenses,
                )
            }
        }
    }

    if (state.confirmingClearJournal) {
        VersoDialog(
            title = stringResource(R.string.settings_clear_journal_dialog_title),
            onDismissRequest = onClearJournalDismiss,
            buttons = {
                VersoTextButton(text = stringResource(R.string.settings_clear_journal_dialog_cancel), onClick = onClearJournalDismiss)
                DangerButton(text = stringResource(R.string.settings_clear_journal_dialog_confirm), onClick = onClearJournalConfirm)
            },
        ) {
            // VersoDialog fournit la couleur (textSecondary) et le style (body) du texte.
            Text(text = stringResource(R.string.settings_clear_journal_dialog_body))
        }
    }
}
