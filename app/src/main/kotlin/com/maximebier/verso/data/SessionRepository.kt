package com.maximebier.verso.data

import com.maximebier.verso.core.journal.SessionThresholds
import com.maximebier.verso.core.stats.ReadingStats
import com.maximebier.verso.core.stats.SessionStat
import com.maximebier.verso.core.stats.readingStats
import com.maximebier.verso.data.db.SessionDao
import com.maximebier.verso.data.db.SessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(private val dao: SessionDao) {

    /** Plus récentes d'abord. */
    fun observeSessions(bookId: Long): Flow<List<SessionEntity>> = dao.observeForBook(bookId)

    /** Statistiques du livre, recalculées à chaque session écrite. */
    fun observeStats(bookId: Long): Flow<ReadingStats> = dao.observeForBook(bookId).map { list ->
        readingStats(list.map { SessionStat(activeMs = it.activeMs, wordsRead = it.wordsRead) })
    }

    /** Statistiques de chaque livre qui a des sessions (vitesse des collections, V4). */
    fun observeAllStats(): Flow<Map<Long, ReadingStats>> = dao.observeAll().map { list ->
        list.groupBy { it.bookId }.mapValues { (_, sessions) ->
            readingStats(sessions.map { SessionStat(activeMs = it.activeMs, wordsRead = it.wordsRead) })
        }
    }

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
