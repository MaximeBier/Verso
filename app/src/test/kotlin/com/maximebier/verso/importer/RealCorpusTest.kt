package com.maximebier.verso.importer

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.ReadiumOpener
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.services.positions
import org.robolectric.ParameterizedRobolectricTestRunner
import java.io.File
import kotlin.time.Duration.Companion.minutes

/** Import, ouverture, positions et comptage de mots sur des EPUB réels et imparfaits (Review Focus n° 1). */
@RunWith(ParameterizedRobolectricTestRunner::class)
class RealCorpusTest(private val case: CorpusCase) {

    enum class CorpusCase(
        val fileName: String,
        val expectedTitle: String,
        val hasCover: Boolean,
        val nestedToc: Boolean,
        val minWords: Long,
        val maxWords: Long,
    ) {
        BOVARY("gutenberg-14155-madame-bovary-fr.epub", "Madame Bovary", true, false, 78_000, 146_000),
        NOTRE_DAME("gutenberg-19657-notre-dame-de-paris-fr.epub", "Notre-Dame de Paris", true, true, 121_000, 225_000),
        FLEURS_DU_MAL("wikisource-les-fleurs-du-mal-1868-fr.epub", "Les Fleurs du mal", false, false, 30_000, 57_000),
        WAR_AND_PEACE("gutenberg-2600-war-and-peace-en.epub", "War and Peace", true, false, 400_000, 740_000),
        PRIDE_EPUB2("gutenberg-1342-pride-and-prejudice-epub2-en.epub", "Pride and Prejudice", true, false, 91_000, 170_000),
        CANDIDE("gutenberg-4650-candide-fr.epub", "Candide, ou l'optimisme", true, true, 26_000, 49_000),
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: VersoDatabase
    private lateinit var books: BookRepository
    private lateinit var opener: ReadiumOpener
    private lateinit var importer: EpubImporter

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).allowMainThreadQueries().build()
        books = BookRepository(db.bookDao(), File(context.filesDir, "books"), File(context.filesDir, "covers"))
        opener = ReadiumOpener(context)
        importer = EpubImporter(context, books, opener)
    }

    @After
    fun tearDown() = db.close()

    private fun copyFromResources(): File {
        val target = File(context.cacheDir, case.fileName)
        val input = requireNotNull(javaClass.getResourceAsStream("/epub/real/${case.fileName}")) {
            "EPUB absent du corpus : ${case.fileName}"
        }
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        return target
    }

    private fun List<Link>.toTocNodes(): List<TocNode> =
        map { TocNode(it.title.orEmpty(), it.href.toString().substringBefore('#'), it.children.toTocNodes()) }

    @Test
    fun importsOpensLocatesAndCountsWords() = runTest(timeout = 3.minutes) {
        val source = copyFromResources()

        val started = System.nanoTime()
        val result = importer.import(Uri.fromFile(source))
        println("${case.name} : import en ${(System.nanoTime() - started) / 1_000_000} ms")

        assertWithMessage("résultat de l'import").that(result).isInstanceOf(ImportResult.Added::class.java)
        val book = requireNotNull(books.book((result as ImportResult.Added).bookId))
        assertThat(book.title).isEqualTo(case.expectedTitle)
        assertWithMessage("couverture").that(book.coverPath != null).isEqualTo(case.hasCover)
        assertThat(book.totalWords).isAtLeast(case.minWords)
        assertThat(book.totalWords).isAtMost(case.maxWords)
        assertThat(File(book.filePath).exists()).isTrue()

        val publication = opener.open(File(book.filePath)).getOrThrow()
        try {
            assertThat(publication.readingOrder).isNotEmpty()
            assertThat(publication.tableOfContents).isNotEmpty()
            assertWithMessage("sommaire imbriqué")
                .that(publication.tableOfContents.any { it.children.isNotEmpty() })
                .isEqualTo(case.nestedToc)

            val positions = publication.positions()
            assertThat(positions).isNotEmpty()
            val middle = positions[positions.size / 2]
            val restored = requireNotNull(Locators.fromJson(Locators.toJson(middle)))
            val position = Locators.toPosition(restored)
            assertThat(position.totalProgression).isWithin(1e-6).of(middle.locations.totalProgression ?: 0.0)
            assertThat(position.totalProgression).isAtLeast(0.2)
            assertThat(position.totalProgression).isAtMost(0.8)

            // Sommaire imparfait (titres concaténés, doublons, fichiers sans entrée) : jamais de plantage.
            chapterPathAt(publication.tableOfContents.toTocNodes(), restored.href.toString().substringBefore('#'))
        } finally {
            publication.close()
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = CorpusCase.entries.map { arrayOf<Any>(it) }
    }
}
