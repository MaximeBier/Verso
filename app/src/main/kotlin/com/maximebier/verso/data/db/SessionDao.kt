package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** Insère (renvoie le rowid) ou met à jour si l'id existe déjà (renvoie -1). */
    @Upsert
    suspend fun upsert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE bookId = :bookId ORDER BY startedAt DESC, id DESC")
    fun observeForBook(bookId: Long): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sessions WHERE bookId = :bookId")
    suspend fun countForBook(bookId: Long): Int
}
