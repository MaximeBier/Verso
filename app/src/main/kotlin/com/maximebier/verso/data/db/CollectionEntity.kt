package com.maximebier.verso.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Une collection créée par l’utilisateur (V4). Son auteur n’est pas enregistré : il se déduit de ses livres. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)
