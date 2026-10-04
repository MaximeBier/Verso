package com.maximebier.verso.data

import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.data.db.HighlightDao
import com.maximebier.verso.data.db.HighlightEntity
import kotlinx.coroutines.flow.Flow

/**
 * Notes d’un livre (V3). Depuis le 2026-10-04, un passage n’est marqué que s’il porte une note : les surlignages sans
 * note enregistrés avant restent en base (et dans les sauvegardes) mais [observe], [observeCount] et [forBook] les ignorent.
 */
class HighlightRepository(private val dao: HighlightDao, private val clock: () -> Long) {

    fun observe(bookId: Long): Flow<List<HighlightEntity>> = dao.observeForBook(bookId)

    fun observeCount(bookId: Long): Flow<Int> = dao.observeCount(bookId)

    suspend fun forBook(bookId: Long): List<HighlightEntity> = dao.forBook(bookId)

    suspend fun get(id: Long): HighlightEntity? = dao.byId(id)

    /** Insère [merged] (id 0) ou le met à jour, et supprime [absorbedIds], dans une transaction ; renvoie l’id. */
    suspend fun saveMerged(merged: HighlightEntity, absorbedIds: List<Long>): Long = dao.replaceMerged(merged, absorbedIds)

    /** Note coupée ; vide ou blanche = sans note. */
    suspend fun setNote(id: Long, note: String?) {
        val row = dao.byId(id) ?: return
        dao.update(row.copy(note = note?.trim()?.takeIf(String::isNotEmpty), updatedAt = clock()))
    }

    /** Supprime et renvoie la ligne (pour « Annuler ») ; null si absente. */
    suspend fun delete(id: Long): HighlightEntity? {
        val row = dao.byId(id) ?: return null
        dao.deleteById(id)
        return row
    }

    /** « Annuler » après une suppression : la même ligne, même id. Sans effet si le livre a été supprimé entre-temps. */
    suspend fun restore(row: HighlightEntity) {
        runCatching { dao.insert(row) }
    }
}

/** Titres du sommaire enregistrés avec le surlignage. */
fun HighlightEntity.chapterPathList(): List<String> = if (chapterPath.isEmpty()) emptyList() else chapterPath.split('\n')

/** Un titre sur plusieurs lignes est ramené à une ligne : '\n' sépare les niveaux. */
fun List<String>.toChapterPathColumn(): String = joinToString("\n") { TextQuotes.normalize(it) }
