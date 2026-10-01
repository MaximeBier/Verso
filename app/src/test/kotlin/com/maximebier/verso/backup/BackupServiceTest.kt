package com.maximebier.verso.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.DocumentStore
import com.maximebier.verso.data.LastBackup
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val paris = ZoneId.of("Europe/Paris")
    private val at = LocalDateTime.of(2026, 9, 20, 21, 14).atZone(paris).toInstant().toEpochMilli()

    private fun service(phone: BackupTestLibrary) = BackupService(
        writer = phone.writer(),
        restorer = phone.restorer(),
        documents = DocumentStore(context),
        settings = phone.settings,
        clock = { at },
        zone = { paris },
    )

    @Test
    fun suggestedNameCarriesTheLocalDate() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        assertThat(service(phone).suggestedFileName()).isEqualTo("verso-sauvegarde-2026-09-20.zip")
        phone.close()
    }

    @Test
    fun createWritesTheZipAndRemembersDateSizeAndName() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        phone.seed()
        val target = File(tmp.newFolder("drive"), "verso-sauvegarde-2026-09-20.zip")

        assertThat(service(phone).create(Uri.fromFile(target))).isTrue()

        assertThat(phone.settings.lastBackup.first()).isEqualTo(LastBackup(at, target.length(), "verso-sauvegarde-2026-09-20.zip"))
        ZipFile(target).use { assertThat(it.getEntry(BackupFormat.DATA_ENTRY)).isNotNull() }
        phone.close()
    }

    @Test
    fun failedCreationKeepsThePreviousCard() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val directory = tmp.newFolder("pas-un-fichier")

        assertThat(service(phone).create(Uri.fromFile(directory))).isFalse()

        assertThat(phone.settings.lastBackup.first()).isNull()
        phone.close()
    }

    @Test
    fun missingDocumentCannotBeRestored() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        assertThat(service(phone).prepareRestore(Uri.fromFile(File(tmp.root, "absent.zip")))).isEqualTo(RestorePreparation.Failed)
        phone.close()
    }

    @Test
    fun restoreGoesThroughPreparationThenConfirmation() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, _, _) = phone.seed()
        val target = File(tmp.newFolder("drive"), "verso-sauvegarde-2026-09-20.zip")
        val service = service(phone)
        service.create(Uri.fromFile(target))
        phone.books.delete(bovary.id)

        assertThat(service.prepareRestore(Uri.fromFile(target))).isEqualTo(RestorePreparation.Ready(currentBooks = 2, backupBooks = 3))
        assertThat(service.confirmRestore()).isEqualTo(RestoreResult.RESTORED)

        assertThat(phone.snapshot().books).hasSize(3)
        phone.close()
    }
}
