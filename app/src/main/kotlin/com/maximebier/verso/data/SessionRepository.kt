package com.maximebier.verso.data

import com.maximebier.verso.core.journal.SessionThresholds
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

    /**
     * Retire du journal les sessions qui n’y sont pas gardées (sans lecture, moins de 30 s de temps actif), dont
     * celles écrites avant ces règles.
     */
    suspend fun deleteDiscarded(thresholds: SessionThresholds = SessionThresholds()) =
        dao.deleteDiscarded(thresholds.minActiveMs)
}
