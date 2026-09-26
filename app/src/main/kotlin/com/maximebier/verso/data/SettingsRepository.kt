package com.maximebier.verso.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Réglages V1 (DataStore). Une valeur illisible ou inconnue revient à la valeur par défaut. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    /** Défaut : true (spec, « Ouverture au lancement »). */
    val reopenLastBook: Flow<Boolean> = preferences.map { it[REOPEN_LAST_BOOK] ?: true }

    /** Défaut : RECENT. */
    val librarySort: Flow<LibrarySort> = preferences.map { prefs ->
        prefs[LIBRARY_SORT]?.let { stored -> LibrarySort.entries.firstOrNull { it.name == stored } } ?: LibrarySort.RECENT
    }

    /** Défaut : LIST. */
    val libraryViewMode: Flow<LibraryViewMode> = preferences.map { prefs ->
        prefs[LIBRARY_VIEW_MODE]?.let { stored -> LibraryViewMode.entries.firstOrNull { it.name == stored } } ?: LibraryViewMode.LIST
    }

    /** Défaut : AUTO (suit le thème du téléphone). */
    val themeMode: Flow<ThemeMode> = preferences.map { prefs ->
        prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.AUTO
    }

    suspend fun setReopenLastBook(value: Boolean) {
        dataStore.edit { it[REOPEN_LAST_BOOK] = value }
    }

    suspend fun setLibrarySort(value: LibrarySort) {
        dataStore.edit { it[LIBRARY_SORT] = value.name }
    }

    suspend fun setLibraryViewMode(value: LibraryViewMode) {
        dataStore.edit { it[LIBRARY_VIEW_MODE] = value.name }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = value.name }
    }

    private companion object {
        val REOPEN_LAST_BOOK = booleanPreferencesKey("reopen_last_book")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
