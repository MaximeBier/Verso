package com.maximebier.verso.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
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
        assertThat(settings.themeMode.first()).isEqualTo(ThemeMode.AUTO)
    }

    @Test
    fun valuesAreWrittenAndReadBack() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "values.preferences_pb") })
        val settings = SettingsRepository(store)
        settings.setReopenLastBook(false)
        settings.setLibrarySort(LibrarySort.AUTHOR)
        settings.setLibraryViewMode(LibraryViewMode.GRID)
        settings.setThemeMode(ThemeMode.DARK)
        assertThat(settings.reopenLastBook.first()).isFalse()
        assertThat(settings.librarySort.first()).isEqualTo(LibrarySort.AUTHOR)
        assertThat(settings.libraryViewMode.first()).isEqualTo(LibraryViewMode.GRID)
        assertThat(settings.themeMode.first()).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun unknownStoredValueFallsBackToTheDefault() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "unknown.preferences_pb") })
        store.edit { it[stringPreferencesKey("library_sort")] = "PAGES" }
        assertThat(SettingsRepository(store).librarySort.first()).isEqualTo(LibrarySort.RECENT)
    }

    @Test
    fun unknownThemeFallsBackToAuto() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "theme.preferences_pb") })
        store.edit { it[stringPreferencesKey("theme_mode")] = "PURPLE" }
        assertThat(SettingsRepository(store).themeMode.first()).isEqualTo(ThemeMode.AUTO)
    }

    @Test
    fun readingSettingsDefaultToTheSpec() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "reading-defaults.preferences_pb") })
        assertThat(SettingsRepository(store).readingSettings.first()).isEqualTo(ReadingSettings())
    }

    @Test
    fun readingSettingsAreWrittenAndReadBack() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "reading-values.preferences_pb") })
        val settings = SettingsRepository(store)
        settings.updateReadingSettings {
            it.copy(font = ReadingFont.ATKINSON, lineSpacing = LineSpacing.AIRY, margins = Margins.WIDE, defaultScrollMode = ScrollMode.PAGES)
                .withFontSize(26)
        }
        settings.updateReadingSettings { it.larger() }
        assertThat(settings.readingSettings.first()).isEqualTo(
            ReadingSettings(ReadingFont.ATKINSON, 27, LineSpacing.AIRY, Margins.WIDE, ScrollMode.PAGES),
        )
    }

    @Test
    fun unknownReadingValuesFallBackToDefaults() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "reading-unknown.preferences_pb") })
        store.edit {
            it[stringPreferencesKey("reading_font")] = "COMIC_SANS"
            it[intPreferencesKey("reading_font_size")] = 99
            it[stringPreferencesKey("reading_line_spacing")] = "DOUBLE"
            it[stringPreferencesKey("reading_margins")] = "NONE"
            it[stringPreferencesKey("default_scroll_mode")] = "SPIRAL"
        }
        assertThat(SettingsRepository(store).readingSettings.first()).isEqualTo(ReadingSettings(fontSizeSp = 32))
    }

    @Test
    fun v1ThemeValueIsReadUnchanged() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "v1-theme.preferences_pb") })
        store.edit { it[stringPreferencesKey("theme_mode")] = "DARK" }
        assertThat(SettingsRepository(store).themeMode.first()).isEqualTo(ThemeMode.DARK)
    }

    /** Le thème Noir (étape 10) a été remplacé par Nuit : un choix enregistré avant reste un thème de nuit. */
    @Test
    fun blackThemeIsReadAsNight() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "black.preferences_pb") })
        store.edit { it[stringPreferencesKey("theme_mode")] = "BLACK" }
        assertThat(SettingsRepository(store).themeMode.first()).isEqualTo(ThemeMode.NIGHT)
    }

    @Test
    fun darkThemeVariantDefaultsToDarkAndIsWrittenBack() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "variant.preferences_pb") })
        val settings = SettingsRepository(store)
        assertThat(settings.darkThemeVariant.first()).isEqualTo(DarkThemeVariant.DARK)
        settings.setDarkThemeVariant(DarkThemeVariant.NIGHT)
        assertThat(settings.darkThemeVariant.first()).isEqualTo(DarkThemeVariant.NIGHT)
        store.edit { it[stringPreferencesKey("dark_theme_variant")] = "PURPLE" }
        assertThat(settings.darkThemeVariant.first()).isEqualTo(DarkThemeVariant.DARK)
    }

    @Test
    fun statisticsAreShownByDefaultAndCanBeHidden() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "stats.preferences_pb") })
        val settings = SettingsRepository(store)
        assertThat(settings.showStatistics.first()).isTrue()
        settings.setShowStatistics(false)
        assertThat(settings.showStatistics.first()).isFalse()
    }

    @Test
    fun lastBackupIsEmptyUntilRecorded() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "last.preferences_pb") })
        val settings = SettingsRepository(store)
        assertThat(settings.lastBackup.first()).isNull()

        settings.setLastBackup(LastBackup(at = 1_790_000_000_000L, sizeBytes = 48_000_000L, fileName = "verso-sauvegarde-2026-09-20.zip"))

        assertThat(settings.lastBackup.first())
            .isEqualTo(LastBackup(at = 1_790_000_000_000L, sizeBytes = 48_000_000L, fileName = "verso-sauvegarde-2026-09-20.zip"))
    }

    @Test
    fun rawSettingsKeepTheirTypeAndLeaveTheLastBackupOut() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "raw.preferences_pb") })
        val settings = SettingsRepository(store)
        settings.setThemeMode(ThemeMode.DARK)
        settings.setReopenLastBook(false)
        settings.updateReadingSettings { it.withFontSize(22) }
        settings.setLastBackup(LastBackup(at = 1L, sizeBytes = 2L, fileName = "a.zip"))

        val exported = settings.exportRaw()

        assertThat(exported).containsAtLeast(
            RawSetting("theme_mode", "DARK"),
            RawSetting("reopen_last_book", false),
            RawSetting("reading_font_size", 22),
        )
        assertThat(exported.map { it.key }.filter { it.startsWith("last_backup") }).isEmpty()
    }

    @Test
    fun importReplacesEverySettingButKeepsThisPhonesLastBackup() = runTest {
        val source = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "source.preferences_pb") }),
        )
        source.setThemeMode(ThemeMode.NIGHT)
        source.updateReadingSettings { it.withFontSize(24) }
        val target = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "target.preferences_pb") }),
        )
        target.setLibrarySort(LibrarySort.AUTHOR)
        target.setLastBackup(LastBackup(at = 3L, sizeBytes = 4L, fileName = "b.zip"))

        target.importRaw(source.exportRaw())

        assertThat(target.themeMode.first()).isEqualTo(ThemeMode.NIGHT)
        assertThat(target.readingSettings.first().fontSizeSp).isEqualTo(24)
        assertThat(target.librarySort.first()).isEqualTo(LibrarySort.RECENT)
        assertThat(target.lastBackup.first()).isEqualTo(LastBackup(at = 3L, sizeBytes = 4L, fileName = "b.zip"))
        assertThat(target.exportRaw()).isEqualTo(source.exportRaw())
    }

    @Test
    fun everyDataStoreTypeSurvivesTheRoundTrip() = runTest {
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "types.preferences_pb") }),
        )
        val values = listOf(
            RawSetting("b", true),
            RawSetting("d", 1.5),
            RawSetting("f", 2.5f),
            RawSetting("i", 3),
            RawSetting("l", 4L),
            RawSetting("s", "texte"),
            RawSetting("set", setOf("x", "y")),
        )

        settings.importRaw(values)

        assertThat(settings.exportRaw()).isEqualTo(values)
    }
}
