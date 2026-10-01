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
 * Base créée comme par la V1 (SQL de app/schemas/…/1.json), puis ouverte par la V3 : Room joue MIGRATION_1_2 puis
 * MIGRATION_2_3 et valide le schéma obtenu contre les entités. Livres, positions et sessions de la V1 sont gardés.
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
