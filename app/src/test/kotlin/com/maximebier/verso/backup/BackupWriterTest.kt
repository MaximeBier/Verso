package com.maximebier.verso.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.LastBackup
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun zipHoldsDataSettingsBooksAndCovers() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, candide, silo) = phone.seed()
        phone.settings.setLastBackup(LastBackup(at = 1L, sizeBytes = 2L, fileName = "ancienne.zip"))
        val zipFile = File(tmp.root, "sauvegarde.zip")

        val written = zipFile.outputStream().use { phone.writer().write(it) }

        assertThat(written).isEqualTo(zipFile.length())
        ZipFile(zipFile).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertThat(names).containsExactly(
                BackupFormat.DATA_ENTRY,
                BackupFormat.SETTINGS_ENTRY,
                BackupFormat.bookEntry(bovary.sha256),
                BackupFormat.coverEntry("${bovary.sha256}.jpg"),
                BackupFormat.bookEntry(candide.sha256),
                BackupFormat.bookEntry(silo.sha256),
                BackupFormat.coverEntry("${silo.sha256}.jpg"),
            ).inOrder()

            val data = BackupJson.decodeFromString(
                BackupDataDto.serializer(),
                zip.getInputStream(zip.getEntry(BackupFormat.DATA_ENTRY)).use { it.readBytes().decodeToString() },
            )
            assertThat(data.format).isEqualTo(BackupFormat.VERSION)
            assertThat(data.createdAt).isEqualTo(BackupTestLibrary.NOW)
            assertThat(data.books.map { it.title }).containsExactly("Madame Bovary", "Candide", "Silo").inOrder()
            assertThat(data.books.map { it.coverFile }).containsExactly("${bovary.sha256}.jpg", null, "${silo.sha256}.jpg").inOrder()
            assertThat(data.sessions).hasSize(2)
            assertThat(data.highlights.map { it.note }).containsExactly("Le cœur du livre", null).inOrder()

            val settings = BackupJson.decodeFromString(
                SettingsFileDto.serializer(),
                zip.getInputStream(zip.getEntry(BackupFormat.SETTINGS_ENTRY)).use { it.readBytes().decodeToString() },
            ).settings
            assertThat(settings.map { it.key }).contains("theme_mode")
            assertThat(settings.map { it.key }.filter { it.startsWith("last_backup") }).isEmpty()

            val epub = zip.getInputStream(zip.getEntry(BackupFormat.bookEntry(candide.sha256))).use { it.readBytes() }
            assertThat(epub).isEqualTo(File(candide.filePath).readBytes())
        }
        phone.close()
    }

    @Test
    fun emptyLibraryGivesAValidZip() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val zipFile = File(tmp.root, "vide.zip")

        zipFile.outputStream().use { phone.writer().write(it) }

        ZipFile(zipFile).use { zip ->
            assertThat(zip.entries().toList().map { it.name }).containsExactly(BackupFormat.DATA_ENTRY, BackupFormat.SETTINGS_ENTRY)
        }
        phone.close()
    }

    @Test
    fun missingEpubMakesTheBackupFail() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val book = phone.addBook("Fantôme")
        File(book.filePath).delete()

        val failure = runCatching { File(tmp.root, "x.zip").outputStream().use { phone.writer().write(it) } }

        assertThat(failure.exceptionOrNull()).isInstanceOf(java.io.FileNotFoundException::class.java)
        phone.close()
    }
}
