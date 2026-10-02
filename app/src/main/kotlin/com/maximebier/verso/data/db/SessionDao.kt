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

    @Query("SELECT * FROM sessions")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    /**
     * Sessions qui ne sont pas gardées au journal, de tous les livres : de moins de [minActiveMs] de temps actif, ou,
     * pour les livres dont les mots sont comptés, de moins de [minWords] mots lus (dont aucun) ou de plus de
     * [maxWordsPerMinute] mots par minute (survol).
     */
    @Query(
        "DELETE FROM sessions WHERE activeMs < :minActiveMs " +
            "OR (bookId IN (SELECT id FROM books WHERE totalWords > 0) " +
            "AND (wordsRead < :minWords OR wordsRead * 60000 > :maxWordsPerMinute * activeMs))",
    )
    suspend fun deleteDiscarded(minActiveMs: Long, minWords: Long, maxWordsPerMinute: Int)

    @Query("SELECT COUNT(*) FROM sessions WHERE bookId = :bookId")
    suspend fun countForBook(bookId: Long): Int
}
