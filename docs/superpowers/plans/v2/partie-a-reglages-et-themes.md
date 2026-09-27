# Verso V2 — partie A : réglages de lecture, polices, thèmes (étapes 9 et 10)

> Plan principal : `docs/superpowers/plans/2026-09-27-verso-v2.md` (contraintes globales, contrat d'architecture, fin d'étape). Design : `docs/superpowers/specs/2026-09-27-verso-v2-design.md`. Chaque tâche suppose les tâches précédentes faites.

Commande de test utilisée partout (Git Bash, racine du dépôt) :

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_SERIAL=192.168.1.10:5555   # téléphone en adb Wi-Fi ; si « device not found » : adb connect 192.168.1.10:5555
```

Toutes les commandes `./gradlew` ci-dessous supposent cette variable.

## Noms produits par cette partie

Les noms partagés sont dans le contrat du plan principal (`2026-09-27-verso-v2.md`, « Contrat d'architecture »), qui fait foi pour les parties B, C et D ; ceux-ci sont propres à la partie A et repris tels quels par les parties suivantes :

- `ReaderStyle` (`reader/ReaderController.kt`), `FragmentReaderController.style`, `ReaderViewModel.onThemeChanged`, `VersoDatabase.build(context, name)`, `VersoDatabase.MIGRATION_1_2`, `LiterataFamily`, `AtkinsonFamily` (déplacée dans `Fonts.kt`), `VersoTypography.of(font)`, `LocalVersoTheme`, `RadioRow` (`ui/components/SettingsRows.kt`), `ReaderScreenDistance.REFERENCE_*`, `V2ScreenFixture`.
- `AppTheme` (et `ThemeMode.resolve`) est créé dès la tâche 9.3, parce que `VersoReadingPreferences.epub(settings, theme, …)` en a besoin. La tâche 10.1 ajoute seulement `SEPIA` et `BLACK` à `ThemeMode` et branche les palettes.
- `V2ScreenCatalog.kt` et `V2ScreenshotTest.kt` sont créés par la tâche **10.2** (captures sépia et noir) : les parties B, C et D y ajoutent leurs écrans (`V2ScreenFixture(ScreenFixture(...))`).
- Le test de migration n'utilise pas `room-testing` (aucune dépendance nouvelle) : il crée une base version 1 avec le SQL exact de `app/schemas/…/1.json`, puis l'ouvre par `VersoDatabase.build`, ce qui fait jouer la migration et la validation du schéma par Room.

---

## Étape 9 — Réglages de lecture et polices

### Task 9.1 : modèle des réglages, DataStore, migration Room 1 → 2

**Files:**
- Create: `core/src/main/kotlin/com/maximebier/verso/core/settings/ReadingSettings.kt`
- Test: `core/src/test/kotlin/com/maximebier/verso/core/settings/ReadingSettingsTest.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/SettingsRepository.kt`
- Test (modify): `app/src/test/kotlin/com/maximebier/verso/data/SettingsRepositoryTest.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/db/BookEntity.kt`, `BookDao.kt`, `VersoDatabase.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/AppContainer.kt` (inchangé si `VersoDatabase.build(appContext)` garde sa signature par défaut)
- Create: `app/schemas/com.maximebier.verso.data.db.VersoDatabase/2.json` (généré par KSP à la compilation, à commiter)
- Test: `app/src/test/kotlin/com/maximebier/verso/data/db/VersoDatabaseMigrationTest.kt`
- Test (modify): `app/src/test/kotlin/com/maximebier/verso/data/db/BookDaoTest.kt`

**Interfaces:**
- Consumes : rien de la V2.
- Produces : `ReadingFont`, `LineSpacing`, `Margins`, `ScrollMode`, `ReadingSettingsLimits`, `ReadingSettings` (contrat) ; `SettingsRepository.readingSettings: Flow<ReadingSettings>`, `SettingsRepository.updateReadingSettings(transform: (ReadingSettings) -> ReadingSettings)` ; `BookEntity.scrollMode: String?`, `BookEntity.stateOverride: String?` ; `BookDao.setScrollMode(id: Long, scrollMode: String?)`, `BookDao.setStateOverride(id: Long, stateOverride: String?)` ; `VersoDatabase.MIGRATION_1_2`, `VersoDatabase.build(context: Context, name: String = NAME)`.

- [ ] **Step 1 : test du modèle (`:core`)**

`core/src/test/kotlin/com/maximebier/verso/core/settings/ReadingSettingsTest.kt` :

```kotlin
package com.maximebier.verso.core.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingSettingsTest {

    @Test
    fun defaultsMatchTheSpec() {
        val settings = ReadingSettings()
        assertThat(settings.font).isEqualTo(ReadingFont.LITERATA)
        assertThat(settings.fontSizeSp).isEqualTo(20)
        assertThat(settings.lineSpacing).isEqualTo(LineSpacing.NORMAL)
        assertThat(settings.margins).isEqualTo(Margins.NORMAL)
        assertThat(settings.defaultScrollMode).isEqualTo(ScrollMode.CONTINUOUS)
    }

    @Test
    fun lineSpacingAndMarginsHaveTheSpecValues() {
        assertThat(LineSpacing.entries.map { it.factor }).containsExactly(1.4, 1.6, 1.8).inOrder()
        assertThat(Margins.entries.map { it.dp }).containsExactly(16.0, 24.0, 32.0).inOrder()
    }

    @Test
    fun sizeGoesUpAndDownByOneSp() {
        val settings = ReadingSettings()
        assertThat(settings.larger().fontSizeSp).isEqualTo(21)
        assertThat(settings.smaller().fontSizeSp).isEqualTo(19)
    }

    @Test
    fun sizeStopsAtTheBounds() {
        val smallest = ReadingSettings(fontSizeSp = ReadingSettingsLimits.MIN_FONT_SIZE_SP)
        val largest = ReadingSettings(fontSizeSp = ReadingSettingsLimits.MAX_FONT_SIZE_SP)
        assertThat(smallest.canShrink).isFalse()
        assertThat(smallest.canGrow).isTrue()
        assertThat(smallest.smaller()).isEqualTo(smallest)
        assertThat(largest.canGrow).isFalse()
        assertThat(largest.canShrink).isTrue()
        assertThat(largest.larger()).isEqualTo(largest)
    }

    @Test
    fun withFontSizeClampsToTheRange() {
        assertThat(ReadingSettings().withFontSize(8).fontSizeSp).isEqualTo(14)
        assertThat(ReadingSettings().withFontSize(99).fontSizeSp).isEqualTo(32)
        assertThat(ReadingSettings().withFontSize(24).fontSizeSp).isEqualTo(24)
    }

    @Test
    fun outOfRangeSizeIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { ReadingSettings(fontSizeSp = 13) }
        assertThrows(IllegalArgumentException::class.java) { ReadingSettings(fontSizeSp = 33) }
    }
}
```

- [ ] **Step 2 : lancer, constater l'échec**

Run : `./gradlew :core:test --tests "com.maximebier.verso.core.settings.ReadingSettingsTest"`
Expected : FAIL, `Unresolved reference: ReadingSettings`.

- [ ] **Step 3 : implémenter**

`core/src/main/kotlin/com/maximebier/verso/core/settings/ReadingSettings.kt` :

```kotlin
package com.maximebier.verso.core.settings

/** Police de toute l'app et du texte de lecture (spec, « Police V2 »). */
enum class ReadingFont { LITERATA, ATKINSON, SYSTEM }

/** Interligne (spec, « Réglages de lecture ») : Serré 1,4, Normal 1,6, Aéré 1,8. */
enum class LineSpacing(val factor: Double) { TIGHT(1.4), NORMAL(1.6), AIRY(1.8) }

/** Marges latérales du texte, en dp : Étroites, Normales (valeur V1), Larges. */
enum class Margins(val dp: Double) { NARROW(16.0), NORMAL(24.0), WIDE(32.0) }

/** Défilement d'un livre : scroll continu ou pages tournées. */
enum class ScrollMode { CONTINUOUS, PAGES }

/** Bornes de la taille du texte de lecture (sp, avant l'échelle de police d'Android). */
object ReadingSettingsLimits {
    const val MIN_FONT_SIZE_SP = 14
    const val MAX_FONT_SIZE_SP = 32
    const val DEFAULT_FONT_SIZE_SP = 20
    const val FONT_SIZE_STEP_SP = 1
}

/**
 * Réglages communs à tous les livres (feuille « Aa » et Paramètres). Le défilement propre à un livre est dans
 * `books.scrollMode` ; [defaultScrollMode] vaut pour les livres sans choix.
 */
data class ReadingSettings(
    val font: ReadingFont = ReadingFont.LITERATA,
    val fontSizeSp: Int = ReadingSettingsLimits.DEFAULT_FONT_SIZE_SP,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val margins: Margins = Margins.NORMAL,
    val defaultScrollMode: ScrollMode = ScrollMode.CONTINUOUS,
) {
    init {
        require(fontSizeSp in ReadingSettingsLimits.MIN_FONT_SIZE_SP..ReadingSettingsLimits.MAX_FONT_SIZE_SP) {
            "Taille hors bornes : $fontSizeSp sp"
        }
    }

    val canGrow: Boolean get() = fontSizeSp < ReadingSettingsLimits.MAX_FONT_SIZE_SP
    val canShrink: Boolean get() = fontSizeSp > ReadingSettingsLimits.MIN_FONT_SIZE_SP

    fun larger(): ReadingSettings = withFontSize(fontSizeSp + ReadingSettingsLimits.FONT_SIZE_STEP_SP)

    fun smaller(): ReadingSettings = withFontSize(fontSizeSp - ReadingSettingsLimits.FONT_SIZE_STEP_SP)

    fun withFontSize(sp: Int): ReadingSettings =
        copy(fontSizeSp = sp.coerceIn(ReadingSettingsLimits.MIN_FONT_SIZE_SP, ReadingSettingsLimits.MAX_FONT_SIZE_SP))
}
```

- [ ] **Step 4 : relancer**

Run : `./gradlew :core:test --tests "com.maximebier.verso.core.settings.ReadingSettingsTest"`
Expected : PASS (6 tests).

- [ ] **Step 5 : tests du dépôt de réglages**

Ajouter dans `SettingsRepositoryTest` (même classe, mêmes imports plus `androidx.datastore.preferences.core.intPreferencesKey` et `com.maximebier.verso.core.settings.*`) :

```kotlin
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
```

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.data.SettingsRepositoryTest"`
Expected : FAIL, `Unresolved reference: readingSettings`.

- [ ] **Step 6 : implémenter dans `SettingsRepository`**

Ajouts (imports : `androidx.datastore.preferences.core.intPreferencesKey`, `com.maximebier.verso.core.settings.LineSpacing`, `Margins`, `ReadingFont`, `ReadingSettings`, `ReadingSettingsLimits`, `ScrollMode`, `kotlinx.coroutines.flow.distinctUntilChanged`) :

```kotlin
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
```

Dans `companion object` :

```kotlin
        val READING_FONT = stringPreferencesKey("reading_font")
        val READING_FONT_SIZE = intPreferencesKey("reading_font_size")
        val READING_LINE_SPACING = stringPreferencesKey("reading_line_spacing")
        val READING_MARGINS = stringPreferencesKey("reading_margins")
        val DEFAULT_SCROLL_MODE = stringPreferencesKey("default_scroll_mode")
```

Remplacer le commentaire de classe par `/** Réglages de l'app (DataStore). Une valeur illisible ou inconnue revient à la valeur par défaut. */`.

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.data.SettingsRepositoryTest"`
Expected : PASS.

- [ ] **Step 7 : test de migration**

`app/src/test/kotlin/com/maximebier/verso/data/db/VersoDatabaseMigrationTest.kt` :

```kotlin
package com.maximebier.verso.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Base créée comme par la V1 (SQL de app/schemas/…/1.json), puis ouverte par la V2 : Room joue MIGRATION_1_2 et
 * valide le schéma obtenu contre les entités. Livres, positions et sessions de la V1 sont gardés.
 */
@RunWith(AndroidJUnit4::class)
class VersoDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-1-2.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1DataSurvivesTheMigration() = runTest {
        createVersion1Database()

        val db = VersoDatabase.build(context, name)
        try {
            val book = db.bookDao().byId(1)!!
            assertThat(book.title).isEqualTo("Madame Bovary")
            assertThat(book.readingLocatorJson).isEqualTo("""{"href":"chapitre-2.xhtml"}""")
            assertThat(book.progression).isEqualTo(0.31)
            assertThat(book.scrollMode).isNull()
            assertThat(book.stateOverride).isNull()
            val sessions = db.sessionDao().observeForBook(1).first()
            assertThat(sessions).hasSize(1)
            assertThat(sessions.single().wordsRead).isEqualTo(4_200)
        } finally {
            db.close()
        }
    }

    @Test
    fun newColumnsAreWritable() = runTest {
        createVersion1Database()
        val db = VersoDatabase.build(context, name)
        try {
            db.bookDao().setScrollMode(1, "PAGES")
            db.bookDao().setStateOverride(1, "FINISHED")
            val book = db.bookDao().byId(1)!!
            assertThat(book.scrollMode).isEqualTo("PAGES")
            assertThat(book.stateOverride).isEqualTo("FINISHED")
            db.bookDao().setScrollMode(1, null)
            assertThat(db.bookDao().byId(1)!!.scrollMode).isNull()
        } finally {
            db.close()
        }
    }

    private fun createVersion1Database() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO books (id, title, author, filePath, sha256, coverPath, sizeBytes, originalFileName, " +
                    "importedAt, lastOpenedAt, readingLocatorJson, progression, totalWords) VALUES (1, 'Madame Bovary', " +
                    "'Gustave Flaubert', '/data/books/abc.epub', 'abc', NULL, 1234, 'bovary.epub', 100, 200, " +
                    "'{\"href\":\"chapitre-2.xhtml\"}', 0.31, 120000)",
            )
            db.execSQL(
                "INSERT INTO sessions (bookId, startedAt, endedAt, activeMs, startLocatorJson, endLocatorJson, " +
                    "startProgression, endProgression, wordsRead) VALUES (1, 1000, 2000, 900000, '{}', '{}', 0.28, 0.31, 4200)",
            )
            db.version = 1
        }
    }

    private companion object {
        /** SQL exact de app/schemas/com.maximebier.verso.data.db.VersoDatabase/1.json (ne pas modifier). */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`author` TEXT NOT NULL, `filePath` TEXT NOT NULL, `sha256` TEXT NOT NULL, `coverPath` TEXT, " +
                "`sizeBytes` INTEGER NOT NULL, `originalFileName` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, " +
                "`lastOpenedAt` INTEGER, `readingLocatorJson` TEXT, `progression` REAL NOT NULL, `totalWords` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_books_sha256` ON `books` (`sha256`)",
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `activeMs` INTEGER NOT NULL, " +
                "`startLocatorJson` TEXT NOT NULL, `endLocatorJson` TEXT NOT NULL, `startProgression` REAL NOT NULL, " +
                "`endProgression` REAL NOT NULL, `wordsRead` INTEGER NOT NULL, FOREIGN KEY(`bookId`) REFERENCES " +
                "`books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_sessions_bookId` ON `sessions` (`bookId`)",
        )
    }
}
```

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.data.db.VersoDatabaseMigrationTest"`
Expected : FAIL, `Unresolved reference: scrollMode` / `build` sans paramètre `name`.

- [ ] **Step 8 : implémenter entité, DAO, migration**

`BookEntity.kt` : ajouter à la fin du constructeur :

```kotlin
    val totalWords: Long,
    val scrollMode: String? = null,     // ScrollMode.name ; null = défilement par défaut des Paramètres (V2)
    val stateOverride: String? = null,  // BookStatus.name choisi dans la fiche ; null = état calculé (V2)
)
```

`BookDao.kt` : ajouter

```kotlin
    @Query("UPDATE books SET scrollMode = :scrollMode WHERE id = :id")
    suspend fun setScrollMode(id: Long, scrollMode: String?)

    @Query("UPDATE books SET stateOverride = :stateOverride WHERE id = :id")
    suspend fun setStateOverride(id: Long, stateOverride: String?)
```

`VersoDatabase.kt` (remplace le fichier) :

```kotlin
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
```

Si `androidx.sqlite.db.SupportSQLiteDatabase` n'est pas résolu (Room 2.8 en mode pilote), utiliser la surcharge `override fun migrate(connection: androidx.sqlite.SQLiteConnection)` avec `connection.execSQL(...)` (import `androidx.sqlite.execSQL`) ; garder une seule surcharge.

Dans `BookDaoTest`, ajouter :

```kotlin
    @Test
    fun scrollModeAndStateOverrideDefaultToNullAndAreWritable() = runTest {
        val id = dao.insert(testBook("ddd", title = "Nana"))
        assertThat(dao.byId(id)!!.scrollMode).isNull()
        assertThat(dao.byId(id)!!.stateOverride).isNull()
        dao.setScrollMode(id, "PAGES")
        dao.setStateOverride(id, "TO_READ")
        assertThat(dao.byId(id)!!.scrollMode).isEqualTo("PAGES")
        assertThat(dao.byId(id)!!.stateOverride).isEqualTo("TO_READ")
    }
```

- [ ] **Step 9 : relancer, vérifier le schéma exporté**

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.data.db.*"`
Expected : PASS. Puis `ls app/schemas/com.maximebier.verso.data.db.VersoDatabase/` → `1.json  2.json` ; `grep -c scrollMode app/schemas/com.maximebier.verso.data.db.VersoDatabase/2.json` → au moins 1.

- [ ] **Step 10 : suite complète et commit**

Run : `./gradlew :core:test :app:testDebugUnitTest`
Expected : PASS (aucun test existant ne dépend de la version de la base).

```bash
git add core/src app/src app/schemas
git commit -m "Étape 9 (partie) : réglages de lecture, migration 1 → 2"
```

### Task 9.2 : Literata et typographie dynamique

**Files:**
- Create: `app/src/main/res/font/literata.ttf`, `app/src/main/res/font/literata_italic.ttf`, `app/src/main/assets/fonts/literata.ttf`, `app/src/main/assets/fonts/literata_italic.ttf`, `app/src/main/res/raw/license_ofl_literata.txt`
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/theme/Fonts.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/theme/Type.kt`, `VersoTheme.kt`, `VersoColors.kt`
- Modify: les 20 fichiers qui utilisent `VersoTypography.` (liste au Step 5)
- Modify: `app/src/main/kotlin/com/maximebier/verso/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/settings/LicensesScreen.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/theme/TypographyTest.kt`
- Test (modify): `app/src/test/kotlin/com/maximebier/verso/ui/settings/LicensesScreenTest.kt`, `app/src/test/kotlin/com/maximebier/verso/screenshots/samples/ReaderSamples.kt`

**Interfaces:**
- Consumes : `ReadingFont`, `SettingsRepository.readingSettings` (9.1).
- Produces : `LiterataFamily`, `AtkinsonFamily`, `fontFamilyFor(font: ReadingFont): FontFamily` (`ui/theme/Fonts.kt`) ; `class VersoTypography(family: FontFamily)` avec les propriétés V1 (`logo`, `display`, `screenTitle`, `sheetTitle`, `bookTitle`, `bookTitleStrong`, `emptyBody`, `emptyHint`, `body`, `bodyStrong`, `rowTitle`, `button`, `buttonOutlined`, `segment`, `segmentSelected`, `caption`, `captionSemiBold`, `captionBold`, `material`) et `VersoTypography.of(font: ReadingFont)` ; `LocalVersoTypography` ; `VersoTheme.typography` (composable) ; `VersoTheme(darkTheme: Boolean = isSystemInDarkTheme(), font: ReadingFont = ReadingFont.LITERATA, content)` (le paramètre `darkTheme` devient `theme: AppTheme` à la tâche 10.1).

- [ ] **Step 1 : télécharger Literata (OFL)**

```bash
cd app/src/main
curl -fL -o res/font/literata.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/literata/Literata%5Bopsz%2Cwght%5D.ttf"
curl -fL -o res/font/literata_italic.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/literata/Literata-Italic%5Bopsz%2Cwght%5D.ttf"
curl -fL -o res/raw/license_ofl_literata.txt "https://raw.githubusercontent.com/google/fonts/main/ofl/literata/OFL.txt"
cp res/font/literata.ttf assets/fonts/literata.ttf
cp res/font/literata_italic.ttf assets/fonts/literata_italic.ttf
head -c 4 res/font/literata.ttf | od -An -tx1   # attendu : 00 01 00 00 (TrueType)
head -3 res/raw/license_ofl_literata.txt        # attendu : « Copyright 2017 The Literata Project Authors … »
cd ../../..
```

Si l'URL change (dossier renommé dans google/fonts), prendre les deux TTF variables et `OFL.txt` du dossier `ofl/literata/` de la branche `main` ; ne jamais prendre les woff2 de `docs/design/fonts/` (Android a besoin des TTF).

- [ ] **Step 2 : test de la typographie**

`app/src/test/kotlin/com/maximebier/verso/ui/theme/TypographyTest.kt` :

```kotlin
package com.maximebier.verso.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingFont
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypographyTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun eachFontHasItsFamily() {
        assertThat(fontFamilyFor(ReadingFont.LITERATA)).isSameInstanceAs(LiterataFamily)
        assertThat(fontFamilyFor(ReadingFont.ATKINSON)).isSameInstanceAs(AtkinsonFamily)
        assertThat(fontFamilyFor(ReadingFont.SYSTEM)).isEqualTo(FontFamily.Default)
    }

    @Test
    fun typographyKeepsTheV1ScaleWithTheChosenFamily() {
        val atkinson = VersoTypography.of(ReadingFont.ATKINSON)
        val literata = VersoTypography.of(ReadingFont.LITERATA)
        assertThat(atkinson.body.fontFamily).isSameInstanceAs(AtkinsonFamily)
        assertThat(literata.body.fontFamily).isSameInstanceAs(LiterataFamily)
        assertThat(literata.body.fontSize).isEqualTo(atkinson.body.fontSize)
        assertThat(literata.screenTitle.lineHeight).isEqualTo(atkinson.screenTitle.lineHeight)
        assertThat(literata.material.bodyLarge).isEqualTo(literata.body)
    }

    @Test
    fun themeProvidesTheChosenTypography() {
        var family: FontFamily? = null
        composeRule.setContent {
            VersoTheme(darkTheme = false, font = ReadingFont.ATKINSON) {
                family = VersoTheme.typography.body.fontFamily
                Text("Aa")
            }
        }
        composeRule.waitForIdle()
        assertThat(family).isSameInstanceAs(AtkinsonFamily)
    }

    @Test
    fun literataIsTheDefault() {
        var family: FontFamily? = null
        composeRule.setContent { VersoTheme(darkTheme = false) { family = VersoTheme.typography.body.fontFamily } }
        composeRule.waitForIdle()
        assertThat(family).isSameInstanceAs(LiterataFamily)
    }
}
```

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.ui.theme.TypographyTest"`
Expected : FAIL, `Unresolved reference: fontFamilyFor`.

- [ ] **Step 3 : `Fonts.kt` et `Type.kt`**

`app/src/main/kotlin/com/maximebier/verso/ui/theme/Fonts.kt` :

```kotlin
@file:OptIn(ExperimentalTextApi::class)

package com.maximebier.verso.ui.theme

import androidx.annotation.FontRes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.ReadingFont

/** Graisses utilisées par l'interface (380 = lecture en thème sombre). */
private val WEIGHTS = listOf(380, 400, 500, 600, 700)

private fun variable(@FontRes resId: Int, weight: Int, style: FontStyle): Font = Font(
    resId = resId,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun variableFamily(@FontRes regular: Int, @FontRes italic: Int): FontFamily = FontFamily(
    WEIGHTS.map { variable(regular, it, FontStyle.Normal) } + variable(italic, 400, FontStyle.Italic),
)

/** Literata, police variable (axes opsz et wght ; la taille optique reste celle par défaut dans l'interface). */
val LiterataFamily: FontFamily = variableFamily(R.font.literata, R.font.literata_italic)

/** Atkinson Hyperlegible Next, police variable (axe wght). */
val AtkinsonFamily: FontFamily =
    variableFamily(R.font.atkinson_hyperlegible_next, R.font.atkinson_hyperlegible_next_italic)

/** Police de toute l'app ; « Police du système » = celle du téléphone. */
fun fontFamilyFor(font: ReadingFont): FontFamily = when (font) {
    ReadingFont.LITERATA -> LiterataFamily
    ReadingFont.ATKINSON -> AtkinsonFamily
    ReadingFont.SYSTEM -> FontFamily.Default
}
```

`Type.kt` (remplace le fichier) :

```kotlin
package com.maximebier.verso.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.maximebier.verso.core.settings.ReadingFont

private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Échelle de v1-ui-reference §3.19 (14 / 16 / 18 / 22 / 28 sp), dans la police choisie (spec, « Police V2 » : la
 * police s'applique à toute l'app). Interlignes en sp (suivent l'agrandissement du texte système).
 */
class VersoTypography(private val family: FontFamily) {

    private fun verso(sizeSp: Float, weight: Int, lineHeightSp: Float? = null): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(weight),
        fontSize = sizeSp.sp,
        lineHeight = lineHeightSp?.sp ?: TextUnit.Unspecified,
        lineHeightStyle = CenteredLineHeight,
    )

    val logo: TextStyle = verso(28f, 700).copy(letterSpacing = (-0.01).em)
    val display: TextStyle = verso(28f, 700, 35f)
    val screenTitle: TextStyle = verso(22f, 700, 27.5f)
    val sheetTitle: TextStyle = verso(22f, 700, 28.6f)
    val bookTitle: TextStyle = verso(18f, 600, 23.4f)
    val bookTitleStrong: TextStyle = verso(18f, 700, 23.4f)
    val emptyBody: TextStyle = verso(18f, 400, 27.9f)
    val emptyHint: TextStyle = verso(16f, 400, 24.8f)
    val body: TextStyle = verso(16f, 400, 24f)
    val bodyStrong: TextStyle = verso(16f, 700, 24f)
    val rowTitle: TextStyle = verso(16f, 600, 21.6f)
    val button: TextStyle = verso(16f, 700, 24f)
    val buttonOutlined: TextStyle = verso(16f, 600, 24f)
    val segment: TextStyle = verso(16f, 500, 21.6f)
    val segmentSelected: TextStyle = verso(16f, 700, 21.6f)
    val caption: TextStyle = verso(14f, 400, 19.6f)
    val captionSemiBold: TextStyle = verso(14f, 600, 19.6f)
    val captionBold: TextStyle = verso(14f, 700, 19.6f)

    /** Correspondance Material 3, pour les composants M3 utilisés tels quels. */
    val material: Typography = Typography(
        displaySmall = display,
        headlineSmall = display,
        titleLarge = screenTitle,
        titleMedium = bookTitle,
        titleSmall = rowTitle,
        bodyLarge = body,
        bodyMedium = caption,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = captionBold,
        labelSmall = caption,
    )

    companion object {
        private val Literata = VersoTypography(LiterataFamily)
        private val Atkinson = VersoTypography(AtkinsonFamily)
        private val System = VersoTypography(FontFamily.Default)

        /** Une instance par police (les styles ne sont pas recréés à chaque composition). */
        fun of(font: ReadingFont): VersoTypography = when (font) {
            ReadingFont.LITERATA -> Literata
            ReadingFont.ATKINSON -> Atkinson
            ReadingFont.SYSTEM -> System
        }
    }
}

/** Typographie du thème courant (police choisie dans les réglages). */
val LocalVersoTypography = staticCompositionLocalOf { VersoTypography.of(ReadingFont.LITERATA) }
```

- [ ] **Step 4 : `VersoTheme.kt`**

Signature et fournisseurs (le reste du fichier ne change pas) :

```kotlin
/** Thème Verso : clair ou sombre, dans la police choisie (réglages). Couleurs générées depuis tokens.json. */
@Composable
fun VersoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    font: ReadingFont = ReadingFont.LITERATA,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) VersoPalette.Dark else VersoPalette.Light
    val covers = if (darkTheme) VersoPalette.CoverDark else VersoPalette.CoverLight
    val typography = VersoTypography.of(font)
    CompositionLocalProvider(
        LocalVersoColors provides colors,
        LocalCoverPalette provides covers,
        LocalVersoDarkTheme provides darkTheme,
        LocalVersoTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(colors, darkTheme),
            typography = typography.material,
            shapes = Shapes(
                extraSmall = VersoShapes.cover,
                small = VersoShapes.small,
                medium = VersoShapes.small,
                large = VersoShapes.card,
                extraLarge = VersoShapes.sheet,
            ),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextStyle provides typography.body,
                content = content,
            )
        }
    }
}
```

Dans `object VersoTheme`, remplacer `val typography: VersoTypography get() = VersoTypography` par :

```kotlin
    /** Styles de texte dans la police choisie : VersoTheme.typography.body… */
    val typography: VersoTypography
        @Composable @ReadOnlyComposable get() = LocalVersoTypography.current
```

Import à ajouter : `com.maximebier.verso.core.settings.ReadingFont`.

- [ ] **Step 5 : remplacer les accès statiques**

```bash
cd app/src
for f in $(grep -rl "VersoTypography\." --include=*.kt main test | grep -v "ui/theme/Type.kt\|ui/theme/VersoTheme.kt\|ui/theme/TypographyTest.kt"); do
  sed -i 's/\bVersoTypography\.\([a-z]\)/VersoTheme.typography.\1/g' "$f"
  sed -i '/^import com\.maximebier\.verso\.ui\.theme\.VersoTypography$/d' "$f"
  grep -q "^import com.maximebier.verso.ui.theme.VersoTheme$" "$f" || \
    sed -i '0,/^import /s//import com.maximebier.verso.ui.theme.VersoTheme\nimport /' "$f"
done
cd ../..
grep -rn "VersoTypography\.[a-z]" --include=*.kt app/src | grep -v "ui/theme/"   # attendu : aucune ligne
```

Fichiers touchés (relevé du 2026-09-27) : `ui/components/BookCover.kt`, `Buttons.kt`, `SettingsRows.kt`, `TopBars.kt`, `VersoBottomSheet.kt`, `VersoDialog.kt`, `VersoSegmentedButton.kt`, `VersoSnackbar.kt`, `ui/details/DetailsFields.kt`, `DetailsScreen.kt`, `ui/library/EmptyLibraryScreen.kt`, `LibraryComponents.kt`, `LibraryScreen.kt`, `ui/reader/JournalSheet.kt`, `ReaderBars.kt`, `ReturnCard.kt`, `TocSheet.kt`, `ui/settings/LicensesScreen.kt`, `SettingsScreen.kt`, `test/…/screenshots/samples/ReaderSamples.kt`.

`VersoTheme.typography` est composable : un accès hors composable (valeur par défaut d'un paramètre de fonction non composable, propriété de haut niveau, `object`) ne compile plus. Pour chaque erreur `@Composable invocations can only happen from the context of a @Composable function` : lire le style dans la fonction composable appelante et le passer en paramètre. Puis `import` réordonnés par l'IDE ou à la main (ordre alphabétique des imports existants).

- [ ] **Step 6 : `MainActivity` passe la police**

Dans `onCreate`, après `initialThemeMode` :

```kotlin
        val initialReading = runBlocking { settings.readingSettings.first() }
```

Dans `setContent` :

```kotlin
            val themeMode by settings.themeMode.collectAsState(initial = initialThemeMode)
            val reading by settings.readingSettings.collectAsState(initial = initialReading)
            val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())
            LaunchedEffect(themeMode) { applyWindowNightMode(themeMode) }
            LaunchedEffect(dark) { enableEdgeToEdge(dark) }
            VersoTheme(darkTheme = dark, font = reading.font) {
```

- [ ] **Step 7 : licence Literata**

`strings.xml`, à la suite de `license_component_atkinson` :

```xml
    <string name="license_component_literata" translatable="false">Literata</string>
```

`LicensesScreen.kt`, `OpenSourceLicenses.entries`, après la ligne Atkinson :

```kotlin
        LicenseEntry(R.string.license_component_literata, R.string.license_name_ofl, R.raw.license_ofl_literata),
```

`LicensesScreenTest.kt` : `hasSize(11)` → `hasSize(12)`.

`ReaderSamples.kt`, `SampleReadingText` : `fontSize = 19.sp, lineHeight = 30.4.sp` → `fontSize = 20.sp, lineHeight = 32.sp` (Literata 20 sp × 1,6, maquette 2.02).

- [ ] **Step 8 : relancer**

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.ui.theme.*" --tests "com.maximebier.verso.ui.settings.*"`
Expected : PASS.

Run : `./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected : PASS ; les captures Roborazzi V1 changent (Literata) : c'est attendu, elles sont régénérées par `./gradlew :app:recordRoborazziDebug` et relues à l'œil (texte en Literata, aucune coupure en `texte-200`).

- [ ] **Step 9 : commit**

```bash
git add app/src
git commit -m "Étape 9 (partie) : Literata et typographie selon la police choisie"
```

### Task 9.3 : réglages appliqués à Readium, sans recréer le lecteur

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/ThemeMode.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/readium/ReadingStyle.kt`, `VersoReadingPreferences.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderController.kt`, `FragmentReaderController.kt`, `ReaderSurface.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreenDistance.kt`, `ReaderViewModel.kt`, `ReaderScreen.kt`
- Test (modify): `app/src/test/kotlin/com/maximebier/verso/readium/ReadingStyleTest.kt`, `VersoReadingPreferencesTest.kt`, `ReadingFontScaleTest.kt`, `app/src/test/kotlin/com/maximebier/verso/reader/FakeReaderController.kt`, `ReaderEngineIntegrationTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderScreenDistanceTest.kt`, `ReaderViewModelTest.kt`, `app/src/test/kotlin/com/maximebier/verso/data/ThemeModeTest.kt`

**Interfaces:**
- Consumes : `ReadingSettings`, `ScrollMode`, `ReadingFont` (9.1), `SettingsRepository.readingSettings` (9.1), `VersoPalette.Light/Sepia/Dark/Black`.
- Produces :
  - `enum class AppTheme(val isDark: Boolean) { LIGHT(false), SEPIA(false), DARK(true), BLACK(true) }` et `ThemeMode.resolve(systemDark: Boolean): AppTheme` (`data/ThemeMode.kt`) ;
  - `ReadingStyle.colors(theme: AppTheme)`, `ReadingStyle.readingFontScale(density: Density, fontSizeSp: Int)`, `ReadingStyle.fontSizeFactor(fontSizeSp: Double)`, `ReadingStyle.paragraphSpacingRem(lineHeight: Double)`, `ReadingStyle.fragmentPageMargins(marginDp: Double, widthDp: Float = 0f)` ;
  - `VersoReadingPreferences.epub(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode, fontScale: Double = 1.0, widthDp: Float = 0f): EpubPreferences`, `VersoReadingPreferences.fontFamilyOf(font: ReadingFont)`, `LITERATA`, `ATKINSON`, `EpubNavigatorFragment.Configuration.applyVerso(theme: AppTheme)` ;
  - `data class ReaderStyle(val settings: ReadingSettings, val theme: AppTheme, val scrollMode: ScrollMode)` et `ReaderController.submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode)` ; `FragmentReaderController.style: StateFlow<ReaderStyle?>` ;
  - `ReaderMetrics(viewportHeightPx, fontScale, density, fontSizeSp: Double = 19.0, lineHeight: Double = 1.6, marginDp: Double = 24.0)` ;
  - `ReaderViewModel(…, readingSettings: Flow<ReadingSettings> = flowOf(ReadingSettings()))`, `ReaderUiState.readingSettings: ReadingSettings`, `ReaderViewModel.onThemeChanged(theme: AppTheme)`. Tant que le mode pages n'existe pas (étape 12), le ViewModel envoie toujours `ScrollMode.CONTINUOUS`.

- [ ] **Step 1 : `AppTheme` (test)**

`ThemeModeTest.kt` : ajouter

```kotlin
    @Test
    fun resolveGivesThePaletteToDisplay() {
        assertThat(ThemeMode.AUTO.resolve(systemDark = false)).isEqualTo(AppTheme.LIGHT)
        assertThat(ThemeMode.AUTO.resolve(systemDark = true)).isEqualTo(AppTheme.DARK)
        assertThat(ThemeMode.LIGHT.resolve(systemDark = true)).isEqualTo(AppTheme.LIGHT)
        assertThat(ThemeMode.DARK.resolve(systemDark = false)).isEqualTo(AppTheme.DARK)
    }

    @Test
    fun onlyDarkAndBlackAreDark() {
        assertThat(AppTheme.entries.filter { it.isDark }).containsExactly(AppTheme.DARK, AppTheme.BLACK)
    }
```

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.data.ThemeModeTest"` → FAIL (`AppTheme` inconnu).

`ThemeMode.kt` (remplace) :

```kotlin
package com.maximebier.verso.data

/** Palette affichée (jetons de tokens.json). Sépia et noir arrivent en V2. */
enum class AppTheme(val isDark: Boolean) { LIGHT(false), SEPIA(false), DARK(true), BLACK(true) }

/** Thème de l'application : automatique (suit le téléphone), ou une palette imposée. */
enum class ThemeMode {
    AUTO,
    LIGHT,
    DARK,
    ;

    /** Palette affichée, `systemDark` étant le thème du téléphone. */
    fun resolve(systemDark: Boolean): AppTheme = when (this) {
        AUTO -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
        LIGHT -> AppTheme.LIGHT
        DARK -> AppTheme.DARK
    }

    /** Thème sombre affiché. */
    fun isDark(systemDark: Boolean): Boolean = resolve(systemDark).isDark
}
```

Relancer → PASS.

- [ ] **Step 2 : tests de `ReadingStyle` et des préférences**

`ReadingStyleTest.kt` (remplace le fichier) :

```kotlin
package com.maximebier.verso.readium

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoPalette
import org.junit.Test

class ReadingStyleTest {

    @Test
    fun spIsReadiumFactorOfSixteenPixelRoot() {
        assertThat(ReadingStyle.fontSizeFactor(20.0)).isWithin(1e-9).of(1.25)
        assertThat(ReadingStyle.fontSizeFactor(24.0)).isWithin(1e-9).of(1.5)
    }

    @Test
    fun paragraphSpacingIsHalfALineInRem() {
        assertThat(ReadingStyle.paragraphSpacingRem(1.6)).isWithin(1e-9).of(0.8)
        assertThat(ReadingStyle.paragraphSpacingRem(1.4)).isWithin(1e-9).of(0.7)
        assertThat(ReadingStyle.paragraphSpacingRem(1.8)).isWithin(1e-9).of(0.9)
    }

    @Test
    fun darkThemeIsLighterAndSpaced() {
        assertThat(ReadingStyle.fontWeightFactor(dark = true)).isWithin(1e-9).of(0.95)
        assertThat(ReadingStyle.fontWeightFactor(dark = false)).isWithin(1e-9).of(1.0)
        assertThat(ReadingStyle.readiumLetterSpacing(dark = true)!!).isWithin(1e-9).of(0.02)
        assertThat(ReadingStyle.readiumLetterSpacing(dark = false)).isNull()
    }

    @Test
    fun marginsFollowTheChoiceAndTheGutter() {
        // Gouttière de ReadiumCSS : 20 px sous 35 em, 30 px dès 35 em, 40 px dès 45 em, 50 px dès 75 em.
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 390f)).isWithin(1e-9).of(1.2)
        assertThat(ReadingStyle.fragmentPageMargins(16.0, widthDp = 390f)).isWithin(1e-9).of(0.8)
        assertThat(ReadingStyle.fragmentPageMargins(32.0, widthDp = 390f)).isWithin(1e-9).of(1.6)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 600f)).isWithin(1e-9).of(0.8)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 844f)).isWithin(1e-9).of(0.6)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 1_280f)).isWithin(1e-9).of(0.48)
        assertThat(ReadingStyle.fragmentPageMargins(24.0, widthDp = 0f)).isWithin(1e-9).of(1.2)
    }

    @Test
    fun colorsComeFromTheTokensOfEachTheme() {
        val palettes = mapOf(
            AppTheme.LIGHT to VersoPalette.Light,
            AppTheme.SEPIA to VersoPalette.Sepia,
            AppTheme.DARK to VersoPalette.Dark,
            AppTheme.BLACK to VersoPalette.Black,
        )
        for ((theme, palette) in palettes) {
            val colors = ReadingStyle.colors(theme)
            assertThat(colors.background).isEqualTo(palette.background.toArgb())
            assertThat(colors.text).isEqualTo(palette.text.toArgb())
            assertThat(colors.link).isEqualTo(palette.accent.toArgb())
        }
    }

    /**
     * La couleur des liens est posée à la création du navigateur (configuration), pas par les préférences : elle
     * doit être la même pour les palettes qui ne recréent pas l'activité entre elles (clair ↔ sépia, sombre ↔ noir).
     */
    @Test
    fun linkColorIsSharedWithinLightAndDarkFamilies() {
        assertThat(ReadingStyle.colors(AppTheme.SEPIA).link).isEqualTo(ReadingStyle.colors(AppTheme.LIGHT).link)
        assertThat(ReadingStyle.colors(AppTheme.BLACK).link).isEqualTo(ReadingStyle.colors(AppTheme.DARK).link)
    }
}
```

`VersoReadingPreferencesTest.kt` (remplace le fichier) :

```kotlin
package com.maximebier.verso.readium

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.Color as CssColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

// Robolectric : Color de Readium s’appuie sur android.graphics.Color.
@OptIn(ExperimentalReadiumApi::class)
@RunWith(AndroidJUnit4::class)
class VersoReadingPreferencesTest {

    private fun prefs(
        settings: ReadingSettings = ReadingSettings(),
        theme: AppTheme = AppTheme.LIGHT,
        scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
    ) = VersoReadingPreferences.epub(settings, theme, scrollMode)

    @Test
    fun defaultsImposeVersoLayout() {
        val p = prefs()
        assertThat(p.scroll).isTrue()
        assertThat(p.textAlign).isEqualTo(TextAlign.START)
        assertThat(p.hyphens).isFalse()
        assertThat(p.publisherStyles).isFalse()
        assertThat(p.fontFamily).isEqualTo(VersoReadingPreferences.LITERATA)
        assertThat(p.fontSize!!).isWithin(1e-9).of(1.25)
        assertThat(p.lineHeight!!).isWithin(1e-9).of(1.6)
        assertThat(p.paragraphSpacing!!).isWithin(1e-9).of(0.8)
        assertThat(p.pageMargins!!).isWithin(1e-9).of(1.2)
        assertThat(p.letterSpacing).isNull()
        assertThat(p.theme).isEqualTo(Theme.LIGHT)
        assertThat(p.backgroundColor?.int).isEqualTo(ReadingStyle.colors(AppTheme.LIGHT).background)
        assertThat(p.textColor?.int).isEqualTo(ReadingStyle.colors(AppTheme.LIGHT).text)
    }

    @Test
    fun userSettingsAreApplied() {
        val p = prefs(
            ReadingSettings(font = ReadingFont.ATKINSON, fontSizeSp = 24, lineSpacing = LineSpacing.AIRY, margins = Margins.WIDE),
        )
        assertThat(p.fontFamily).isEqualTo(VersoReadingPreferences.ATKINSON)
        assertThat(p.fontSize!!).isWithin(1e-9).of(1.5)
        assertThat(p.lineHeight!!).isWithin(1e-9).of(1.8)
        assertThat(p.paragraphSpacing!!).isWithin(1e-9).of(0.9)
        assertThat(p.pageMargins!!).isWithin(1e-9).of(1.6)
    }

    @Test
    fun systemFontIsTheWebViewSansSerif() {
        assertThat(prefs(ReadingSettings(font = ReadingFont.SYSTEM)).fontFamily).isEqualTo(FontFamily.SANS_SERIF)
    }

    @Test
    fun pagesModeTurnsScrollOff() {
        assertThat(prefs(scrollMode = ScrollMode.PAGES).scroll).isFalse()
    }

    @Test
    fun darkPalettesAreLighterAndSpaced() {
        for (theme in listOf(AppTheme.DARK, AppTheme.BLACK)) {
            val p = prefs(theme = theme)
            assertThat(p.fontWeight!!).isWithin(1e-9).of(0.95)
            assertThat(p.letterSpacing!!).isWithin(1e-9).of(0.02)
            assertThat(p.theme).isEqualTo(Theme.DARK)
            assertThat(p.backgroundColor?.int).isEqualTo(ReadingStyle.colors(theme).background)
        }
        assertThat(prefs(theme = AppTheme.SEPIA).fontWeight!!).isWithin(1e-9).of(1.0)
        assertThat(prefs(theme = AppTheme.SEPIA).theme).isEqualTo(Theme.LIGHT)
    }

    @Test
    fun configurationServesBundledFontsAndKeepsScrollInsideTheChapter() {
        val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso(AppTheme.LIGHT) } }
        assertThat(configuration.servedAssets).contains(ReadingStyle.SERVED_ASSETS_PATTERN)
        assertThat(configuration.disablePageTurnsWhileScrolling).isTrue()
    }

    @Test
    fun linksUseTheAccentColorInEveryTheme() {
        for (theme in AppTheme.entries) {
            val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso(theme) } }
            val accent = CssColor.Int(ReadingStyle.colors(theme).link)
            assertThat(configuration.readiumCssRsProperties.linkColor).isEqualTo(accent)
            assertThat(configuration.readiumCssRsProperties.visitedColor).isEqualTo(accent)
        }
    }

    @Test
    fun configurationLeavesInsetsToVerso() {
        val configuration = EpubNavigatorFragment.Configuration { with(VersoReadingPreferences) { applyVerso(AppTheme.LIGHT) } }
        assertThat(configuration.shouldApplyInsetsPadding).isFalse()
    }
}
```

`ReadingFontScaleTest.kt` : remplacer les deux tests par

```kotlin
    @Test
    fun readingScaleFollowsTheSystemFontSize() {
        assertThat(ReadingStyle.readingFontScale(Density(2.625f, fontScale = 1f), fontSizeSp = 20)).isWithin(1e-6).of(1.0)
        // Android 14 : échelle non linéaire, 20 sp à 200 % ne doublent pas (les grandes tailles grossissent moins).
        assertThat(ReadingStyle.readingFontScale(Density(2.625f, fontScale = 2f), fontSizeSp = 20))
            .isIn(com.google.common.collect.Range.open(1.5, 2.0))
    }

    @Test
    fun readingTextGrowsWithTheScale() {
        val prefs = VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS, fontScale = 2.0)
        assertThat(prefs.fontSize!!).isWithin(1e-9).of(2.5)
        assertThat(prefs.pageMargins!!).isWithin(1e-9).of(1.2)
    }
```

(imports : `com.maximebier.verso.core.settings.ReadingSettings`, `com.maximebier.verso.core.settings.ScrollMode`, `com.maximebier.verso.data.AppTheme`).

`ReaderEngineIntegrationTest.kt` ligne 52 : `VersoReadingPreferences.epub(dark = false)` → `VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS)` (mêmes imports) ; si le test appelle `applyVerso()` sans argument, passer `AppTheme.LIGHT`.

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.readium.*"`
Expected : FAIL (signatures inconnues).

- [ ] **Step 3 : implémenter `ReadingStyle` et `VersoReadingPreferences`**

`ReadingStyle.kt` : remplacer `READING_FONT_SIZE_SP`, `LINE_HEIGHT` et les fonctions qui en dépendent ; le fichier devient :

```kotlin
package com.maximebier.verso.readium

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoColors
import com.maximebier.verso.ui.theme.VersoPalette

/** Couleurs passées à Readium, tirées des jetons (aucune couleur en dur). */
data class ReadingColors(
    @ColorInt val background: Int,
    @ColorInt val text: Int,
    @ColorInt val link: Int,
)

/** Traduction des réglages de lecture de Verso en valeurs Readium (spec, « Lecture » et « Réglages de la V2 »). */
object ReadingStyle {
    const val ATKINSON_FAMILY_NAME = "Atkinson Hyperlegible Next"
    const val ATKINSON_ASSET_REGULAR = "fonts/atkinson_hyperlegible_next.ttf"
    const val ATKINSON_ASSET_ITALIC = "fonts/atkinson_hyperlegible_next_italic.ttf"
    const val LITERATA_FAMILY_NAME = "Literata"
    const val LITERATA_ASSET_REGULAR = "fonts/literata.ttf"
    const val LITERATA_ASSET_ITALIC = "fonts/literata_italic.ttf"

    /** Motif « glob simple » des assets servis aux WebView (dossier `src/main/assets/fonts/`). */
    const val SERVED_ASSETS_PATTERN = "fonts/.*"

    /** Plages de l’axe `wght` des polices variables. */
    val ATKINSON_WEIGHT_AXIS: IntRange = 200..800
    val LITERATA_WEIGHT_AXIS: IntRange = 200..900

    const val PARAGRAPH_SPACING_IN_LINES = 0.5

    /** Taille racine CSS d’une WebView (px CSS = dp) : `fontSize = 1,0` chez Readium. */
    const val WEBVIEW_ROOT_FONT_SIZE_PX = 16.0

    /** `--RS__pageGutter` de Readium CSS 1 (navigateur Fragment) sous 35 em de large. */
    const val FRAGMENT_PAGE_GUTTER_PX = 20.0

    const val NORMAL_FONT_WEIGHT = 400.0
    const val DARK_FONT_WEIGHT = 380.0
    const val DARK_LETTER_SPACING_EM = 0.01

    /**
     * Échelle du texte de lecture voulue par la taille de police d’Android : taille réelle de `fontSizeSp` (en dp)
     * / `fontSizeSp`. Passe par la conversion sp → dp du système, donc suit l’échelle non linéaire d’Android 14.
     */
    fun readingFontScale(density: Density, fontSizeSp: Int): Double =
        with(density) { fontSizeSp.toFloat().sp.toDp().value } / fontSizeSp.toDouble()

    /** 20 sp ⇒ 20 / 16 = 1,25 (facteur de la racine CSS de 16 px). */
    fun fontSizeFactor(fontSizeSp: Double): Double = fontSizeSp / WEBVIEW_ROOT_FONT_SIZE_PX

    /** Readium pose la valeur en rem en marge haute et basse (fusionnées) : 0,5 × 1,6 = 0,8 rem. */
    fun paragraphSpacingRem(lineHeight: Double): Double = lineHeight * PARAGRAPH_SPACING_IN_LINES

    /** Readium applique `400 × facteur` : 380 ⇒ 0,95. */
    fun fontWeightFactor(dark: Boolean): Double =
        (if (dark) DARK_FONT_WEIGHT else NORMAL_FONT_WEIGHT) / NORMAL_FONT_WEIGHT

    /** Readium divise la valeur par 2 (`Length.Rem(value / 2)`) : 0,01 em ⇒ 0,02. Rien en clair. */
    fun readiumLetterSpacing(dark: Boolean): Double? =
        if (dark) DARK_LETTER_SPACING_EM * 2 else null

    /**
     * Navigateur Fragment : marge voulue (16, 24 ou 32 dp) / gouttière de ReadiumCSS, qui s’élargit avec la largeur
     * de l’écran (paliers de `--RS__pageGutter` en em de 16 px). Indépendant de la taille du texte.
     */
    fun fragmentPageMargins(marginDp: Double, widthDp: Float = 0f): Double = marginDp / fragmentPageGutterPx(widthDp)

    private fun fragmentPageGutterPx(widthDp: Float): Double {
        val widthEm = widthDp / WEBVIEW_ROOT_FONT_SIZE_PX
        return when {
            widthEm >= 75 -> 50.0
            widthEm >= 45 -> 40.0
            widthEm >= 35 -> 30.0
            else -> FRAGMENT_PAGE_GUTTER_PX
        }
    }

    fun palette(theme: AppTheme): VersoColors = when (theme) {
        AppTheme.LIGHT -> VersoPalette.Light
        AppTheme.SEPIA -> VersoPalette.Sepia
        AppTheme.DARK -> VersoPalette.Dark
        AppTheme.BLACK -> VersoPalette.Black
    }

    fun colors(theme: AppTheme): ReadingColors {
        val palette = palette(theme)
        return ReadingColors(
            background = palette.background.toArgb(),
            text = palette.text.toArgb(),
            link = palette.accent.toArgb(),
        )
    }
}
```

`VersoReadingPreferences.kt` (remplace le fichier) :

```kotlin
@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.readium

import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.Color as CssColor
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Réglages de Verso imposés au navigateur EPUB classique de Readium (Fragment), par-dessus le CSS de l’éditeur. */
object VersoReadingPreferences {

    val LITERATA: FontFamily = FontFamily(ReadingStyle.LITERATA_FAMILY_NAME)
    val ATKINSON: FontFamily = FontFamily(ReadingStyle.ATKINSON_FAMILY_NAME)

    /** « Police du système » : la police sans empattements de la WebView (celle du téléphone). */
    fun fontFamilyOf(font: ReadingFont): FontFamily = when (font) {
        ReadingFont.LITERATA -> LITERATA
        ReadingFont.ATKINSON -> ATKINSON
        ReadingFont.SYSTEM -> FontFamily.SANS_SERIF
    }

    /**
     * @param fontScale échelle de la taille de police d’Android ([ReadingStyle.readingFontScale]).
     * @param widthDp largeur de la surface de lecture : la gouttière de ReadiumCSS s’élargit avec elle.
     */
    fun epub(
        settings: ReadingSettings,
        theme: AppTheme,
        scrollMode: ScrollMode,
        fontScale: Double = 1.0,
        widthDp: Float = 0f,
    ): EpubPreferences {
        val colors = ReadingStyle.colors(theme)
        val lineHeight = settings.lineSpacing.factor
        return EpubPreferences(
            backgroundColor = Color(colors.background),
            textColor = Color(colors.text),
            fontFamily = fontFamilyOf(settings.font),
            fontSize = ReadingStyle.fontSizeFactor(settings.fontSizeSp * fontScale),
            fontWeight = ReadingStyle.fontWeightFactor(theme.isDark),
            hyphens = false,
            letterSpacing = ReadingStyle.readiumLetterSpacing(theme.isDark),
            lineHeight = lineHeight,
            pageMargins = ReadingStyle.fragmentPageMargins(settings.margins.dp, widthDp),
            paragraphSpacing = ReadingStyle.paragraphSpacingRem(lineHeight),
            publisherStyles = false,
            scroll = scrollMode == ScrollMode.CONTINUOUS,
            textAlign = TextAlign.START,
            theme = if (theme.isDark) Theme.DARK else Theme.LIGHT,
        )
    }

    /**
     * Literata et Atkinson servies depuis les assets ; le défilement reste dans le chapitre (Verso enchaîne
     * lui-même). Les insets sont posés par Verso (`readerContentInsets`) : Readium ajouterait la découpe de l’écran
     * une seconde fois. La couleur des liens est fixée à la création : elle est commune au clair et au sépia, et au
     * sombre et au noir (`ReadingStyleTest.linkColorIsSharedWithinLightAndDarkFamilies`), et passer de l’un à
     * l’autre groupe recrée l’activité.
     */
    fun EpubNavigatorFragment.Configuration.applyVerso(theme: AppTheme) {
        servedAssets = listOf(ReadingStyle.SERVED_ASSETS_PATTERN)
        // Liens (visités ou non) à la couleur d’accent des jetons, contrastée à 7:1 : en sombre, le mode nuit de
        // ReadiumCSS mettrait les liens visités en #0099E5 (5,8:1). Variables en ligne : elles priment sur ce mode.
        val link = CssColor.Int(ReadingStyle.colors(theme).link)
        readiumCssRsProperties = readiumCssRsProperties.copy(linkColor = link, visitedColor = link)
        disablePageTurnsWhileScrolling = true
        shouldApplyInsetsPadding = false
        declareVariableFont(LITERATA, ReadingStyle.LITERATA_ASSET_REGULAR, ReadingStyle.LITERATA_ASSET_ITALIC, ReadingStyle.LITERATA_WEIGHT_AXIS)
        declareVariableFont(ATKINSON, ReadingStyle.ATKINSON_ASSET_REGULAR, ReadingStyle.ATKINSON_ASSET_ITALIC, ReadingStyle.ATKINSON_WEIGHT_AXIS)
    }

    private fun EpubNavigatorFragment.Configuration.declareVariableFont(
        family: FontFamily,
        regular: String,
        italic: String,
        weights: IntRange,
    ) {
        addFontFamilyDeclaration(family) {
            addFontFace {
                addSource(regular, preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(weights)
            }
            addFontFace {
                addSource(italic)
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(weights)
            }
        }
    }
}
```


Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.readium.*"`
Expected : PASS (les appelants de l’app ne compilent pas encore : les Steps 4 à 6 les corrigent ; si Gradle refuse de lancer les tests pour cette raison, enchaîner directement les Steps 4 à 6 puis lancer).

- [ ] **Step 4 : contrôleur (`submit`)**

`ReaderController.kt`, ajouter dans l’interface (imports `com.maximebier.verso.core.settings.ReadingSettings`, `com.maximebier.verso.core.settings.ScrollMode`, `com.maximebier.verso.data.AppTheme`) :

```kotlin
    /** Nouveaux réglages de lecture, appliqués sans recréer le lecteur (Readium garde la position). */
    fun submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode)
```

et, après `GestureSignal` :

```kotlin
/** Réglages envoyés au moteur de lecture. */
data class ReaderStyle(val settings: ReadingSettings, val theme: AppTheme, val scrollMode: ScrollMode)
```

`FragmentReaderController.kt` : champs et méthode

```kotlin
    private val styleState = MutableStateFlow<ReaderStyle?>(null)

    /** Derniers réglages demandés ; la surface les passe à `submitPreferences`. */
    val style: StateFlow<ReaderStyle?> = styleState.asStateFlow()

    override fun submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode) {
        styleState.value = ReaderStyle(settings, theme, scrollMode)
    }
```

`FakeReaderController.kt` (test) :

```kotlin
    /** Réglages reçus par `submit()`, dans l’ordre. */
    val submitted = mutableListOf<ReaderStyle>()

    override fun submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode) {
        submitted += ReaderStyle(settings, theme, scrollMode)
    }
```

Test ajouté dans `FragmentReaderControllerTest.kt` (même construction de contrôleur que les tests existants du fichier ; utiliser leur fabrique locale) :

```kotlin
    @Test
    fun submitPublishesTheLatestStyle() = runTest {
        val controller = controller()
        assertThat(controller.style.value).isNull()
        controller.submit(ReadingSettings(fontSizeSp = 22), AppTheme.SEPIA, ScrollMode.CONTINUOUS)
        assertThat(controller.style.value).isEqualTo(ReaderStyle(ReadingSettings(fontSizeSp = 22), AppTheme.SEPIA, ScrollMode.CONTINUOUS))
    }
```

(`controller()` : nom de la fabrique du fichier ; si elle s’appelle autrement, utiliser la sienne.)

- [ ] **Step 5 : `ReaderSurface`**

Remplacer le paramètre `dark: Boolean` par `initialStyle: ReaderStyle` et supprimer le paramètre `fontScale` (calculé dans la surface à partir de la taille choisie). Code modifié :

```kotlin
@Composable
fun ReaderSurface(
    publication: Publication,
    initialLocator: Locator?,
    initialStyle: ReaderStyle,
    onReady: (ReaderController) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
    positions: ReadingOrderPositions? = null,
    onInternalLink: (Url) -> Unit = {},
    onFailed: () -> Unit = {},
) {
    val activity = LocalActivity.current as? FragmentActivity
    val densityInfo = LocalDensity.current
    val density = densityInfo.density
    …
    val background = Color(ReadingStyle.colors(initialStyle.theme).background)
```

(le bloc « mise en page fixe » garde `background`). Après la création de `controller` :

```kotlin
    val requestedStyle by controller.style.collectAsState()
    val style = requestedStyle ?: initialStyle
    // Taille réelle voulue par Android pour la taille choisie (échelle non linéaire : dépend de la taille).
    val fontScale = remember(densityInfo, style.settings.fontSizeSp) {
        ReadingStyle.readingFontScale(densityInfo, style.settings.fontSizeSp)
    }
```

Fabrique :

```kotlin
        EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            listener = listener,
            initialPreferences = VersoReadingPreferences.epub(
                initialStyle.settings, initialStyle.theme, initialStyle.scrollMode,
                fontScale = ReadingStyle.readingFontScale(densityInfo, initialStyle.settings.fontSizeSp),
            ),
            configuration = EpubNavigatorFragment.Configuration { applyVerso(initialStyle.theme) },
        )
```

Préférences à chaque changement :

```kotlin
    LaunchedEffect(navigator, style, fontScale, widthDp) {
        navigator?.submitPreferences(
            VersoReadingPreferences.epub(style.settings, style.theme, style.scrollMode, fontScale = fontScale, widthDp = widthDp),
        )
    }

    Box(modifier.fillMaxSize().background(Color(ReadingStyle.colors(style.theme).background))) {
```

Imports ajoutés : `androidx.compose.runtime.collectAsState`. Mettre à jour le KDoc (`initialStyle` lu à la création ; les changements passent par `ReaderController.submit`).

- [ ] **Step 6 : distance en écrans avec les réglages réels**

Tests ajoutés à `ReaderScreenDistanceTest.kt` :

```kotlin
    @Test
    fun largerTextAndLineSpacingMeanMoreScreens() {
        val base = ReaderScreenDistance.bookScreens(60_000, phone)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(fontSizeSp = 26.0))).isGreaterThan(base)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(lineHeight = 1.8))).isGreaterThan(base)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(marginDp = 32.0))).isGreaterThan(base)
        assertThat(ReaderScreenDistance.bookScreens(60_000, phone.copy(marginDp = 16.0))).isLessThan(base)
    }

    @Test
    fun wordsPerLineShrinkWithSizeAndMargins() {
        assertThat(ReaderScreenDistance.wordsPerLine(fontScale = 1f)).isWithin(1e-9).of(6.0)
        assertThat(ReaderScreenDistance.wordsPerLine(fontScale = 1f, fontSizeSp = 38.0)).isWithin(1e-9).of(3.0)
        assertThat(ReaderScreenDistance.wordsPerLine(fontScale = 1f, marginDp = 32.0)).isLessThan(6.0)
    }
```

`ReaderScreenDistance.kt` :

```kotlin
/** Ce qui détermine la taille d’un « écran » de texte. Valeurs par défaut : référence V1 (Atkinson 19 sp). */
data class ReaderMetrics(
    val viewportHeightPx: Int,
    val fontScale: Float,
    val density: Float,
    val fontSizeSp: Double = ReaderScreenDistance.REFERENCE_FONT_SIZE_SP,
    val lineHeight: Double = ReaderScreenDistance.REFERENCE_LINE_HEIGHT,
    val marginDp: Double = ReaderScreenDistance.REFERENCE_MARGIN_DP,
)

object ReaderScreenDistance {
    /** Environ 35 caractères par ligne en 19 sp sur 390 dp avec 24 dp de marges (spec, « Lecture »). */
    const val BASE_WORDS_PER_LINE = 6.0
    const val REFERENCE_FONT_SIZE_SP = 19.0
    const val REFERENCE_LINE_HEIGHT = 1.6
    const val REFERENCE_MARGIN_DP = 24.0
    const val REFERENCE_WIDTH_DP = 390.0

    const val FALLBACK_VIEWPORT_HEIGHT_PX = 2_000
    const val FALLBACK_DENSITY = 2.625f
    const val MIN_BOOK_SCREENS = 1.0

    fun lineHeightPx(
        fontScale: Float,
        density: Float,
        fontSizeSp: Double = REFERENCE_FONT_SIZE_SP,
        lineHeight: Double = REFERENCE_LINE_HEIGHT,
    ): Float = (fontSizeSp * lineHeight * fontScale * density).toFloat()

    /** Mots par ligne : inversement proportionnels à la taille, proportionnels à la largeur du texte. */
    fun wordsPerLine(
        fontScale: Float,
        fontSizeSp: Double = REFERENCE_FONT_SIZE_SP,
        marginDp: Double = REFERENCE_MARGIN_DP,
    ): Double {
        val sizeRatio = REFERENCE_FONT_SIZE_SP / (fontSizeSp * fontScale.coerceAtLeast(0.1f))
        val widthRatio = (REFERENCE_WIDTH_DP - 2 * marginDp) / (REFERENCE_WIDTH_DP - 2 * REFERENCE_MARGIN_DP)
        return BASE_WORDS_PER_LINE * sizeRatio * widthRatio
    }

    fun bookScreens(totalWords: Long, metrics: ReaderMetrics): Double {
        require(metrics.viewportHeightPx > 0) { "La surface de lecture n’est pas mesurée" }
        val screens = estimateBookScreens(
            totalWords = totalWords,
            viewportHeightPx = metrics.viewportHeightPx,
            lineHeightPx = lineHeightPx(metrics.fontScale, metrics.density, metrics.fontSizeSp, metrics.lineHeight),
            wordsPerLine = wordsPerLine(metrics.fontScale, metrics.fontSizeSp, metrics.marginDp),
        )
        return screens.coerceAtLeast(MIN_BOOK_SCREENS)
    }
    // distance() et fallbackDistance() inchangées.
}
```

Supprimer l’import de `ReadingStyle` devenu inutile.

- [ ] **Step 7 : ViewModel et écran**

Test ajouté à `ReaderViewModelTest.kt` (imports : `com.maximebier.verso.core.settings.ReadingSettings`, `com.maximebier.verso.core.settings.ScrollMode`, `com.maximebier.verso.data.AppTheme`, `com.maximebier.verso.reader.ReaderStyle`, `kotlinx.coroutines.flow.MutableStateFlow`). La fabrique `factory(…)` reçoit un paramètre `readingSettings: Flow<ReadingSettings> = flowOf(ReadingSettings())` passé au constructeur (import `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.flowOf`).

```kotlin
    @Test
    fun settingsAndThemeAreSubmittedWithoutMovingTheReadingPosition() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.3000)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val settings = MutableStateFlow(ReadingSettings())
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id, readingSettings = settings))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()
        assertThat(fake.submitted.last()).isEqualTo(ReaderStyle(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS))

        settings.value = ReadingSettings(fontSizeSp = 24)
        runCurrent()
        viewModel.onThemeChanged(AppTheme.SEPIA)
        runCurrent()

        assertThat(fake.submitted.last()).isEqualTo(ReaderStyle(ReadingSettings(fontSizeSp = 24), AppTheme.SEPIA, ScrollMode.CONTINUOUS))
        assertThat(viewModel.uiState.value.readingSettings.fontSizeSp).isEqualTo(24)
        assertThat(viewModel.uiState.value.returnCard).isNull()
        assertThat(books.book(id)!!.progression).isEqualTo(0.30)
        store.clear()
    }
```

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReaderViewModelTest"` → FAIL (paramètre inconnu).

`ReaderViewModel.kt` :

- constructeur : ajouter en dernier `private val readingSettings: Flow<ReadingSettings> = flowOf(ReadingSettings()),` ;
- `ReaderUiState` : ajouter `val readingSettings: ReadingSettings = ReadingSettings(),` ;
- champs : `private var theme: AppTheme? = null` ;
- dans `init` (ou au début de la classe, après les champs) :

```kotlin
    init {
        viewModelScope.launch {
            readingSettings.collect { settings ->
                _uiState.update { it.copy(readingSettings = settings) }
                submitStyle()
                refreshDistance()
            }
        }
    }
```

  (si la classe a déjà un bloc `init`, y ajouter ce `launch`) ;
- méthodes :

```kotlin
    /** Palette affichée (réglage Thème de Verso, ou celle du téléphone en automatique), envoyée par ReaderScreen. */
    fun onThemeChanged(theme: AppTheme) {
        if (this.theme == theme) return
        this.theme = theme
        submitStyle()
    }

    /** Réglages courants vers le moteur : jamais un saut ni un geste pour la machine à états. */
    private fun submitStyle() {
        val reader = controller ?: return
        val current = theme ?: return
        // Mode pages : étape 12 (défilement du livre ou défaut des Paramètres). Jusque-là, toujours continu.
        reader.submit(_uiState.value.readingSettings, current, ScrollMode.CONTINUOUS)
    }
```

- `onReaderReady` : appeler `submitStyle()` juste après `controller = readerController` ;
- `refreshDistance()` : construire la mesure avec les réglages courants

```kotlin
    private fun refreshDistance() {
        val base = metrics ?: return
        val height = controller?.viewportHeightPx ?: return
        if (height <= 0) return
        val settings = _uiState.value.readingSettings
        val current = base.copy(
            viewportHeightPx = height,
            fontSizeSp = settings.fontSizeSp.toDouble(),
            lineHeight = settings.lineSpacing.factor,
            marginDp = settings.margins.dp,
        )
        if (current == appliedMetrics) return
        appliedMetrics = current
        coordinator?.updateDistance(ReaderScreenDistance.distance(_uiState.value.totalWords, current))
    }
```

- fabrique `factory(bookId)` : `readingSettings = container.settings.readingSettings,`.

`ReaderScreen.kt` :

```kotlin
    val theme = if (VersoTheme.isDark) AppTheme.DARK else AppTheme.LIGHT   // remplacé par VersoTheme.theme en 10.1
    LaunchedEffect(theme) { viewModel.onThemeChanged(theme) }
    // Le texte de lecture suit la taille de police d’Android ; la distance en écrans utilise la même échelle.
    val readingFontScale = remember(density, state.readingSettings.fontSizeSp) {
        ReadingStyle.readingFontScale(density, state.readingSettings.fontSizeSp)
    }
```

et l’appel :

```kotlin
            ReaderSurface(
                publication = publication,
                initialLocator = state.initialLocator,
                initialStyle = ReaderStyle(state.readingSettings, theme, ScrollMode.CONTINUOUS),
                positions = state.readingPositions,
                …
```

(`initialStyle` n’est lu qu’à la création du lecteur ; les changements passent par `submit`).

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.*" --tests "com.maximebier.verso.reader.*" --tests "com.maximebier.verso.readium.*"`
Expected : PASS.

- [ ] **Step 8 : suite complète, lint, commit**

Run : `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected : PASS, lint sans avertissement.

```bash
git add app/src
git commit -m "Étape 9 (partie) : réglages appliqués au lecteur sans le recréer"
```

### Task 9.4 : checklist V2 et fin de l'étape 9

**Files:**
- Create: `docs/acceptance-v2.md`
- Modify: `docs/SPEC.md` (critère 1 coché s’il est vérifié)

**Interfaces:**
- Consumes : tout ce qui précède.
- Produces : `docs/acceptance-v2.md`, tenu à jour par chaque fin d'étape.

- [ ] **Step 1 : écrire `docs/acceptance-v2.md`**

```markdown
# Verso V2 — checklist d’acceptation

Référence : « Critères d’acceptation V2 » de `docs/SPEC.md`. Une case de la spec n’est cochée que lorsque **toutes** les vérifications de sa ligne sont faites et conformes.

- **Auto** : test automatisé, lancé par `./gradlew :core:test :app:testDebugUnitTest`.
- **Claude** : fait par Claude sur le téléphone (adb Wi-Fi, `ANDROID_SERIAL=192.168.1.10:5555`) sans toucher aux réglages système (captures dans `build/acceptance/<horodatage>/`).
- **Maxime** : fait à la main par Maxime (réglage système, redémarrage, écoute TalkBack).

| # | Étape | Critère | Auto | Claude | Maxime | Résultat |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 9 | Mise à jour : Literata partout, bibliothèque, positions et journal V1 intacts | `VersoDatabaseMigrationTest`, `SettingsRepositoryTest.v1ThemeValueIsReadUnchanged`, `TypographyTest.literataIsTheDefault` | installation par-dessus la V1 (`installDebug`, sans effacer les données) : livres, carte Reprendre, journal, texte en Literata | — | ☐ |
| 2 | 11 | Atkinson puis police du système : toute l’app et le texte changent, le choix survit au redémarrage | `TypographyTest`, `VersoReadingPreferencesTest.systemFontIsTheWebViewSansSerif` | cartes Police de la feuille « Aa » (étape 11 ; Paramètres à l’étape 14), captures bibliothèque et lecture ; arrêt forcé puis relance | redémarrage du téléphone | ☐ |
| 3 | 11 | Taille, interligne, marges en lecture : effet immédiat, même paragraphe | `ReaderViewModelTest.settingsAndThemeAreSubmittedWithoutMovingTheReadingPosition`, `VersoReadingPreferencesTest.userSettingsAreApplied` | feuille « Aa » : capture avant/après, même premier paragraphe | — | ☐ |
| 4 | 10 | Sépia et Noir sur toute l’app et le texte, sans zone d’une autre couleur ; Automatique suit le téléphone | `V2ScreenshotTest`, `ThemeModeTest` | captures bibliothèque, fiche, Paramètres, lecture, barres, sommaire, journal en sépia et en noir | Automatique avec le thème du téléphone changé | ☐ |
| 5 | 10 | Contraste ≥ 7:1 dans les cinq thèmes | `PaletteContrastTest` (quatre palettes) | — | — | ☐ |
| 6 | 15 | Barre : Sommaire, Journal, Rechercher, Réglages avec texte | | | | ☐ |
| 7 | 12 | Feuille « Aa » à mi-hauteur sans voile, effet en direct, réglages du bas en glissant | | | | ☐ |
| 8 | 12 | Mode pages : swipe et tap latéral, aucune ligne coupée, pied de page | | | | ☐ |
| 9 | 12 | Mode mémorisé par livre, position gardée au changement de mode | | | | ☐ |
| 10 | 12 | Feuilletage rapide = navigation et carte « Revenir » ; lecture page par page = progression | | | | ☐ |
| 11 | 13 | États À lire / En cours / Terminé et filtres | | | | ☐ |
| 12 | 13 | État choisi dans la fiche, gardé après redémarrage et relecture | | | | ☐ |
| 13 | 13 | « Trier et afficher » : tri et affichage persistés | | | | ☐ |
| 14 | 14 | Statistiques de la fiche après deux sessions, « Voir le journal de lecture » | | | | ☐ |
| 15 | 14 | Interrupteur des statistiques ; journal toujours tenu | | | | ☐ |
| 16 | 14 | Section Lecture des Paramètres (2.09) | | | | ☐ |
| 17 | 15 | Recherche : résultats au fur et à mesure, par chapitre, mot marqué (fond, gras, soulignement) | | | | ☐ |
| 18 | 15 | Toucher un résultat : passage marqué, carte « Revenir » | | | | ☐ |
| 19 | 16 | Texte à 200 % et commandes ≥ 48 dp intitulées sur les nouveaux écrans | | | | ☐ |
| 20 | 16 | Aucune permission réseau ni autre | | | | ☐ |

Les colonnes vides sont remplies par l’étape qui porte le critère.
```

- [ ] **Step 2 : fin de l’étape 9 (orchestrateur, plan principal « Fin de chaque étape »)**

1. `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → vert.
2. Regrouper : `git reset --soft <commit précédant la tâche 9.1>` puis `git add -A && git commit -m "Étape 9 : réglages de lecture et polices"` (uniquement si aucun commit de l’étape n’a été poussé), `git push`.
3. `./gradlew :app:installDebug` **sans désinstaller** (critère 1 : mise à jour par-dessus la V1), `adb shell am start -n com.maximebier.verso/.MainActivity`, captures : bibliothèque (Literata, livres V1 présents), lecture d’un livre V1 (même position qu’avant la mise à jour, Literata 20 sp), Paramètres.
4. Critères 2 et 3 : ils se vérifient à l’étape 11 (cartes Police et réglages de la feuille « Aa ») ; rien à faire ici pour eux.
5. Cocher dans `docs/SPEC.md` et `docs/acceptance-v2.md` les critères vérifiés ; les autres restent ouverts avec la raison.

---

## Étape 10 — Thèmes sépia et noir

### Task 10.1 : cinq thèmes dans l'app, le lecteur et les Paramètres

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/ThemeMode.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/theme/VersoTheme.kt`, `VersoColors.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/SettingsRows.kt`, `app/src/main/kotlin/com/maximebier/verso/ui/settings/SettingsScreen.kt`, `app/src/main/res/values/strings.xml`
- Modify (tests) : tous les `VersoTheme(darkTheme = …)` de `app/src/test` (liste au Step 4)
- Test (modify): `app/src/test/kotlin/com/maximebier/verso/data/ThemeModeTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/settings/SettingsScreenTest.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/theme/VersoThemeTest.kt`

**Interfaces:**
- Consumes : `AppTheme`, `ThemeMode.resolve` (9.3), `ReadingStyle.palette(theme)` (9.3), `VersoTypography.of` (9.2).
- Produces : `ThemeMode { AUTO, LIGHT, SEPIA, DARK, BLACK }` (contrat) ; `@Composable fun VersoTheme(theme: AppTheme = <thème du téléphone>, font: ReadingFont = ReadingFont.LITERATA, content)` ; `VersoTheme.theme: AppTheme` ; `LocalVersoTheme` ; `RadioRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, horizontalPadding: Dp = 24.dp)` (réutilisée par les dialogues des Paramètres, tâche 14.5) dans `ui/components/SettingsRows.kt` ; chaînes `settings_theme_sepia`, `settings_theme_black`.

- [ ] **Step 1 : tests**

`ThemeModeTest.kt`, ajouter :

```kotlin
    @Test
    fun sepiaAndBlackIgnoreThePhone() {
        assertThat(ThemeMode.SEPIA.resolve(systemDark = true)).isEqualTo(AppTheme.SEPIA)
        assertThat(ThemeMode.BLACK.resolve(systemDark = false)).isEqualTo(AppTheme.BLACK)
        assertThat(ThemeMode.SEPIA.isDark(systemDark = true)).isFalse()
        assertThat(ThemeMode.BLACK.isDark(systemDark = false)).isTrue()
    }

    @Test
    fun settingsOrderMatchesTheMockups() {
        assertThat(ThemeMode.entries).containsExactly(
            ThemeMode.AUTO, ThemeMode.LIGHT, ThemeMode.SEPIA, ThemeMode.DARK, ThemeMode.BLACK,
        ).inOrder()
    }
```

`app/src/test/kotlin/com/maximebier/verso/ui/theme/VersoThemeTest.kt` :

```kotlin
package com.maximebier.verso.ui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VersoThemeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun eachThemeProvidesItsPaletteAndDarkness() {
        val expected = mapOf(
            AppTheme.LIGHT to VersoPalette.Light,
            AppTheme.SEPIA to VersoPalette.Sepia,
            AppTheme.DARK to VersoPalette.Dark,
            AppTheme.BLACK to VersoPalette.Black,
        )
        val seen = mutableMapOf<AppTheme, Triple<VersoColors, Boolean, AppTheme>>()
        composeRule.setContent {
            for (theme in AppTheme.entries) {
                VersoTheme(theme = theme) { seen[theme] = Triple(VersoTheme.colors, VersoTheme.isDark, VersoTheme.theme) }
            }
        }
        composeRule.waitForIdle()
        for ((theme, palette) in expected) {
            assertThat(seen.getValue(theme).first).isEqualTo(palette)
            assertThat(seen.getValue(theme).second).isEqualTo(theme.isDark)
            assertThat(seen.getValue(theme).third).isEqualTo(theme)
        }
    }

    @Test
    fun darkPalettesUseTheDarkCovers() {
        var black: List<androidx.compose.ui.graphics.Color>? = null
        var sepia: List<androidx.compose.ui.graphics.Color>? = null
        composeRule.setContent {
            VersoTheme(theme = AppTheme.BLACK) { black = VersoTheme.coverPalette }
            VersoTheme(theme = AppTheme.SEPIA) { sepia = VersoTheme.coverPalette }
        }
        composeRule.waitForIdle()
        assertThat(black).isEqualTo(VersoPalette.CoverDark)
        assertThat(sepia).isEqualTo(VersoPalette.CoverLight)
    }
}
```

`SettingsScreenTest.kt`, ajouter :

```kotlin
    @Test
    fun themeSelectorOffersTheFiveThemesAsRadioButtons() {
        show()
        val labels = listOf(
            R.string.settings_theme_auto, R.string.settings_theme_light, R.string.settings_theme_sepia,
            R.string.settings_theme_dark, R.string.settings_theme_black,
        ).map(ctx::getString)
        labels.forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithText(ctx.getString(R.string.settings_theme_sepia)).performClick()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_theme_sepia)).assertIsSelected()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_theme_black)).performScrollTo().performClick()
        assertThat(events).containsExactly("theme=SEPIA", "theme=BLACK").inOrder()
    }
```

(imports : `androidx.compose.ui.test.assertHeightIsAtLeast`, `androidx.compose.ui.unit.dp` s’ils manquent.)

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.data.ThemeModeTest" --tests "com.maximebier.verso.ui.theme.VersoThemeTest" --tests "com.maximebier.verso.ui.settings.SettingsScreenTest"`
Expected : FAIL (`SEPIA` inconnu, paramètre `theme` inconnu).

- [ ] **Step 2 : `ThemeMode` à cinq valeurs**

```kotlin
/** Thème de l'application : automatique (suit le téléphone), ou une palette imposée. Ordre des Paramètres. */
enum class ThemeMode {
    AUTO,
    LIGHT,
    SEPIA,
    DARK,
    BLACK,
    ;

    /** Palette affichée, `systemDark` étant le thème du téléphone. */
    fun resolve(systemDark: Boolean): AppTheme = when (this) {
        AUTO -> if (systemDark) AppTheme.DARK else AppTheme.LIGHT
        LIGHT -> AppTheme.LIGHT
        SEPIA -> AppTheme.SEPIA
        DARK -> AppTheme.DARK
        BLACK -> AppTheme.BLACK
    }

    fun isDark(systemDark: Boolean): Boolean = resolve(systemDark).isDark
}
```

(le commentaire de `AppTheme` perd « arrivent en V2 »). Les valeurs V1 stockées (`AUTO`, `LIGHT`, `DARK`) restent lisibles : les noms ne changent pas.

- [ ] **Step 3 : `VersoTheme(theme, font)`**

`VersoColors.kt`, ajouter :

```kotlin
/** Palette affichée (réglage Thème de Verso, ou celle du téléphone en automatique). */
val LocalVersoTheme = staticCompositionLocalOf { AppTheme.LIGHT }
```

(import `com.maximebier.verso.data.AppTheme`).

`VersoTheme.kt`, fonction :

```kotlin
/** Thème Verso : une des quatre palettes de tokens.json, dans la police choisie. */
@Composable
fun VersoTheme(
    theme: AppTheme = if (isSystemInDarkTheme()) AppTheme.DARK else AppTheme.LIGHT,
    font: ReadingFont = ReadingFont.LITERATA,
    content: @Composable () -> Unit,
) {
    val colors = paletteOf(theme)
    val covers = if (theme.isDark) VersoPalette.CoverDark else VersoPalette.CoverLight
    val typography = VersoTypography.of(font)
    CompositionLocalProvider(
        LocalVersoColors provides colors,
        LocalCoverPalette provides covers,
        LocalVersoDarkTheme provides theme.isDark,
        LocalVersoTheme provides theme,
        LocalVersoTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(colors, theme.isDark),
            … (inchangé)
```

et

```kotlin
/** Palette de tokens.json pour un thème. */
fun paletteOf(theme: AppTheme): VersoColors = when (theme) {
    AppTheme.LIGHT -> VersoPalette.Light
    AppTheme.SEPIA -> VersoPalette.Sepia
    AppTheme.DARK -> VersoPalette.Dark
    AppTheme.BLACK -> VersoPalette.Black
}
```

Dans `object VersoTheme`, ajouter :

```kotlin
    /** Palette affichée. */
    val theme: AppTheme
        @Composable @ReadOnlyComposable get() = LocalVersoTheme.current
```

`ReadingStyle.palette(theme)` (9.3) devient `= paletteOf(theme)` (une seule correspondance thème → palette).

- [ ] **Step 4 : appelants**

```bash
cd app/src/test
grep -rl "VersoTheme(darkTheme = false)" --include=*.kt . | xargs sed -i 's/VersoTheme(darkTheme = false)/VersoTheme(theme = AppTheme.LIGHT)/g'
grep -rl "VersoTheme(darkTheme = dark)" --include=*.kt . | xargs sed -i 's/VersoTheme(darkTheme = dark)/VersoTheme(theme = if (dark) AppTheme.DARK else AppTheme.LIGHT)/g'
for f in $(grep -rl "AppTheme\." --include=*.kt .); do
  grep -q "^import com.maximebier.verso.data.AppTheme$" "$f" || sed -i '0,/^import /s//import com.maximebier.verso.data.AppTheme\nimport /' "$f"
done
grep -rn "darkTheme" --include=*.kt . ../main   # attendu : aucune ligne
cd ../../..
```

Fichiers concernés (relevé du 2026-09-27) : `ComponentsTest.kt`, `DetailsFieldsTest.kt`, `EmptyLibraryScreenTest.kt`, `VersoNavHostTest.kt`, `ReaderBarsTest.kt`, `ReturnCardTest.kt`, `TocSheetTest.kt`, `TypographyTest.kt` (9.2 : `VersoTheme(darkTheme = false, font = …)` → `VersoTheme(theme = AppTheme.LIGHT, font = …)`, à corriger à la main si le `sed` ne l’a pas pris).

`MainActivity.kt` :

```kotlin
        enableEdgeToEdge(initialThemeMode.resolve(systemDark = isSystemNight()))
        …
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = initialThemeMode)
            val reading by settings.readingSettings.collectAsState(initial = initialReading)
            val theme = themeMode.resolve(systemDark = isSystemInDarkTheme())
            LaunchedEffect(themeMode) { applyWindowNightMode(themeMode) }
            LaunchedEffect(theme) { enableEdgeToEdge(theme) }
            VersoTheme(theme = theme, font = reading.font) {
```

```kotlin
    /** Icônes des barres système lisibles sur le fond du thème affiché (même voile que le défaut d'AndroidX). */
    private fun enableEdgeToEdge(theme: AppTheme) {
        val scrim = navigationScrim(theme)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { theme.isDark },
            navigationBarStyle = SystemBarStyle.auto(scrim, scrim) { theme.isDark },
        )
    }
```

`applyWindowNightMode` :

```kotlin
        val night = when (mode) {
            ThemeMode.AUTO -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT, ThemeMode.SEPIA -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK, ThemeMode.BLACK -> UiModeManager.MODE_NIGHT_YES
        }
```

(le KDoc précise : sépia est un thème de jour et noir un thème de nuit pour la fenêtre ; passer de clair à sépia ne recrée pas l’activité).

`companion object` :

```kotlin
    private companion object {
        // Voile de la barre de navigation à trois boutons (API 26 à 28 seulement) : fond des jetons du thème affiché,
        // opacité par défaut de SystemBarStyle (90 % pour les thèmes clairs, 50 % pour les sombres).
        fun navigationScrim(theme: AppTheme): Int =
            paletteOf(theme).background.copy(alpha = if (theme.isDark) 0.5f else 0.9f).toArgb()
    }
```

(imports : `com.maximebier.verso.data.AppTheme`, `com.maximebier.verso.ui.theme.paletteOf` ; retirer `VersoPalette` s’il n’est plus utilisé.)

`ReaderScreen.kt` : `val theme = if (VersoTheme.isDark) AppTheme.DARK else AppTheme.LIGHT` → `val theme = VersoTheme.theme`.

- [ ] **Step 5 : choix du thème dans les Paramètres**

`strings.xml`, après `settings_theme_light` et `settings_theme_dark` (ordre des maquettes 2.02) :

```xml
    <string name="settings_theme_sepia">Sépia</string>
    …
    <string name="settings_theme_black">Noir</string>
```

(`settings_theme_sepia` entre `light` et `dark`, `settings_theme_black` après `dark`.)

`SettingsRows.kt`, ajouter (imports : `androidx.compose.foundation.selection.selectable`, `androidx.compose.material3.RadioButton`, `androidx.compose.material3.RadioButtonDefaults`, `androidx.compose.ui.semantics.Role`, `androidx.compose.foundation.layout.heightIn` s’ils manquent) :

```kotlin
/** Ligne d’un choix unique : bouton radio et libellé, toute la ligne cliquable (≥ 48 dp), annoncée comme bouton radio. */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 24 dp dans une liste plein écran ; 0 dans un dialogue, qui a déjà sa marge. */
    horizontalPadding: Dp = 24.dp,
) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.outline),
        )
        Text(
            text = title,
            style = if (selected) VersoTheme.typography.bodyStrong else VersoTheme.typography.body,
            color = colors.text,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
```

Le libellé choisi passe en gras (jamais la couleur seule). `SettingsScreen.kt`, `ThemeSelector` (remplace la fonction) :

```kotlin
/** Thème Automatique / Clair / Sépia / Sombre / Noir, dans l'ordre de ThemeMode.entries (maquette 2.02). */
@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val label = stringResource(R.string.settings_theme)
    Text(
        text = label,
        style = VersoTheme.typography.rowTitle,
        color = VersoTheme.colors.text,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp),
    )
    Column(Modifier.selectableGroup().semantics { contentDescription = label }) {
        ThemeMode.entries.forEach { mode ->
            RadioRow(title = stringResource(mode.labelRes()), selected = mode == selected, onClick = { onSelect(mode) })
        }
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.AUTO -> R.string.settings_theme_auto
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.SEPIA -> R.string.settings_theme_sepia
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.BLACK -> R.string.settings_theme_black
}
```

(imports : `androidx.annotation.StringRes`, `androidx.compose.foundation.selection.selectableGroup`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`, `com.maximebier.verso.ui.components.RadioRow` ; retirer `VersoSegmentedButton` s’il n’est plus utilisé dans le fichier). Cinq pastilles ne tiennent pas en un segmenté de 342 dp (« Automatique ») : liste radio, en attendant la ligne « Thème › Automatique » de la maquette 2.09 (étape 14, partie C).

Le test existant `themeSelectorSelectsTheChosenMode` reste valable (le nœud fusionné de la ligne porte le texte et l’état sélectionné).

- [ ] **Step 6 : relancer**

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.data.*" --tests "com.maximebier.verso.ui.theme.*" --tests "com.maximebier.verso.ui.settings.*" --tests "com.maximebier.verso.ui.reader.*"`
Expected : PASS.

Run : `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected : PASS.

- [ ] **Step 7 : commit**

```bash
git add app/src
git commit -m "Étape 10 (partie) : thèmes sépia et noir"
```

### Task 10.2 : captures sépia et noir, contraste des quatre palettes, fin de l'étape 10

**Files:**
- Test (modify): `app/src/test/kotlin/com/maximebier/verso/ui/theme/PaletteContrastTest.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt`, `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenshotTest.kt`
- Modify (si un contraste échoue) : `docs/design/tokens.json`, `docs/SPEC.md` (« Couleurs » et « Historique »)

**Interfaces:**
- Consumes : `VersoTheme(theme, font)` (10.1), `ScreenFixture`, `ScreenVariant`, `VariantRule` (`V1ScreenCatalog.kt`), échantillons de `screenshots/samples/`.
- Produces : `V2ScreenCatalog.fixtures: List<V2ScreenFixture>` et `V2ScreenshotTest` (sortie `build/outputs/roborazzi/v2/<id>-<clair|sepia|sombre|noir>.png`, et `<id>-<première palette>-texte-200.png`). Les parties B, C et D y **ajoutent** leurs écrans, sous la forme `V2ScreenFixture(ScreenFixture("<id>") { … })` (quatre palettes par défaut).

- [ ] **Step 1 : contraste des quatre palettes**

`PaletteContrastTest.kt` : remplacer `v1Themes` par

```kotlin
    /** Les quatre palettes (V1 : clair et sombre ; V2 : sépia et noir). */
    private val themes = listOf(
        "clair" to VersoPalette.Light,
        "sépia" to VersoPalette.Sepia,
        "sombre" to VersoPalette.Dark,
        "noir" to VersoPalette.Black,
    )
```

et `v1Themes` → `themes` dans `everyTextPairReachesSevenToOne` et `everyNonTextPairReachesThreeToOne`. Renommer le KDoc de `textPairs` : « Couples texte/fond réellement utilisés par les écrans : ≥ 7:1 (WCAG AAA). » Dans `generatedCoversReachFourAndAHalfToOne`, ajouter :

```kotlin
        for (cover in VersoPalette.CoverLight) {
            assertWithMessage("sépia $cover").that(contrast(VersoPalette.Sepia.onCover, cover)).isAtLeast(4.5)
        }
        for (cover in VersoPalette.CoverDark) {
            assertWithMessage("noir $cover").that(contrast(VersoPalette.Black.onCover, cover)).isAtLeast(4.5)
        }
```

Ajouter le couple de la recherche et des surlignages (fond `highlight`, étape 15) :

```kotlin
        ColorPair("text/highlight", { it.text }, { it.highlight }),
```

Run : `./gradlew :app:testDebugUnitTest --tests "com.maximebier.verso.ui.theme.PaletteContrastTest"`
Expected : PASS. Si un couple échoue (message « sépia textSecondary/surfaceHigh » par exemple) : foncer (clair, sépia) ou éclaircir (sombre, noir) le jeton en cause dans `docs/design/tokens.json` jusqu’à 7:1 (texte) ou 3:1 (non-texte), relancer, et reporter la nouvelle valeur dans `docs/SPEC.md` (tableau « Couleurs » et une ligne dans « Historique ») dans le commit de l’étape.

- [ ] **Step 2 : catalogue et test de captures V2**

`app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt` :

```kotlin
package com.maximebier.verso.screenshots

import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.screenshots.samples.JournalSample
import com.maximebier.verso.screenshots.samples.LibraryListSample
import com.maximebier.verso.screenshots.samples.ReaderBarsSample
import com.maximebier.verso.screenshots.samples.ReturnCardSample
import com.maximebier.verso.screenshots.samples.SampleReadingText
import com.maximebier.verso.screenshots.samples.SettingsSample
import com.maximebier.verso.screenshots.samples.TocSheetSample
import com.maximebier.verso.screenshots.samples.DetailsSample

/** Un écran V2 et les palettes où il est capturé (toutes par défaut). */
data class V2ScreenFixture(val screen: ScreenFixture, val themes: List<AppTheme> = AppTheme.entries) {
    override fun toString(): String = screen.id
}

/**
 * Écrans V2 dessinables par Compose. 2.03 et 2.04 : texte de lecture (rendu Compose ; le vrai rendu Readium est
 * vérifié sur le téléphone) et écrans V1 dans les palettes sépia et noire, pour « sans zone d’une autre couleur ».
 */
object V2ScreenCatalog {
    private val paperThemes = listOf(AppTheme.SEPIA, AppTheme.BLACK)

    val fixtures: List<V2ScreenFixture> = listOf(
        V2ScreenFixture(ScreenFixture("2.03-2.04-lecture") { SampleReadingText() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-barre-affichee") { ReaderBarsSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-bibliotheque") { LibraryListSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-details") { DetailsSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-parametres") { SettingsSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-sommaire") { TocSheetSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-journal") { JournalSample() }, paperThemes),
        V2ScreenFixture(ScreenFixture("2.03-2.04-revenir") { ReturnCardSample() }, paperThemes),
    )
}
```

Vérifier le paquetage et le nom exact des échantillons dans `screenshots/samples/*.kt` (`internal fun …Sample()`) ; `SampleReadingText` est dans `ReaderSamples.kt`. Si un échantillon n’est pas `internal` mais `private`, le rendre `internal`.

`app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenshotTest.kt` :

```kotlin
package com.maximebier.verso.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures V2 : chaque écran dans chacune de ses palettes, plus le texte Android à 200 % dans la palette claire
 * (ou la première de la liste). Même convention que V1ScreenshotTest (390 × 844 dp, xxhdpi).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class V2ScreenshotTest(
    private val fixture: V2ScreenFixture,
    private val theme: AppTheme,
    private val variant: ScreenVariant,
) {
    private val composeRule = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(VariantRule(variant)).around(composeRule)

    @Test
    fun capture() {
        fixture.screen.show(composeRule) { content -> VersoTheme(theme = theme) { content() } }
        val suffix = if (variant == ScreenVariant.FONT_200) "${theme.fileSuffix}-texte-200" else theme.fileSuffix
        captureScreenRoboImage("build/outputs/roborazzi/v2/${fixture.screen.id}-$suffix.png")
    }

    /** Suffixe des fichiers : clair, sepia, sombre, noir. */
    private val AppTheme.fileSuffix: String
        get() = when (this) {
            AppTheme.LIGHT -> "clair"
            AppTheme.SEPIA -> "sepia"
            AppTheme.DARK -> "sombre"
            AppTheme.BLACK -> "noir"
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun parameters(): List<Array<Any>> = V2ScreenCatalog.fixtures.flatMap { fixture ->
            fixture.themes.map { arrayOf<Any>(fixture, it, ScreenVariant.LIGHT) } +
                listOf(arrayOf<Any>(fixture, fixture.themes.first(), ScreenVariant.FONT_200))
        }
    }
}
```

(`ScreenVariant.LIGHT` n’applique aucun qualificatif système : la palette vient de `theme`.)

- [ ] **Step 3 : lancer et relire les captures**

Run : `./gradlew :app:recordRoborazziDebug --tests "com.maximebier.verso.screenshots.V2ScreenshotTest"`
Expected : PASS, 24 PNG dans `app/build/outputs/roborazzi/v2/`. Les ouvrir (outil Read) et comparer à `docs/design/screens/2.03-theme-sepia.png` et `2.04-theme-noir.png` : fond, texte, barres, feuilles et cartes dans la palette ; aucune surface claire en noir ni blanche en sépia ; rien de coupé en `texte-200`.

- [ ] **Step 4 : suite complète et commit**

Run : `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected : PASS.

```bash
git add app/src docs
git commit -m "Étape 10 (partie) : captures sépia et noir, contraste des quatre palettes"
```

- [ ] **Step 5 : fin de l’étape 10 (orchestrateur)**

1. Suite complète verte (sortie gardée).
2. Regrouper les commits de l’étape en `Étape 10 : thèmes sépia et noir`, `git push`.
3. `./gradlew :app:installDebug`, lancer, puis dans Paramètres › Affichage choisir Sépia puis Noir (réglage de Verso, pas du téléphone) ; captures : bibliothèque, fiche, Paramètres, lecture (texte Readium en sépia puis en noir, graisse allégée en noir), barres, sommaire, journal, carte « Revenir ». Comparer à 2.03 et 2.04. Remettre le thème de Verso sur sa valeur d’avant la vérification.
4. Cocher les critères 4 et 5 dans `docs/SPEC.md` et `docs/acceptance-v2.md` ; « Automatique suit le thème du téléphone » demande de changer le thème du téléphone : colonne Maxime.
