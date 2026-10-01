package com.maximebier.verso.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.importer.Sha256
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestorerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun File.asideOf(): File = File(parentFile, "$name.avant-restauration")

    /** Sauvegarde de [phone] dans un fichier du dossier de test. */
    private suspend fun backupOf(phone: BackupTestLibrary, name: String = "sauvegarde.zip"): File {
        val zip = File(tmp.root, name)
        zip.outputStream().use { phone.writer().write(it) }
        return zip
    }

    private fun assertNoLeftovers(phone: BackupTestLibrary) {
        assertThat(phone.workDir.exists()).isFalse()
        assertThat(phone.booksDir.asideOf().exists()).isFalse()
        assertThat(phone.coversDir.asideOf().exists()).isFalse()
    }

    @Test
    fun restoreAfterDeletingTwoBooksBringsEverythingBack() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, candide, _) = phone.seed()
        val library = phone.snapshot()
        val files = phone.files()
        val settings = phone.settings.exportRaw()
        val zip = backupOf(phone)
        phone.settings.setLastBackup(LastBackup(at = 7L, sizeBytes = zip.length(), fileName = "verso-sauvegarde-2026-09-20.zip"))
        phone.books.delete(bovary.id)
        phone.books.delete(candide.id)
        phone.settings.setThemeMode(ThemeMode.LIGHT)
        val restorer = phone.restorer()

        assertThat(zip.inputStream().use { restorer.prepare(it) })
            .isEqualTo(RestorePreparation.Ready(currentBooks = 1, backupBooks = 3))
        assertThat(restorer.apply()).isEqualTo(RestoreResult.RESTORED)

        assertThat(phone.snapshot()).isEqualTo(library)
        assertThat(phone.files()).isEqualTo(files)
        assertThat(phone.settings.exportRaw()).isEqualTo(settings)
        assertThat(phone.settings.lastBackup.first()?.fileName).isEqualTo("verso-sauvegarde-2026-09-20.zip")
        assertNoLeftovers(phone)
        phone.close()
    }

    @Test
    fun restoreOnAnotherPhoneRewritesThePaths() = runTest {
        val source = BackupTestLibrary(tmp.root, backgroundScope, name = "ancien")
        source.seed()
        val zip = backupOf(source)
        val target = BackupTestLibrary(tmp.root, backgroundScope, name = "nouveau")
        target.addBook("Germinal")
        val restorer = target.restorer()

        assertThat(zip.inputStream().use { restorer.prepare(it) }).isEqualTo(RestorePreparation.Ready(currentBooks = 1, backupBooks = 3))
        assertThat(restorer.apply()).isEqualTo(RestoreResult.RESTORED)

        val restored = target.snapshot()
        val original = source.snapshot()
        assertThat(restored.books.map { it.filePath }).containsExactlyElementsIn(
            original.books.map { File(target.booksDir, "${it.sha256}.epub").absolutePath },
        )
        assertThat(restored.books.map { it.copy(filePath = "", coverPath = null) })
            .isEqualTo(original.books.map { it.copy(filePath = "", coverPath = null) })
        assertThat(restored.sessions).isEqualTo(original.sessions)
        assertThat(restored.highlights).isEqualTo(original.highlights)
        assertThat(target.files()).isEqualTo(source.files())
        assertThat(target.settings.exportRaw()).isEqualTo(source.settings.exportRaw())
        assertNoLeftovers(target)
        source.close()
        target.close()
    }

    @Test
    fun invalidFilesLeaveTheLibraryIntact() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (_, candide, _) = phone.seed()
        val zip = backupOf(phone)
        phone.books.delete(candide.id)
        val library = phone.snapshot()
        val files = phone.files()
        val settings = phone.settings.exportRaw()
        val bytes = zip.readBytes()
        val truncated = File(tmp.root, "tronque.zip").apply { writeBytes(bytes.copyOf(bytes.size / 2)) }
        val notABackup = zipOf(File(tmp.root, "livre.zip"), mapOf("mimetype" to "application/epub+zip".toByteArray(), "OEBPS/content.opf" to "<package/>".toByteArray()))
        val notAZip = File(tmp.root, "texte.zip").apply { writeText("pas un zip") }
        val missingEpub = File(tmp.root, "sans-candide.zip").also { rezip(zip, it) { name, data -> data.takeUnless { name == BackupFormat.bookEntry(candide.sha256) } } }
        val alteredEpub = File(tmp.root, "abime.zip").also {
            rezip(zip, it) { name, data -> if (name == BackupFormat.bookEntry(candide.sha256)) "abîmé".toByteArray() else data }
        }
        val badSettings = File(tmp.root, "reglages.zip").also {
            rezip(zip, it) { name, data -> if (name == BackupFormat.SETTINGS_ENTRY) """{"settings":[{"key":"x","type":"date","value":"hier"}]}""".toByteArray() else data }
        }
        val restorer = phone.restorer()

        for (file in listOf(truncated, notABackup, notAZip, missingEpub, alteredEpub, badSettings)) {
            assertThat(file.inputStream().use { restorer.prepare(it) }).isEqualTo(RestorePreparation.Invalid)
            assertThat(phone.workDir.exists()).isFalse()
        }
        assertThat(restorer.apply()).isEqualTo(RestoreResult.FAILED)

        assertThat(phone.snapshot()).isEqualTo(library)
        assertThat(phone.files()).isEqualTo(files)
        assertThat(phone.settings.exportRaw()).isEqualTo(settings)
        phone.close()
    }

    @Test
    fun newerFormatIsRefused() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val newer = zipOf(
            File(tmp.root, "v2.zip"),
            mapOf(
                BackupFormat.DATA_ENTRY to """{"format":${BackupFormat.VERSION + 1},"bibliotheque":{"nouveau":true}}""".toByteArray(),
                BackupFormat.SETTINGS_ENTRY to "{}".toByteArray(),
            ),
        )

        assertThat(newer.inputStream().use { phone.restorer().prepare(it) }).isEqualTo(RestorePreparation.NewerFormat)
        assertThat(phone.workDir.exists()).isFalse()
        phone.close()
    }

    @Test
    fun failureDuringReplacementPutsEverythingBack() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, candide, _) = phone.seed()
        val zip = backupOf(phone)
        phone.books.delete(bovary.id)
        phone.books.delete(candide.id)
        phone.settings.setThemeMode(ThemeMode.LIGHT)
        val library = phone.snapshot()
        val files = phone.files()
        val settings = phone.settings.exportRaw()

        for (step in RestoreStep.entries) {
            val restorer = phone.restorer(onStep = { if (it == step) throw IOException("Plus d’espace disque ($step)") })
            assertThat(zip.inputStream().use { restorer.prepare(it) }).isInstanceOf(RestorePreparation.Ready::class.java)

            assertThat(restorer.apply()).isEqualTo(RestoreResult.FAILED)

            assertThat(phone.snapshot()).isEqualTo(library)
            assertThat(phone.files()).isEqualTo(files)
            assertThat(phone.settings.exportRaw()).isEqualTo(settings)
            assertNoLeftovers(phone)
        }
        phone.close()
    }

    @Test
    fun unreferencedFilesAreNotRestored() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        phone.seed()
        val orphan = "orphelin".toByteArray()
        val orphanSha = Sha256.of(orphan.inputStream())
        val zip = File(tmp.root, "avec-orphelin.zip").also {
            rezip(backupOf(phone), it, extra = mapOf(BackupFormat.bookEntry(orphanSha) to orphan, "notes/lisez-moi.txt" to "x".toByteArray()))
        }
        val restorer = phone.restorer()

        assertThat(zip.inputStream().use { restorer.prepare(it) }).isInstanceOf(RestorePreparation.Ready::class.java)
        assertThat(restorer.apply()).isEqualTo(RestoreResult.RESTORED)

        assertThat(File(phone.booksDir, "$orphanSha.epub").exists()).isFalse()
        phone.close()
    }

    @Test
    fun discardForgetsThePreparedBackup() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        phone.seed()
        val zip = backupOf(phone)
        val restorer = phone.restorer()
        zip.inputStream().use { restorer.prepare(it) }
        assertThat(phone.workDir.exists()).isTrue()

        restorer.discard()

        assertThat(phone.workDir.exists()).isFalse()
        assertThat(restorer.apply()).isEqualTo(RestoreResult.FAILED)
        phone.close()
    }
}
