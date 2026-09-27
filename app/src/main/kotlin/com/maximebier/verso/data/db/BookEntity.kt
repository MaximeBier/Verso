package com.maximebier.verso.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un livre importé. L'empreinte SHA-256 est unique : jamais deux lignes pour le même fichier. */
@Entity(tableName = "books", indices = [Index(value = ["sha256"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String,                 // "" si inconnu
    val filePath: String,               // absolu, dans filesDir/books/<sha256>.epub
    val sha256: String,
    val coverPath: String?,             // filesDir/covers/<sha256>.png, null si pas de couverture
    val sizeBytes: Long,
    val originalFileName: String,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val readingLocatorJson: String?,    // null = jamais ouvert
    val progression: Double = 0.0,      // totalProgression de la position de lecture
    val totalWords: Long,
    val scrollMode: String? = null,     // ScrollMode.name ; null = défilement par défaut des Paramètres (V2)
    val stateOverride: String? = null,  // BookStatus.name choisi dans la fiche ; null = état calculé (V2)
)
