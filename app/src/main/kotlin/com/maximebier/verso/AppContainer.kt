package com.maximebier.verso

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.maximebier.verso.backup.BackupRestorer
import com.maximebier.verso.backup.BackupService
import com.maximebier.verso.backup.BackupWriter
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.DocumentStore
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.importer.EpubImporter
import com.maximebier.verso.readium.ReadiumOpener
import java.io.File

// Fichier illisible (corrompu) : remplacé par les réglages par défaut plutôt que de faire échouer chaque lecture ou écriture.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

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

    val highlights: HighlightRepository = HighlightRepository(database.highlightDao(), System::currentTimeMillis)

    val documents: DocumentStore = DocumentStore(appContext)

    val settings: SettingsRepository = SettingsRepository(appContext.settingsDataStore)

    val clock: () -> Long = System::currentTimeMillis

    val readiumOpener: ReadiumOpener = ReadiumOpener(appContext)

    val importer: EpubImporter = EpubImporter(appContext, books, readiumOpener, clock)

    private val backupDao = database.backupDao()

    val backups: BackupService = BackupService(
        writer = BackupWriter(backupDao, settings, BuildConfig.VERSION_NAME, clock),
        restorer = BackupRestorer(
            dao = backupDao,
            settings = settings,
            booksDir = File(appContext.filesDir, "books"),
            coversDir = File(appContext.filesDir, "covers"),
            workDir = File(appContext.cacheDir, "restauration"),
        ),
        documents = documents,
        settings = settings,
        clock = clock,
    )
}
