package com.maximebier.verso.data

import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.SessionEntity

/** Fabriques de données de test. */
fun testBook(
    sha256: String,
    title: String = "Titre $sha256",
    author: String = "",
    importedAt: Long = 0L,
    lastOpenedAt: Long? = null,
    filePath: String = "/books/$sha256.epub",
    coverPath: String? = null,
): BookEntity = BookEntity(
    title = title,
    author = author,
    filePath = filePath,
    sha256 = sha256,
    coverPath = coverPath,
    sizeBytes = 1_000L,
    originalFileName = "$sha256.epub",
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    readingLocatorJson = null,
    totalWords = 50_000L,
)

fun testSession(bookId: Long, startedAt: Long, id: Long = 0L): SessionEntity = SessionEntity(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = startedAt + 60_000L,
    activeMs = 55_000L,
    startLocatorJson = """{"href":"ch1.xhtml","type":"application/xhtml+xml"}""",
    endLocatorJson = """{"href":"ch2.xhtml","type":"application/xhtml+xml"}""",
    startProgression = 0.1,
    endProgression = 0.2,
    wordsRead = 1_200L,
)
