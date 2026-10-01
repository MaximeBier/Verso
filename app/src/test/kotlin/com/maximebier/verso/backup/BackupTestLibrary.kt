package com.maximebier.verso.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.LibrarySnapshot
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import com.maximebier.verso.importer.Sha256
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope

/** Un téléphone de test : base en mémoire, dossiers books/covers et DataStore sous [root]/[name]. */
class BackupTestLibrary(root: File, scope: CoroutineScope, name: String = "telephone") {
    val db: VersoDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val booksDir: File = File(root, "$name/files/books").apply { mkdirs() }
    val coversDir: File = File(root, "$name/files/covers").apply { mkdirs() }
    val workDir: File = File(root, "$name/cache/restauration")
    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(root, "$name/settings.preferences_pb") })
    val books = BookRepository(db.bookDao(), booksDir, coversDir)

    fun writer(): BackupWriter = BackupWriter(db.backupDao(), settings, appVersion = "3.0.0", clock = { NOW })

    fun restorer(onStep: (RestoreStep) -> Unit = {}): BackupRestorer =
        BackupRestorer(db.backupDao(), settings, booksDir, coversDir, workDir, onStep)

    suspend fun snapshot(): LibrarySnapshot = db.backupDao().snapshot()

    /** Fichiers des dossiers books et covers : « books/<nom> » → contenu. */
    fun files(): Map<String, List<Byte>> = (booksDir.listFiles().orEmpty().toList() + coversDir.listFiles().orEmpty().toList())
        .associate { "${it.parentFile!!.name}/${it.name}" to it.readBytes().toList() }

    /** Range un EPUB (et une couverture JPEG) comme l’import, puis lit et règle le livre comme après une lecture. */
    suspend fun addBook(title: String, withCover: Boolean = false): BookEntity {
        val bytes = "EPUB de $title".toByteArray()
        val sha = Sha256.of(bytes.inputStream())
        val epub = File(booksDir, "$sha.epub").apply { writeBytes(bytes) }
        val cover = if (withCover) File(coversDir, "$sha.jpg").apply { writeBytes("JPEG de $title".toByteArray()) } else null
        val id = books.insert(testBook(sha, title = title, filePath = epub.absolutePath, coverPath = cover?.absolutePath))
        books.saveReadingPosition(id, """{"href":"c2.xhtml","locations":{"totalProgression":0.31}}""", 0.31)
        books.markOpened(id, 1_000L + id)
        books.setScrollMode(id, ScrollMode.PAGES)
        return db.bookDao().byId(id)!!
    }

    /** Trois livres (deux avec couverture), deux sessions, deux surlignages (un annoté), deux collections (une vide), réglages changés. */
    suspend fun seed(): List<BookEntity> {
        val bovary = addBook("Madame Bovary", withCover = true)
        val candide = addBook("Candide")
        val silo = addBook("Silo", withCover = true)
        db.sessionDao().upsert(testSession(bovary.id, startedAt = 1_000L))
        db.sessionDao().upsert(testSession(silo.id, startedAt = 2_000L))
        db.highlightDao().insert(highlight(bovary.id, "Elle s’ennuyait", note = "Le cœur du livre"))
        db.highlightDao().insert(highlight(candide.id, "Il faut cultiver notre jardin", note = null))
        db.collectionDao().create("Classiques", createdAt = 7_000L, bookIds = listOf(candide.id, bovary.id))
        db.collectionDao().create("Vide", createdAt = 8_000L, bookIds = emptyList())
        settings.setThemeMode(ThemeMode.DARK)
        settings.updateReadingSettings { it.withFontSize(22) }
        settings.setReopenLastBook(false)
        return listOf(bovary, candide, silo)
    }

    fun close() = db.close()

    companion object {
        const val NOW = 1_790_000_000_000L

        fun highlight(bookId: Long, text: String, note: String?) = HighlightEntity(
            bookId = bookId,
            locatorJson = """{"href":"c1.xhtml","text":{"highlight":"$text"}}""",
            text = text,
            note = note,
            progression = 0.12,
            chapterPath = "Première partie\nI",
            createdAt = 5_000L,
            updatedAt = 6_000L,
        )
    }
}

/** Recopie [source] dans [target] : [change] renvoie les nouveaux octets d’une entrée, ou null pour l’enlever ; [extra] est ajouté. */
fun rezip(
    source: File,
    target: File,
    extra: Map<String, ByteArray> = emptyMap(),
    change: (name: String, bytes: ByteArray) -> ByteArray? = { _, bytes -> bytes },
) {
    ZipFile(source).use { zip ->
        ZipOutputStream(target.outputStream()).use { out ->
            for (entry in zip.entries()) {
                val bytes = change(entry.name, zip.getInputStream(entry).use { it.readBytes() }) ?: continue
                out.putNextEntry(ZipEntry(entry.name))
                out.write(bytes)
                out.closeEntry()
            }
            extra.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }
}

/** Zip fait main : [entries] nom → contenu. */
fun zipOf(target: File, entries: Map<String, ByteArray>): File {
    ZipOutputStream(target.outputStream()).use { out ->
        entries.forEach { (name, bytes) ->
            out.putNextEntry(ZipEntry(name))
            out.write(bytes)
            out.closeEntry()
        }
    }
    return target
}
