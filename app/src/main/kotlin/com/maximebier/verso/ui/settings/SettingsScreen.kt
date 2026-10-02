package com.maximebier.verso.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.BuildConfig
import com.maximebier.verso.R
import com.maximebier.verso.data.DarkThemeVariant
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.components.ActionRow
import com.maximebier.verso.ui.components.AppInfoRow
import com.maximebier.verso.ui.components.DangerButton
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.components.SettingsParagraph
import com.maximebier.verso.ui.components.SettingsSection
import com.maximebier.verso.ui.components.SwitchRow
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Point d'entrée de SettingsRoute (signature figée par 2.2, appelé par VersoNavHost) :
 * relie le ViewModel, la version de BuildConfig et l'ouverture du lien.
 */
@Composable
fun SettingsDestination(onBack: () -> Unit, onOpenLicenses: () -> Unit, onOpenBackup: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.settings_source_code_url)
    SettingsScreen(
        state = state,
        versionName = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onReopenLastBookChange = viewModel::onReopenLastBookChange,
        onThemeModeChange = viewModel::onThemeModeChange,
        onDarkThemeVariantChange = viewModel::onDarkThemeVariantChange,
        onClearJournalClick = viewModel::onClearJournalClick,
        onClearJournalConfirm = viewModel::onClearJournalConfirm,
        onClearJournalDismiss = viewModel::onClearJournalDismiss,
        onOpenSourceCode = { openUrl(context, sourceUrl) },
        onOpenLicenses = onOpenLicenses,
        onFontChange = viewModel::onFontChange,
        onFontSizeChange = viewModel::onFontSizeChange,
        onDefaultScrollModeChange = viewModel::onDefaultScrollModeChange,
        onShowStatisticsChange = viewModel::onShowStatisticsChange,
        onOpenBackup = onOpenBackup,
    )
}

/**
 * Ouvre le lien dans le navigateur (ACTION_VIEW). Verso n'a pas la permission INTERNET :
 * c'est le navigateur qui charge la page. Sans navigateur installé, il ne se passe rien.
 */
internal fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
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
    onThemeModeChange: (ThemeMode) -> Unit,
    onDarkThemeVariantChange: (DarkThemeVariant) -> Unit,
    onClearJournalClick: () -> Unit,
    onClearJournalConfirm: () -> Unit,
    onClearJournalDismiss: () -> Unit,
    onOpenSourceCode: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    onFontChange: (ReadingFont) -> Unit = {},
    onFontSizeChange: (Int) -> Unit = {},
    onDefaultScrollModeChange: (ScrollMode) -> Unit = {},
    onShowStatisticsChange: (Boolean) -> Unit = {},
    onOpenBackup: () -> Unit = {},
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
            ReadingSettingsSection(
                reading = state.readingSettings,
                themeMode = state.themeMode,
                darkThemeVariant = state.darkThemeVariant,
                onFontChange = onFontChange,
                onThemeModeChange = onThemeModeChange,
                onDarkThemeVariantChange = onDarkThemeVariantChange,
                onFontSizeChange = onFontSizeChange,
                onDefaultScrollModeChange = onDefaultScrollModeChange,
            )
            SettingsSection(title = stringResource(R.string.settings_section_startup)) {
                SwitchRow(
                    title = stringResource(R.string.settings_reopen_last_book),
                    summary = stringResource(R.string.settings_reopen_last_book_summary),
                    checked = state.reopenLastBook,
                    onCheckedChange = onReopenLastBookChange,
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_statistics)) {
                SwitchRow(
                    title = stringResource(R.string.settings_show_statistics),
                    summary = stringResource(R.string.settings_show_statistics_summary),
                    checked = state.showStatistics,
                    onCheckedChange = onShowStatisticsChange,
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_privacy)) {
                SettingsParagraph(text = stringResource(R.string.settings_privacy_body))
                ActionRow(
                    title = stringResource(R.string.settings_backup),
                    summary = stringResource(R.string.settings_backup_summary),
                    icon = VersoIcons.ChevronRight,
                    onClick = onOpenBackup,
                )
                ActionRow(
                    title = stringResource(R.string.settings_clear_journal),
                    summary = stringResource(R.string.settings_clear_journal_summary),
                    icon = VersoIcons.Trash,
                    onClick = onClearJournalClick,
                    iconTint = colors.danger,
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_about), showDivider = false) {
                AppInfoRow(
                    title = stringResource(R.string.app_name),
                    summary = stringResource(R.string.settings_version, versionName),
                )
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