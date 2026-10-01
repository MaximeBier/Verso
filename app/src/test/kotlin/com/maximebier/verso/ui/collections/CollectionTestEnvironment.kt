package com.maximebier.verso.ui.collections

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.CollectionRepository
import com.maximebier.verso.data.SessionRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.VersoDatabase
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Base en mémoire (requêtes sur le fil du test), dépôts et réglages pour les ViewModels des collections. */
@OptIn(ExperimentalCoroutinesApi::class)
class CollectionTestEnvironment(root: File) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var clockMs = 1_000L

    val db: VersoDatabase = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    val books = BookRepository(db.bookDao(), File(root, "books").apply { mkdirs() }, File(root, "covers").apply { mkdirs() })
    val sessions = SessionRepository(db.sessionDao())
    /** Horloge qui avance d’une seconde à chaque création : « la plus récente d’abord » est déterministe. */
    val collections = CollectionRepository(db.collectionDao()) { clockMs++ }
    val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(root, "settings.preferences_pb") },
    )

    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    fun close() {
        db.close()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }
}
