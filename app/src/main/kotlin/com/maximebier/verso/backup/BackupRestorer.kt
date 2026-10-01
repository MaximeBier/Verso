package com.maximebier.verso.backup

import android.util.Log
import com.maximebier.verso.core.backup.BackupCheck
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.RawSetting
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.BackupDao
import com.maximebier.verso.data.db.LibrarySnapshot
import com.maximebier.verso.importer.Sha256
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Étapes du remplacement, signalées à [BackupRestorer] `onStep` (tests : une exception y simule une panne). */
enum class RestoreStep { FILES_SWAPPED, DATABASE_REPLACED, SETTINGS_REPLACED }

sealed interface RestorePreparation {
    /** Sauvegarde lue et valide, prête à remplacer ; nombres de livres pour la confirmation. */
    data class Ready(val currentBooks: Int, val backupBooks: Int) : RestorePreparation
    data object NewerFormat : RestorePreparation
    /** Pas une sauvegarde Verso, incomplète ou abîmée. */
    data object Invalid : RestorePreparation
    /** Lecture du document ou écriture locale impossible (fichier inaccessible, espace disque). */
    data object Failed : RestorePreparation
}

enum class RestoreResult { RESTORED, FAILED }

/**
 * Restauration en deux temps. [prepare] décompresse tout dans [workDir] et valide tout (format, JSON, réglages,
 * empreinte de chaque EPUB, références) sans rien toucher. [apply], après confirmation, remplace dossiers, base et
 * réglages ; au moindre échec, chaque étape faite est défaite. Le dossier temporaire est toujours effacé à la fin.
 */
class BackupRestorer(
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val booksDir: File,
    private val coversDir: File,
    private val workDir: File,
    private val onStep: (RestoreStep) -> Unit = {},
) {
    private class Prepared(val library: LibrarySnapshot, val settings: List<RawSetting>)

    /** Fichier qui n’est pas une sauvegarde valide ; jamais une IOException (celles-ci veulent dire « échec local »). */
    private class InvalidBackupException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    private val mutex = Mutex()
    private var prepared: Prepared? = null

    suspend fun prepare(input: InputStream): RestorePreparation = mutex.withLock {
        withContext(Dispatchers.IO) {
            prepared = null
            workDir.deleteRecursively()
            val result = try {
                readAndCheck(input)
            } catch (e: InvalidBackupException) {
                Log.w(TAG, "Sauvegarde invalide : ${e.message}", e)
                RestorePreparation.Invalid
            } catch (e: IllegalArgumentException) { // JSON mal formé (SerializationException) ou réglage incompatible
                Log.w(TAG, "Sauvegarde invalide", e)
                RestorePreparation.Invalid
            } catch (e: IOException) {
                Log.w(TAG, "Lecture de la sauvegarde impossible", e)
                RestorePreparation.Failed
            }
            if (result !is RestorePreparation.Ready) workDir.deleteRecursively()
            result
        }
    }

    private suspend fun readAndCheck(input: InputStream): RestorePreparation {
        if (!workDir.mkdirs()) throw IOException("Dossier temporaire impossible à créer : $workDir")
        val entries = extract(input)
        val dataText = File(workDir, BackupFormat.DATA_ENTRY).takeIf { it.isFile }?.readText()
            ?: throw InvalidBackupException("${BackupFormat.DATA_ENTRY} absent")
        val header = BackupJson.decodeFromString(BackupHeaderDto.serializer(), dataText)
        if (BackupFormat.checkVersion(header.format) == BackupCheck.NewerFormat) return RestorePreparation.NewerFormat
        val data = BackupJson.decodeFromString(BackupDataDto.serializer(), dataText)
        val settingsText = File(workDir, BackupFormat.SETTINGS_ENTRY).takeIf { it.isFile }?.readText()
            ?: throw InvalidBackupException("${BackupFormat.SETTINGS_ENTRY} absent")
        val rawSettings = BackupJson.decodeFromString(SettingsFileDto.serializer(), settingsText).settings.map { it.toRawSetting() }
        when (val check = data.manifest(entries).check()) {
            BackupCheck.Valid -> Unit
            BackupCheck.NewerFormat -> return RestorePreparation.NewerFormat
            is BackupCheck.Invalid -> throw InvalidBackupException(check.reason)
        }
        removeUnreferenced(entries, data)
        val restoredCollections = data.collections.map { it.toEntities() }
        prepared = Prepared(
            library = LibrarySnapshot(
                books = data.books.map { it.toEntity(booksDir, coversDir) },
                sessions = data.sessions.map { it.toEntity() },
                highlights = data.highlights.map { it.toEntity() },
                collections = restoredCollections.map { it.first },
                collectionBooks = restoredCollections.flatMap { it.second },
            ),
            settings = rawSettings,
        )
        return RestorePreparation.Ready(currentBooks = dao.bookCount(), backupBooks = data.books.size)
    }

    /**
     * Décompresse les seules entrées connues ([BackupFormat.isKnownEntry]) dans [workDir] ; recalcule l’empreinte de
     * chaque EPUB. Erreur de lecture du zip → InvalidBackupException ; erreur d’écriture locale → IOException.
     */
    private fun extract(input: InputStream): Set<String> {
        val names = mutableSetOf<String>()
        val zip = ZipInputStream(BufferedInputStream(input, BUFFER_SIZE))
        val guarded = ZipReadGuard(zip)
        while (true) {
            val entry = guarded.nextEntry() ?: break
            val name = entry.name
            if (entry.isDirectory || !BackupFormat.isKnownEntry(name)) continue
            if (!names.add(name)) throw InvalidBackupException("Entrée en double : $name")
            val target = File(workDir, name)
            target.parentFile?.mkdirs()
            val expectedSha = BackupFormat.bookSha256(name)
            target.outputStream().use { out ->
                if (expectedSha != null) {
                    val actual = Sha256.copyAndHash(guarded, out)
                    if (actual != expectedSha) throw InvalidBackupException("EPUB abîmé : $name")
                } else {
                    guarded.copyTo(out, BUFFER_SIZE)
                }
            }
        }
        return names
    }

    /** EPUB et couvertures présents dans le zip mais d’aucun livre : effacés avant la mise en place. */
    private fun removeUnreferenced(entries: Set<String>, data: BackupDataDto) {
        val referenced = data.books.flatMap { book ->
            listOfNotNull(BackupFormat.bookEntry(book.sha256), book.coverFile?.let(BackupFormat::coverEntry))
        }.toSet()
        entries.filter { it != BackupFormat.DATA_ENTRY && it != BackupFormat.SETTINGS_ENTRY && it !in referenced }
            .forEach { File(workDir, it).delete() }
    }

    suspend fun apply(): RestoreResult = mutex.withLock {
        // Jamais interrompu à mi-chemin, même si l’écran qui l’a lancé disparaît.
        withContext(NonCancellable + Dispatchers.IO) {
            val ready = prepared ?: return@withContext RestoreResult.FAILED
            prepared = null
            val booksAside = asideOf(booksDir)
            val coversAside = asideOf(coversDir)
            var previousLibrary: LibrarySnapshot? = null
            var previousSettings: List<RawSetting>? = null
            var databaseReplaced = false
            var settingsReplaced = false
            val result = try {
                previousLibrary = dao.snapshot()
                previousSettings = settings.exportRaw()
                booksAside.deleteRecursively()
                coversAside.deleteRecursively()
                booksDir.mkdirs()
                coversDir.mkdirs()
                moveDir(booksDir, booksAside)
                moveDir(coversDir, coversAside)
                moveDir(File(workDir, BackupFormat.BOOKS_FOLDER), booksDir)
                moveDir(File(workDir, BackupFormat.COVERS_FOLDER), coversDir)
                onStep(RestoreStep.FILES_SWAPPED)
                dao.replaceAll(ready.library)
                databaseReplaced = true
                onStep(RestoreStep.DATABASE_REPLACED)
                settings.importRaw(ready.settings)
                settingsReplaced = true
                onStep(RestoreStep.SETTINGS_REPLACED)
                RestoreResult.RESTORED
            } catch (e: Exception) {
                Log.w(TAG, "Restauration interrompue : retour à l’état d’avant", e)
                if (settingsReplaced) previousSettings?.let { old -> undo("réglages") { settings.importRaw(old) } }
                if (databaseReplaced) previousLibrary?.let { old -> undo("base") { dao.replaceAll(old) } }
                undo("livres") { putBack(booksDir, booksAside) }
                undo("couvertures") { putBack(coversDir, coversAside) }
                RestoreResult.FAILED
            }
            if (result == RestoreResult.RESTORED) {
                booksAside.deleteRecursively()
                coversAside.deleteRecursively()
            }
            workDir.deleteRecursively()
            result
        }
    }

    /** Oublie la sauvegarde préparée (confirmation refusée). */
    suspend fun discard(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            prepared = null
            workDir.deleteRecursively()
        }
    }

    private fun asideOf(dir: File): File = File(dir.parentFile, dir.name + ASIDE_SUFFIX)

    /** Renommage (même volume), sinon copie complète puis effacement ; une copie ratée ne laisse pas de cible partielle. */
    private fun moveDir(source: File, target: File) {
        if (!source.exists()) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Dossier impossible à créer : $target")
            return
        }
        if (source.renameTo(target)) return
        try {
            source.copyRecursively(target, overwrite = false)
        } catch (e: Exception) {
            target.deleteRecursively()
            throw e
        }
        source.deleteRecursively()
    }

    /** Remet le dossier mis de côté, s’il l’a été. */
    private fun putBack(dir: File, aside: File) {
        if (!aside.exists()) return
        dir.deleteRecursively()
        moveDir(aside, dir)
    }

    private suspend fun undo(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Retour en arrière impossible ($what)", e)
        }
    }

    /** Lecture du zip : toute IOException veut dire « fichier illisible », jamais « disque local ». */
    private class ZipReadGuard(private val zip: ZipInputStream) : FilterInputStream(zip) {
        fun nextEntry() = guard { zip.nextEntry }

        override fun read(): Int = guard { zip.read() }

        override fun read(b: ByteArray, off: Int, len: Int): Int = guard { zip.read(b, off, len) }

        private inline fun <T> guard(block: () -> T): T = try {
            block()
        } catch (e: IOException) {
            throw InvalidBackupException("Zip illisible ou tronqué", e)
        }
    }

    private companion object {
        const val TAG = "BackupRestorer"
        const val BUFFER_SIZE = 64 * 1024
        const val ASIDE_SUFFIX = ".avant-restauration"
    }
}
