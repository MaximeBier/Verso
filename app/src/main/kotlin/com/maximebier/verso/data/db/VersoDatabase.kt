package com.maximebier.verso.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base locale (schéma exporté dans app/schemas). Les clés étrangères sont activées par Room (cascade des sessions). */
@Database(entities = [BookEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class VersoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "verso.db"

        fun build(context: Context): VersoDatabase =
            Room.databaseBuilder(context.applicationContext, VersoDatabase::class.java, NAME).build()
    }
}
