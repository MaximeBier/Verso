package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionDao {
    /** La plus récente d’abord. */
    @Query("SELECT * FROM collections ORDER BY createdAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    abstract fun observeById(id: Long): Flow<CollectionEntity?>

    /** Toutes les appartenances, dans l’ordre de chaque collection. */
    @Query("SELECT * FROM collection_books ORDER BY collectionId, position, bookId")
    abstract fun observeMemberships(): Flow<List<CollectionBookEntity>>

    @Query("SELECT collectionId FROM collection_books WHERE bookId = :bookId")
    abstract fun observeCollectionIdsOf(bookId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertCollection(collection: CollectionEntity): Long

    /** IGNORE : un livre déjà présent garde sa place. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMember(member: CollectionBookEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM collection_books WHERE collectionId = :collectionId")
    abstract suspend fun nextPosition(collectionId: Long): Int

    @Query("UPDATE collection_books SET position = :position WHERE collectionId = :collectionId AND bookId = :bookId")
    abstract suspend fun setPosition(collectionId: Long, bookId: Long, position: Int)

    @Query("DELETE FROM collection_books WHERE collectionId = :collectionId AND bookId = :bookId")
    abstract suspend fun remove(collectionId: Long, bookId: Long)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Transaction
    open suspend fun create(name: String, createdAt: Long, bookIds: List<Long>): Long {
        val id = insertCollection(CollectionEntity(name = name, createdAt = createdAt))
        bookIds.distinct().forEachIndexed { index, bookId -> insertMember(CollectionBookEntity(id, bookId, index)) }
        return id
    }

    @Transaction
    open suspend fun add(collectionId: Long, bookId: Long) {
        insertMember(CollectionBookEntity(collectionId, bookId, nextPosition(collectionId)))
    }

    @Transaction
    open suspend fun reorder(collectionId: Long, bookIds: List<Long>) {
        bookIds.forEachIndexed { index, bookId -> setPosition(collectionId, bookId, index) }
    }
}
