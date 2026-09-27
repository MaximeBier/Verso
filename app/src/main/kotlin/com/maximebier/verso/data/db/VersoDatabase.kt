package com.maximebier.verso.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Base locale (schéma exporté dans app/schemas). Les clés étrangères sont activées par Room (cascade des sessions). */
@Database(entities = [BookEntity::class, SessionEntity::class], version = 2, exportSchema = true)
abstract class VersoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "verso.db"

        /** V2 : défilement mémorisé par livre et état choisi à la main, null par défaut (données V1 intactes). */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `scrollMode` TEXT")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `stateOverride` TEXT")
            }
        }

        fun build(context: Context, name: String = NAME): VersoDatabase =
            Room.databaseBuilder(context.applicationContext, VersoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
