package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.ui.settings.BackupActions
import com.maximebier.verso.ui.settings.BackupBusy
import com.maximebier.verso.ui.settings.BackupScreen
import com.maximebier.verso.ui.settings.BackupUiState
import com.maximebier.verso.ui.settings.RestoreConfirmation
import java.time.LocalDateTime
import java.time.ZoneId

/** « 20 septembre 2026, 21 h 14 » dans le fuseau de la machine qui capture. */
private val SampleBackupAt: Long = LocalDateTime.of(2026, 9, 20, 21, 14).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Sauvegarde : carte de la dernière sauvegarde (48 Mo · verso-sauvegarde-2026-09-20.zip). */
internal val BackupSampleState = BackupUiState(lastBackup = LastBackup(SampleBackupAt, 48_000_000L, "verso-sauvegarde-2026-09-20.zip"))

@Composable
internal fun BackupSample(state: BackupUiState = BackupSampleState) {
    BackupScreen(state = state, actions = BackupActions(onBack = {}, onCreate = {}, onRestore = {}, onRestoreConfirm = {}, onRestoreDismiss = {}))
}

@Composable
internal fun BackupEmptySample() = BackupSample(BackupUiState())

@Composable
internal fun BackupBusySample() = BackupSample(BackupSampleState.copy(busy = BackupBusy.CREATING))

@Composable
internal fun BackupConfirmationSample() = BackupSample(BackupSampleState.copy(confirmation = RestoreConfirmation(currentBooks = 1, backupBooks = 12)))
