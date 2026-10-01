package com.maximebier.verso.backup

import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.BackupDao
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écrit la sauvegarde en flux : `donnees.json`, `reglages.json`, puis chaque EPUB et sa couverture, copiés par blocs
 * (jamais un livre entier en mémoire). EPUB et JPEG sont déjà compressés : ils sont stockés sans recompression.
 */
class BackupWriter(
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val appVersion: String,
    private val clock: () -> Long,
) {
    /** Renvoie le nombre d’octets écrits. Ne ferme pas [output]. FileNotFoundException si un EPUB a disparu. */
    suspend fun write(output: OutputStream): Long = withContext(Dispatchers.IO) {
        val library = dao.snapshot()
        val rawSettings = settings.exportRaw()
        val covers: Map<Long, File?> = library.books.associate { book ->
            book.id to book.coverPath?.let(::File)?.takeIf { it.isFile && BackupFormat.isCoverFileOf(book.sha256, it.name) }
        }
        val data = BackupDataDto(
            format = BackupFormat.VERSION,
            createdAt = clock(),
            appVersion = appVersion,
            books = library.books.map { it.toDto(coverFile = covers[it.id]?.name) },
            sessions = library.sessions.map { it.toDto() },
            highlights = library.highlights.map { it.toDto() },
        )
        val counting = CountingOutputStream(output)
        val zip = ZipOutputStream(BufferedOutputStream(counting, BUFFER_SIZE))
        zip.putText(BackupFormat.DATA_ENTRY, BackupJson.encodeToString(BackupDataDto.serializer(), data))
        zip.putText(
            BackupFormat.SETTINGS_ENTRY,
            BackupJson.encodeToString(SettingsFileDto.serializer(), SettingsFileDto(rawSettings.map { it.toDto() })),
        )
        zip.setLevel(Deflater.NO_COMPRESSION)
        for (book in library.books) {
            val epub = File(book.filePath)
            if (!epub.isFile) throw FileNotFoundException("EPUB absent pour le livre ${book.id}")
            zip.putFile(BackupFormat.bookEntry(book.sha256), epub)
            covers[book.id]?.let { zip.putFile(BackupFormat.coverEntry(it.name), it) }
        }
        zip.finish()
        zip.flush()
        counting.count
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.encodeToByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this, BUFFER_SIZE) }
        closeEntry()
    }

    /** Compte les octets réellement passés au document choisi. */
    private class CountingOutputStream(private val target: OutputStream) : FilterOutputStream(target) {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
