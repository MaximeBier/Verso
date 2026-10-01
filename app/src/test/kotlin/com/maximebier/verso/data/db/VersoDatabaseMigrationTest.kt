package com.maximebier.verso.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Base créée comme par la V1 (SQL de app/schemas/…/1.json), puis ouverte par la V4 : Room joue MIGRATION_1_2,
 * MIGRATION_2_3 puis MIGRATION_3_4 et valide le schéma obtenu contre les entités. Livres, positions et sessions de la V1 sont gardés.
 */
@RunWith(AndroidJUnit4::class)
class VersoDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-1-2.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1DataSurvivesTheMigration() = runTest {
        createVersion1Database()

        val db = VersoDatabase.build(context, name)
        try {
            val book = db.bookDao().byId(1)!!
            assertThat(book.title).isEqualTo("Madame Bovary")
            assertThat(book.readingLocatorJson).isEqualTo("""{"href":"chapitre-2.xhtml"}""")
            assertThat(book.progression).isEqualTo(0.31)
            assertThat(book.scrollMode).isNull()
            assertThat(book.stateOverride).isNull()
            val sessions = db.sessionDao().observeForBook(1).first()
            assertThat(sessions).hasSize(1)
            assertThat(sessions.single().wordsRead).isEqualTo(4_200)
        } finally {
            db.close()
        }
    }

    @Test
    fun newColumnsAreWritable() = runTest {
        createVersion1Database()
        val db = VersoDatabase.build(context, name)
        try {
            db.bookDao().setScrollMode(1, "PAGES")
            db.bookDao().setStateOverride(1, "FINISHED")
            val book = db.bookDao().byId(1)!!
            assertThat(book.scrollMode).isEqualTo("PAGES")
            assertThat(book.stateOverride).isEqualTo("FINISHED")
            db.bookDao().setScrollMode(1, null)
            assertThat(db.bookDao().byId(1)!!.scrollMode).isNull()
        } finally {
            db.close()
        }
    }

    @Test
    fun highlightsTableExistsAfterMigration() = runTest {
        createVersion1Database()
        val db = VersoDatabase.build(context, name)
        try {
            val id = db.highlightDao().insert(
                HighlightEntity(
                    bookId = 1, locatorJson = "{}", text = "passage", note = "note", progression = 0.3,
                    chapterPath = "", createdAt = 1, updatedAt = 1,
                ),
            )
            assertThat(db.highlightDao().byId(id)!!.note).isEqualTo("note")
            assertThat(db.bookDao().byId(1)!!.title).isEqualTo("Madame Bovary")
        } finally {
            db.close()
        }
    }

    @Test
    fun collectionTablesExistAfterMigration() = runTest {
        createVersion1Database()
        val db = VersoDatabase.build(context, name)
        try {
            val id = db.collectionDao().create("Les Rougon-Macquart", createdAt = 5, bookIds = listOf(1L))
            assertThat(db.collectionDao().observeMemberships().first().single().bookId).isEqualTo(1L)
            db.bookDao().deleteById(1)
            assertThat(db.collectionDao().observeMemberships().first()).isEmpty()
            assertThat(db.collectionDao().observeById(id).first()!!.name).isEqualTo("Les Rougon-Macquart")
        } finally {
            db.close()
        }
    }

    @Test
    fun version3DataSurvivesTheMigrationTo4() = runTest {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V3_SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO books (id, title, author, filePath, sha256, coverPath, sizeBytes, originalFileName, importedAt, " +
                    "lastOpenedAt, readingLocatorJson, progression, totalWords, scrollMode, stateOverride) VALUES (1, 'Candide', " +
                    "'Voltaire', '/data/books/c.epub', 'c', NULL, 99, 'candide.epub', 100, 200, '{\"href\":\"c3.xhtml\"}', 0.03, " +
                    "30000, 'PAGES', 'FINISHED')",
            )
            db.execSQL(
                "INSERT INTO sessions (bookId, startedAt, endedAt, activeMs, startLocatorJson, endLocatorJson, startProgression, " +
                    "endProgression, wordsRead) VALUES (1, 1000, 2000, 600000, '{}', '{}', 0.0, 0.03, 900)",
            )
            db.execSQL(
                "INSERT INTO highlights (bookId, locatorJson, text, note, progression, chapterPath, createdAt, updatedAt) " +
                    "VALUES (1, '{}', 'Il faut cultiver notre jardin', 'fin', 0.99, 'XXX', 5, 6)",
            )
            db.version = 3
        }

        val db = VersoDatabase.build(context, name)
        try {
            val book = db.bookDao().byId(1)!!
            assertThat(book.readingLocatorJson).isEqualTo("""{"href":"c3.xhtml"}""")
            assertThat(book.progression).isEqualTo(0.03)
            assertThat(book.scrollMode).isEqualTo("PAGES")
            assertThat(book.stateOverride).isEqualTo("FINISHED")
            assertThat(db.sessionDao().observeForBook(1).first().single().wordsRead).isEqualTo(900)
            assertThat(db.highlightDao().forBook(1).single().note).isEqualTo("fin")

            // Supprimer une collection garde le livre ; supprimer le livre emporte encore ses surlignages.
            val id = db.collectionDao().create("Voltaire", createdAt = 7, bookIds = listOf(1L))
            db.collectionDao().delete(id)
            assertThat(db.bookDao().byId(1)).isNotNull()
            db.bookDao().deleteById(1)
            assertThat(db.highlightDao().forBook(1)).isEmpty()
        } finally {
            db.close()
        }
    }

    private fun createVersion1Database() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO books (id, title, author, filePath, sha256, coverPath, sizeBytes, originalFileName, " +
                    "importedAt, lastOpenedAt, readingLocatorJson, progression, totalWords) VALUES (1, 'Madame Bovary', " +
                    "'Gustave Flaubert', '/data/books/abc.epub', 'abc', NULL, 1234, 'bovary.epub', 100, 200, " +
                    "'{\"href\":\"chapitre-2.xhtml\"}', 0.31, 120000)",
            )
            db.execSQL(
                "INSERT INTO sessions (bookId, startedAt, endedAt, activeMs, startLocatorJson, endLocatorJson, " +
                    "startProgression, endProgression, wordsRead) VALUES (1, 1000, 2000, 900000, '{}', '{}', 0.28, 0.31, 4200)",
            )
            db.version = 1
        }
    }

    private companion object {
        /** SQL exact de app/schemas/…/3.json (ne pas modifier). */
        val V3_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `filePath` TEXT NOT NULL, `sha256` TEXT NOT NULL, `coverPath` TEXT, `sizeBytes` INTEGER NOT NULL, `originalFileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `lastOpenedAt` INTEGER, `readingLocatorJson` TEXT, `progression` REAL NOT NULL, `totalWords` INTEGER NOT NULL, `scrollMode` TEXT, `stateOverride` TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_sha256` ON `books` (`sha256`)",
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `activeMs` INTEGER NOT NULL, `startLocatorJson` TEXT NOT NULL, `endLocatorJson` TEXT NOT NULL, `startProgression` REAL NOT NULL, `endProgression` REAL NOT NULL, `wordsRead` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_sessions_bookId` ON `sessions` (`bookId`)",
            "CREATE TABLE IF NOT EXISTS `highlights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `locatorJson` TEXT NOT NULL, `text` TEXT NOT NULL, `note` TEXT, `progression` REAL NOT NULL, `chapterPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_highlights_bookId` ON `highlights` (`bookId`)",
        )

        /** SQL exact de app/schemas/com.maximebier.verso.data.db.VersoDatabase/1.json (ne pas modifier). */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`author` TEXT NOT NULL, `filePath` TEXT NOT NULL, `sha256` TEXT NOT NULL, `coverPath` TEXT, " +
                "`sizeBytes` INTEGER NOT NULL, `originalFileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, " +
                "`lastOpenedAt` INTEGER, `readingLocatorJson` TEXT, `progression` REAL NOT NULL, `totalWords` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_sha256` ON `books` (`sha256`)",
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `activeMs` INTEGER NOT NULL, " +
                "`startLocatorJson` TEXT NOT NULL, `endLocatorJson` TEXT NOT NULL, `startProgression` REAL NOT NULL, " +
                "`endProgression` REAL NOT NULL, `wordsRead` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES " +
                "`books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_sessions_bookId` ON `sessions` (`bookId`)",
        )
    }
}
