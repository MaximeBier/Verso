package com.maximebier.verso.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un surlignage, avec sa note éventuelle. Supprimé avec son livre (cascade). */
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locatorJson: String,      // locator Readium de la plage (texte, contexte, href, progressions)
    val text: String,             // passage, espaces normalisés
    val note: String?,            // null = sans note
    val progression: Double,      // totalProgression du début du passage : ordre du livre et pourcentage
    val chapterPath: String,      // chemin du sommaire, titres séparés par '\n' ; "" = hors sommaire
    val createdAt: Long,
    val updatedAt: Long,
)
