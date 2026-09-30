package com.maximebier.verso.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ReadingSettingsLimits
import com.maximebier.verso.core.settings.ScrollMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Réglages de l'app (DataStore). Une valeur illisible ou inconnue revient à la valeur par défaut. */
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

    /** Défaut : AUTO (suit le thème du téléphone). L'ancien thème Noir, remplacé par Nuit, se lit comme Nuit. */
    val themeMode: Flow<ThemeMode> = preferences.map { prefs ->
        val stored = prefs[THEME_MODE]
        if (stored == LEGACY_BLACK_THEME) ThemeMode.NIGHT else enumOrDefault(stored, ThemeMode.AUTO)
    }

    /** Thème foncé de l'automatique quand le téléphone est en sombre. Défaut : DARK (Sombre). */
    val darkThemeVariant: Flow<DarkThemeVariant> = preferences.map { prefs ->
        enumOrDefault(prefs[DARK_THEME_VARIANT], DarkThemeVariant.DARK)
    }

    suspend fun setReopenLastBook(value: Boolean) {
        save { it[REOPEN_LAST_BOOK] = value }
    }

    suspend fun setLibrarySort(value: LibrarySort) {
        save { it[LIBRARY_SORT] = value.name }
    }

    suspend fun setLibraryViewMode(value: LibraryViewMode) {
        save { it[LIBRARY_VIEW_MODE] = value.name }
    }

    suspend fun setThemeMode(value: ThemeMode) {
        save { it[THEME_MODE] = value.name }
    }

    suspend fun setDarkThemeVariant(value: DarkThemeVariant) {
        save { it[DARK_THEME_VARIANT] = value.name }
    }

    /** Réglages de lecture V2 (spec, « Réglages de la V2 ») ; valeur inconnue → défaut, taille bornée. */
    val readingSettings: Flow<ReadingSettings> = preferences.map(::readingSettingsOf).distinctUntilChanged()

    /** Lit, transforme et réécrit les réglages dans la même transaction DataStore. */
    suspend fun updateReadingSettings(transform: (ReadingSettings) -> ReadingSettings) {
        save { prefs ->
            val next = transform(readingSettingsOf(prefs))
            prefs[READING_FONT] = next.font.name
            prefs[READING_FONT_SIZE] = next.fontSizeSp
            prefs[READING_LINE_SPACING] = next.lineSpacing.name
            prefs[READING_MARGINS] = next.margins.name
            prefs[DEFAULT_SCROLL_MODE] = next.defaultScrollMode.name
        }
    }

    private fun readingSettingsOf(prefs: Preferences): ReadingSettings = ReadingSettings(
        font = enumOrDefault(prefs[READING_FONT], ReadingFont.LITERATA),
        fontSizeSp = (prefs[READING_FONT_SIZE] ?: ReadingSettingsLimits.DEFAULT_FONT_SIZE_SP)
            .coerceIn(ReadingSettingsLimits.MIN_FONT_SIZE_SP, ReadingSettingsLimits.MAX_FONT_SIZE_SP),
        lineSpacing = enumOrDefault(prefs[READING_LINE_SPACING], LineSpacing.NORMAL),
        margins = enumOrDefault(prefs[READING_MARGINS], Margins.NORMAL),
        defaultScrollMode = enumOrDefault(prefs[DEFAULT_SCROLL_MODE], ScrollMode.CONTINUOUS),
    )

    private inline fun <reified E : Enum<E>> enumOrDefault(stored: String?, default: E): E =
        stored?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: default

    /** Défaut : true. Le journal est toujours tenu ; seul l'affichage des statistiques se désactive. */
    val showStatistics: Flow<Boolean> = preferences.map { it[SHOW_STATISTICS] ?: true }

    suspend fun setShowStatistics(value: Boolean) {
        save { it[SHOW_STATISTICS] = value }
    }

    /** Écriture ratée (disque plein) : journalisée, le réglage garde sa valeur précédente ; jamais de plantage. */
    private suspend fun save(change: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { change(it) }
        } catch (e: IOException) {
            Log.w(TAG, "Écriture des réglages impossible", e)
        }
    }

    private companion object {
        val REOPEN_LAST_BOOK = booleanPreferencesKey("reopen_last_book")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DARK_THEME_VARIANT = stringPreferencesKey("dark_theme_variant")

        /** Valeur écrite par le thème Noir (étape 10), remplacé par Nuit le 2026-09-29. */
        const val LEGACY_BLACK_THEME = "BLACK"
        val READING_FONT = stringPreferencesKey("reading_font")
        val READING_FONT_SIZE = intPreferencesKey("reading_font_size")
        val READING_LINE_SPACING = stringPreferencesKey("reading_line_spacing")
        val READING_MARGINS = stringPreferencesKey("reading_margins")
        val DEFAULT_SCROLL_MODE = stringPreferencesKey("default_scroll_mode")
        val SHOW_STATISTICS = booleanPreferencesKey("show_statistics")
        const val TAG = "SettingsRepository"
    }
}
