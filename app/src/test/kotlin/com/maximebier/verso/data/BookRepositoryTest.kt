package com.maximebier.verso.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.db.VersoDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: VersoDatabase
    private lateinit var booksDir: File
    private lateinit var coversDir: File
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        booksDir = tmp.newFolder("books")
        coversDir = tmp.newFolder("covers")
        repository = BookRepository(db.bookDao(), booksDir, coversDir)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteRemovesFiles() = runTest {
        val epub = File(booksDir, "abc.epub").apply { writeText("epub") }
        val cover = File(coversDir, "abc.png").apply { writeText("png") }
        val id = repository.insert(testBook("abc", filePath = epub.absolutePath, coverPath = cover.absolutePath))

        repository.delete(id)

        assertThat(repository.book(id)).isNull()
        assertThat(epub.exists()).isFalse()
        assertThat(cover.exists()).isFalse()
    }

    @Test
    fun deleteNeverTouchesFilesOutsideTheAppFolders() = runTest {
        val outside = tmp.newFile("original.epub").apply { writeText("original") }
        val id = repository.insert(testBook("out", filePath = outside.absolutePath))

        repository.delete(id)

        assertThat(repository.book(id)).isNull()
        assertThat(outside.exists()).isTrue()
    }

    @Test
    fun titleSortUsesFrenchCollation() = runTest {
        repository.insert(testBook("1", title = "Zola"))
        repository.insert(testBook("2", title = "Eugénie Grandet"))
        repository.insert(testBook("3", title = "à rebours"))
        repository.insert(testBook("4", title = "Émaux et camées"))
        val titles = repository.observeBooks(LibrarySort.TITLE).first().map { it.title }
        assertThat(titles).containsExactly("à rebours", "Émaux et camées", "Eugénie Grandet", "Zola").inOrder()
    }

    @Test
    fun authorSortPutsUnknownAuthorsLastThenSortsByTitle() = runTest {
        repository.insert(testBook("1", title = "Nana", author = "Émile Zola"))
        repository.insert(testBook("2", title = "Sans auteur", author = ""))
        repository.insert(testBook("3", title = "Germinal", author = "Émile Zola"))
        repository.insert(testBook("4", title = "Madame Bovary", author = "Gustave Flaubert"))
        val titles = repository.observeBooks(LibrarySort.AUTHOR).first().map { it.title }
        assertThat(titles).containsExactly("Germinal", "Nana", "Madame Bovary", "Sans auteur").inOrder()
    }

    @Test
    fun recentSortComesFromTheDatabase() = runTest {
        repository.insert(testBook("old", importedAt = 1))
        val opened = repository.insert(testBook("opened", importedAt = 0))
        repository.markOpened(opened, now = 100)
        assertThat(repository.observeBooks(LibrarySort.RECENT).first().first().id).isEqualTo(opened)
        assertThat(repository.lastOpened()?.id).isEqualTo(opened)
    }

    @Test
    fun saveReadingPositionAndCorrectionsArePersisted() = runTest {
        val id = repository.insert(testBook("p"))
        repository.saveReadingPosition(id, """{"href":"ch1.xhtml"}""", 0.5)
        repository.updateTitle(id, "Titre corrigé")
        repository.updateAuthor(id, "Autrice")
        val book = repository.observeBook(id).first()!!
        assertThat(book.readingLocatorJson).isEqualTo("""{"href":"ch1.xhtml"}""")
        assertThat(book.progression).isEqualTo(0.5)
        assertThat(book.title).isEqualTo("Titre corrigé")
        assertThat(book.author).isEqualTo("Autrice")
        assertThat(repository.findBySha256("p")?.id).isEqualTo(id)
    }
}
