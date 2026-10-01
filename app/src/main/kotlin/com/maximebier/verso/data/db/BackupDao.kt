package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Toute la bibliothèque : livres, sessions, surlignages et collections, dans l’ordre des id. */
data class LibrarySnapshot(
    val books: List<BookEntity>,
    val sessions: List<SessionEntity>,
    val highlights: List<HighlightEntity>,
    val collections: List<CollectionEntity> = emptyList(),
    /** Dans l’ordre de chaque collection. */
    val collectionBooks: List<CollectionBookEntity> = emptyList(),
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

    @Query("SELECT * FROM collections ORDER BY id")
    abstract suspend fun collections(): List<CollectionEntity>

    @Query("SELECT * FROM collection_books ORDER BY collectionId, position, bookId")
    abstract suspend fun collectionBooks(): List<CollectionBookEntity>

    @Query("SELECT COUNT(*) FROM books")
    abstract suspend fun bookCount(): Int

    /** Les livres des collections partent en cascade. */
    @Query("DELETE FROM collections")
    abstract suspend fun deleteCollections()

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCollections(collections: List<CollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCollectionBooks(members: List<CollectionBookEntity>)

    /** Lecture cohérente : aucune écriture ne s’intercale entre les tables. */
    @Transaction
    open suspend fun snapshot(): LibrarySnapshot = LibrarySnapshot(books(), sessions(), highlights(), collections(), collectionBooks())

    /** Vide puis remplit toutes les tables avec les id d’origine ; tout ou rien. */
    @Transaction
    open suspend fun replaceAll(library: LibrarySnapshot) {
        deleteCollections()
        deleteHighlights()
        deleteSessions()
        deleteBooks()
        insertBooks(library.books)
        insertSessions(library.sessions)
        insertHighlights(library.highlights)
        insertCollections(library.collections)
        insertCollectionBooks(library.collectionBooks)
    }
}
