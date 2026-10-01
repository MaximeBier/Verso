package com.maximebier.verso.ui.settings

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.backup.Backups
import com.maximebier.verso.backup.RestorePreparation
import com.maximebier.verso.backup.RestoreResult
import com.maximebier.verso.data.LastBackup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {

    private class FakeBackups : Backups {
        override val lastBackup = MutableStateFlow<LastBackup?>(null)
        var created = CompletableDeferred<Boolean>()
        var preparation = CompletableDeferred<RestorePreparation>()
        var restored = CompletableDeferred<RestoreResult>()
        val createdUris = mutableListOf<Uri>()
        var cancelled = 0

        override fun suggestedFileName() = "verso-sauvegarde-2026-09-20.zip"
        override suspend fun create(uri: Uri): Boolean {
            createdUris += uri
            return created.await()
        }
        override suspend fun prepareRestore(uri: Uri) = preparation.await()
        override suspend fun confirmRestore() = restored.await()
        override suspend fun cancelRestore() {
            cancelled++
        }
    }

    private val uri = Uri.parse("content://documents/verso-sauvegarde-2026-09-20.zip")
    private val backups = FakeBackups()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(): BackupViewModel {
        val vm = BackupViewModel(backups)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun lastBackupComesFromTheService() = runTest {
        val vm = viewModel()
        backups.lastBackup.value = LastBackup(1L, 2L, "a.zip")
        assertThat(vm.state.value.lastBackup).isEqualTo(LastBackup(1L, 2L, "a.zip"))
        assertThat(vm.suggestedFileName()).isEqualTo("verso-sauvegarde-2026-09-20.zip")
    }

    @Test
    fun creationShowsProgressThenItsResult() = runTest {
        val vm = viewModel()

        vm.onCreateDocument(uri)
        assertThat(vm.state.value.busy).isEqualTo(BackupBusy.CREATING)
        vm.onCreateDocument(uri) // second toucher pendant la création : ignoré
        backups.created.complete(true)

        assertThat(backups.createdUris).containsExactly(uri)
        assertThat(vm.state.value.busy).isNull()
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.CREATED)
        vm.onMessageShown(BackupMessage.CREATED)
        assertThat(vm.state.value.message).isNull()
    }

    @Test
    fun failedCreationSaysSo() = runTest {
        val vm = viewModel()
        vm.onCreateDocument(uri)
        backups.created.complete(false)
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.CREATE_FAILED)
    }

    @Test
    fun closedPickerDoesNothing() = runTest {
        val vm = viewModel()
        vm.onCreateDocument(null)
        vm.onRestoreDocument(null)
        assertThat(vm.state.value).isEqualTo(BackupUiState())
        assertThat(backups.createdUris).isEmpty()
    }

    @Test
    fun restoreAsksConfirmationWithBothCountsThenGoesToTheLibrary() = runTest {
        val vm = viewModel()

        vm.onRestoreDocument(uri)
        assertThat(vm.state.value.busy).isEqualTo(BackupBusy.CHECKING)
        backups.preparation.complete(RestorePreparation.Ready(currentBooks = 1, backupBooks = 3))
        assertThat(vm.state.value.confirmation).isEqualTo(RestoreConfirmation(currentBooks = 1, backupBooks = 3))
        assertThat(vm.state.value.busy).isNull()

        vm.onRestoreConfirm()
        assertThat(vm.state.value.confirmation).isNull()
        assertThat(vm.state.value.busy).isEqualTo(BackupBusy.RESTORING)
        backups.restored.complete(RestoreResult.RESTORED)

        assertThat(vm.state.value.restored).isTrue()
        vm.onRestoredHandled()
        assertThat(vm.state.value.restored).isFalse()
    }

    @Test
    fun refusedPreparationsGiveTheirMessage() = runTest {
        val vm = viewModel()
        val cases = mapOf(
            RestorePreparation.Invalid to BackupMessage.RESTORE_INVALID,
            RestorePreparation.NewerFormat to BackupMessage.RESTORE_NEWER,
            RestorePreparation.Failed to BackupMessage.RESTORE_FAILED,
        )
        for ((preparation, message) in cases) {
            backups.preparation = CompletableDeferred(preparation)
            vm.onRestoreDocument(uri)
            assertThat(vm.state.value.message).isEqualTo(message)
            assertThat(vm.state.value.confirmation).isNull()
            vm.onMessageShown(message)
        }
    }

    @Test
    fun dismissingTheConfirmationForgetsTheBackup() = runTest {
        val vm = viewModel()
        backups.preparation.complete(RestorePreparation.Ready(1, 3))
        vm.onRestoreDocument(uri)

        vm.onRestoreDismiss()

        assertThat(vm.state.value.confirmation).isNull()
        assertThat(backups.cancelled).isEqualTo(1)
    }

    @Test
    fun failedRestoreSaysTheLibraryIsUnchanged() = runTest {
        val vm = viewModel()
        backups.preparation.complete(RestorePreparation.Ready(1, 3))
        vm.onRestoreDocument(uri)
        vm.onRestoreConfirm()
        backups.restored.complete(RestoreResult.FAILED)

        assertThat(vm.state.value.restored).isFalse()
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.RESTORE_FAILED)
    }
}
