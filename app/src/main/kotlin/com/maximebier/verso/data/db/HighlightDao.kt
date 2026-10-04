package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(highlight: HighlightEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(highlights: List<HighlightEntity>)

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun byId(id: Long): HighlightEntity?

    /** Notes du livre (les surlignages sans note d’avant le 2026-10-04 sont masqués), dans l’ordre du livre ; à progression égale, ordre de création. */
    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND note IS NOT NULL ORDER BY progression, id")
    suspend fun forBook(bookId: Long): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND note IS NOT NULL ORDER BY progression, id")
    fun observeForBook(bookId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT COUNT(*) FROM highlights WHERE bookId = :bookId AND note IS NOT NULL")
    fun observeCount(bookId: Long): Flow<Int>

    @Query("SELECT * FROM highlights ORDER BY id")
    suspend fun all(): List<HighlightEntity>

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM highlights WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM highlights")
    suspend fun clearAll()

    /** Surlignage fusionné : insère [merged] (id 0) ou le met à jour, et supprime les absorbés, d’un seul coup. */
    @Transaction
    suspend fun replaceMerged(merged: HighlightEntity, absorbedIds: List<Long>): Long {
        val others = absorbedIds.filter { it != merged.id }
        if (others.isNotEmpty()) deleteByIds(others)
        return if (merged.id == 0L) insert(merged) else merged.id.also { update(merged) }
    }
}
