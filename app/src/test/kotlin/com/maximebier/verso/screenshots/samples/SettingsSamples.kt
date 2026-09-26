package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.ui.settings.LicensesScreen
import com.maximebier.verso.ui.settings.SettingsScreen
import com.maximebier.verso.ui.settings.SettingsUiState

@Composable
internal fun SettingsSample(state: SettingsUiState = SettingsUiState(reopenLastBook = true)) {
    SettingsScreen(
        state = state,
        versionName = "1.0.0",
        onBack = {},
        onReopenLastBookChange = {},
        onThemeModeChange = {},
        onClearJournalClick = {},
        onClearJournalConfirm = {},
        onClearJournalDismiss = {},
        onOpenSourceCode = {},
        onOpenLicenses = {},
    )
}

@Composable
internal fun SettingsClearJournalDialogSample() =
    SettingsSample(SettingsUiState(reopenLastBook = true, confirmingClearJournal = true))

@Composable
internal fun LicensesSample() = LicensesScreen(onBack = {})
