package com.maximebier.verso.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Base locale (schéma exporté dans app/schemas). Les clés étrangères sont activées par Room (cascade des sessions). */
@Database(entities = [BookEntity::class, SessionEntity::class, HighlightEntity::class], version = 3, exportSchema = true)
abstract class VersoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao
    abstract fun highlightDao(): HighlightDao
    abstract fun backupDao(): BackupDao

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

        fun build(context: Context, name: String = NAME): VersoDatabase =
            Room.databaseBuilder(context.applicationContext, VersoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
