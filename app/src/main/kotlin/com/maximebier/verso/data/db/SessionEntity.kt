package com.maximebier.verso.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Une session du journal de lecture. Supprimée avec son livre (cascade). */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val activeMs: Long,
    val startLocatorJson: String,
    val endLocatorJson: String,
    val startProgression: Double,
    val endProgression: Double,
    val wordsRead: Long,
)
