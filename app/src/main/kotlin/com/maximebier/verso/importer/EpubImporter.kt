package com.maximebier.verso.importer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.readium.OpenFailureException
import com.maximebier.verso.readium.ReadiumOpener
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Importe un EPUB depuis une URI (SAF, « Ouvrir avec », « Partager vers ») :
 * copie dans cacheDir/import, empreinte, doublon, inspection Readium, puis
 * filesDir/books/<sha>.epub + filesDir/covers/<sha>.png + ligne Room.
 * Aucun fichier ne reste en cas d'échec.
 */
class EpubImporter(
    private val context: Context,
    private val books: BookRepository,
    private val opener: ReadiumOpener,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Sérialise recherche par empreinte → déplacement → insertion : jamais deux lignes pour un même fichier. */
    private val mutex = Mutex()

    private val appContext: Context get() = context.applicationContext
    private val importDir: File get() = File(appContext.cacheDir, IMPORT_DIR)
    private val booksDir: File get() = File(appContext.filesDir, BOOKS_DIR)
    private val coversDir: File get() = File(appContext.filesDir, COVERS_DIR)

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        purgeStaleTemps()
        val copied = copyToTemp(uri) ?: return@withContext ImportResult.Rejected(RejectReason.UNREADABLE)
        val originalName = displayName(uri)
        mutex.withLock { importTemp(copied.file, copied.sha256, originalName) }
    }

    suspend fun replace(duplicate: ImportResult.Duplicate): ImportResult.Added = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pending = duplicate.pending
            try {
                val existing = books.book(duplicate.existing.id)
                if (existing == null) {
                    // Le livre a été supprimé entre-temps : import normal du fichier en attente.
                    val result = importTemp(pending.tempFile, pending.sha256, pending.originalFileName)
                    return@withLock result as? ImportResult.Added
                        ?: throw IOException("Remplacement impossible : $result")
                }
                val bookFile = File(booksDir, "${pending.sha256}$BOOK_EXTENSION")
                val coverFile = File(coversDir, "${pending.sha256}$COVER_EXTENSION")
                val info = opener.inspect(pending.tempFile).getOrNull()
                moveInto(pending.tempFile, bookFile)
                val coverPath = info?.cover?.let { writeCover(it, coverFile) } ?: existing.coverPath
                books.replaceFile(
                    id = existing.id,
                    filePath = bookFile.absolutePath,
                    coverPath = coverPath,
                    sizeBytes = pending.sizeBytes,
                    originalFileName = pending.originalFileName,
                    totalWords = info?.totalWords ?: existing.totalWords,
                )
                if (existing.filePath != bookFile.absolutePath) File(existing.filePath).delete()
                ImportResult.Added(existing.id, existing.title)
            } finally {
                pending.tempFile.delete()
            }
        }
    }

    suspend fun ignore(duplicate: ImportResult.Duplicate) {
        withContext(Dispatchers.IO) { duplicate.pending.tempFile.delete() }
    }

    /** Nom du fichier d'origine : OpenableColumns.DISPLAY_NAME, sinon dernier segment de l'URI. */
    fun displayName(uri: Uri): String {
        val queried = try {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        return queried?.trim()?.takeIf { it.isNotEmpty() }
            ?: uri.lastPathSegment?.let { File(it).name }?.trim()?.takeIf { it.isNotEmpty() }
            ?: uri.toString()
    }

    private class CopiedFile(val file: File, val sha256: String)

    private fun copyToTemp(uri: Uri): CopiedFile? {
        importDir.mkdirs()
        val temp = File.createTempFile(TEMP_PREFIX, TEMP_SUFFIX, importDir)
        return try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Flux indisponible : $uri")
            val sha256 = input.use { source -> temp.outputStream().use { target -> Sha256.copyAndHash(source, target) } }
            CopiedFile(temp, sha256)
        } catch (e: Exception) {
            temp.delete()
            null
        }
    }

    private suspend fun importTemp(temp: File, sha256: String, originalName: String): ImportResult {
        val bookFile = File(booksDir, "$sha256$BOOK_EXTENSION")
        val coverFile = File(coversDir, "$sha256$COVER_EXTENSION")
        var moved = false
        return try {
            val existing = books.findBySha256(sha256)
            if (existing != null) {
                return ImportResult.Duplicate(existing, PendingImport(temp, sha256, originalName, temp.length()))
            }
            val info = opener.inspect(temp).getOrElse { error ->
                temp.delete()
                return ImportResult.Rejected(rejectReasonOf(error))
            }
            val sizeBytes = temp.length()
            moveInto(temp, bookFile)
            moved = true
            val coverPath = info.cover?.let { writeCover(it, coverFile) }
            val title = info.title ?: defaultTitle(originalName)
            val id = books.insert(
                BookEntity(
                    title = title,
                    author = info.author ?: "",
                    filePath = bookFile.absolutePath,
                    sha256 = sha256,
                    coverPath = coverPath,
                    sizeBytes = sizeBytes,
                    originalFileName = originalName,
                    importedAt = clock(),
                    lastOpenedAt = null,
                    readingLocatorJson = null,
                    progression = 0.0,
                    totalWords = info.totalWords,
                ),
            )
            ImportResult.Added(id, title)
        } catch (e: CancellationException) {
            discard(temp, bookFile.takeIf { moved }, coverFile.takeIf { moved })
            throw e
        } catch (e: Exception) {
            discard(temp, bookFile.takeIf { moved }, coverFile.takeIf { moved })
            ImportResult.Rejected(RejectReason.UNREADABLE)
        }
    }

    private fun discard(vararg files: File?) {
        files.forEach { it?.delete() }
    }

    private fun rejectReasonOf(error: Throwable): RejectReason =
        when ((error as? OpenFailureException)?.failure) {
            ReadiumOpener.Failure.NotEpub -> RejectReason.NOT_EPUB
            ReadiumOpener.Failure.Drm -> RejectReason.DRM
            else -> RejectReason.UNREADABLE
        }

    private fun defaultTitle(originalName: String): String =
        originalName.substringBeforeLast('.').trim().ifEmpty { originalName }

    private fun moveInto(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) throw IOException("Impossible de remplacer ${target.name}")
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    /** PNG de la couverture ; null (vignette générée) si l'écriture échoue. */
    private fun writeCover(cover: Bitmap, target: File): String? =
        try {
            target.parentFile?.mkdirs()
            val written = target.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            if (written) target.absolutePath else null.also { target.delete() }
        } catch (e: IOException) {
            target.delete()
            null
        }

    private fun purgeStaleTemps() {
        val limit = System.currentTimeMillis() - STALE_TEMP_MS
        importDir.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < limit }
            .forEach { it.delete() }
    }

    private companion object {
        const val IMPORT_DIR = "import"
        const val BOOKS_DIR = "books"
        const val COVERS_DIR = "covers"
        const val TEMP_PREFIX = "import-"
        const val TEMP_SUFFIX = ".tmp"
        const val BOOK_EXTENSION = ".epub"
        const val COVER_EXTENSION = ".png"
        const val PNG_QUALITY = 100
        const val STALE_TEMP_MS = 24L * 60 * 60 * 1000
    }
}
