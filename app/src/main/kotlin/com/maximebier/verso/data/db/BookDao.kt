package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** ABORT : une empreinte déjà présente lève SQLiteConstraintException (le doublon est traité en amont par l'import). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE sha256 = :sha256 LIMIT 1")
    suspend fun bySha256(sha256: String): BookEntity?

    /** « Récents » : dernier ouvert d'abord, jamais ouverts à la fin (par date d'import décroissante). Sans NULLS LAST (SQLite 3.18 sur Android 8.0). */
    @Query("SELECT * FROM books ORDER BY lastOpenedAt IS NULL, lastOpenedAt DESC, importedAt DESC, id DESC")
    fun observeRecent(): Flow<List<BookEntity>>

    /** Tous les livres, sans ordre : les tris Titre et Auteur sont faits en Kotlin (Collator français). */
    @Query("SELECT * FROM books")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC, id DESC LIMIT 1")
    fun observeLastOpened(): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC, id DESC LIMIT 1")
    suspend fun lastOpened(): BookEntity?

    @Query("UPDATE books SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE books SET author = :author WHERE id = :id")
    suspend fun updateAuthor(id: Long, author: String)

    @Query("UPDATE books SET readingLocatorJson = :locatorJson, progression = :progression WHERE id = :id")
    suspend fun updateReadingPosition(id: Long, locatorJson: String, progression: Double)

    @Query("UPDATE books SET lastOpenedAt = :now WHERE id = :id")
    suspend fun markOpened(id: Long, now: Long)

    @Query(
        "UPDATE books SET filePath = :filePath, coverPath = :coverPath, sizeBytes = :sizeBytes, " +
            "originalFileName = :originalFileName, totalWords = :totalWords WHERE id = :id",
    )
    suspend fun replaceFile(id: Long, filePath: String, coverPath: String?, sizeBytes: Long, originalFileName: String, totalWords: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
