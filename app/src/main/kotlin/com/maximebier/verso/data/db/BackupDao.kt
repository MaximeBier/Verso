package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Toute la bibliothèque : livres, sessions et surlignages, dans l’ordre des id. */
data class LibrarySnapshot(
    val books: List<BookEntity>,
    val sessions: List<SessionEntity>,
    val highlights: List<HighlightEntity>,
)

/** Lecture et remplacement de toute la bibliothèque (sauvegarde et restauration), chacun en une transaction. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM books ORDER BY id")
    abstract suspend fun books(): List<BookEntity>

    @Query("SELECT * FROM sessions ORDER BY id")
    abstract suspend fun sessions(): List<SessionEntity>

    @Query("SELECT * FROM highlights ORDER BY id")
    abstract suspend fun highlights(): List<HighlightEntity>

    @Query("SELECT COUNT(*) FROM books")
    abstract suspend fun bookCount(): Int

    @Query("DELETE FROM highlights")
    abstract suspend fun deleteHighlights()

    @Query("DELETE FROM sessions")
    abstract suspend fun deleteSessions()

    @Query("DELETE FROM books")
    abstract suspend fun deleteBooks()

    /** ABORT : une empreinte ou un id en double fait échouer toute la transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSessions(sessions: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertHighlights(highlights: List<HighlightEntity>)

    /** Lecture cohérente : aucune écriture ne s’intercale entre les trois tables. */
    @Transaction
    open suspend fun snapshot(): LibrarySnapshot = LibrarySnapshot(books(), sessions(), highlights())

    /** Vide puis remplit les trois tables avec les id d’origine ; tout ou rien. */
    @Transaction
    open suspend fun replaceAll(library: LibrarySnapshot) {
        deleteHighlights()
        deleteSessions()
        deleteBooks()
        insertBooks(library.books)
        insertSessions(library.sessions)
        insertHighlights(library.highlights)
    }
}
