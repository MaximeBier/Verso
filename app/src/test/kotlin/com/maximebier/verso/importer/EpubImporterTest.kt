package com.maximebier.verso.importer

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.readium.ReadiumOpener
import java.io.File
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubImporterTest {

    @get:Rule val tmp = TemporaryFolder()

    private val app: Context = ApplicationProvider.getApplicationContext()
    private lateinit var context: TestDirsContext
    private lateinit var db: VersoDatabase
    private lateinit var books: BookRepository
    private lateinit var opener: ReadiumOpener
    private lateinit var importer: EpubImporter
    private lateinit var sources: File

    private val booksDir get() = File(context.filesDir, "books")
    private val coversDir get() = File(context.filesDir, "covers")
    private val importDir get() = File(context.cacheDir, "import")

    @Before
    fun setUp() {
        context = TestDirsContext(app, tmp.newFolder("files"), tmp.newFolder("cache"))
        sources = tmp.newFolder("sources")
        db = Room.inMemoryDatabaseBuilder(app, VersoDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        books = BookRepository(db.bookDao(), booksDir, coversDir)
        opener = ReadiumOpener(app)
        importer = EpubImporter(context, books, opener, clock = { IMPORTED_AT })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun source(name: String, make: (File) -> File): Uri = Uri.fromFile(make(File(sources, name)))

    private suspend fun allBooks() = books.observeBooks(LibrarySort.RECENT).first()

    private fun File.names(): List<String> = listFiles().orEmpty().map { it.name }

    private suspend fun assertNoTrace() {
        assertThat(allBooks()).isEmpty()
        assertThat(booksDir.names()).isEmpty()
        assertThat(coversDir.names()).isEmpty()
        assertThat(importDir.names()).isEmpty()
    }

    @Test
    fun addsGutenbergBookWithMetadataCoverAndWords() = runTest {
        val uri = source("candide.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) }
        val result = importer.import(uri)

        assertThat(result).isInstanceOf(ImportResult.Added::class.java)
        val added = result as ImportResult.Added
        val book = requireNotNull(books.book(added.bookId))
        assertThat(added.title).isEqualTo(book.title)
        assertThat(book.title).startsWith("Candide")
        assertThat(book.author).isEqualTo("Voltaire")
        assertThat(book.sha256).isEqualTo(Sha256.of(File(sources, "candide.epub")))
        assertThat(book.filePath).isEqualTo(File(booksDir, "${book.sha256}.epub").absolutePath)
        assertThat(File(book.filePath).length()).isEqualTo(File(sources, "candide.epub").length())
        assertThat(book.coverPath).isEqualTo(File(coversDir, "${book.sha256}.jpg").absolutePath)
        assertThat(File(requireNotNull(book.coverPath)).exists()).isTrue()
        assertThat(book.sizeBytes).isEqualTo(File(sources, "candide.epub").length())
        assertThat(book.originalFileName).isEqualTo("candide.epub")
        assertThat(book.importedAt).isEqualTo(IMPORTED_AT)
        assertThat(book.lastOpenedAt).isNull()
        assertThat(book.readingLocatorJson).isNull()
        assertThat(book.totalWords).isGreaterThan(10_000L)
        assertThat(importDir.names()).isEmpty()
    }

    @Test
    fun twoDifferentBooksGiveTwoRows() = runTest {
        importer.import(source("candide.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) })
        importer.import(source("alice.epub") { EpubFixtures.resource(EpubFixtures.ALICE, it) })
        assertThat(allBooks().map { it.author }).containsExactly("Voltaire", "Lewis Carroll")
    }

    @Test
    fun minimalEpubWithoutTitleUsesFileNameAndEmptyAuthor() = runTest {
        val uri = source("mon-livre.epub") { EpubFixtures.epub(it, title = null, author = null) }
        val added = importer.import(uri) as ImportResult.Added
        val book = requireNotNull(books.book(added.bookId))
        assertThat(book.title).isEqualTo("mon-livre")
        assertThat(book.author).isEmpty()
        assertThat(book.coverPath).isNull()
        assertThat(coversDir.names()).isEmpty()
    }

    @Test
    fun duplicateIsDetectedThenIgnored() = runTest {
        val first = importer.import(source("candide.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) }) as ImportResult.Added
        val second = importer.import(source("copie.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) })

        assertThat(second).isInstanceOf(ImportResult.Duplicate::class.java)
        val duplicate = second as ImportResult.Duplicate
        assertThat(duplicate.existing.id).isEqualTo(first.bookId)
        assertThat(duplicate.pending.originalFileName).isEqualTo("copie.epub")
        assertThat(duplicate.pending.tempFile.exists()).isTrue()
        assertThat(allBooks()).hasSize(1)

        importer.ignore(duplicate)
        assertThat(duplicate.pending.tempFile.exists()).isFalse()
        assertThat(allBooks()).hasSize(1)
        assertThat(requireNotNull(books.book(first.bookId)).originalFileName).isEqualTo("candide.epub")
    }

    @Test
    fun replaceKeepsIdCorrectedTitleAuthorAndPosition() = runTest {
        val first = importer.import(source("candide.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) }) as ImportResult.Added
        books.updateTitle(first.bookId, "Candide corrigé")
        books.updateAuthor(first.bookId, "François-Marie Arouet")
        books.saveReadingPosition(first.bookId, LOCATOR_JSON, 0.42)

        val duplicate = importer.import(source("candide-bis.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) }) as ImportResult.Duplicate
        val replaced = importer.replace(duplicate)

        assertThat(replaced.bookId).isEqualTo(first.bookId)
        assertThat(replaced.title).isEqualTo("Candide corrigé")
        val book = requireNotNull(books.book(first.bookId))
        assertThat(book.title).isEqualTo("Candide corrigé")
        assertThat(book.author).isEqualTo("François-Marie Arouet")
        assertThat(book.readingLocatorJson).isEqualTo(LOCATOR_JSON)
        assertThat(book.progression).isEqualTo(0.42)
        assertThat(book.originalFileName).isEqualTo("candide-bis.epub")
        assertThat(File(book.filePath).exists()).isTrue()
        assertThat(duplicate.pending.tempFile.exists()).isFalse()
        assertThat(allBooks()).hasSize(1)
        assertThat(importDir.names()).isEmpty()
    }

    @Test
    fun textFileIsRejectedWithoutTrace() = runTest {
        val result = importer.import(source("texte.epub") { EpubFixtures.textFile(it) })
        assertThat(result).isEqualTo(ImportResult.Rejected(RejectReason.NOT_EPUB))
        assertNoTrace()
    }

    @Test
    fun imagesZipIsRejectedWithoutTrace() = runTest {
        val result = importer.import(source("images.zip") { EpubFixtures.imagesZip(it) })
        assertThat(result).isEqualTo(ImportResult.Rejected(RejectReason.NOT_EPUB))
        assertNoTrace()
    }

    @Test
    fun adobeDrmIsRejectedWithoutTrace() = runTest {
        val result = importer.import(source("adept.epub") { EpubFixtures.adobeDrm(it) })
        assertThat(result).isEqualTo(ImportResult.Rejected(RejectReason.DRM))
        assertNoTrace()
    }

    @Test
    fun lcpDrmIsRejectedWithoutTrace() = runTest {
        val result = importer.import(source("lcp.epub") { EpubFixtures.lcpDrm(it) })
        assertThat(result).isEqualTo(ImportResult.Rejected(RejectReason.DRM))
        assertNoTrace()
    }

    @Test
    fun brokenEpubIsRejectedAsUnreadable() = runTest {
        val result = importer.import(source("casse.epub") { EpubFixtures.brokenEpub(it) })
        assertThat(result).isEqualTo(ImportResult.Rejected(RejectReason.UNREADABLE))
        assertNoTrace()
    }

    @Test
    fun missingSourceIsRejectedAsUnreadable() = runTest {
        val result = importer.import(Uri.fromFile(File(sources, "absent.epub")))
        assertThat(result).isEqualTo(ImportResult.Rejected(RejectReason.UNREADABLE))
        assertNoTrace()
    }

    @Test
    fun orphanFileIsDeletedOnFailure() = runTest {
        // L'horloge n'est lue qu'après le déplacement du fichier et l'écriture de la couverture.
        val failing = EpubImporter(context, books, opener, clock = { throw IOException("horloge en panne") })
        val result = failing.import(source("candide.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) })
        assertThat(result).isEqualTo(ImportResult.Rejected(RejectReason.UNREADABLE))
        assertNoTrace()
    }

    @Test
    fun concurrentImportOfSameFileCreatesOneRow() = runTest {
        val uri = source("candide.epub") { EpubFixtures.resource(EpubFixtures.CANDIDE, it) }
        val a = async { importer.import(uri) }
        val b = async { importer.import(uri) }
        val results = listOf(a.await(), b.await())

        assertThat(results.filterIsInstance<ImportResult.Added>()).hasSize(1)
        assertThat(results.filterIsInstance<ImportResult.Duplicate>()).hasSize(1)
        assertThat(allBooks()).hasSize(1)
        assertThat(booksDir.names()).hasSize(1)
        results.filterIsInstance<ImportResult.Duplicate>().forEach { importer.ignore(it) }
        assertThat(importDir.names()).isEmpty()
    }

    @Test
    fun displayNameFallsBackToLastPathSegment() {
        assertThat(importer.displayName(Uri.fromFile(File(sources, "germinal.epub")))).isEqualTo("germinal.epub")
    }

    private companion object {
        const val IMPORTED_AT = 1_789_000_000_000L
        const val LOCATOR_JSON =
            """{"href":"OEBPS/chap01.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5,"totalProgression":0.42}}"""
    }
}
