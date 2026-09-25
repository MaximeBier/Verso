package com.maximebier.verso.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Sous Robolectric (et non en JUnit nu) : `Build.VERSION.SDK_INT` y est correctement renseigné
 * (34, cf. robolectric.properties). DataStore choisit alors en interne le remplacement de fichier
 * via `java.nio.file.Files.move(..., REPLACE_EXISTING)` (API 26+), fiable sous Windows. Sans
 * `@RunWith`, le jar stub Android laisse `SDK_INT` à 0 : DataStore retombe sur l'antique
 * `File.renameTo`, qui échoue sous Windows dès que le fichier cible existe déjà — l'écriture d'un
 * réglage déjà écrit une première fois (ce que fait `valuesAreWrittenAndReadBack`) plantait donc à
 * tort ici, alors que Room/Robolectric contourne déjà ce problème pour les autres tests de ce module.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun defaultsMatchTheSpec() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "defaults.preferences_pb") })
        val settings = SettingsRepository(store)
        assertThat(settings.reopenLastBook.first()).isTrue()
        assertThat(settings.librarySort.first()).isEqualTo(LibrarySort.RECENT)
        assertThat(settings.libraryViewMode.first()).isEqualTo(LibraryViewMode.LIST)
    }

    @Test
    fun valuesAreWrittenAndReadBack() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "values.preferences_pb") })
        val settings = SettingsRepository(store)
        settings.setReopenLastBook(false)
        settings.setLibrarySort(LibrarySort.AUTHOR)
        settings.setLibraryViewMode(LibraryViewMode.GRID)
        assertThat(settings.reopenLastBook.first()).isFalse()
        assertThat(settings.librarySort.first()).isEqualTo(LibrarySort.AUTHOR)
        assertThat(settings.libraryViewMode.first()).isEqualTo(LibraryViewMode.GRID)
    }

    @Test
    fun unknownStoredValueFallsBackToTheDefault() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "unknown.preferences_pb") })
        store.edit { it[stringPreferencesKey("library_sort")] = "PAGES" }
        assertThat(SettingsRepository(store).librarySort.first()).isEqualTo(LibrarySort.RECENT)
    }
}
