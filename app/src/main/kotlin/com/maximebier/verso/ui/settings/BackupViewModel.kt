package com.maximebier.verso.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.backup.Backups
import com.maximebier.verso.backup.RestorePreparation
import com.maximebier.verso.backup.RestoreResult
import com.maximebier.verso.data.LastBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Opération en cours : boutons désactivés, texte de progression, retour bloqué. */
enum class BackupBusy { CREATING, CHECKING, RESTORING }

enum class BackupMessage { CREATED, CREATE_FAILED, RESTORE_INVALID, RESTORE_NEWER, RESTORE_FAILED }

data class RestoreConfirmation(val currentBooks: Int, val backupBooks: Int)

/** État de l’écran « Sauvegarde » (3.07). [restored] : aller à la bibliothèque restaurée. */
data class BackupUiState(
    val lastBackup: LastBackup? = null,
    val busy: BackupBusy? = null,
    val confirmation: RestoreConfirmation? = null,
    val message: BackupMessage? = null,
    val restored: Boolean = false,
)

class BackupViewModel(private val backups: Backups) : ViewModel() {

    private val transient = MutableStateFlow(BackupUiState())

    val state: StateFlow<BackupUiState> = combine(backups.lastBackup, transient) { last, current -> current.copy(lastBackup = last) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun suggestedFileName(): String = backups.suggestedFileName()

    /** Retour du sélecteur « Créer un document » ; null = sélecteur fermé sans choix. */
    fun onCreateDocument(uri: Uri?) {
        if (uri == null || !start(BackupBusy.CREATING)) return
        viewModelScope.launch {
            val created = backups.create(uri)
            transient.update { it.copy(busy = null, message = if (created) BackupMessage.CREATED else BackupMessage.CREATE_FAILED) }
        }
    }

    /** Retour du sélecteur « Ouvrir un document » : lecture et validation complètes, puis confirmation. */
    fun onRestoreDocument(uri: Uri?) {
        if (uri == null || !start(BackupBusy.CHECKING)) return
        viewModelScope.launch {
            val preparation = backups.prepareRestore(uri)
            transient.update {
                when (preparation) {
                    is RestorePreparation.Ready ->
                        it.copy(busy = null, confirmation = RestoreConfirmation(preparation.currentBooks, preparation.backupBooks))
                    RestorePreparation.NewerFormat -> it.copy(busy = null, message = BackupMessage.RESTORE_NEWER)
                    RestorePreparation.Invalid -> it.copy(busy = null, message = BackupMessage.RESTORE_INVALID)
                    RestorePreparation.Failed -> it.copy(busy = null, message = BackupMessage.RESTORE_FAILED)
                }
            }
        }
    }

    fun onRestoreConfirm() {
        if (transient.value.confirmation == null) return
        transient.update { it.copy(confirmation = null, busy = BackupBusy.RESTORING) }
        viewModelScope.launch {
            val result = backups.confirmRestore()
            transient.update {
                if (result == RestoreResult.RESTORED) it.copy(busy = null, restored = true)
                else it.copy(busy = null, message = BackupMessage.RESTORE_FAILED)
            }
        }
    }

    fun onRestoreDismiss() {
        if (transient.value.confirmation == null) return
        transient.update { it.copy(confirmation = null) }
        viewModelScope.launch { backups.cancelRestore() }
    }

    /** Efface ce message seulement : un nouveau, arrivé pendant l’affichage, reste à montrer. */
    fun onMessageShown(shown: BackupMessage) {
        transient.update { if (it.message == shown) it.copy(message = null) else it }
    }

    fun onRestoredHandled() {
        transient.update { it.copy(restored = false) }
    }

    /** Une opération à la fois (appelé sur le thread principal). */
    private fun start(busy: BackupBusy): Boolean {
        val current = transient.value
        if (current.busy != null || current.confirmation != null) return false
        transient.value = current.copy(busy = busy, message = null)
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication
                BackupViewModel(app.container.backups)
            }
        }
    }
}
