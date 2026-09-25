package com.maximebier.verso

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.VersoDatabase
import java.io.File

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Injection manuelle : une instance par processus, créée par VersoApplication. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val database: VersoDatabase = VersoDatabase.build(appContext)

    val books: BookRepository = BookRepository(
        dao = database.bookDao(),
        booksDir = File(appContext.filesDir, "books").apply { mkdirs() },
        coversDir = File(appContext.filesDir, "covers").apply { mkdirs() },
    )

    val sessions: SessionRepository = SessionRepository(database.sessionDao())

    val settings: SettingsRepository = SettingsRepository(appContext.settingsDataStore)

    val clock: () -> Long = System::currentTimeMillis
}
