package com.maximebier.verso.data

import com.maximebier.verso.data.db.CollectionBookEntity
import com.maximebier.verso.data.db.CollectionDao
import com.maximebier.verso.data.db.CollectionEntity
import kotlinx.coroutines.flow.Flow

/** Collections et ordre de lecture de leurs livres (V4). */
class CollectionRepository(private val dao: CollectionDao, private val clock: () -> Long) {

    fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeAll()

    fun observeCollection(id: Long): Flow<CollectionEntity?> = dao.observeById(id)

    fun observeMemberships(): Flow<List<CollectionBookEntity>> = dao.observeMemberships()

    fun observeCollectionIdsOf(bookId: Long): Flow<List<Long>> = dao.observeCollectionIdsOf(bookId)

    /** Nom coupé ; [bookIds] dans l’ordre de lecture. IllegalArgumentException si le nom est vide. */
    suspend fun create(name: String, bookIds: List<Long>): Long {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Nom de collection vide" }
        return dao.create(clean, clock(), bookIds)
    }

    suspend fun add(collectionId: Long, bookId: Long) = dao.add(collectionId, bookId)

    suspend fun remove(collectionId: Long, bookId: Long) = dao.remove(collectionId, bookId)

    suspend fun reorder(collectionId: Long, bookIds: List<Long>) = dao.reorder(collectionId, bookIds)

    /** Nom vide ou blanc : sans effet. */
    suspend fun rename(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) dao.rename(id, clean)
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
