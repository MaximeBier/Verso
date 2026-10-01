package com.maximebier.verso.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** JSON de la sauvegarde : champs inconnus ignorés (une version future peut en ajouter sans changer de format). */
val BackupJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Lu en premier : seul le numéro de format, quel que soit le reste du fichier. */
@Serializable
data class BackupHeaderDto(val format: Int)

/** `donnees.json`. Aucun chemin de fichier : ils sont recalculés sur le téléphone qui restaure. */
@Serializable
data class BackupDataDto(
    val format: Int,
    val createdAt: Long,
    val appVersion: String,
    val books: List<BookDto>,
    val sessions: List<SessionDto>,
    val highlights: List<HighlightDto>,
)

@Serializable
data class BookDto(
    val id: Long,
    val title: String,
    val author: String,
    val sha256: String,
    /** Nom de la couverture dans `couvertures/` (`<sha256>.jpg` ou `.png`) ; null sans couverture. */
    val coverFile: String?,
    val sizeBytes: Long,
    val originalFileName: String,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val readingLocatorJson: String?,
    val progression: Double,
    val totalWords: Long,
    val scrollMode: String?,
    val stateOverride: String?,
)

@Serializable
data class SessionDto(
    val id: Long,
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

@Serializable
data class HighlightDto(
    val id: Long,
    val bookId: Long,
    val locatorJson: String,
    val text: String,
    val note: String?,
    val progression: Double,
    val chapterPath: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Une clé DataStore : [type] parmi boolean, int, long, float, double, string, stringSet. */
@Serializable
data class SettingDto(val key: String, val type: String, val value: JsonElement)

/** `reglages.json`. */
@Serializable
data class SettingsFileDto(val settings: List<SettingDto>)
