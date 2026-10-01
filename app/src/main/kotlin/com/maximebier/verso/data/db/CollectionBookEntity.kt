package com.maximebier.verso.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Un livre dans une collection. [position] ne sert qu’à ordonner : un retrait laisse un trou, « Réordonner »
 * réécrit 0..n-1. Supprimé avec le livre ou avec la collection (cascade).
 */
@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    foreignKeys = [
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class CollectionBookEntity(
    val collectionId: Long,
    val bookId: Long,
    val position: Int,
)
