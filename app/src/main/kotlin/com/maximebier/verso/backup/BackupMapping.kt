package com.maximebier.verso.backup

import com.maximebier.verso.core.backup.BackupBookRef
import com.maximebier.verso.core.backup.BackupCollectionRef
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.core.backup.BackupItemRef
import com.maximebier.verso.core.backup.BackupManifest
import com.maximebier.verso.data.RawSetting
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.CollectionBookEntity
import com.maximebier.verso.data.db.CollectionEntity
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.SessionEntity
import com.maximebier.verso.data.toChapterPathColumn
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun BookEntity.toDto(coverFile: String?): BookDto = BookDto(
    id = id,
    title = title,
    author = author,
    sha256 = sha256,
    coverFile = coverFile,
    sizeBytes = sizeBytes,
    originalFileName = originalFileName,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    readingLocatorJson = readingLocatorJson,
    progression = progression,
    totalWords = totalWords,
    scrollMode = scrollMode,
    stateOverride = stateOverride,
)

/** Chemins réécrits vers ce téléphone : `booksDir/<sha256>.epub` et `coversDir/<coverFile>`. */
fun BookDto.toEntity(booksDir: File, coversDir: File): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    filePath = File(booksDir, sha256 + BackupFormat.BOOK_EXTENSION).absolutePath,
    sha256 = sha256,
    coverPath = coverFile?.let { File(coversDir, it).absolutePath },
    sizeBytes = sizeBytes,
    originalFileName = originalFileName,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    readingLocatorJson = readingLocatorJson,
    progression = progression,
    totalWords = totalWords,
    scrollMode = scrollMode,
    stateOverride = stateOverride,
)

fun SessionEntity.toDto(): SessionDto = SessionDto(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = endedAt,
    activeMs = activeMs,
    startLocatorJson = startLocatorJson,
    endLocatorJson = endLocatorJson,
    startProgression = startProgression,
    endProgression = endProgression,
    wordsRead = wordsRead,
)

fun SessionDto.toEntity(): SessionEntity = SessionEntity(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = endedAt,
    activeMs = activeMs,
    startLocatorJson = startLocatorJson,
    endLocatorJson = endLocatorJson,
    startProgression = startProgression,
    endProgression = endProgression,
    wordsRead = wordsRead,
)

fun HighlightEntity.toDto(): HighlightDto = HighlightDto(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    text = text,
    note = note,
    progression = progression,
    chapterPath = chapterPathList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun HighlightDto.toEntity(): HighlightEntity = HighlightEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    text = text,
    note = note,
    progression = progression,
    chapterPath = chapterPath.toChapterPathColumn(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Livres de la collection dans l’ordre des positions (les trous laissés par un retrait sont ignorés). */
fun CollectionEntity.toDto(members: List<CollectionBookEntity>): CollectionDto = CollectionDto(
    id = id,
    name = name,
    createdAt = createdAt,
    bookIds = members.filter { it.collectionId == id }.sortedWith(compareBy({ it.position }, { it.bookId })).map { it.bookId },
)

/** Positions réécrites 0..n-1. */
fun CollectionDto.toEntities(): Pair<CollectionEntity, List<CollectionBookEntity>> =
    CollectionEntity(id = id, name = name, createdAt = createdAt) to
        bookIds.mapIndexed { index, bookId -> CollectionBookEntity(collectionId = id, bookId = bookId, position = index) }

fun RawSetting.toDto(): SettingDto = when (val v = value) {
    is Boolean -> SettingDto(key, "boolean", JsonPrimitive(v))
    is Int -> SettingDto(key, "int", JsonPrimitive(v))
    is Long -> SettingDto(key, "long", JsonPrimitive(v))
    is Float -> SettingDto(key, "float", JsonPrimitive(v))
    is Double -> SettingDto(key, "double", JsonPrimitive(v))
    is String -> SettingDto(key, "string", JsonPrimitive(v))
    is Set<*> -> SettingDto(key, "stringSet", JsonArray(v.map { JsonPrimitive(it as String) }))
    else -> throw IllegalArgumentException("Type de réglage non pris en charge : ${v::class.simpleName}")
}

/** IllegalArgumentException si le type est inconnu ou la valeur mal formée (la sauvegarde est alors invalide). */
fun SettingDto.toRawSetting(): RawSetting {
    val primitive = value as? JsonPrimitive
    val parsed: Any? = when (type) {
        "boolean" -> primitive?.booleanOrNull
        "int" -> primitive?.intOrNull
        "long" -> primitive?.longOrNull
        "float" -> primitive?.floatOrNull
        "double" -> primitive?.doubleOrNull
        "string" -> primitive?.takeIf { it.isString }?.content
        "stringSet" -> (value as? JsonArray)?.map { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("Réglage $key : élément non texte")
        }?.toSet()
        else -> throw IllegalArgumentException("Réglage $key : type « $type » inconnu")
    }
    requireNotNull(parsed) { "Réglage $key : valeur incompatible avec le type « $type »" }
    return RawSetting(key, parsed)
}

fun BackupDataDto.manifest(entries: Set<String>): BackupManifest = BackupManifest(
    format = format,
    books = books.map { BackupBookRef(it.id, it.sha256, it.coverFile) },
    sessions = sessions.map { BackupItemRef(it.id, it.bookId) },
    highlights = highlights.map { BackupItemRef(it.id, it.bookId) },
    entries = entries,
    collections = collections.map { BackupCollectionRef(it.id, it.bookIds) },
)
