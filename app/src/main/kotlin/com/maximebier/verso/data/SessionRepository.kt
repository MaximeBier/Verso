package com.maximebier.verso.data

import com.maximebier.verso.data.db.SessionDao
import com.maximebier.verso.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: SessionDao) {

    /** Plus récentes d'abord. */
    fun observeSessions(bookId: Long): Flow<List<SessionEntity>> = dao.observeForBook(bookId)

    /** Renvoie l'id de la session (nouvel id à la première insertion, id inchangé ensuite). */
    suspend fun upsert(session: SessionEntity): Long {
        val rowId = dao.upsert(session)
        return if (session.id != 0L) session.id else rowId
    }

    suspend fun clearAll() = dao.clearAll()

    /** Retire du journal les sessions sans lecture (dont celles écrites avant cette règle). */
    suspend fun deleteEmpty() = dao.deleteEmpty()
}
