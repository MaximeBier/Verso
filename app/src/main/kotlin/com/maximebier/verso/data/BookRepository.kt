package com.maximebier.verso.data

import com.maximebier.verso.data.db.BookDao
import com.maximebier.verso.data.db.BookEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Catalogue : lignes Room + fichiers EPUB et couvertures copiés dans le stockage privé. */
class BookRepository(private val dao: BookDao, private val booksDir: File, private val coversDir: File) {

    fun observeBooks(sort: LibrarySort): Flow<List<BookEntity>> = when (sort) {
        LibrarySort.RECENT -> dao.observeRecent()
        LibrarySort.TITLE -> dao.observeAll().map { books -> books.sortedWith(LibraryOrdering.byTitle()) }
        LibrarySort.AUTHOR -> dao.observeAll().map { books -> books.sortedWith(LibraryOrdering.byAuthor()) }
    }

    fun observeBook(id: Long): Flow<BookEntity?> = dao.observeById(id)

    fun observeLastOpened(): Flow<BookEntity?> = dao.observeLastOpened()

    suspend fun book(id: Long): BookEntity? = dao.byId(id)

    suspend fun findBySha256(sha256: String): BookEntity? = dao.bySha256(sha256)

    suspend fun lastOpened(): BookEntity? = dao.lastOpened()

    suspend fun insert(book: BookEntity): Long = dao.insert(book)

    suspend fun replaceFile(id: Long, filePath: String, coverPath: String?, sizeBytes: Long, originalFileName: String, totalWords: Long) =
        dao.replaceFile(id, filePath, coverPath, sizeBytes, originalFileName, totalWords)

    suspend fun updateTitle(id: Long, title: String) = dao.updateTitle(id, title)

    suspend fun updateAuthor(id: Long, author: String) = dao.updateAuthor(id, author)

    suspend fun saveReadingPosition(id: Long, locatorJson: String, progression: Double) =
        dao.updateReadingPosition(id, locatorJson, progression)

    suspend fun markOpened(id: Long, now: Long) = dao.markOpened(id, now)

    /** Supprime la ligne (sessions en cascade), puis l'EPUB et la couverture — seulement s'ils sont dans les dossiers de l'app. */
    suspend fun delete(id: Long) {
        val book = dao.byId(id) ?: return
        dao.deleteById(id)
        withContext(Dispatchers.IO) {
            deleteIfInside(booksDir, book.filePath)
            book.coverPath?.let { deleteIfInside(coversDir, it) }
        }
    }

    private fun deleteIfInside(dir: File, path: String) {
        val file = File(path).canonicalFile
        val root = dir.canonicalFile
        if (file.path.startsWith(root.path + File.separator)) {
            file.delete()
        }
    }
}
