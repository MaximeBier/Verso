package com.maximebier.verso.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Base locale (schéma exporté dans app/schemas). Les clés étrangères sont activées par Room (cascade des sessions). */
@Database(
    entities = [BookEntity::class, SessionEntity::class, HighlightEntity::class, CollectionEntity::class, CollectionBookEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class VersoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao
    abstract fun highlightDao(): HighlightDao
    abstract fun backupDao(): BackupDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "verso.db"

        /** V2 : défilement mémorisé par livre et état choisi à la main, null par défaut (données V1 intactes). */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `scrollMode` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `stateOverride` TEXT")
            }
        }

        /** V3 : surlignages et notes, supprimés avec leur livre. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `highlights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER NOT NULL, `locatorJson` TEXT NOT NULL, `text` TEXT NOT NULL, `note` TEXT, " +
                        "`progression` REAL NOT NULL, `chapterPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_bookId` ON `highlights` (`bookId`)")
            }
        }

        /** V4 : collections et leurs livres, supprimés avec la collection ou avec le livre. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_books` (`collectionId` INTEGER NOT NULL, `bookId` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `bookId`), " +
                        "FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_books_bookId` ON `collection_books` (`bookId`)")
            }
        }

        fun build(context: Context, name: String = NAME): VersoDatabase =
            Room.databaseBuilder(context.applicationContext, VersoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
