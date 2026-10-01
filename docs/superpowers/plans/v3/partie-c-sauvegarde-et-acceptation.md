# Verso V3 — partie C : sauvegarde et restauration (étape 20), passe d’acceptation V3 (étape 21)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Plan maître : `docs/superpowers/plans/2026-10-01-verso-v3.md` (Global Constraints, Review Focus n° 4, **Contrat d’architecture**). Design : `docs/superpowers/specs/2026-09-30-verso-v3-design.md` (« Sauvegarde »). Maquette : `docs/design/screens/3.07-sauvegarde.png` et `docs/design/html/3.07-sauvegarde.html`. Critères V3 : 10, 11, 12 (étape 20) ; 13, 14 et revue de tous (étape 21).

**Point de départ supposé (étapes 17 à 19 faites selon le contrat)** :

- `HighlightEntity` (table `highlights`, clé étrangère en cascade vers `books`), `HighlightDao` (dont `insert`), `VersoDatabase` version 3 avec `highlightDao()`, `HighlightEntity.chapterPathList()` et `List<String>.toChapterPathColumn()` (`com.maximebier.verso.data`, 17.2).
- `DocumentStore(context)` avec `openOutput`, `openInput`, `displayName` (`com.maximebier.verso.data`, 19.4) et `AppContainer.documents`.
- `V3ScreenCatalog` / `V3ScreenshotTest` / type `V3ScreenFixture(screen: ScreenFixture, themes: List<AppTheme> = …)` (17.7), couverts par `AccessibilityTreeTest` ; `docs/acceptance-v3.md` (17).

Commandes (Git Bash, depuis la racine du dépôt) :

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :core:test --tests '<classe>'                # :core seul
./gradlew :app:testDebugUnitTest --tests '<classe>'    # une classe de :app
```

Espaces typographiques dans `strings.xml` : **U+202F** avant `: ; ! ?` et `%` ; **U+00A0** dans les tailles et les heures (« 48 Mo », « 21 h 14 »). Dans les blocs XML ci-dessous, ces caractères sont écrits tels quels : les recopier sans les remplacer (`StringsTest` le vérifie). Dans le code Kotlin des tests, ils sont écrits ` ` et ` `.

## Décisions de cette partie (à reporter dans `docs/SPEC.md` à la tâche 20.9)

- **Contenu du zip** (format 1) : `donnees.json` (champ `format`, puis livres, sessions, surlignages), `reglages.json` (clés DataStore avec leur type), `livres/<sha256>.epub`, `couvertures/<sha256>.jpg` (ou `.png`). Le numéro de format est **dans `donnees.json`**, lu en premier par un en-tête qui ignore le reste : une sauvegarde plus récente est refusée même si le reste du JSON a changé de forme.
- **Couvertures** : l’import les écrit en JPEG (`covers/<sha256>.jpg`, `EpubImporter.COVER_EXTENSION`), pas en PNG comme le dit le commentaire de `BookEntity` ; la sauvegarde garde le nom du fichier (`coverFile`), `.jpg` ou `.png`.
- **Intégrité** : à la restauration, l’empreinte SHA-256 de chaque EPUB est recalculée pendant la décompression et comparée à son nom ; un EPUB abîmé rend la sauvegarde invalide. Les entrées inconnues du zip sont ignorées (jamais écrites sur le disque), les fichiers non référencés ne sont pas restaurés.
- **Transaction** : un DAO dédié, `BackupDao` (classe abstraite, `@Transaction`), lit toute la bibliothèque et la remplace en une transaction Room ; pas de `withTransaction` (room-ktx n’est pas une dépendance).
- **Tout ou rien** : dossiers `books` et `covers` renommés `*.avant-restauration`, nouveaux dossiers mis en place, base remplacée, réglages remplacés ; au moindre échec, chaque étape faite est défaite (réglages, base, dossiers), le dossier temporaire est effacé. Limite connue : si Android tue Verso pendant le remplacement lui-même (moins d’une seconde, après confirmation), il n’y a pas de reprise au lancement suivant.
- **Ligne « Sauvegarde »** dans la section **Confidentialité** des Paramètres, juste sous la phrase « Verso n’utilise pas Internet… » : c’est la section des données personnelles, la phrase dit où elles restent et la ligne suivante dit comment les emporter ; pas de nouveau titre de section (aucune maquette n’en dessine).
- **Taille affichée** : entière à partir de 10 Mo (« 48 Mo », comme la maquette), sinon comme la fiche (« 1,2 Mo », « 850 ko »). Taille = celle que donne le fournisseur du document après écriture, à défaut le nombre d’octets écrits ; nom = nom affiché du document, à défaut le nom proposé.
- **Pendant une opération** : boutons désactivés, texte de progression annoncé par TalkBack, retour bloqué ; création et remplacement ne sont jamais annulés à mi-chemin (`NonCancellable`). Un document créé dont l’écriture échoue est supprimé.
- **Libellés proposés** (absents des maquettes, marqués `PROPOSÉ`) : « Aucune sauvegarde pour l’instant », « Création de la sauvegarde… », « Vérification de la sauvegarde… », « Restauration en cours… », « Sauvegarde enregistrée. », les messages d’échec, la confirmation « Remplacer votre bibliothèque ? » et le résumé de la ligne des Paramètres.

---

## Étape 20 : sauvegarde et restauration

Critères V3 couverts : 10, 11, 12.

### Task 20.1 : `BackupManifest` et format du zip (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/maximebier/verso/core/backup/BackupManifest.kt`
- Test: `core/src/test/kotlin/com/maximebier/verso/core/backup/BackupManifestTest.kt`

**Interfaces:**
- Consumes : rien.
- Produces (contrat, complété) :
  - `object BackupFormat { const val VERSION = 1; const val DATA_ENTRY = "donnees.json"; const val SETTINGS_ENTRY = "reglages.json"; const val BOOKS_FOLDER = "livres"; const val COVERS_FOLDER = "couvertures"; const val BOOK_EXTENSION = ".epub"; val COVER_EXTENSIONS: List<String>; fun isSha256(value: String): Boolean; fun bookEntry(sha256: String): String; fun coverEntry(coverFile: String): String; fun isCoverFileOf(sha256: String, coverFile: String): Boolean; fun bookSha256(entry: String): String?; fun isKnownEntry(entry: String): Boolean; fun fileName(date: LocalDate): String; fun checkVersion(format: Int): BackupCheck }`
  - `sealed interface BackupCheck { data object Valid; data object NewerFormat; data class Invalid(val reason: String) }` (contrat)
  - **Ajouts au contrat** : `data class BackupBookRef(val id: Long, val sha256: String, val coverFile: String?)`, `data class BackupItemRef(val id: Long, val bookId: Long)`, `data class BackupManifest(val format: Int, val books: List<BackupBookRef>, val sessions: List<BackupItemRef>, val highlights: List<BackupItemRef>, val entries: Set<String>) { fun check(): BackupCheck }`.

- [ ] **Step 1 : écrire les tests qui échouent**

`core/src/test/kotlin/com/maximebier/verso/core/backup/BackupManifestTest.kt` :

```kotlin
package com.maximebier.verso.core.backup

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class BackupManifestTest {
    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)
    private val allEntries = setOf(
        BackupFormat.DATA_ENTRY,
        BackupFormat.SETTINGS_ENTRY,
        BackupFormat.bookEntry(shaA),
        BackupFormat.coverEntry("$shaA.jpg"),
        BackupFormat.bookEntry(shaB),
    )

    private fun manifest(
        format: Int = BackupFormat.VERSION,
        books: List<BackupBookRef> = listOf(BackupBookRef(1, shaA, "$shaA.jpg"), BackupBookRef(2, shaB, null)),
        sessions: List<BackupItemRef> = listOf(BackupItemRef(10, 1), BackupItemRef(11, 2)),
        highlights: List<BackupItemRef> = listOf(BackupItemRef(20, 2)),
        entries: Set<String> = allEntries,
    ) = BackupManifest(format, books, sessions, highlights, entries)

    @Test
    fun completeBackupIsValid() {
        assertThat(manifest().check()).isEqualTo(BackupCheck.Valid)
    }

    @Test
    fun emptyLibraryIsValid() {
        val empty = manifest(
            books = emptyList(),
            sessions = emptyList(),
            highlights = emptyList(),
            entries = setOf(BackupFormat.DATA_ENTRY, BackupFormat.SETTINGS_ENTRY),
        )
        assertThat(empty.check()).isEqualTo(BackupCheck.Valid)
    }

    @Test
    fun newerFormatIsRefusedBeforeAnythingElse() {
        assertThat(manifest(format = BackupFormat.VERSION + 1, entries = emptySet()).check()).isEqualTo(BackupCheck.NewerFormat)
        assertThat(BackupFormat.checkVersion(BackupFormat.VERSION + 1)).isEqualTo(BackupCheck.NewerFormat)
        assertThat(BackupFormat.checkVersion(BackupFormat.VERSION)).isEqualTo(BackupCheck.Valid)
    }

    @Test
    fun formatZeroIsInvalid() {
        assertThat(manifest(format = 0).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun missingDataOrSettingsIsInvalid() {
        assertThat(manifest(entries = allEntries - BackupFormat.DATA_ENTRY).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(entries = allEntries - BackupFormat.SETTINGS_ENTRY).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun missingEpubIsInvalid() {
        assertThat(manifest(entries = allEntries - BackupFormat.bookEntry(shaB)).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun missingCoverIsInvalid() {
        assertThat(manifest(entries = allEntries - BackupFormat.coverEntry("$shaA.jpg")).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun coverMustBeNamedAfterItsBook() {
        val wrongCover = listOf(BackupBookRef(1, shaA, "$shaB.jpg"), BackupBookRef(2, shaB, null))
        assertThat(manifest(books = wrongCover, entries = allEntries + BackupFormat.coverEntry("$shaB.jpg")).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun sessionOrHighlightOfAnAbsentBookIsInvalid() {
        assertThat(manifest(sessions = listOf(BackupItemRef(10, 3))).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(highlights = listOf(BackupItemRef(20, 3))).check()).isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun duplicateIdsOrFingerprintsAreInvalid() {
        val sameId = listOf(BackupBookRef(1, shaA, null), BackupBookRef(1, shaB, null))
        val sameSha = listOf(BackupBookRef(1, shaA, null), BackupBookRef(2, shaA, null))
        assertThat(manifest(books = sameId).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(books = sameSha).check()).isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(sessions = listOf(BackupItemRef(10, 1), BackupItemRef(10, 2))).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
        assertThat(manifest(highlights = listOf(BackupItemRef(20, 1), BackupItemRef(20, 2))).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun malformedFingerprintIsInvalid() {
        val evil = listOf(BackupBookRef(1, "../../donnees", null))
        assertThat(manifest(books = evil, sessions = emptyList(), highlights = emptyList()).check())
            .isInstanceOf(BackupCheck.Invalid::class.java)
    }

    @Test
    fun onlyBackupEntriesAreKnown() {
        assertThat(BackupFormat.isKnownEntry(BackupFormat.DATA_ENTRY)).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.SETTINGS_ENTRY)).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.bookEntry(shaA))).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.coverEntry("$shaA.jpg"))).isTrue()
        assertThat(BackupFormat.isKnownEntry(BackupFormat.coverEntry("$shaA.png"))).isTrue()
        assertThat(BackupFormat.isKnownEntry("../donnees.json")).isFalse()
        assertThat(BackupFormat.isKnownEntry("livres/../../x.epub")).isFalse()
        assertThat(BackupFormat.isKnownEntry("livres/${shaA.uppercase()}.epub")).isFalse()
        assertThat(BackupFormat.isKnownEntry("couvertures/$shaA.gif")).isFalse()
        assertThat(BackupFormat.isKnownEntry("mimetype")).isFalse()
    }

    @Test
    fun bookFingerprintIsReadFromTheEntryName() {
        assertThat(BackupFormat.bookSha256(BackupFormat.bookEntry(shaA))).isEqualTo(shaA)
        assertThat(BackupFormat.bookSha256(BackupFormat.DATA_ENTRY)).isNull()
        assertThat(BackupFormat.bookSha256("livres/abc.epub")).isNull()
    }

    @Test
    fun fileNameCarriesTheDate() {
        assertThat(BackupFormat.fileName(LocalDate.of(2026, 9, 20))).isEqualTo("verso-sauvegarde-2026-09-20.zip")
    }
}
```

- [ ] **Step 2 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:test --tests 'com.maximebier.verso.core.backup.BackupManifestTest'`
Expected : échec de compilation (`Unresolved reference: BackupManifest`, `BackupFormat`).

- [ ] **Step 3 : implémenter**

`core/src/main/kotlin/com/maximebier/verso/core/backup/BackupManifest.kt` :

```kotlin
package com.maximebier.verso.core.backup

import java.time.LocalDate

/** Format du zip de sauvegarde : JSON (pas une copie de la base), EPUB et couvertures. */
object BackupFormat {
    /** Écrit dans le champ `format` de `donnees.json` ; une sauvegarde d’un numéro supérieur est refusée. */
    const val VERSION = 1
    const val DATA_ENTRY = "donnees.json"
    const val SETTINGS_ENTRY = "reglages.json"
    const val BOOKS_FOLDER = "livres"
    const val COVERS_FOLDER = "couvertures"
    const val BOOK_EXTENSION = ".epub"

    /** Couvertures : JPEG depuis la V2 (EpubImporter), PNG accepté. */
    val COVER_EXTENSIONS: List<String> = listOf(".jpg", ".png")

    private val SHA256 = Regex("[0-9a-f]{64}")

    fun isSha256(value: String): Boolean = SHA256.matches(value)

    fun bookEntry(sha256: String): String = "$BOOKS_FOLDER/$sha256$BOOK_EXTENSION"

    fun coverEntry(coverFile: String): String = "$COVERS_FOLDER/$coverFile"

    /** Nom de couverture permis pour ce livre : `<sha256>.jpg` ou `<sha256>.png`. */
    fun isCoverFileOf(sha256: String, coverFile: String): Boolean = COVER_EXTENSIONS.any { coverFile == sha256 + it }

    /** Empreinte d’une entrée `livres/<sha256>.epub` ; null pour toute autre entrée. */
    fun bookSha256(entry: String): String? {
        val prefix = "$BOOKS_FOLDER/"
        if (!entry.startsWith(prefix) || !entry.endsWith(BOOK_EXTENSION)) return null
        return entry.removePrefix(prefix).removeSuffix(BOOK_EXTENSION).takeIf(::isSha256)
    }

    /**
     * Entrée que la restauration extrait. Tout le reste est ignoré : jamais de chemin choisi par le fichier
     * (`../`, majuscules, autres extensions), donc jamais d’écriture hors du dossier temporaire.
     */
    fun isKnownEntry(entry: String): Boolean =
        entry == DATA_ENTRY || entry == SETTINGS_ENTRY || bookSha256(entry) != null || isCoverEntry(entry)

    private fun isCoverEntry(entry: String): Boolean {
        val prefix = "$COVERS_FOLDER/"
        if (!entry.startsWith(prefix)) return false
        val file = entry.removePrefix(prefix)
        return COVER_EXTENSIONS.any { extension -> file.endsWith(extension) && isSha256(file.removeSuffix(extension)) }
    }

    /** « verso-sauvegarde-2026-09-20.zip ». */
    fun fileName(date: LocalDate): String = "verso-sauvegarde-$date.zip"

    fun checkVersion(format: Int): BackupCheck = when {
        format > VERSION -> BackupCheck.NewerFormat
        format < 1 -> BackupCheck.Invalid("format $format")
        else -> BackupCheck.Valid
    }
}

sealed interface BackupCheck {
    data object Valid : BackupCheck
    data object NewerFormat : BackupCheck
    data class Invalid(val reason: String) : BackupCheck
}

data class BackupBookRef(val id: Long, val sha256: String, val coverFile: String?)

/** Session ou surlignage : son id et le livre auquel il appartient. */
data class BackupItemRef(val id: Long, val bookId: Long)

/** Ce que la restauration a lu : données de `donnees.json` et entrées extraites du zip. */
data class BackupManifest(
    val format: Int,
    val books: List<BackupBookRef>,
    val sessions: List<BackupItemRef>,
    val highlights: List<BackupItemRef>,
    val entries: Set<String>,
) {
    /** Format d’abord (une sauvegarde plus récente est refusée telle quelle), puis fichiers et identifiants. */
    fun check(): BackupCheck {
        val version = BackupFormat.checkVersion(format)
        if (version != BackupCheck.Valid) return version
        if (BackupFormat.DATA_ENTRY !in entries) return BackupCheck.Invalid("${BackupFormat.DATA_ENTRY} absent")
        if (BackupFormat.SETTINGS_ENTRY !in entries) return BackupCheck.Invalid("${BackupFormat.SETTINGS_ENTRY} absent")
        val bookIds = HashSet<Long>()
        val fingerprints = HashSet<String>()
        for (book in books) {
            if (book.id <= 0 || !bookIds.add(book.id)) return BackupCheck.Invalid("livre ${book.id} invalide ou en double")
            if (!BackupFormat.isSha256(book.sha256)) return BackupCheck.Invalid("empreinte mal formée (livre ${book.id})")
            if (!fingerprints.add(book.sha256)) return BackupCheck.Invalid("empreinte en double (livre ${book.id})")
            if (BackupFormat.bookEntry(book.sha256) !in entries) return BackupCheck.Invalid("EPUB absent (livre ${book.id})")
            val cover = book.coverFile
            if (cover != null && (!BackupFormat.isCoverFileOf(book.sha256, cover) || BackupFormat.coverEntry(cover) !in entries)) {
                return BackupCheck.Invalid("couverture absente (livre ${book.id})")
            }
        }
        return checkItems("session", sessions, bookIds) ?: checkItems("surlignage", highlights, bookIds) ?: BackupCheck.Valid
    }

    private fun checkItems(kind: String, items: List<BackupItemRef>, bookIds: Set<Long>): BackupCheck? {
        val seen = HashSet<Long>()
        for (item in items) {
            if (item.id <= 0 || !seen.add(item.id)) return BackupCheck.Invalid("$kind ${item.id} invalide ou en double")
            if (item.bookId !in bookIds) return BackupCheck.Invalid("$kind ${item.id} sans livre ${item.bookId}")
        }
        return null
    }
}
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :core:test --tests 'com.maximebier.verso.core.backup.BackupManifestTest'`
Expected : PASS (14 tests).

- [ ] **Step 5 : commit**

```bash
git add core/src
git commit -m "20.1 : format et validation d’une sauvegarde (BackupManifest)"
```

### Task 20.2 : `BackupDao`, réglages bruts et dernière sauvegarde

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/data/db/BackupDao.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/db/VersoDatabase.kt` (une méthode abstraite ; schéma inchangé, pas de migration)
- Create: `app/src/main/kotlin/com/maximebier/verso/data/RawSetting.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/SettingsRepository.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/data/db/BackupDaoTest.kt`, `app/src/test/kotlin/com/maximebier/verso/data/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes : `HighlightEntity` (17.2), `testBook`, `testSession` (`TestBooks.kt`).
- Produces (**ajouts au contrat**) :
  - `data class LibrarySnapshot(val books: List<BookEntity>, val sessions: List<SessionEntity>, val highlights: List<HighlightEntity>)` (`data.db`)
  - `abstract class BackupDao { suspend fun snapshot(): LibrarySnapshot; suspend fun replaceAll(library: LibrarySnapshot); suspend fun bookCount(): Int; … }` et `VersoDatabase.backupDao(): BackupDao`
  - `data class RawSetting(val key: String, val value: Any)`, `data class LastBackup(val at: Long, val sizeBytes: Long, val fileName: String)` (`data`)
  - `SettingsRepository.lastBackup: Flow<LastBackup?>`, `suspend fun setLastBackup(value: LastBackup)`, `suspend fun exportRaw(): List<RawSetting>`, `suspend fun importRaw(values: List<RawSetting>)`

Le contrat prévoyait `all`/`insertAll`/`clearAll` dans chaque DAO : une transaction Room (`@Transaction`) ne peut appeler que des méthodes de son propre DAO, d’où un DAO de sauvegarde unique. `HighlightDao.all`/`clearAll`/`insertAll` restent tels que la partie A les a faits ; la sauvegarde ne s’en sert pas.

- [ ] **Step 1 : écrire les tests qui échouent**

`app/src/test/kotlin/com/maximebier/verso/data/db/BackupDaoTest.kt` :

```kotlin
package com.maximebier.verso.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDaoTest {
    private lateinit var db: VersoDatabase
    private lateinit var dao: BackupDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.backupDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun highlight(id: Long, bookId: Long) = HighlightEntity(
        id = id,
        bookId = bookId,
        locatorJson = """{"href":"c1.xhtml"}""",
        text = "Il faut cultiver notre jardin",
        note = "Fin",
        progression = 0.99,
        chapterPath = "XXX",
        createdAt = 1L,
        updatedAt = 2L,
    )

    @Test
    fun snapshotReturnsTheWholeLibraryInIdOrder() = runTest {
        val second = db.bookDao().insert(testBook("b"))
        val first = db.bookDao().insert(testBook("a"))
        db.sessionDao().upsert(testSession(first, startedAt = 10L))
        db.highlightDao().insert(highlight(0, second))

        val snapshot = dao.snapshot()

        assertThat(snapshot.books.map { it.id }).containsExactly(second, first).inOrder()
        assertThat(snapshot.sessions.single().bookId).isEqualTo(first)
        assertThat(snapshot.highlights.single().bookId).isEqualTo(second)
        assertThat(dao.bookCount()).isEqualTo(2)
    }

    @Test
    fun replaceAllKeepsTheOriginalIds() = runTest {
        db.bookDao().insert(testBook("ancien"))
        val library = LibrarySnapshot(
            books = listOf(testBook("x").copy(id = 7), testBook("y").copy(id = 9)),
            sessions = listOf(testSession(bookId = 9, startedAt = 5L, id = 42)),
            highlights = listOf(highlight(id = 13, bookId = 7)),
        )

        dao.replaceAll(library)

        assertThat(dao.snapshot()).isEqualTo(library)
    }

    @Test
    fun failedReplaceKeepsThePreviousLibrary() = runTest {
        val id = db.bookDao().insert(testBook("garde"))
        db.sessionDao().upsert(testSession(id, startedAt = 1L))
        val before = dao.snapshot()
        val broken = LibrarySnapshot(
            books = listOf(testBook("double").copy(id = 1), testBook("double").copy(id = 2)),
            sessions = emptyList(),
            highlights = emptyList(),
        )

        val failure = runCatching { dao.replaceAll(broken) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLiteConstraintException::class.java)

        assertThat(dao.snapshot()).isEqualTo(before)
    }
}
```

Ajouter à `SettingsRepositoryTest.kt` (imports : `kotlinx.coroutines.flow.first` s’il manque, `com.maximebier.verso.core.settings.ReadingSettingsLimits`) :

```kotlin
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
```

- [ ] **Step 2 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.data.db.BackupDaoTest' --tests 'com.maximebier.verso.data.SettingsRepositoryTest'`
Expected : échec de compilation (`backupDao`, `LibrarySnapshot`, `LastBackup`, `RawSetting`, `exportRaw` inconnus).

- [ ] **Step 3 : implémenter**

`app/src/main/kotlin/com/maximebier/verso/data/db/BackupDao.kt` :

```kotlin
package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Toute la bibliothèque : livres, sessions et surlignages, dans l’ordre des id. */
data class LibrarySnapshot(
    val books: List<BookEntity>,
    val sessions: List<SessionEntity>,
    val highlights: List<HighlightEntity>,
)

/** Lecture et remplacement de toute la bibliothèque (sauvegarde et restauration), chacun en une transaction. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM books ORDER BY id")
    abstract suspend fun books(): List<BookEntity>

    @Query("SELECT * FROM sessions ORDER BY id")
    abstract suspend fun sessions(): List<SessionEntity>

    @Query("SELECT * FROM highlights ORDER BY id")
    abstract suspend fun highlights(): List<HighlightEntity>

    @Query("SELECT COUNT(*) FROM books")
    abstract suspend fun bookCount(): Int

    @Query("DELETE FROM highlights")
    abstract suspend fun deleteHighlights()

    @Query("DELETE FROM sessions")
    abstract suspend fun deleteSessions()

    @Query("DELETE FROM books")
    abstract suspend fun deleteBooks()

    /** ABORT : une empreinte ou un id en double fait échouer toute la transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSessions(sessions: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertHighlights(highlights: List<HighlightEntity>)

    /** Lecture cohérente : aucune écriture ne s’intercale entre les trois tables. */
    @Transaction
    open suspend fun snapshot(): LibrarySnapshot = LibrarySnapshot(books(), sessions(), highlights())

    /** Vide puis remplit les trois tables avec les id d’origine ; tout ou rien. */
    @Transaction
    open suspend fun replaceAll(library: LibrarySnapshot) {
        deleteHighlights()
        deleteSessions()
        deleteBooks()
        insertBooks(library.books)
        insertSessions(library.sessions)
        insertHighlights(library.highlights)
    }
}
```

Dans `VersoDatabase.kt`, sous `abstract fun highlightDao(): HighlightDao` (17.2) :

```kotlin
    abstract fun backupDao(): BackupDao
```

`app/src/main/kotlin/com/maximebier/verso/data/RawSetting.kt` :

```kotlin
package com.maximebier.verso.data

/** Une clé DataStore et sa valeur typée : Boolean, Int, Long, Float, Double, String ou Set<String>. */
data class RawSetting(val key: String, val value: Any) {
    init {
        require(isSupported(value)) { "Type de réglage non pris en charge : ${value::class.simpleName}" }
    }

    companion object {
        fun isSupported(value: Any): Boolean =
            value is Boolean || value is Int || value is Long || value is Float || value is Double || value is String ||
                (value is Set<*> && value.all { it is String })
    }
}

/** Carte « Dernière sauvegarde » (3.07) : propre à ce téléphone, jamais dans la sauvegarde. */
data class LastBackup(val at: Long, val sizeBytes: Long, val fileName: String)
```

Dans `SettingsRepository.kt`, imports à ajouter :

```kotlin
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
```

Ajouter dans la classe, avant `save` :

```kotlin
    /** Dernière sauvegarde créée sur ce téléphone ; null tant qu’aucune n’a été enregistrée. */
    val lastBackup: Flow<LastBackup?> = preferences.map { prefs ->
        val at = prefs[LAST_BACKUP_AT]
        val size = prefs[LAST_BACKUP_SIZE]
        val name = prefs[LAST_BACKUP_NAME]
        if (at != null && size != null && name != null) LastBackup(at, size, name) else null
    }.distinctUntilChanged()

    suspend fun setLastBackup(value: LastBackup) {
        save {
            it[LAST_BACKUP_AT] = value.at
            it[LAST_BACKUP_SIZE] = value.sizeBytes
            it[LAST_BACKUP_NAME] = value.fileName
        }
    }

    /**
     * Toutes les clés avec leur type, triées par nom, sauf la carte « Dernière sauvegarde » (propre au téléphone).
     * Contrairement aux lectures de l’écran, une lecture ratée lève l’IOException : la sauvegarde échoue plutôt que
     * d’enregistrer des réglages vides.
     */
    suspend fun exportRaw(): List<RawSetting> = dataStore.data.first().asMap()
        .filterKeys { it.name !in DEVICE_ONLY_KEYS }
        .filterValues(RawSetting::isSupported)
        .map { (key, value) -> RawSetting(key.name, value) }
        .sortedBy { it.key }

    /**
     * Remplace tous les réglages par [values] en une seule écriture DataStore, sauf la carte « Dernière sauvegarde »,
     * gardée. Une écriture ratée lève l’IOException (la restauration revient alors en arrière).
     */
    suspend fun importRaw(values: List<RawSetting>) {
        dataStore.edit { prefs ->
            val kept = prefs.asMap().filterKeys { it.name in DEVICE_ONLY_KEYS }.map { (key, value) -> RawSetting(key.name, value) }
            prefs.clear()
            (values.filter { it.key !in DEVICE_ONLY_KEYS } + kept).forEach { prefs.putRaw(it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun MutablePreferences.putRaw(setting: RawSetting) {
        when (val value = setting.value) {
            is Boolean -> this[booleanPreferencesKey(setting.key)] = value
            is Int -> this[intPreferencesKey(setting.key)] = value
            is Long -> this[longPreferencesKey(setting.key)] = value
            is Float -> this[floatPreferencesKey(setting.key)] = value
            is Double -> this[doublePreferencesKey(setting.key)] = value
            is String -> this[stringPreferencesKey(setting.key)] = value
            is Set<*> -> this[stringSetPreferencesKey(setting.key)] = value as Set<String>
            else -> error("Type de réglage non pris en charge : ${value::class.simpleName}")
        }
    }
```

Dans le `private companion object`, ajouter :

```kotlin
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_SIZE = longPreferencesKey("last_backup_size")
        val LAST_BACKUP_NAME = stringPreferencesKey("last_backup_name")

        /** Clés propres au téléphone : ni sauvegardées, ni remplacées par une restauration. */
        val DEVICE_ONLY_KEYS = setOf(LAST_BACKUP_AT.name, LAST_BACKUP_SIZE.name, LAST_BACKUP_NAME.name)
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.data.db.BackupDaoTest' --tests 'com.maximebier.verso.data.SettingsRepositoryTest' --tests 'com.maximebier.verso.data.db.VersoDatabaseMigrationTest'`
Expected : PASS (la migration reste verte : un DAO ne change pas le schéma ; `app/schemas/…/3.json` inchangé, `git status` le confirme).

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "20.2 : BackupDao, réglages bruts et dernière sauvegarde"
```

### Task 20.3 : données JSON de la sauvegarde (DTO et conversions)

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/backup/BackupDtos.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/backup/BackupMapping.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/backup/BackupMappingTest.kt`

**Interfaces:**
- Consumes : `BackupFormat`, `BackupManifest`, `BackupBookRef`, `BackupItemRef` (20.1) ; `RawSetting` (20.2) ; `HighlightEntity.chapterPathList()`, `List<String>.toChapterPathColumn()` (17.2).
- Produces (**ajouts au contrat**, `com.maximebier.verso.backup`) : `BackupJson: Json` ; `@Serializable` `BackupHeaderDto(format)`, `BackupDataDto(format, createdAt, appVersion, books, sessions, highlights)`, `BookDto`, `SessionDto`, `HighlightDto`, `SettingDto(key, type, value: JsonElement)`, `SettingsFileDto(settings)` ; `BookEntity.toDto(coverFile: String?): BookDto`, `BookDto.toEntity(booksDir: File, coversDir: File): BookEntity`, `SessionEntity.toDto()`, `SessionDto.toEntity()`, `HighlightEntity.toDto()`, `HighlightDto.toEntity()`, `RawSetting.toDto()`, `SettingDto.toRawSetting()`, `BackupDataDto.manifest(entries: Set<String>): BackupManifest`.

- [ ] **Step 1 : écrire les tests qui échouent**

`app/src/test/kotlin/com/maximebier/verso/backup/BackupMappingTest.kt` :

```kotlin
package com.maximebier.verso.backup

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupBookRef
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.core.backup.BackupItemRef
import com.maximebier.verso.data.RawSetting
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupMappingTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sha = "c".repeat(64)

    @Test
    fun bookKeepsItsDataAndGetsThePathsOfThisPhone() {
        val book = testBook(sha, title = "Madame Bovary", author = "Gustave Flaubert", filePath = "/ancien/books/$sha.epub", coverPath = "/ancien/covers/$sha.jpg")
            .copy(id = 7, lastOpenedAt = 99L, readingLocatorJson = """{"href":"c2.xhtml"}""", progression = 0.31, scrollMode = "PAGES", stateOverride = "FINISHED")
        val booksDir = tmp.newFolder("books")
        val coversDir = tmp.newFolder("covers")

        val dto = book.toDto(coverFile = "$sha.jpg")
        val restored = dto.toEntity(booksDir, coversDir)

        assertThat(dto.coverFile).isEqualTo("$sha.jpg")
        assertThat(restored).isEqualTo(
            book.copy(filePath = File(booksDir, "$sha.epub").absolutePath, coverPath = File(coversDir, "$sha.jpg").absolutePath),
        )
    }

    @Test
    fun bookWithoutCoverHasNoCoverPath() {
        val dto = testBook(sha).copy(id = 1).toDto(coverFile = null)
        assertThat(dto.toEntity(tmp.newFolder("b"), tmp.newFolder("c")).coverPath).isNull()
    }

    @Test
    fun sessionsAndHighlightsAreUnchanged() {
        val session = testSession(bookId = 7, startedAt = 1_000L, id = 42)
        val highlight = HighlightEntity(
            id = 13, bookId = 7, locatorJson = """{"href":"c1.xhtml"}""", text = "Elle s’ennuyait", note = "Le cœur du livre",
            progression = 0.12, chapterPath = "Première partie\nI", createdAt = 5L, updatedAt = 6L,
        )
        assertThat(session.toDto().toEntity()).isEqualTo(session)
        assertThat(highlight.toDto().chapterPath).containsExactly("Première partie", "I").inOrder()
        assertThat(highlight.toDto().toEntity()).isEqualTo(highlight)
    }

    @Test
    fun everySettingTypeSurvivesJson() {
        val settings = listOf(
            RawSetting("b", true), RawSetting("d", 1.5), RawSetting("f", 2.5f), RawSetting("i", 3),
            RawSetting("l", 4L), RawSetting("s", "texte"), RawSetting("set", setOf("x", "y")),
        )
        val json = BackupJson.encodeToString(SettingsFileDto.serializer(), SettingsFileDto(settings.map { it.toDto() }))
        val back = BackupJson.decodeFromString(SettingsFileDto.serializer(), json).settings.map { it.toRawSetting() }
        assertThat(back).isEqualTo(settings)
    }

    @Test
    fun unknownSettingTypeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { SettingDto("x", "date", JsonPrimitive("hier")).toRawSetting() }
        assertThrows(IllegalArgumentException::class.java) { SettingDto("x", "int", JsonPrimitive("pas un nombre")).toRawSetting() }
    }

    @Test
    fun headerIsReadWhateverTheRestOfTheFile() {
        val header = BackupJson.decodeFromString(BackupHeaderDto.serializer(), """{"format":2,"livres":{"nouveau":true}}""")
        assertThat(header.format).isEqualTo(2)
    }

    @Test
    fun dataSurvivesJsonAndDescribesItsManifest() {
        val data = BackupDataDto(
            format = BackupFormat.VERSION,
            createdAt = 1L,
            appVersion = "3.0.0",
            books = listOf(testBook(sha).copy(id = 7).toDto(coverFile = "$sha.jpg")),
            sessions = listOf(testSession(bookId = 7, startedAt = 1L, id = 42).toDto()),
            highlights = emptyList(),
        )
        val back = BackupJson.decodeFromString(BackupDataDto.serializer(), BackupJson.encodeToString(BackupDataDto.serializer(), data))
        assertThat(back).isEqualTo(data)

        val manifest = back.manifest(setOf("donnees.json"))
        assertThat(manifest.books).containsExactly(BackupBookRef(7, sha, "$sha.jpg"))
        assertThat(manifest.sessions).containsExactly(BackupItemRef(42, 7))
        assertThat(manifest.entries).containsExactly("donnees.json")
    }
}
```

- [ ] **Step 2 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.BackupMappingTest'`
Expected : échec de compilation (paquet `backup` vide).

- [ ] **Step 3 : implémenter**

`app/src/main/kotlin/com/maximebier/verso/backup/BackupDtos.kt` :

```kotlin
package com.maximebier.verso.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** JSON de la sauvegarde : champs inconnus ignorés (une version future peut en ajouter sans changer de format). */
val BackupJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Lu en premier : seul le numéro de format, quel que soit le reste du fichier. */
@Serializable
data class BackupHeaderDto(val format: Int)

/** `donnees.json`. Aucun chemin de fichier : ils sont recalculés sur le téléphone qui restaure. */
@Serializable
data class BackupDataDto(
    val format: Int,
    val createdAt: Long,
    val appVersion: String,
    val books: List<BookDto>,
    val sessions: List<SessionDto>,
    val highlights: List<HighlightDto>,
)

@Serializable
data class BookDto(
    val id: Long,
    val title: String,
    val author: String,
    val sha256: String,
    /** Nom de la couverture dans `couvertures/` (`<sha256>.jpg` ou `.png`) ; null sans couverture. */
    val coverFile: String?,
    val sizeBytes: Long,
    val originalFileName: String,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val readingLocatorJson: String?,
    val progression: Double,
    val totalWords: Long,
    val scrollMode: String?,
    val stateOverride: String?,
)

@Serializable
data class SessionDto(
    val id: Long,
    val bookId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val activeMs: Long,
    val startLocatorJson: String,
    val endLocatorJson: String,
    val startProgression: Double,
    val endProgression: Double,
    val wordsRead: Long,
)

@Serializable
data class HighlightDto(
    val id: Long,
    val bookId: Long,
    val locatorJson: String,
    val text: String,
    val note: String?,
    val progression: Double,
    val chapterPath: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Une clé DataStore : [type] parmi boolean, int, long, float, double, string, stringSet. */
@Serializable
data class SettingDto(val key: String, val type: String, val value: JsonElement)

/** `reglages.json`. */
@Serializable
data class SettingsFileDto(val settings: List<SettingDto>)
```

`app/src/main/kotlin/com/maximebier/verso/backup/BackupMapping.kt` :

```kotlin
package com.maximebier.verso.backup

import com.maximebier.verso.core.backup.BackupBookRef
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.core.backup.BackupItemRef
import com.maximebier.verso.core.backup.BackupManifest
import com.maximebier.verso.data.RawSetting
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.SessionEntity
import com.maximebier.verso.data.toChapterPathColumn
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun BookEntity.toDto(coverFile: String?): BookDto = BookDto(
    id = id,
    title = title,
    author = author,
    sha256 = sha256,
    coverFile = coverFile,
    sizeBytes = sizeBytes,
    originalFileName = originalFileName,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    readingLocatorJson = readingLocatorJson,
    progression = progression,
    totalWords = totalWords,
    scrollMode = scrollMode,
    stateOverride = stateOverride,
)

/** Chemins réécrits vers ce téléphone : `booksDir/<sha256>.epub` et `coversDir/<coverFile>`. */
fun BookDto.toEntity(booksDir: File, coversDir: File): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    filePath = File(booksDir, sha256 + BackupFormat.BOOK_EXTENSION).absolutePath,
    sha256 = sha256,
    coverPath = coverFile?.let { File(coversDir, it).absolutePath },
    sizeBytes = sizeBytes,
    originalFileName = originalFileName,
    importedAt = importedAt,
    lastOpenedAt = lastOpenedAt,
    readingLocatorJson = readingLocatorJson,
    progression = progression,
    totalWords = totalWords,
    scrollMode = scrollMode,
    stateOverride = stateOverride,
)

fun SessionEntity.toDto(): SessionDto = SessionDto(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = endedAt,
    activeMs = activeMs,
    startLocatorJson = startLocatorJson,
    endLocatorJson = endLocatorJson,
    startProgression = startProgression,
    endProgression = endProgression,
    wordsRead = wordsRead,
)

fun SessionDto.toEntity(): SessionEntity = SessionEntity(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    endedAt = endedAt,
    activeMs = activeMs,
    startLocatorJson = startLocatorJson,
    endLocatorJson = endLocatorJson,
    startProgression = startProgression,
    endProgression = endProgression,
    wordsRead = wordsRead,
)

fun HighlightEntity.toDto(): HighlightDto = HighlightDto(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    text = text,
    note = note,
    progression = progression,
    chapterPath = chapterPathList(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun HighlightDto.toEntity(): HighlightEntity = HighlightEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    text = text,
    note = note,
    progression = progression,
    chapterPath = chapterPath.toChapterPathColumn(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RawSetting.toDto(): SettingDto = when (val v = value) {
    is Boolean -> SettingDto(key, "boolean", JsonPrimitive(v))
    is Int -> SettingDto(key, "int", JsonPrimitive(v))
    is Long -> SettingDto(key, "long", JsonPrimitive(v))
    is Float -> SettingDto(key, "float", JsonPrimitive(v))
    is Double -> SettingDto(key, "double", JsonPrimitive(v))
    is String -> SettingDto(key, "string", JsonPrimitive(v))
    is Set<*> -> SettingDto(key, "stringSet", JsonArray(v.map { JsonPrimitive(it as String) }))
    else -> throw IllegalArgumentException("Type de réglage non pris en charge : ${v::class.simpleName}")
}

/** IllegalArgumentException si le type est inconnu ou la valeur mal formée (la sauvegarde est alors invalide). */
fun SettingDto.toRawSetting(): RawSetting {
    val primitive = value as? JsonPrimitive
    val parsed: Any? = when (type) {
        "boolean" -> primitive?.booleanOrNull
        "int" -> primitive?.intOrNull
        "long" -> primitive?.longOrNull
        "float" -> primitive?.floatOrNull
        "double" -> primitive?.doubleOrNull
        "string" -> primitive?.takeIf { it.isString }?.content
        "stringSet" -> (value as? JsonArray)?.map { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("Réglage $key : élément non texte")
        }?.toSet()
        else -> throw IllegalArgumentException("Réglage $key : type « $type » inconnu")
    }
    requireNotNull(parsed) { "Réglage $key : valeur incompatible avec le type « $type »" }
    return RawSetting(key, parsed)
}

fun BackupDataDto.manifest(entries: Set<String>): BackupManifest = BackupManifest(
    format = format,
    books = books.map { BackupBookRef(it.id, it.sha256, it.coverFile) },
    sessions = sessions.map { BackupItemRef(it.id, it.bookId) },
    highlights = highlights.map { BackupItemRef(it.id, it.bookId) },
    entries = entries,
)
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.BackupMappingTest'`
Expected : PASS (7 tests).

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "20.3 : données JSON de la sauvegarde"
```

### Task 20.4 : `BackupWriter`, zip écrit en flux

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/backup/BackupWriter.kt`
- Create (tests, partagé avec 20.5 et 20.6) : `app/src/test/kotlin/com/maximebier/verso/backup/BackupTestLibrary.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/backup/BackupWriterTest.kt`

**Interfaces:**
- Consumes : `BackupDao.snapshot()`, `SettingsRepository.exportRaw()` (20.2) ; DTO et conversions (20.3) ; `BackupFormat` (20.1).
- Produces (**ajout au contrat**) : `class BackupWriter(dao: BackupDao, settings: SettingsRepository, appVersion: String, clock: () -> Long) { suspend fun write(output: OutputStream): Long }` — renvoie le nombre d’octets écrits ; ne ferme pas [output].

- [ ] **Step 1 : écrire la bibliothèque de test et les tests qui échouent**

`app/src/test/kotlin/com/maximebier/verso/backup/BackupTestLibrary.kt` (la fonction `restorer` ne compile qu’à la tâche 20.5 : l’écrire maintenant, en commentaire, puis la décommenter en 20.5) :

```kotlin
package com.maximebier.verso.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.LibrarySnapshot
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.data.testSession
import com.maximebier.verso.importer.Sha256
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope

/** Un téléphone de test : base en mémoire, dossiers books/covers et DataStore sous [root]/[name]. */
class BackupTestLibrary(root: File, scope: CoroutineScope, name: String = "telephone") {
    val db: VersoDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val booksDir: File = File(root, "$name/files/books").apply { mkdirs() }
    val coversDir: File = File(root, "$name/files/covers").apply { mkdirs() }
    val workDir: File = File(root, "$name/cache/restauration")
    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(root, "$name/settings.preferences_pb") })
    val books = BookRepository(db.bookDao(), booksDir, coversDir)

    fun writer(): BackupWriter = BackupWriter(db.backupDao(), settings, appVersion = "3.0.0", clock = { NOW })

    // fun restorer(onStep: (RestoreStep) -> Unit = {}): BackupRestorer =
    //     BackupRestorer(db.backupDao(), settings, booksDir, coversDir, workDir, onStep)

    suspend fun snapshot(): LibrarySnapshot = db.backupDao().snapshot()

    /** Fichiers des dossiers books et covers : « books/<nom> » → contenu. */
    fun files(): Map<String, List<Byte>> = (booksDir.listFiles().orEmpty() + coversDir.listFiles().orEmpty())
        .associate { "${it.parentFile!!.name}/${it.name}" to it.readBytes().toList() }

    /** Range un EPUB (et une couverture JPEG) comme l’import, puis lit et règle le livre comme après une lecture. */
    suspend fun addBook(title: String, withCover: Boolean = false): BookEntity {
        val bytes = "EPUB de $title".toByteArray()
        val sha = Sha256.of(bytes.inputStream())
        val epub = File(booksDir, "$sha.epub").apply { writeBytes(bytes) }
        val cover = if (withCover) File(coversDir, "$sha.jpg").apply { writeBytes("JPEG de $title".toByteArray()) } else null
        val id = books.insert(testBook(sha, title = title, filePath = epub.absolutePath, coverPath = cover?.absolutePath))
        books.saveReadingPosition(id, """{"href":"c2.xhtml","locations":{"totalProgression":0.31}}""", 0.31)
        books.markOpened(id, 1_000L + id)
        books.setScrollMode(id, ScrollMode.PAGES)
        return db.bookDao().byId(id)!!
    }

    /** Trois livres (deux avec couverture), deux sessions, deux surlignages (un annoté), réglages changés. */
    suspend fun seed(): List<BookEntity> {
        val bovary = addBook("Madame Bovary", withCover = true)
        val candide = addBook("Candide")
        val silo = addBook("Silo", withCover = true)
        db.sessionDao().upsert(testSession(bovary.id, startedAt = 1_000L))
        db.sessionDao().upsert(testSession(silo.id, startedAt = 2_000L))
        db.highlightDao().insert(highlight(bovary.id, "Elle s’ennuyait", note = "Le cœur du livre"))
        db.highlightDao().insert(highlight(candide.id, "Il faut cultiver notre jardin", note = null))
        settings.setThemeMode(ThemeMode.DARK)
        settings.updateReadingSettings { it.withFontSize(22) }
        settings.setReopenLastBook(false)
        return listOf(bovary, candide, silo)
    }

    fun close() = db.close()

    companion object {
        const val NOW = 1_790_000_000_000L

        fun highlight(bookId: Long, text: String, note: String?) = HighlightEntity(
            bookId = bookId,
            locatorJson = """{"href":"c1.xhtml","text":{"highlight":"$text"}}""",
            text = text,
            note = note,
            progression = 0.12,
            chapterPath = "Première partie\nI",
            createdAt = 5_000L,
            updatedAt = 6_000L,
        )
    }
}

/** Recopie [source] dans [target] : [change] renvoie les nouveaux octets d’une entrée, ou null pour l’enlever ; [extra] est ajouté. */
fun rezip(
    source: File,
    target: File,
    extra: Map<String, ByteArray> = emptyMap(),
    change: (name: String, bytes: ByteArray) -> ByteArray? = { _, bytes -> bytes },
) {
    ZipFile(source).use { zip ->
        ZipOutputStream(target.outputStream()).use { out ->
            for (entry in zip.entries()) {
                val bytes = change(entry.name, zip.getInputStream(entry).use { it.readBytes() }) ?: continue
                out.putNextEntry(ZipEntry(entry.name))
                out.write(bytes)
                out.closeEntry()
            }
            extra.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }
}

/** Zip fait main : [entries] nom → contenu. */
fun zipOf(target: File, entries: Map<String, ByteArray>): File {
    ZipOutputStream(target.outputStream()).use { out ->
        entries.forEach { (name, bytes) ->
            out.putNextEntry(ZipEntry(name))
            out.write(bytes)
            out.closeEntry()
        }
    }
    return target
}
```

`app/src/test/kotlin/com/maximebier/verso/backup/BackupWriterTest.kt` :

```kotlin
package com.maximebier.verso.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.LastBackup
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun zipHoldsDataSettingsBooksAndCovers() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, candide, silo) = phone.seed()
        phone.settings.setLastBackup(LastBackup(at = 1L, sizeBytes = 2L, fileName = "ancienne.zip"))
        val zipFile = File(tmp.root, "sauvegarde.zip")

        val written = zipFile.outputStream().use { phone.writer().write(it) }

        assertThat(written).isEqualTo(zipFile.length())
        ZipFile(zipFile).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertThat(names).containsExactly(
                BackupFormat.DATA_ENTRY,
                BackupFormat.SETTINGS_ENTRY,
                BackupFormat.bookEntry(bovary.sha256),
                BackupFormat.coverEntry("${bovary.sha256}.jpg"),
                BackupFormat.bookEntry(candide.sha256),
                BackupFormat.bookEntry(silo.sha256),
                BackupFormat.coverEntry("${silo.sha256}.jpg"),
            ).inOrder()

            val data = BackupJson.decodeFromString(
                BackupDataDto.serializer(),
                zip.getInputStream(zip.getEntry(BackupFormat.DATA_ENTRY)).use { it.readBytes().decodeToString() },
            )
            assertThat(data.format).isEqualTo(BackupFormat.VERSION)
            assertThat(data.createdAt).isEqualTo(BackupTestLibrary.NOW)
            assertThat(data.books.map { it.title }).containsExactly("Madame Bovary", "Candide", "Silo").inOrder()
            assertThat(data.books.map { it.coverFile }).containsExactly("${bovary.sha256}.jpg", null, "${silo.sha256}.jpg").inOrder()
            assertThat(data.sessions).hasSize(2)
            assertThat(data.highlights.map { it.note }).containsExactly("Le cœur du livre", null).inOrder()

            val settings = BackupJson.decodeFromString(
                SettingsFileDto.serializer(),
                zip.getInputStream(zip.getEntry(BackupFormat.SETTINGS_ENTRY)).use { it.readBytes().decodeToString() },
            ).settings
            assertThat(settings.map { it.key }).contains("theme_mode")
            assertThat(settings.map { it.key }.filter { it.startsWith("last_backup") }).isEmpty()

            val epub = zip.getInputStream(zip.getEntry(BackupFormat.bookEntry(candide.sha256))).use { it.readBytes() }
            assertThat(epub).isEqualTo(File(candide.filePath).readBytes())
        }
        phone.close()
    }

    @Test
    fun emptyLibraryGivesAValidZip() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val zipFile = File(tmp.root, "vide.zip")

        zipFile.outputStream().use { phone.writer().write(it) }

        ZipFile(zipFile).use { zip ->
            assertThat(zip.entries().toList().map { it.name }).containsExactly(BackupFormat.DATA_ENTRY, BackupFormat.SETTINGS_ENTRY)
        }
        phone.close()
    }

    @Test
    fun missingEpubMakesTheBackupFail() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val book = phone.addBook("Fantôme")
        File(book.filePath).delete()

        val failure = runCatching { File(tmp.root, "x.zip").outputStream().use { phone.writer().write(it) } }

        assertThat(failure.exceptionOrNull()).isInstanceOf(java.io.FileNotFoundException::class.java)
        phone.close()
    }
}
```

- [ ] **Step 2 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.BackupWriterTest'`
Expected : échec de compilation (`Unresolved reference: BackupWriter`).

- [ ] **Step 3 : implémenter**

`app/src/main/kotlin/com/maximebier/verso/backup/BackupWriter.kt` :

```kotlin
package com.maximebier.verso.backup

import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.BackupDao
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écrit la sauvegarde en flux : `donnees.json`, `reglages.json`, puis chaque EPUB et sa couverture, copiés par blocs
 * (jamais un livre entier en mémoire). EPUB et JPEG sont déjà compressés : ils sont stockés sans recompression.
 */
class BackupWriter(
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val appVersion: String,
    private val clock: () -> Long,
) {
    /** Renvoie le nombre d’octets écrits. Ne ferme pas [output]. FileNotFoundException si un EPUB a disparu. */
    suspend fun write(output: OutputStream): Long = withContext(Dispatchers.IO) {
        val library = dao.snapshot()
        val rawSettings = settings.exportRaw()
        val covers: Map<Long, File?> = library.books.associate { book ->
            book.id to book.coverPath?.let(::File)?.takeIf { it.isFile && BackupFormat.isCoverFileOf(book.sha256, it.name) }
        }
        val data = BackupDataDto(
            format = BackupFormat.VERSION,
            createdAt = clock(),
            appVersion = appVersion,
            books = library.books.map { it.toDto(coverFile = covers[it.id]?.name) },
            sessions = library.sessions.map { it.toDto() },
            highlights = library.highlights.map { it.toDto() },
        )
        val counting = CountingOutputStream(output)
        val zip = ZipOutputStream(BufferedOutputStream(counting, BUFFER_SIZE))
        zip.putText(BackupFormat.DATA_ENTRY, BackupJson.encodeToString(BackupDataDto.serializer(), data))
        zip.putText(
            BackupFormat.SETTINGS_ENTRY,
            BackupJson.encodeToString(SettingsFileDto.serializer(), SettingsFileDto(rawSettings.map { it.toDto() })),
        )
        zip.setLevel(Deflater.NO_COMPRESSION)
        for (book in library.books) {
            val epub = File(book.filePath)
            if (!epub.isFile) throw FileNotFoundException("EPUB absent pour le livre ${book.id}")
            zip.putFile(BackupFormat.bookEntry(book.sha256), epub)
            covers[book.id]?.let { zip.putFile(BackupFormat.coverEntry(it.name), it) }
        }
        zip.finish()
        zip.flush()
        counting.count
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.encodeToByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this, BUFFER_SIZE) }
        closeEntry()
    }

    /** Compte les octets réellement passés au document choisi. */
    private class CountingOutputStream(private val target: OutputStream) : FilterOutputStream(target) {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.BackupWriterTest'`
Expected : PASS (3 tests).

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "20.4 : BackupWriter, zip de sauvegarde écrit en flux"
```

### Task 20.5 : `BackupRestorer`, validation complète puis remplacement tout ou rien

Review Focus n° 4. Relecture de cette tâche avant de passer à la suivante (remplacement des données de Maxime).

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/backup/BackupRestorer.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/backup/BackupTestLibrary.kt` (décommenter `restorer`)
- Test: `app/src/test/kotlin/com/maximebier/verso/backup/BackupRestorerTest.kt`

**Interfaces:**
- Consumes : `BackupDao.snapshot()`, `replaceAll()`, `bookCount()`, `SettingsRepository.exportRaw()`, `importRaw()` (20.2) ; DTO et conversions (20.3) ; `BackupManifest.check()`, `BackupFormat` (20.1) ; `Sha256.copyAndHash` (`importer`).
- Produces (**ajouts au contrat**, `com.maximebier.verso.backup`) :
  - `enum class RestoreStep { FILES_SWAPPED, DATABASE_REPLACED, SETTINGS_REPLACED }`
  - `sealed interface RestorePreparation { data class Ready(val currentBooks: Int, val backupBooks: Int); data object NewerFormat; data object Invalid; data object Failed }`
  - `enum class RestoreResult { RESTORED, FAILED }`
  - `class BackupRestorer(dao: BackupDao, settings: SettingsRepository, booksDir: File, coversDir: File, workDir: File, onStep: (RestoreStep) -> Unit = {}) { suspend fun prepare(input: InputStream): RestorePreparation; suspend fun apply(): RestoreResult; suspend fun discard() }`

- [ ] **Step 1 : écrire les tests qui échouent**

Dans `BackupTestLibrary.kt`, décommenter `restorer(…)`.

`app/src/test/kotlin/com/maximebier/verso/backup/BackupRestorerTest.kt` :

```kotlin
package com.maximebier.verso.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.importer.Sha256
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestorerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun File.asideOf(): File = File(parentFile, "$name.avant-restauration")

    /** Sauvegarde de [phone] dans un fichier du dossier de test. */
    private suspend fun backupOf(phone: BackupTestLibrary, name: String = "sauvegarde.zip"): File {
        val zip = File(tmp.root, name)
        zip.outputStream().use { phone.writer().write(it) }
        return zip
    }

    private fun assertNoLeftovers(phone: BackupTestLibrary) {
        assertThat(phone.workDir.exists()).isFalse()
        assertThat(phone.booksDir.asideOf().exists()).isFalse()
        assertThat(phone.coversDir.asideOf().exists()).isFalse()
    }

    @Test
    fun restoreAfterDeletingTwoBooksBringsEverythingBack() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, candide, _) = phone.seed()
        val library = phone.snapshot()
        val files = phone.files()
        val settings = phone.settings.exportRaw()
        val zip = backupOf(phone)
        phone.settings.setLastBackup(LastBackup(at = 7L, sizeBytes = zip.length(), fileName = "verso-sauvegarde-2026-09-20.zip"))
        phone.books.delete(bovary.id)
        phone.books.delete(candide.id)
        phone.settings.setThemeMode(ThemeMode.LIGHT)
        val restorer = phone.restorer()

        assertThat(zip.inputStream().use { restorer.prepare(it) })
            .isEqualTo(RestorePreparation.Ready(currentBooks = 1, backupBooks = 3))
        assertThat(restorer.apply()).isEqualTo(RestoreResult.RESTORED)

        assertThat(phone.snapshot()).isEqualTo(library)
        assertThat(phone.files()).isEqualTo(files)
        assertThat(phone.settings.exportRaw()).isEqualTo(settings)
        assertThat(phone.settings.lastBackup.first()?.fileName).isEqualTo("verso-sauvegarde-2026-09-20.zip")
        assertNoLeftovers(phone)
        phone.close()
    }

    @Test
    fun restoreOnAnotherPhoneRewritesThePaths() = runTest {
        val source = BackupTestLibrary(tmp.root, backgroundScope, name = "ancien")
        source.seed()
        val zip = backupOf(source)
        val target = BackupTestLibrary(tmp.root, backgroundScope, name = "nouveau")
        target.addBook("Germinal")
        val restorer = target.restorer()

        assertThat(zip.inputStream().use { restorer.prepare(it) }).isEqualTo(RestorePreparation.Ready(currentBooks = 1, backupBooks = 3))
        assertThat(restorer.apply()).isEqualTo(RestoreResult.RESTORED)

        val restored = target.snapshot()
        val original = source.snapshot()
        assertThat(restored.books.map { it.filePath }).containsExactlyElementsIn(
            original.books.map { File(target.booksDir, "${it.sha256}.epub").absolutePath },
        )
        assertThat(restored.books.map { it.copy(filePath = "", coverPath = null) })
            .isEqualTo(original.books.map { it.copy(filePath = "", coverPath = null) })
        assertThat(restored.sessions).isEqualTo(original.sessions)
        assertThat(restored.highlights).isEqualTo(original.highlights)
        assertThat(target.files()).isEqualTo(source.files())
        assertThat(target.settings.exportRaw()).isEqualTo(source.settings.exportRaw())
        assertNoLeftovers(target)
        source.close()
        target.close()
    }

    @Test
    fun invalidFilesLeaveTheLibraryIntact() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (_, candide, _) = phone.seed()
        val zip = backupOf(phone)
        phone.books.delete(candide.id)
        val library = phone.snapshot()
        val files = phone.files()
        val settings = phone.settings.exportRaw()
        val bytes = zip.readBytes()
        val truncated = File(tmp.root, "tronque.zip").apply { writeBytes(bytes.copyOf(bytes.size / 2)) }
        val notABackup = zipOf(File(tmp.root, "livre.zip"), mapOf("mimetype" to "application/epub+zip".toByteArray(), "OEBPS/content.opf" to "<package/>".toByteArray()))
        val notAZip = File(tmp.root, "texte.zip").apply { writeText("pas un zip") }
        val missingEpub = File(tmp.root, "sans-candide.zip").also { rezip(zip, it) { name, data -> data.takeUnless { name == BackupFormat.bookEntry(candide.sha256) } } }
        val alteredEpub = File(tmp.root, "abime.zip").also {
            rezip(zip, it) { name, data -> if (name == BackupFormat.bookEntry(candide.sha256)) "abîmé".toByteArray() else data }
        }
        val badSettings = File(tmp.root, "reglages.zip").also {
            rezip(zip, it) { name, data -> if (name == BackupFormat.SETTINGS_ENTRY) """{"settings":[{"key":"x","type":"date","value":"hier"}]}""".toByteArray() else data }
        }
        val restorer = phone.restorer()

        for (file in listOf(truncated, notABackup, notAZip, missingEpub, alteredEpub, badSettings)) {
            assertThat(file.inputStream().use { restorer.prepare(it) }).isEqualTo(RestorePreparation.Invalid)
            assertThat(phone.workDir.exists()).isFalse()
        }
        assertThat(restorer.apply()).isEqualTo(RestoreResult.FAILED)

        assertThat(phone.snapshot()).isEqualTo(library)
        assertThat(phone.files()).isEqualTo(files)
        assertThat(phone.settings.exportRaw()).isEqualTo(settings)
        phone.close()
    }

    @Test
    fun newerFormatIsRefused() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val newer = zipOf(
            File(tmp.root, "v2.zip"),
            mapOf(
                BackupFormat.DATA_ENTRY to """{"format":${BackupFormat.VERSION + 1},"bibliotheque":{"nouveau":true}}""".toByteArray(),
                BackupFormat.SETTINGS_ENTRY to "{}".toByteArray(),
            ),
        )

        assertThat(newer.inputStream().use { phone.restorer().prepare(it) }).isEqualTo(RestorePreparation.NewerFormat)
        assertThat(phone.workDir.exists()).isFalse()
        phone.close()
    }

    @Test
    fun failureDuringReplacementPutsEverythingBack() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, candide, _) = phone.seed()
        val zip = backupOf(phone)
        phone.books.delete(bovary.id)
        phone.books.delete(candide.id)
        phone.settings.setThemeMode(ThemeMode.LIGHT)
        val library = phone.snapshot()
        val files = phone.files()
        val settings = phone.settings.exportRaw()

        for (step in RestoreStep.entries) {
            val restorer = phone.restorer(onStep = { if (it == step) throw IOException("Plus d’espace disque ($step)") })
            assertThat(zip.inputStream().use { restorer.prepare(it) }).isInstanceOf(RestorePreparation.Ready::class.java)

            assertThat(restorer.apply()).isEqualTo(RestoreResult.FAILED)

            assertThat(phone.snapshot()).isEqualTo(library)
            assertThat(phone.files()).isEqualTo(files)
            assertThat(phone.settings.exportRaw()).isEqualTo(settings)
            assertNoLeftovers(phone)
        }
        phone.close()
    }

    @Test
    fun unreferencedFilesAreNotRestored() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        phone.seed()
        val orphan = "orphelin".toByteArray()
        val orphanSha = Sha256.of(orphan.inputStream())
        val zip = File(tmp.root, "avec-orphelin.zip").also {
            rezip(backupOf(phone), it, extra = mapOf(BackupFormat.bookEntry(orphanSha) to orphan, "notes/lisez-moi.txt" to "x".toByteArray()))
        }
        val restorer = phone.restorer()

        assertThat(zip.inputStream().use { restorer.prepare(it) }).isInstanceOf(RestorePreparation.Ready::class.java)
        assertThat(restorer.apply()).isEqualTo(RestoreResult.RESTORED)

        assertThat(File(phone.booksDir, "$orphanSha.epub").exists()).isFalse()
        phone.close()
    }

    @Test
    fun discardForgetsThePreparedBackup() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        phone.seed()
        val zip = backupOf(phone)
        val restorer = phone.restorer()
        zip.inputStream().use { restorer.prepare(it) }
        assertThat(phone.workDir.exists()).isTrue()

        restorer.discard()

        assertThat(phone.workDir.exists()).isFalse()
        assertThat(restorer.apply()).isEqualTo(RestoreResult.FAILED)
        phone.close()
    }
}
```

- [ ] **Step 2 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.BackupRestorerTest'`
Expected : échec de compilation (`Unresolved reference: BackupRestorer`, `RestoreStep`, `RestorePreparation`).

- [ ] **Step 3 : implémenter**

`app/src/main/kotlin/com/maximebier/verso/backup/BackupRestorer.kt` :

```kotlin
package com.maximebier.verso.backup

import android.util.Log
import com.maximebier.verso.core.backup.BackupCheck
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.RawSetting
import com.maximebier.verso.data.SettingsRepository
import com.maximebier.verso.data.db.BackupDao
import com.maximebier.verso.data.db.LibrarySnapshot
import com.maximebier.verso.importer.Sha256
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Étapes du remplacement, signalées à [BackupRestorer] `onStep` (tests : une exception y simule une panne). */
enum class RestoreStep { FILES_SWAPPED, DATABASE_REPLACED, SETTINGS_REPLACED }

sealed interface RestorePreparation {
    /** Sauvegarde lue et valide, prête à remplacer ; nombres de livres pour la confirmation. */
    data class Ready(val currentBooks: Int, val backupBooks: Int) : RestorePreparation
    data object NewerFormat : RestorePreparation
    /** Pas une sauvegarde Verso, incomplète ou abîmée. */
    data object Invalid : RestorePreparation
    /** Lecture du document ou écriture locale impossible (fichier inaccessible, espace disque). */
    data object Failed : RestorePreparation
}

enum class RestoreResult { RESTORED, FAILED }

/**
 * Restauration en deux temps. [prepare] décompresse tout dans [workDir] et valide tout (format, JSON, réglages,
 * empreinte de chaque EPUB, références) sans rien toucher. [apply], après confirmation, remplace dossiers, base et
 * réglages ; au moindre échec, chaque étape faite est défaite. Le dossier temporaire est toujours effacé à la fin.
 */
class BackupRestorer(
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val booksDir: File,
    private val coversDir: File,
    private val workDir: File,
    private val onStep: (RestoreStep) -> Unit = {},
) {
    private class Prepared(val library: LibrarySnapshot, val settings: List<RawSetting>)

    /** Fichier qui n’est pas une sauvegarde valide ; jamais une IOException (celles-ci veulent dire « échec local »). */
    private class InvalidBackupException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    private val mutex = Mutex()
    private var prepared: Prepared? = null

    suspend fun prepare(input: InputStream): RestorePreparation = mutex.withLock {
        withContext(Dispatchers.IO) {
            prepared = null
            workDir.deleteRecursively()
            val result = try {
                readAndCheck(input)
            } catch (e: InvalidBackupException) {
                Log.w(TAG, "Sauvegarde invalide : ${e.message}", e)
                RestorePreparation.Invalid
            } catch (e: IllegalArgumentException) { // JSON mal formé (SerializationException) ou réglage incompatible
                Log.w(TAG, "Sauvegarde invalide", e)
                RestorePreparation.Invalid
            } catch (e: IOException) {
                Log.w(TAG, "Lecture de la sauvegarde impossible", e)
                RestorePreparation.Failed
            }
            if (result !is RestorePreparation.Ready) workDir.deleteRecursively()
            result
        }
    }

    private suspend fun readAndCheck(input: InputStream): RestorePreparation {
        if (!workDir.mkdirs()) throw IOException("Dossier temporaire impossible à créer : $workDir")
        val entries = extract(input)
        val dataText = File(workDir, BackupFormat.DATA_ENTRY).takeIf { it.isFile }?.readText()
            ?: throw InvalidBackupException("${BackupFormat.DATA_ENTRY} absent")
        val header = BackupJson.decodeFromString(BackupHeaderDto.serializer(), dataText)
        if (BackupFormat.checkVersion(header.format) == BackupCheck.NewerFormat) return RestorePreparation.NewerFormat
        val data = BackupJson.decodeFromString(BackupDataDto.serializer(), dataText)
        val settingsText = File(workDir, BackupFormat.SETTINGS_ENTRY).takeIf { it.isFile }?.readText()
            ?: throw InvalidBackupException("${BackupFormat.SETTINGS_ENTRY} absent")
        val rawSettings = BackupJson.decodeFromString(SettingsFileDto.serializer(), settingsText).settings.map { it.toRawSetting() }
        when (val check = data.manifest(entries).check()) {
            BackupCheck.Valid -> Unit
            BackupCheck.NewerFormat -> return RestorePreparation.NewerFormat
            is BackupCheck.Invalid -> throw InvalidBackupException(check.reason)
        }
        removeUnreferenced(entries, data)
        prepared = Prepared(
            library = LibrarySnapshot(
                books = data.books.map { it.toEntity(booksDir, coversDir) },
                sessions = data.sessions.map { it.toEntity() },
                highlights = data.highlights.map { it.toEntity() },
            ),
            settings = rawSettings,
        )
        return RestorePreparation.Ready(currentBooks = dao.bookCount(), backupBooks = data.books.size)
    }

    /**
     * Décompresse les seules entrées connues ([BackupFormat.isKnownEntry]) dans [workDir] ; recalcule l’empreinte de
     * chaque EPUB. Erreur de lecture du zip → InvalidBackupException ; erreur d’écriture locale → IOException.
     */
    private fun extract(input: InputStream): Set<String> {
        val names = mutableSetOf<String>()
        val zip = ZipInputStream(BufferedInputStream(input, BUFFER_SIZE))
        val guarded = ZipReadGuard(zip)
        while (true) {
            val entry = guarded.nextEntry() ?: break
            val name = entry.name
            if (entry.isDirectory || !BackupFormat.isKnownEntry(name)) continue
            if (!names.add(name)) throw InvalidBackupException("Entrée en double : $name")
            val target = File(workDir, name)
            target.parentFile?.mkdirs()
            val expectedSha = BackupFormat.bookSha256(name)
            target.outputStream().use { out ->
                if (expectedSha != null) {
                    val actual = Sha256.copyAndHash(guarded, out)
                    if (actual != expectedSha) throw InvalidBackupException("EPUB abîmé : $name")
                } else {
                    guarded.copyTo(out, BUFFER_SIZE)
                }
            }
        }
        return names
    }

    /** EPUB et couvertures présents dans le zip mais d’aucun livre : effacés avant la mise en place. */
    private fun removeUnreferenced(entries: Set<String>, data: BackupDataDto) {
        val referenced = data.books.flatMap { book ->
            listOfNotNull(BackupFormat.bookEntry(book.sha256), book.coverFile?.let(BackupFormat::coverEntry))
        }.toSet()
        entries.filter { it != BackupFormat.DATA_ENTRY && it != BackupFormat.SETTINGS_ENTRY && it !in referenced }
            .forEach { File(workDir, it).delete() }
    }

    suspend fun apply(): RestoreResult = mutex.withLock {
        // Jamais interrompu à mi-chemin, même si l’écran qui l’a lancé disparaît.
        withContext(NonCancellable + Dispatchers.IO) {
            val ready = prepared ?: return@withContext RestoreResult.FAILED
            prepared = null
            val booksAside = asideOf(booksDir)
            val coversAside = asideOf(coversDir)
            var previousLibrary: LibrarySnapshot? = null
            var previousSettings: List<RawSetting>? = null
            var databaseReplaced = false
            var settingsReplaced = false
            val result = try {
                previousLibrary = dao.snapshot()
                previousSettings = settings.exportRaw()
                booksAside.deleteRecursively()
                coversAside.deleteRecursively()
                booksDir.mkdirs()
                coversDir.mkdirs()
                moveDir(booksDir, booksAside)
                moveDir(coversDir, coversAside)
                moveDir(File(workDir, BackupFormat.BOOKS_FOLDER), booksDir)
                moveDir(File(workDir, BackupFormat.COVERS_FOLDER), coversDir)
                onStep(RestoreStep.FILES_SWAPPED)
                dao.replaceAll(ready.library)
                databaseReplaced = true
                onStep(RestoreStep.DATABASE_REPLACED)
                settings.importRaw(ready.settings)
                settingsReplaced = true
                onStep(RestoreStep.SETTINGS_REPLACED)
                RestoreResult.RESTORED
            } catch (e: Exception) {
                Log.w(TAG, "Restauration interrompue : retour à l’état d’avant", e)
                if (settingsReplaced) previousSettings?.let { old -> undo("réglages") { settings.importRaw(old) } }
                if (databaseReplaced) previousLibrary?.let { old -> undo("base") { dao.replaceAll(old) } }
                undo("livres") { putBack(booksDir, booksAside) }
                undo("couvertures") { putBack(coversDir, coversAside) }
                RestoreResult.FAILED
            }
            if (result == RestoreResult.RESTORED) {
                booksAside.deleteRecursively()
                coversAside.deleteRecursively()
            }
            workDir.deleteRecursively()
            result
        }
    }

    /** Oublie la sauvegarde préparée (confirmation refusée). */
    suspend fun discard() = mutex.withLock {
        withContext(Dispatchers.IO) {
            prepared = null
            workDir.deleteRecursively()
        }
    }

    private fun asideOf(dir: File): File = File(dir.parentFile, dir.name + ASIDE_SUFFIX)

    /** Renommage (même volume), sinon copie complète puis effacement ; une copie ratée ne laisse pas de cible partielle. */
    private fun moveDir(source: File, target: File) {
        if (!source.exists()) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Dossier impossible à créer : $target")
            return
        }
        if (source.renameTo(target)) return
        try {
            source.copyRecursively(target, overwrite = false)
        } catch (e: Exception) {
            target.deleteRecursively()
            throw e
        }
        source.deleteRecursively()
    }

    /** Remet le dossier mis de côté, s’il l’a été. */
    private fun putBack(dir: File, aside: File) {
        if (!aside.exists()) return
        dir.deleteRecursively()
        moveDir(aside, dir)
    }

    private suspend fun undo(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Retour en arrière impossible ($what)", e)
        }
    }

    /** Lecture du zip : toute IOException veut dire « fichier illisible », jamais « disque local ». */
    private class ZipReadGuard(private val zip: ZipInputStream) : FilterInputStream(zip) {
        fun nextEntry() = guard { zip.nextEntry }

        override fun read(): Int = guard { zip.read() }

        override fun read(b: ByteArray, off: Int, len: Int): Int = guard { zip.read(b, off, len) }

        private inline fun <T> guard(block: () -> T): T = try {
            block()
        } catch (e: IOException) {
            throw InvalidBackupException("Zip illisible ou tronqué", e)
        }
    }

    private companion object {
        const val TAG = "BackupRestorer"
        const val BUFFER_SIZE = 64 * 1024
        const val ASIDE_SUFFIX = ".avant-restauration"
    }
}
```

Note : `copyTo(out, BUFFER_SIZE)` sur `guarded` appelle `read(ByteArray, Int, Int)`, donc la garde s’applique ; `Sha256.copyAndHash` appelle `read(ByteArray)`, que `FilterInputStream` renvoie vers `read(b, 0, b.size)`.

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.*'`
Expected : PASS (`BackupRestorerTest` : 7 tests ; `BackupWriterTest` et `BackupMappingTest` restent verts). Si `truncated` donne `Ready` (troncature tombée pile entre deux entrées complètes), c’est que `Candide` ou `Silo` manque : le manifeste doit alors répondre `Invalid` ; vérifier `BackupManifest.check()` plutôt que de changer la longueur de troncature.

- [ ] **Step 5 : relecture ciblée, puis commit**

Relire `apply()` en suivant chaque `RestoreStep` : à chaque point de panne, quel dossier est où, quelle base, quels réglages ; dossier temporaire et dossiers `*.avant-restauration` effacés dans tous les cas.

```bash
git add app/src
git commit -m "20.5 : BackupRestorer, validation complète et remplacement tout ou rien"
```

### Task 20.6 : `BackupService`, `DocumentStore` et `AppContainer.backups`

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/backup/BackupService.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/DocumentStore.kt` (19.4)
- Modify: `app/src/main/kotlin/com/maximebier/verso/AppContainer.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/backup/BackupServiceTest.kt`

**Interfaces:**
- Consumes : `BackupWriter` (20.4), `BackupRestorer` (20.5), `DocumentStore.openOutput/openInput/displayName` (19.4), `SettingsRepository.lastBackup/setLastBackup` (20.2), `BackupFormat.fileName` (20.1).
- Produces :
  - **Ajouts au contrat** : `DocumentStore.size(uri: Uri): Long?`, `DocumentStore.delete(uri: Uri): Boolean` ; `interface Backups { val lastBackup: Flow<LastBackup?>; fun suggestedFileName(): String; suspend fun create(uri: Uri): Boolean; suspend fun prepareRestore(uri: Uri): RestorePreparation; suspend fun confirmRestore(): RestoreResult; suspend fun cancelRestore() }` (interface consommée par le ViewModel, remplaçable dans ses tests).
  - Contrat : `class BackupService(writer: BackupWriter, restorer: BackupRestorer, documents: DocumentStore, settings: SettingsRepository, clock: () -> Long, zone: () -> ZoneId = ZoneId::systemDefault) : Backups` ; `BackupService.MIME_TYPE = "application/zip"`, `BackupService.RESTORE_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")` ; `AppContainer.backups: BackupService`.

- [ ] **Step 1 : écrire les tests qui échouent**

`app/src/test/kotlin/com/maximebier/verso/backup/BackupServiceTest.kt` (URI `file://` : `DocumentStore` passe par `ContentResolver`, que Robolectric laisse ouvrir les fichiers ; si `DocumentStoreTest` (19.4) a dû enregistrer des flux avec `shadowOf(contentResolver)`, faire de même ici) :

```kotlin
package com.maximebier.verso.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.DocumentStore
import com.maximebier.verso.data.LastBackup
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val paris = ZoneId.of("Europe/Paris")
    private val at = LocalDateTime.of(2026, 9, 20, 21, 14).atZone(paris).toInstant().toEpochMilli()

    private fun service(phone: BackupTestLibrary) = BackupService(
        writer = phone.writer(),
        restorer = phone.restorer(),
        documents = DocumentStore(context),
        settings = phone.settings,
        clock = { at },
        zone = { paris },
    )

    @Test
    fun suggestedNameCarriesTheLocalDate() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        assertThat(service(phone).suggestedFileName()).isEqualTo("verso-sauvegarde-2026-09-20.zip")
        phone.close()
    }

    @Test
    fun createWritesTheZipAndRemembersDateSizeAndName() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        phone.seed()
        val target = File(tmp.newFolder("drive"), "verso-sauvegarde-2026-09-20.zip")

        assertThat(service(phone).create(Uri.fromFile(target))).isTrue()

        assertThat(phone.settings.lastBackup.first()).isEqualTo(LastBackup(at, target.length(), "verso-sauvegarde-2026-09-20.zip"))
        ZipFile(target).use { assertThat(it.getEntry(BackupFormat.DATA_ENTRY)).isNotNull() }
        phone.close()
    }

    @Test
    fun failedCreationKeepsThePreviousCard() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val directory = tmp.newFolder("pas-un-fichier")

        assertThat(service(phone).create(Uri.fromFile(directory))).isFalse()

        assertThat(phone.settings.lastBackup.first()).isNull()
        phone.close()
    }

    @Test
    fun missingDocumentCannotBeRestored() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        assertThat(service(phone).prepareRestore(Uri.fromFile(File(tmp.root, "absent.zip")))).isEqualTo(RestorePreparation.Failed)
        phone.close()
    }

    @Test
    fun restoreGoesThroughPreparationThenConfirmation() = runTest {
        val phone = BackupTestLibrary(tmp.root, backgroundScope)
        val (bovary, _, _) = phone.seed()
        val target = File(tmp.newFolder("drive"), "verso-sauvegarde-2026-09-20.zip")
        val service = service(phone)
        service.create(Uri.fromFile(target))
        phone.books.delete(bovary.id)

        assertThat(service.prepareRestore(Uri.fromFile(target))).isEqualTo(RestorePreparation.Ready(currentBooks = 2, backupBooks = 3))
        assertThat(service.confirmRestore()).isEqualTo(RestoreResult.RESTORED)

        assertThat(phone.snapshot().books).hasSize(3)
        phone.close()
    }
}
```

- [ ] **Step 2 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.BackupServiceTest'`
Expected : échec de compilation (`Unresolved reference: BackupService`).

- [ ] **Step 3 : implémenter**

Dans `DocumentStore.kt` (19.4), imports `android.provider.DocumentsContract`, `android.provider.OpenableColumns` s’ils manquent, puis sous `displayName` (le champ `Context` y est supposé s’appeler `context` ; reprendre le nom réel) :

```kotlin
    /** Taille annoncée par le fournisseur du document ; null s’il ne la donne pas. */
    fun size(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    /** Supprime un document créé par le sélecteur (écriture ratée) ; false si le fournisseur refuse. */
    fun delete(uri: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
```

`app/src/main/kotlin/com/maximebier/verso/backup/BackupService.kt` :

```kotlin
package com.maximebier.verso.backup

import android.net.Uri
import android.util.Log
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.DocumentStore
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.data.SettingsRepository
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Ce que l’écran « Sauvegarde » demande (remplacé par un double dans les tests du ViewModel). */
interface Backups {
    val lastBackup: Flow<LastBackup?>
    fun suggestedFileName(): String
    /** true si le zip est entièrement écrit et la carte « Dernière sauvegarde » mise à jour. */
    suspend fun create(uri: Uri): Boolean
    suspend fun prepareRestore(uri: Uri): RestorePreparation
    suspend fun confirmRestore(): RestoreResult
    suspend fun cancelRestore()
}

/** Sauvegarde et restauration à travers les documents choisis par les sélecteurs Android. Aucun réseau dans Verso. */
class BackupService(
    private val writer: BackupWriter,
    private val restorer: BackupRestorer,
    private val documents: DocumentStore,
    private val settings: SettingsRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : Backups {

    override val lastBackup: Flow<LastBackup?> = settings.lastBackup

    override fun suggestedFileName(): String =
        BackupFormat.fileName(Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate())

    override suspend fun create(uri: Uri): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        try {
            val written = documents.openOutput(uri).use { writer.write(it) }
            settings.setLastBackup(
                LastBackup(
                    at = clock(),
                    sizeBytes = documents.size(uri) ?: written,
                    fileName = documents.displayName(uri) ?: suggestedFileName(),
                ),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // IOException, SecurityException, IllegalStateException du fournisseur
            Log.w(TAG, "Sauvegarde impossible", e)
            documents.delete(uri)
            false
        }
    }

    override suspend fun prepareRestore(uri: Uri): RestorePreparation = try {
        withContext(Dispatchers.IO) { documents.openInput(uri).use { restorer.prepare(it) } }
    } catch (e: IOException) {
        Log.w(TAG, "Document de sauvegarde illisible", e)
        restorer.discard()
        RestorePreparation.Failed
    } catch (e: SecurityException) {
        Log.w(TAG, "Document de sauvegarde refusé", e)
        restorer.discard()
        RestorePreparation.Failed
    }

    override suspend fun confirmRestore(): RestoreResult = restorer.apply()

    override suspend fun cancelRestore() = restorer.discard()

    companion object {
        const val MIME_TYPE = "application/zip"
        /** Certains fournisseurs (Drive, partages réseau) présentent un zip comme octet-stream. */
        val RESTORE_MIME_TYPES: Array<String> = arrayOf("application/zip", "application/octet-stream")
        private const val TAG = "BackupService"
    }
}
```

Dans `AppContainer.kt`, imports `com.maximebier.verso.backup.BackupRestorer`, `BackupService`, `BackupWriter` ; à la fin de la classe (après `clock` et `documents`, l’ordre d’initialisation compte) :

```kotlin
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
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.backup.*' --tests 'com.maximebier.verso.VersoApplicationTest'`
Expected : PASS (`BackupServiceTest` : 5 tests ; `VersoApplicationTest` construit toujours le conteneur).

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "20.6 : BackupService et AppContainer.backups"
```

### Task 20.7 : `BackupViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/settings/BackupViewModel.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/settings/BackupViewModelTest.kt`

**Interfaces:**
- Consumes : `Backups`, `RestorePreparation`, `RestoreResult` (20.5, 20.6), `LastBackup` (20.2), `AppContainer.backups`.
- Produces (**ajouts au contrat**, `ui.settings`) : `enum class BackupBusy { CREATING, CHECKING, RESTORING }`, `enum class BackupMessage { CREATED, CREATE_FAILED, RESTORE_INVALID, RESTORE_NEWER, RESTORE_FAILED }`, `data class RestoreConfirmation(val currentBooks: Int, val backupBooks: Int)`, `data class BackupUiState(lastBackup, busy, confirmation, message, restored)`, `class BackupViewModel(backups: Backups)` avec `state`, `suggestedFileName()`, `onCreateDocument(uri: Uri?)`, `onRestoreDocument(uri: Uri?)`, `onRestoreConfirm()`, `onRestoreDismiss()`, `onMessageShown(shown: BackupMessage)`, `onRestoredHandled()`, `Factory`.

- [ ] **Step 1 : écrire les tests qui échouent**

`app/src/test/kotlin/com/maximebier/verso/ui/settings/BackupViewModelTest.kt` :

```kotlin
package com.maximebier.verso.ui.settings

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.backup.Backups
import com.maximebier.verso.backup.RestorePreparation
import com.maximebier.verso.backup.RestoreResult
import com.maximebier.verso.data.LastBackup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {

    private class FakeBackups : Backups {
        override val lastBackup = MutableStateFlow<LastBackup?>(null)
        var created = CompletableDeferred<Boolean>()
        var preparation = CompletableDeferred<RestorePreparation>()
        var restored = CompletableDeferred<RestoreResult>()
        val createdUris = mutableListOf<Uri>()
        var cancelled = 0

        override fun suggestedFileName() = "verso-sauvegarde-2026-09-20.zip"
        override suspend fun create(uri: Uri): Boolean {
            createdUris += uri
            return created.await()
        }
        override suspend fun prepareRestore(uri: Uri) = preparation.await()
        override suspend fun confirmRestore() = restored.await()
        override suspend fun cancelRestore() {
            cancelled++
        }
    }

    private val uri = Uri.parse("content://documents/verso-sauvegarde-2026-09-20.zip")
    private val backups = FakeBackups()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(): BackupViewModel {
        val vm = BackupViewModel(backups)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun lastBackupComesFromTheService() = runTest {
        val vm = viewModel()
        backups.lastBackup.value = LastBackup(1L, 2L, "a.zip")
        assertThat(vm.state.value.lastBackup).isEqualTo(LastBackup(1L, 2L, "a.zip"))
        assertThat(vm.suggestedFileName()).isEqualTo("verso-sauvegarde-2026-09-20.zip")
    }

    @Test
    fun creationShowsProgressThenItsResult() = runTest {
        val vm = viewModel()

        vm.onCreateDocument(uri)
        assertThat(vm.state.value.busy).isEqualTo(BackupBusy.CREATING)
        vm.onCreateDocument(uri) // second toucher pendant la création : ignoré
        backups.created.complete(true)

        assertThat(backups.createdUris).containsExactly(uri)
        assertThat(vm.state.value.busy).isNull()
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.CREATED)
        vm.onMessageShown(BackupMessage.CREATED)
        assertThat(vm.state.value.message).isNull()
    }

    @Test
    fun failedCreationSaysSo() = runTest {
        val vm = viewModel()
        vm.onCreateDocument(uri)
        backups.created.complete(false)
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.CREATE_FAILED)
    }

    @Test
    fun closedPickerDoesNothing() = runTest {
        val vm = viewModel()
        vm.onCreateDocument(null)
        vm.onRestoreDocument(null)
        assertThat(vm.state.value).isEqualTo(BackupUiState())
        assertThat(backups.createdUris).isEmpty()
    }

    @Test
    fun restoreAsksConfirmationWithBothCountsThenGoesToTheLibrary() = runTest {
        val vm = viewModel()

        vm.onRestoreDocument(uri)
        assertThat(vm.state.value.busy).isEqualTo(BackupBusy.CHECKING)
        backups.preparation.complete(RestorePreparation.Ready(currentBooks = 1, backupBooks = 3))
        assertThat(vm.state.value.confirmation).isEqualTo(RestoreConfirmation(currentBooks = 1, backupBooks = 3))
        assertThat(vm.state.value.busy).isNull()

        vm.onRestoreConfirm()
        assertThat(vm.state.value.confirmation).isNull()
        assertThat(vm.state.value.busy).isEqualTo(BackupBusy.RESTORING)
        backups.restored.complete(RestoreResult.RESTORED)

        assertThat(vm.state.value.restored).isTrue()
        vm.onRestoredHandled()
        assertThat(vm.state.value.restored).isFalse()
    }

    @Test
    fun refusedPreparationsGiveTheirMessage() = runTest {
        val vm = viewModel()
        val cases = mapOf(
            RestorePreparation.Invalid to BackupMessage.RESTORE_INVALID,
            RestorePreparation.NewerFormat to BackupMessage.RESTORE_NEWER,
            RestorePreparation.Failed to BackupMessage.RESTORE_FAILED,
        )
        for ((preparation, message) in cases) {
            backups.preparation = CompletableDeferred(preparation)
            vm.onRestoreDocument(uri)
            assertThat(vm.state.value.message).isEqualTo(message)
            assertThat(vm.state.value.confirmation).isNull()
            vm.onMessageShown(message)
        }
    }

    @Test
    fun dismissingTheConfirmationForgetsTheBackup() = runTest {
        val vm = viewModel()
        backups.preparation.complete(RestorePreparation.Ready(1, 3))
        vm.onRestoreDocument(uri)

        vm.onRestoreDismiss()

        assertThat(vm.state.value.confirmation).isNull()
        assertThat(backups.cancelled).isEqualTo(1)
    }

    @Test
    fun failedRestoreSaysTheLibraryIsUnchanged() = runTest {
        val vm = viewModel()
        backups.preparation.complete(RestorePreparation.Ready(1, 3))
        vm.onRestoreDocument(uri)
        vm.onRestoreConfirm()
        backups.restored.complete(RestoreResult.FAILED)

        assertThat(vm.state.value.restored).isFalse()
        assertThat(vm.state.value.message).isEqualTo(BackupMessage.RESTORE_FAILED)
    }
}
```

- [ ] **Step 2 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.settings.BackupViewModelTest'`
Expected : échec de compilation (`Unresolved reference: BackupViewModel`).

- [ ] **Step 3 : implémenter**

`app/src/main/kotlin/com/maximebier/verso/ui/settings/BackupViewModel.kt` :

```kotlin
package com.maximebier.verso.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.backup.Backups
import com.maximebier.verso.backup.RestorePreparation
import com.maximebier.verso.backup.RestoreResult
import com.maximebier.verso.data.LastBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Opération en cours : boutons désactivés, texte de progression, retour bloqué. */
enum class BackupBusy { CREATING, CHECKING, RESTORING }

enum class BackupMessage { CREATED, CREATE_FAILED, RESTORE_INVALID, RESTORE_NEWER, RESTORE_FAILED }

data class RestoreConfirmation(val currentBooks: Int, val backupBooks: Int)

/** État de l’écran « Sauvegarde » (3.07). [restored] : aller à la bibliothèque restaurée. */
data class BackupUiState(
    val lastBackup: LastBackup? = null,
    val busy: BackupBusy? = null,
    val confirmation: RestoreConfirmation? = null,
    val message: BackupMessage? = null,
    val restored: Boolean = false,
)

class BackupViewModel(private val backups: Backups) : ViewModel() {

    private val transient = MutableStateFlow(BackupUiState())

    val state: StateFlow<BackupUiState> = combine(backups.lastBackup, transient) { last, current -> current.copy(lastBackup = last) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun suggestedFileName(): String = backups.suggestedFileName()

    /** Retour du sélecteur « Créer un document » ; null = sélecteur fermé sans choix. */
    fun onCreateDocument(uri: Uri?) {
        if (uri == null || !start(BackupBusy.CREATING)) return
        viewModelScope.launch {
            val created = backups.create(uri)
            transient.update { it.copy(busy = null, message = if (created) BackupMessage.CREATED else BackupMessage.CREATE_FAILED) }
        }
    }

    /** Retour du sélecteur « Ouvrir un document » : lecture et validation complètes, puis confirmation. */
    fun onRestoreDocument(uri: Uri?) {
        if (uri == null || !start(BackupBusy.CHECKING)) return
        viewModelScope.launch {
            val preparation = backups.prepareRestore(uri)
            transient.update {
                when (preparation) {
                    is RestorePreparation.Ready ->
                        it.copy(busy = null, confirmation = RestoreConfirmation(preparation.currentBooks, preparation.backupBooks))
                    RestorePreparation.NewerFormat -> it.copy(busy = null, message = BackupMessage.RESTORE_NEWER)
                    RestorePreparation.Invalid -> it.copy(busy = null, message = BackupMessage.RESTORE_INVALID)
                    RestorePreparation.Failed -> it.copy(busy = null, message = BackupMessage.RESTORE_FAILED)
                }
            }
        }
    }

    fun onRestoreConfirm() {
        if (transient.value.confirmation == null) return
        transient.update { it.copy(confirmation = null, busy = BackupBusy.RESTORING) }
        viewModelScope.launch {
            val result = backups.confirmRestore()
            transient.update {
                if (result == RestoreResult.RESTORED) it.copy(busy = null, restored = true)
                else it.copy(busy = null, message = BackupMessage.RESTORE_FAILED)
            }
        }
    }

    fun onRestoreDismiss() {
        if (transient.value.confirmation == null) return
        transient.update { it.copy(confirmation = null) }
        viewModelScope.launch { backups.cancelRestore() }
    }

    /** Efface ce message seulement : un nouveau, arrivé pendant l’affichage, reste à montrer. */
    fun onMessageShown(shown: BackupMessage) {
        transient.update { if (it.message == shown) it.copy(message = null) else it }
    }

    fun onRestoredHandled() {
        transient.update { it.copy(restored = false) }
    }

    /** Une opération à la fois (appelé sur le thread principal). */
    private fun start(busy: BackupBusy): Boolean {
        val current = transient.value
        if (current.busy != null || current.confirmation != null) return false
        transient.value = current.copy(busy = busy, message = null)
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication
                BackupViewModel(app.container.backups)
            }
        }
    }
}
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.settings.BackupViewModelTest'`
Expected : PASS (8 tests).

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "20.7 : BackupViewModel"
```

### Task 20.8 : écran « Sauvegarde » (3.07), confirmation, ligne des Paramètres, route, captures

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/settings/BackupScreen.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/Buttons.kt` (`enabled` sur `PrimaryButton` et `OutlinedPillButton`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/VersoIcons.kt` (`Download`, `Upload`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/settings/SettingsScreen.kt` (paramètre `onOpenBackup`, ligne « Sauvegarde »)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/nav/Routes.kt`, `app/src/main/kotlin/com/maximebier/verso/ui/nav/VersoNavHost.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/kotlin/com/maximebier/verso/screenshots/samples/BackupSamples.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/V3ScreenCatalog.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/settings/BackupScreenTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/settings/SettingsScreenTest.kt`, `app/src/test/kotlin/com/maximebier/verso/StringsTest.kt`

**Interfaces:**
- Consumes : `BackupUiState`, `BackupViewModel` (20.7), `BackupService.MIME_TYPE`, `RESTORE_MIME_TYPES` (20.6), `formatDate` (`ui/common/BookTexts.kt`), `sizeParts`, `SizeParts` (`ui/details/DetailsScreen.kt`), `R.plurals.library_book_count`, `VersoDialog`, `DangerButton`, `VersoTextButton`, `VersoSnackbarHost`, `DetailTopBar`, `ActionRow`.
- Produces (**ajouts au contrat**) : `@Serializable data object BackupRoute` ; `data class BackupActions(onBack, onCreate, onRestore, onRestoreConfirm, onRestoreDismiss)` ; `@Composable fun BackupScreen(state: BackupUiState, actions: BackupActions, modifier: Modifier = Modifier, snackbarHostState: SnackbarHostState = remember { SnackbarHostState() })` ; `@Composable fun BackupDestination(onBack: () -> Unit, onRestored: () -> Unit)` ; `fun backupSizeParts(bytes: Long): SizeParts` ; `SettingsDestination(onBack, onOpenLicenses, onOpenBackup)` ; `SettingsScreen(…, onOpenBackup: () -> Unit = {}, modifier)` ; `PrimaryButton(…, enabled: Boolean = true)`, `OutlinedPillButton(…, enabled: Boolean = true)` ; `VersoIcons.Download`, `VersoIcons.Upload` ; `BackupSample(state: BackupUiState)` (tests).

- [ ] **Step 1 : chaînes**

Dans `strings.xml`, dans le bloc « Paramètres (1.09) », juste après `settings_clear_journal_summary` :

```xml
    <!-- ===================== Paramètres : ligne Sauvegarde (3.07) ===================== -->
    <string name="settings_backup">Sauvegarde</string>
    <!-- PROPOSÉ : résumé de la ligne (non dessiné) -->
    <string name="settings_backup_summary">Créer ou restaurer une sauvegarde de votre bibliothèque</string>
```

À la fin du fichier, avant `</resources>` :

```xml
    <!-- ===================== Sauvegarde (3.07) ===================== -->
    <string name="backup_title">Sauvegarde</string>
    <string name="backup_intro">Un seul fichier contient vos livres, vos positions, vos notes et vos réglages. Gardez-le sur votre Drive ou votre ordinateur.</string>
    <string name="backup_last_title">Dernière sauvegarde</string>
    <!-- « 20 septembre 2026, 21 h 14 » : U+00A0 autour du h, « h » entre apostrophes (lettre de motif) -->
    <string name="backup_last_date_pattern" translatable="false">d MMMM yyyy, H \'h\' mm</string>
    <!-- « 48 Mo · verso-sauvegarde-2026-09-20.zip » ; %1$s = taille (details_size_mb / details_size_kb) -->
    <string name="backup_last_details">%1$s · %2$s</string>
    <string name="backup_create">Créer une sauvegarde</string>
    <string name="backup_restore">Restaurer une sauvegarde</string>
    <string name="backup_restore_warning">La restauration remplace votre bibliothèque actuelle. Verso vous demandera de confirmer.</string>
    <!-- PROPOSÉ : carte sans sauvegarde (texte de la spec, non dessiné) -->
    <string name="backup_none">Aucune sauvegarde pour l’instant</string>
    <!-- PROPOSÉ : progression, sous les boutons désactivés -->
    <string name="backup_creating">Création de la sauvegarde…</string>
    <string name="backup_checking">Vérification de la sauvegarde…</string>
    <string name="backup_restoring">Restauration en cours…</string>
    <!-- PROPOSÉ : messages (snackbar) -->
    <string name="backup_created">Sauvegarde enregistrée.</string>
    <string name="backup_create_failed">La sauvegarde n’a pas pu être enregistrée.</string>
    <string name="backup_restore_invalid">Ce fichier n’est pas une sauvegarde Verso, ou il est incomplet. Votre bibliothèque n’a pas changé.</string>
    <string name="backup_restore_newer">Cette sauvegarde vient d’une version plus récente de Verso. Mettez Verso à jour pour la restaurer.</string>
    <string name="backup_restore_failed">La restauration n’a pas abouti. Votre bibliothèque n’a pas changé.</string>
    <!-- PROPOSÉ : confirmation de restauration (spec : « avec le nombre de livres », non dessinée) ; %1$s et %2$s = library_book_count -->
    <string name="backup_restore_dialog_title">Remplacer votre bibliothèque ?</string>
    <string name="backup_restore_dialog_body">Votre bibliothèque actuelle (%1$s) sera remplacée par celle de la sauvegarde (%2$s), avec ses positions, son journal, ses notes et ses réglages.</string>
    <string name="backup_restore_dialog_cancel">Annuler</string>
    <string name="backup_restore_dialog_confirm">Remplacer</string>
```

Dans `backup_last_date_pattern`, ` ` est l’échappement Android (AAPT le convertit) et `\'` protège l’apostrophe ; le motif obtenu est `d MMMM yyyy, H 'h' mm` avec des U+00A0. Le titre du dialogue porte un U+202F avant `?`.

- [ ] **Step 2 : écrire les tests qui échouent**

Ajouter à `StringsTest.kt` (imports `com.maximebier.verso.ui.common.formatDate`, `java.time.LocalDateTime`, `java.time.ZoneId`) :

```kotlin
    @Test
    fun backupLabelsFollowFrenchTypography() {
        val at = LocalDateTime.of(2026, 9, 20, 21, 14).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertThat(formatDate(at, context.getString(R.string.backup_last_date_pattern))).isEqualTo("20 septembre 2026, 21 h 14")
        assertThat(context.getString(R.string.backup_restore_dialog_title)).isEqualTo("Remplacer votre bibliothèque\u202F?")
        assertThat(context.getString(R.string.backup_none)).isEqualTo("Aucune sauvegarde pour l’instant")
        assertThat(context.getString(R.string.backup_creating)).isEqualTo("Création de la sauvegarde…")
    }
```

`app/src/test/kotlin/com/maximebier/verso/ui/settings/BackupScreenTest.kt` :

```kotlin
package com.maximebier.verso.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.ui.details.SizeParts
import com.maximebier.verso.ui.theme.VersoTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class BackupScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private var state by mutableStateOf(BackupUiState())
    private val calls = mutableListOf<String>()
    private val actions = BackupActions(
        onBack = { calls += "retour" },
        onCreate = { calls += "créer" },
        onRestore = { calls += "restaurer" },
        onRestoreConfirm = { calls += "remplacer" },
        onRestoreDismiss = { calls += "annuler" },
    )
    private val at = LocalDateTime.of(2026, 9, 20, 21, 14).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun show() = composeRule.setContent { VersoTheme { BackupScreen(state = state, actions = actions) } }

    @Test
    fun cardShowsDateSizeAndNameOfTheLastBackup() {
        state = BackupUiState(lastBackup = LastBackup(at, 48_000_000L, "verso-sauvegarde-2026-09-20.zip"))
        show()

        composeRule.onNodeWithText(ctx.getString(R.string.backup_intro)).assertExists()
        composeRule.onNodeWithText("Dernière sauvegarde").assertExists()
        composeRule.onNodeWithText("20 septembre 2026, 21 h 14").assertExists()
        composeRule.onNodeWithText("48 Mo · verso-sauvegarde-2026-09-20.zip").assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restore_warning)).assertExists()
    }

    @Test
    fun withoutBackupTheCardSaysSo() {
        show()
        composeRule.onNodeWithText("Aucune sauvegarde pour l’instant").assertExists()
    }

    @Test
    fun buttonsCallTheirActionsAndAreAtLeast48dp() {
        show()
        composeRule.onNodeWithText("Créer une sauvegarde").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Restaurer une sauvegarde").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(calls).containsExactly("créer", "restaurer").inOrder()
    }

    @Test
    fun whileBusyButtonsAreDisabledAndProgressIsShown() {
        state = BackupUiState(busy = BackupBusy.RESTORING)
        show()

        composeRule.onNodeWithText("Créer une sauvegarde").assertIsNotEnabled()
        composeRule.onNodeWithText("Restaurer une sauvegarde").assertIsNotEnabled()
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restoring)).assertExists()
    }

    @Test
    fun confirmationGivesBothCountsAndReplacesOnlyOnConfirm() {
        state = BackupUiState(confirmation = RestoreConfirmation(currentBooks = 1, backupBooks = 3))
        show()

        val current = ctx.resources.getQuantityString(R.plurals.library_book_count, 1, 1)
        val backup = ctx.resources.getQuantityString(R.plurals.library_book_count, 3, 3)
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restore_dialog_title)).assertExists()
        composeRule.onNodeWithText(ctx.getString(R.string.backup_restore_dialog_body, current, backup)).assertExists()
        composeRule.onNodeWithText("Annuler").performClick()
        composeRule.onNodeWithText("Remplacer").performClick()
        assertThat(calls).containsExactly("annuler", "remplacer").inOrder()
    }

    @Test
    fun sizeIsWholeFromTenMegabytes() {
        assertThat(backupSizeParts(48_000_000L)).isEqualTo(SizeParts(megabytes = true, value = "48"))
        assertThat(backupSizeParts(47_600_000L)).isEqualTo(SizeParts(megabytes = true, value = "48"))
        assertThat(backupSizeParts(1_200_000L)).isEqualTo(SizeParts(megabytes = true, value = "1,2"))
        assertThat(backupSizeParts(850_000L)).isEqualTo(SizeParts(megabytes = false, value = "850"))
    }
}
```

Ajouter à `SettingsScreenTest.kt` (imports `com.maximebier.verso.ui.settings.SettingsUiState` déjà dans le paquet) :

```kotlin
    @Test
    fun backupRowOpensTheBackupScreen() {
        var opened = 0
        composeRule.setContent {
            VersoTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    versionName = "3.0.0",
                    onBack = {},
                    onReopenLastBookChange = {},
                    onThemeModeChange = {},
                    onDarkThemeVariantChange = {},
                    onClearJournalClick = {},
                    onClearJournalConfirm = {},
                    onClearJournalDismiss = {},
                    onOpenSourceCode = {},
                    onOpenLicenses = {},
                    onOpenBackup = { opened++ },
                )
            }
        }

        composeRule.onNodeWithText(ctx.getString(R.string.settings_backup)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()

        assertThat(opened).isEqualTo(1)
    }
```

- [ ] **Step 3 : lancer, vérifier l’échec**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.settings.BackupScreenTest' --tests 'com.maximebier.verso.ui.settings.SettingsScreenTest' --tests 'com.maximebier.verso.StringsTest'`
Expected : échec de compilation (`BackupScreen`, `BackupActions`, `backupSizeParts`, `onOpenBackup` inconnus).

- [ ] **Step 4 : implémenter les composants partagés**

`VersoIcons.kt`, après `Undo` :

```kotlin
    val Download: ImageVector = strokeIcon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Upload: ImageVector = strokeIcon("upload", "M12 20V9M7 14l5-5 5 5M5 4h14")
```

`Buttons.kt` : `PrimaryButton` reçoit `enabled: Boolean = true` (dernier paramètre) ; dans son `Button`, ajouter `enabled = enabled,` et remplacer `colors` par :

```kotlin
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.surfaceHigh,
            disabledContentColor = colors.textSecondary,
        ),
```

`OutlinedPillButton` reçoit `enabled: Boolean = true` (dernier paramètre) ; dans son `OutlinedButton`, ajouter `enabled = enabled,` et remplacer `border` et `colors` par :

```kotlin
        border = BorderStroke(1.dp, if (enabled) colors.outline else colors.divider),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text, disabledContentColor = colors.textSecondary),
```

(Un bouton désactivé n’est pas tenu au contraste 7:1 ; `textSecondary` sur `surfaceHigh` le dépasse de toute façon, et le texte de progression dit pourquoi : jamais la couleur seule.)

- [ ] **Step 5 : implémenter l’écran**

`app/src/main/kotlin/com/maximebier/verso/ui/settings/BackupScreen.kt` :

```kotlin
package com.maximebier.verso.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maximebier.verso.R
import com.maximebier.verso.backup.BackupService
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.ui.common.formatDate
import com.maximebier.verso.ui.components.DangerButton
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.components.OutlinedPillButton
import com.maximebier.verso.ui.components.PrimaryButton
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSnackbarHost
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.details.SizeParts
import com.maximebier.verso.ui.details.sizeParts
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

data class BackupActions(
    val onBack: () -> Unit,
    val onCreate: () -> Unit,
    val onRestore: () -> Unit,
    val onRestoreConfirm: () -> Unit,
    val onRestoreDismiss: () -> Unit,
)

/** Point d’entrée de BackupRoute : sélecteurs Android, messages, arrivée sur la bibliothèque après restauration. */
@Composable
fun BackupDestination(onBack: () -> Unit, onRestored: () -> Unit) {
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupService.MIME_TYPE)) { uri ->
        viewModel.onCreateDocument(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onRestoreDocument(uri)
    }
    val message = state.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(message = messageText, duration = SnackbarDuration.Long)
            viewModel.onMessageShown(message)
        }
    }
    LaunchedEffect(state.restored) {
        if (state.restored) {
            viewModel.onRestoredHandled()
            onRestored()
        }
    }
    // Création et restauration vont jusqu’au bout : le retour attend la fin.
    BackHandler(enabled = state.busy != null) {}
    BackupScreen(
        state = state,
        actions = BackupActions(
            onBack = { if (state.busy == null) onBack() },
            onCreate = { createLauncher.launch(viewModel.suggestedFileName()) },
            onRestore = { restoreLauncher.launch(BackupService.RESTORE_MIME_TYPES) },
            onRestoreConfirm = viewModel::onRestoreConfirm,
            onRestoreDismiss = viewModel::onRestoreDismiss,
        ),
        snackbarHostState = snackbarHostState,
    )
}

/** Écran « Sauvegarde » (3.07), sans état propre. */
@Composable
fun BackupScreen(
    state: BackupUiState,
    actions: BackupActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val idle = state.busy == null
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            DetailTopBar(title = stringResource(R.string.backup_title), onBack = actions.onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(text = stringResource(R.string.backup_intro), style = typography.body, color = colors.text)
                LastBackupCard(state.lastBackup)
                PrimaryButton(
                    text = stringResource(R.string.backup_create),
                    onClick = actions.onCreate,
                    icon = VersoIcons.Download,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedPillButton(
                    text = stringResource(R.string.backup_restore),
                    onClick = actions.onRestore,
                    icon = VersoIcons.Upload,
                    enabled = idle,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.busy?.let { busy ->
                    Text(
                        text = stringResource(busy.textRes),
                        style = typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = VersoIcons.AlertTriangle,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp).size(VersoDimens.iconSmall),
                    )
                    Text(
                        text = stringResource(R.string.backup_restore_warning),
                        style = typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        VersoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    state.confirmation?.let { RestoreDialog(it, actions) }
}

/** Carte « Dernière sauvegarde » : lue d’un bloc par TalkBack, annoncée quand elle change. */
@Composable
private fun LastBackupCard(last: LastBackup?) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    Column(
        Modifier
            .fillMaxWidth()
            .clip(VersoShapes.card)
            .background(colors.surface)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(R.string.backup_last_title), style = typography.caption, color = colors.textSecondary)
        if (last == null) {
            Text(text = stringResource(R.string.backup_none), style = typography.body, color = colors.text)
        } else {
            Text(
                text = formatDate(last.at, stringResource(R.string.backup_last_date_pattern)),
                style = typography.bookTitleStrong,
                color = colors.text,
            )
            Text(
                text = stringResource(R.string.backup_last_details, backupSizeText(last.sizeBytes), last.fileName),
                style = typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun RestoreDialog(confirmation: RestoreConfirmation, actions: BackupActions) {
    val current = pluralStringResource(R.plurals.library_book_count, confirmation.currentBooks, confirmation.currentBooks)
    val backup = pluralStringResource(R.plurals.library_book_count, confirmation.backupBooks, confirmation.backupBooks)
    VersoDialog(
        title = stringResource(R.string.backup_restore_dialog_title),
        onDismissRequest = actions.onRestoreDismiss,
        buttons = {
            VersoTextButton(text = stringResource(R.string.backup_restore_dialog_cancel), onClick = actions.onRestoreDismiss)
            DangerButton(text = stringResource(R.string.backup_restore_dialog_confirm), onClick = actions.onRestoreConfirm)
        },
    ) {
        // VersoDialog fournit la couleur (textSecondary) et le style (body) du texte.
        Text(text = stringResource(R.string.backup_restore_dialog_body, current, backup))
    }
}

private const val BYTES_PER_MB = 1_000_000L

/** Au-delà, la taille est un nombre entier de Mo. */
private const val WHOLE_MEGABYTES_FROM = 10_000_000L

/** « 48 Mo » à partir de 10 Mo (maquette 3.07) ; en dessous, comme la fiche (« 1,2 Mo », « 850 ko »). */
fun backupSizeParts(bytes: Long): SizeParts =
    if (bytes >= WHOLE_MEGABYTES_FROM) {
        SizeParts(megabytes = true, value = ((bytes + BYTES_PER_MB / 2) / BYTES_PER_MB).toString())
    } else {
        sizeParts(bytes)
    }

@Composable
private fun backupSizeText(bytes: Long): String {
    val parts = backupSizeParts(bytes)
    return stringResource(if (parts.megabytes) R.string.details_size_mb else R.string.details_size_kb, parts.value)
}

@get:StringRes
private val BackupBusy.textRes: Int
    get() = when (this) {
        BackupBusy.CREATING -> R.string.backup_creating
        BackupBusy.CHECKING -> R.string.backup_checking
        BackupBusy.RESTORING -> R.string.backup_restoring
    }

@get:StringRes
private val BackupMessage.textRes: Int
    get() = when (this) {
        BackupMessage.CREATED -> R.string.backup_created
        BackupMessage.CREATE_FAILED -> R.string.backup_create_failed
        BackupMessage.RESTORE_INVALID -> R.string.backup_restore_invalid
        BackupMessage.RESTORE_NEWER -> R.string.backup_restore_newer
        BackupMessage.RESTORE_FAILED -> R.string.backup_restore_failed
    }
```

- [ ] **Step 6 : ligne des Paramètres et route**

`SettingsScreen.kt` : `SettingsDestination(onBack: () -> Unit, onOpenLicenses: () -> Unit, onOpenBackup: () -> Unit)` transmet `onOpenBackup = onOpenBackup` à `SettingsScreen` ; `SettingsScreen` reçoit `onOpenBackup: () -> Unit = {},` juste avant `modifier`. Dans la section Confidentialité, entre `SettingsParagraph(...)` et la ligne « Effacer le journal de lecture » :

```kotlin
                ActionRow(
                    title = stringResource(R.string.settings_backup),
                    summary = stringResource(R.string.settings_backup_summary),
                    icon = VersoIcons.ChevronRight,
                    onClick = onOpenBackup,
                )
```

`Routes.kt` :

```kotlin
@Serializable data object BackupRoute
```

`VersoNavHost.kt` : import `com.maximebier.verso.ui.settings.BackupDestination` ; remplacer la destination `SettingsRoute` et ajouter `BackupRoute` :

```kotlin
        composable<SettingsRoute> { entry ->
            SettingsDestination(
                onBack = { if (entry.resumed()) navController.popBackStack() },
                onOpenLicenses = { if (entry.resumed()) navController.navigate(LicensesRoute) },
                onOpenBackup = { if (entry.resumed()) navController.navigate(BackupRoute) },
            )
        }
        composable<BackupRoute> { entry ->
            BackupDestination(
                onBack = { if (entry.resumed()) navController.popBackStack() },
                // Bibliothèque restaurée : on y arrive directement, Paramètres et Sauvegarde sont dépilés.
                onRestored = { navController.popBackStack<LibraryRoute>(inclusive = false) },
            )
        }
```

Mettre à jour le commentaire de `VersoNavHost` (« Les 5 routes de la V1 ») : « Routes de l’app ».

- [ ] **Step 7 : captures**

`app/src/test/kotlin/com/maximebier/verso/screenshots/samples/BackupSamples.kt` :

```kotlin
package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.ui.settings.BackupActions
import com.maximebier.verso.ui.settings.BackupBusy
import com.maximebier.verso.ui.settings.BackupScreen
import com.maximebier.verso.ui.settings.BackupUiState
import com.maximebier.verso.ui.settings.RestoreConfirmation
import java.time.LocalDateTime
import java.time.ZoneId

/** « 20 septembre 2026, 21 h 14 » dans le fuseau de la machine qui capture. */
private val MockupBackupAt: Long = LocalDateTime.of(2026, 9, 20, 21, 14).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 3.07 : carte de la maquette (48 Mo · verso-sauvegarde-2026-09-20.zip). */
internal val BackupMockupState = BackupUiState(lastBackup = LastBackup(MockupBackupAt, 48_000_000L, "verso-sauvegarde-2026-09-20.zip"))

@Composable
internal fun BackupSample(state: BackupUiState = BackupMockupState) {
    BackupScreen(state = state, actions = BackupActions(onBack = {}, onCreate = {}, onRestore = {}, onRestoreConfirm = {}, onRestoreDismiss = {}))
}

@Composable
internal fun BackupEmptySample() = BackupSample(BackupUiState())

@Composable
internal fun BackupBusySample() = BackupSample(BackupMockupState.copy(busy = BackupBusy.CREATING))

@Composable
internal fun BackupConfirmationSample() = BackupSample(BackupMockupState.copy(confirmation = RestoreConfirmation(currentBooks = 1, backupBooks = 12)))
```

Dans `V3ScreenCatalog.fixtures` (imports des quatre échantillons) :

```kotlin
        V3ScreenFixture(ScreenFixture("3.07-sauvegarde") { BackupSample() }),
        V3ScreenFixture(ScreenFixture("3.07-sauvegarde-aucune") { BackupEmptySample() }),
        V3ScreenFixture(ScreenFixture("3.07-sauvegarde-en-cours") { BackupBusySample() }),
        V3ScreenFixture(ScreenFixture("3.07-restauration-confirmation") { BackupConfirmationSample() }),
```

- [ ] **Step 8 : lancer, vérifier le succès**

Run : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.settings.*' --tests 'com.maximebier.verso.StringsTest' --tests 'com.maximebier.verso.ui.nav.*' --tests 'com.maximebier.verso.ui.components.*' --tests 'com.maximebier.verso.screenshots.AccessibilityTreeTest'`
Expected : PASS (`BackupScreenTest` 6 tests, `SettingsScreenTest` + 1, `StringsTest` + 1 ; `AccessibilityTreeTest` couvre les quatre écrans 3.07 si `V3ScreenCatalog` y est branché depuis 17.7 — sinon l’y ajouter ici, comme `V2ScreenCatalog`).

- [ ] **Step 9 : commit**

```bash
git add app/src
git commit -m "20.8 : écran Sauvegarde, confirmation de restauration et ligne des Paramètres"
```

### Task 20.9 : fin de l’étape 20

**Files:**
- Modify: `docs/SPEC.md`, `docs/acceptance-v3.md`
- Captures : `app/build/outputs/roborazzi/v3/3.07-*`, `app/build/outputs/roborazzi/v2/2.09-parametres-*` et `2.03-2.04-parametres-*` (la ligne ajoutée peut y apparaître)

- [ ] **Step 1 : suite complète, lint, APK, captures**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug 2>&1 | tee build/etape-20.log
./gradlew :app:recordRoborazziDebug
bash scripts/check-no-internet.sh
```

Expected : BUILD SUCCESSFUL, lint sans avertissement, `NoPermissionsTest` vert, `check-no-internet.sh` conforme. Regarder `3.07-sauvegarde-{clair,sombre,nuit}.png` à côté de `docs/design/screens/3.07-sauvegarde.png` (marges 24 dp, carte 20 dp d’arrondi sur `surface`, écarts de 20 dp, boutons pleine largeur de 48 dp, icônes 20 dp), la variante `-texte-200` (aucun texte coupé, la carte et l’avertissement passent à la ligne), la confirmation et l’état en cours ; vérifier que les captures V2 des Paramètres n’ont changé que par la ligne « Sauvegarde ».

- [ ] **Step 2 : relecture de l’étape**

Relire tout le diff de l’étape (`git diff <commit avant 20.1>..HEAD`), en particulier : aucun chemin d’entrée de zip utilisé sans `isKnownEntry`, aucune IOException avalée dans `apply()` sans retour en arrière, aucun texte en dur, aucune couleur en dur.

- [ ] **Step 3 : SPEC et checklist**

Dans `docs/SPEC.md`, « Décisions de la V3 », remplacer la puce « Sauvegarde : … » par :

```markdown
- Sauvegarde : ligne « Sauvegarde » dans la section Confidentialité des Paramètres, sous la phrase sur Internet. Un zip `verso-sauvegarde-AAAA-MM-JJ.zip` enregistré par le sélecteur Android : `donnees.json` (champ `format`, livres et métadonnées corrigées, positions, états, défilement par livre, sessions, surlignages et notes ; aucun chemin de fichier), `reglages.json` (clés DataStore avec leur type, sauf la carte « Dernière sauvegarde », propre au téléphone), `livres/<sha256>.epub`, `couvertures/<sha256>.jpg`. La carte affiche date, taille (entière à partir de 10 Mo) et nom du document.
- Restauration : tout le fichier est décompressé dans le cache et validé (format, JSON, réglages, empreinte de chaque EPUB, références des sessions et surlignages) avant de remplacer quoi que ce soit ; une sauvegarde d’un format plus récent est refusée avec un message. Confirmation avec le nombre de livres actuels et de la sauvegarde ; remplacement tout ou rien (dossiers mis de côté, base remplacée en une transaction, réglages) ; au moindre échec, retour à l’état d’avant et message. Arrivée sur la bibliothèque restaurée. Limite : si Android tue Verso pendant le remplacement lui-même (moins d’une seconde), il n’y a pas de reprise au lancement suivant.
```

Dans « Historique », ajouter : `- 2026-10-xx : étape 20, sauvegarde et restauration (format 1 du zip, validation complète avant remplacement, ligne dans la section Confidentialité, libellés proposés à confirmer).` (date du jour).

Dans `docs/acceptance-v3.md`, remplir les lignes 10, 11 et 12 : Auto (10 : `BackupWriterTest`, `BackupServiceTest`, `BackupScreenTest`, `V3ScreenshotTest` 3.07 ; 11 : `BackupRestorerTest.restoreAfterDeletingTwoBooksBringsEverythingBack`, `restoreOnAnotherPhoneRewritesThePaths`, `BackupDaoTest`, `SettingsRepositoryTest` ; 12 : `BackupRestorerTest.invalidFilesLeaveTheLibraryIntact`, `newerFormatIsRefused`, `failureDuringReplacementPutsEverythingBack`, `BackupViewModelTest`) ; Claude : 10 (création vers « Téléchargements » depuis le sélecteur, carte capturée) ; Maxime : 11 (supprimer deux livres puis restaurer, sur sa vraie bibliothèque) et 12 (choisir un zip qui n’est pas une sauvegarde). Les cases de la spec ne sont cochées qu’à la passe du téléphone.

- [ ] **Step 4 : commit d’étape et envoi**

```bash
git reset --soft <commit précédant 20.1>
git add -A core/src app/src docs/SPEC.md docs/acceptance-v3.md
git commit -m "Étape 20 : sauvegarde et restauration"
git push
```

- [ ] **Step 5 : téléphone (verrou)**

```bash
mkdir .superpowers/phone.lock   # attendre s’il existe
export ANDROID_SERIAL=192.168.1.10:5555
./gradlew :app:installDebug
adb shell am start -n com.maximebier.verso/.MainActivity
```

Passe groupée (ne pas piloter si une autre app que Verso ou le lanceur est au premier plan) : Paramètres → « Sauvegarde » (capture, comparée à 3.07 en clair, sombre et nuit) ; « Créer une sauvegarde » → sélecteur Android → « Téléchargements » → enregistrer ; la carte montre date, taille et nom (capture) ; « Restaurer une sauvegarde » ouvre le sélecteur (puis retour sans choisir). Ne rien supprimer ni restaurer sur la bibliothèque de Maxime : critères 11 et 12 à faire par lui (lui donner la marche à suivre : supprimer deux livres, restaurer le zip de « Téléchargements », vérifier bibliothèque, positions, états, journal, notes et réglages ; puis choisir un zip qui n’est pas une sauvegarde). Toujours rendre le verrou :

```bash
rm -rf .superpowers/phone.lock
```

---

## Étape 21 : passe d’acceptation V3 et README

Critères V3 : 13, 14, et revue des critères 1 à 12.

### Task 21.1 : passe d’acceptation V3 et README

**Files:**
- Modify: `docs/acceptance-v3.md`, `docs/SPEC.md`, `README.md`, `CLAUDE.md` (règle « V2 terminée »)
- Create: `docs/readme/surlignage.png`, `docs/readme/note.png`, `docs/readme/notes.png`, `docs/readme/sauvegarde.png`

**Interfaces:**
- Consumes : tout ce qui précède.
- Produces : rien de nouveau dans le code.

- [ ] **Step 1 : vérifications automatiques**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease 2>&1 | tee build/etape-21.log
./gradlew :app:recordRoborazziDebug
bash scripts/check-no-internet.sh
bash scripts/acceptance-phone.sh --apk-only
```

Expected : tout vert ; `NoPermissionsTest` et `check-no-internet.sh` (debug et release) conformes (critère 14) ; `AccessibilityTreeTest` vert sur tous les écrans V3 en clair et à 200 % (cibles ≥ 48 dp, intitulés des boutons à icône seule, texte non coupé : critère 13, partie automatique). Compter les tests (`grep -c "<testcase" app/build/test-results/testDebugUnitTest/*.xml core/build/test-results/test/*.xml | awk -F: '{s+=$2} END {print s}'`) pour le README.

- [ ] **Step 2 : passe sur le téléphone (verrou)**

```bash
mkdir .superpowers/phone.lock   # attendre s’il existe
export ANDROID_SERIAL=192.168.1.10:5555
./gradlew :app:installDebug
adb shell am start -n com.maximebier.verso/.MainActivity
adb shell dumpsys package com.maximebier.verso | grep -i permission
```

Parcourir les critères 1 à 10 avec les EPUB réels (Madame Bovary, Candide, Silo), en continu et en mode pages, captures dans `build/acceptance/<AAAAMMJJ>/` : barre de sélection 3.04, surlignage dans les cinq thèmes, copier, note 3.05 (Enregistrer, Annuler), fusion, toucher un surlignage (Modifier, Supprimer, Annuler), position inchangée et pas de carte « Revenir » pendant la sélection, « Notes et surlignages » 3.06 depuis la fiche et la barre (saut et carte « Revenir »), export Markdown vers « Téléchargements », création de sauvegarde. Arrêt forcé (`adb shell am force-stop com.maximebier.verso`) puis relance : surlignages et notes présents. `dumpsys` : aucune permission demandée ni accordée (critère 14). Captures README en thème clair : barre de sélection (`surlignage.png`), feuille de note (`note.png`), liste 3.06 (`notes.png`), écran 3.07 (`sauvegarde.png`), recadrées comme les captures existantes de `docs/readme/`.

```bash
rm -rf .superpowers/phone.lock
```

Demander à Maxime (colonne Maxime de `docs/acceptance-v3.md`) : critères 11 et 12 s’ils ne sont pas faits ; texte Android à 200 % et TalkBack sur 3.04 à 3.07, la barre de lecture à cinq outils et le dialogue de restauration (critère 13) ; redémarrage complet du téléphone (surlignages, notes, dernière sauvegarde). Le croire sur parole une fois la vérification annoncée faite, sans relance.

- [ ] **Step 3 : checklist et spec**

`docs/acceptance-v3.md` : compléter toutes les colonnes et les résultats (☑ seulement quand toutes les vérifications de la ligne sont faites). `docs/SPEC.md` : cocher les 14 « Critères d’acceptation V3 » vérifiés ; reporter les libellés proposés confirmés ou corrigés par Maxime (V3 : barre, feuille, liste, export, sauvegarde) ; ligne d’« Historique » : `- 2026-10-xx : V3 terminée, critères d’acceptation cochés (passe du téléphone, TalkBack et texte à 200 % confirmés par Maxime).`

- [ ] **Step 4 : README**

Dans `README.md` :

- sous les captures existantes, une seconde rangée :

```html
<p align="center">
  <img src="docs/readme/surlignage.png" alt="Passage sélectionné : Surligner, Note, Copier" width="200">
  <img src="docs/readme/note.png" alt="Ajouter une note à un passage" width="200">
  <img src="docs/readme/notes.png" alt="Notes et surlignages d’un livre" width="200">
  <img src="docs/readme/sauvegarde.png" alt="Sauvegarde et restauration" width="200">
</p>
```

- dans « Ce que fait Verso », après « Recherche plein texte » :

```markdown
- **Surlignages et notes** : un appui long sur un passage, puis « Surligner », « Note » ou « Copier » ; le surlignage est un fond et un soulignement, les passages qui se recoupent fusionnent sans perdre de note ; la liste d’un livre, dans l’ordre du texte, s’exporte en Markdown.
- **Sauvegarde** : un seul fichier zip (livres, positions, journal, notes et réglages) enregistré où vous voulez par le sélecteur Android ; la restauration vérifie tout le fichier avant de remplacer la bibliothèque, tout ou rien.
```

- puce « Privé » : `aucune permission, pas d’Internet, pas d’analytics. Rien ne quitte le téléphone, sauf la sauvegarde que vous enregistrez vous-même.`
- « Architecture » : ajouter à la puce `:core` « la fusion des surlignages, le Markdown des notes et la validation d’une sauvegarde » ; puce Room : « Room garde livres, positions, sessions et surlignages » ; nombre de tests mis à jour (Step 1).

- [ ] **Step 5 : `CLAUDE.md`**

Remplacer la règle « **V2 terminée** (2026-09-30 ; V1 le 2026-09-27) : ne pas coder la V3 ou la V4 sans décision explicite. » par « **V3 terminée** (2026-10-xx ; V2 le 2026-09-30, V1 le 2026-09-27) : ne pas coder la V4 sans décision explicite. » et ajouter le plan V3 à la ligne des plans (`docs/superpowers/plans/2026-10-01-verso-v3.md`).

- [ ] **Step 6 : commit d’étape, envoi, installation**

```bash
git add README.md CLAUDE.md docs/SPEC.md docs/acceptance-v3.md docs/readme/
git commit -m "Étape 21 : passe d’acceptation V3 et README"
git push
```

Puis, verrou pris : `./gradlew :app:installDebug`, lancement, capture de la bibliothèque ; verrou rendu (`rm -rf .superpowers/phone.lock`).

---

## Auto-relecture

- **Critère 10** (créer, carte date/taille/nom) : `BackupWriterTest`, `BackupServiceTest.createWritesTheZipAndRemembersDateSizeAndName`, `BackupScreenTest.cardShowsDateSizeAndNameOfTheLastBackup`, captures 3.07, téléphone (20.9).
- **Critère 11** (supprimer deux livres puis restaurer, tout revient) : `BackupRestorerTest.restoreAfterDeletingTwoBooksBringsEverythingBack` (base entière, fichiers, réglages, carte du téléphone gardée), `restoreOnAnotherPhoneRewritesThePaths`, confirmation avec les deux nombres (`BackupViewModelTest`, `BackupScreenTest`), arrivée sur la bibliothèque (`VersoNavHost`), Maxime sur le téléphone.
- **Critère 12** (fichier invalide ou restauration interrompue → intacte, message) : `invalidFilesLeaveTheLibraryIntact` (tronqué, zip d’EPUB, pas un zip, EPUB manquant, EPUB abîmé, réglage inconnu), `newerFormatIsRefused`, `failureDuringReplacementPutsEverythingBack` (panne après chaque étape, dont l’espace disque), dossier temporaire et dossiers mis de côté effacés ; messages : `BackupViewModelTest.refusedPreparationsGiveTheirMessage`, `failedRestoreSaysTheLibraryIsUnchanged`. Limite écrite dans la spec : pas de reprise si le processus est tué pendant le remplacement.
- **Critère 13** : `AccessibilityTreeTest` sur les écrans 3.07 (dont la variante à 200 %), `BackupScreenTest` (48 dp), `SettingsScreenTest.backupRowOpensTheBackupScreen` (48 dp), Maxime avec TalkBack ; aucune icône seule cliquable dans 3.07 (icônes décoratives à côté d’un texte).
- **Critère 14** : aucune permission ajoutée (sélecteurs Android seulement), `NoPermissionsTest`, `check-no-internet.sh`, `dumpsys`.
- **Noms du contrat** : `BackupFormat`, `BackupCheck` (Valid, NewerFormat, Invalid(reason)), `HighlightEntity`, `HighlightDao.insert`, `DocumentStore` (`openOutput`, `openInput`, `displayName`), `AppContainer.backups: BackupService` respectés. Ajouts signalés dans les blocs **Produces** : `BackupManifest`, `BackupBookRef`, `BackupItemRef`, `LibrarySnapshot`, `BackupDao`, `VersoDatabase.backupDao()`, `RawSetting`, `LastBackup`, `SettingsRepository.lastBackup/setLastBackup/exportRaw/importRaw`, DTO et conversions (`backup/`), `BackupWriter`, `BackupRestorer`, `RestoreStep`, `RestorePreparation`, `RestoreResult`, `Backups`, `DocumentStore.size/delete`, `BackupViewModel` et ses types, `BackupScreen`, `BackupActions`, `BackupDestination`, `BackupRoute`, `backupSizeParts`, `PrimaryButton/OutlinedPillButton(enabled)`, `VersoIcons.Download/Upload`, `SettingsScreen(onOpenBackup)`.
- **Écart au brief** : couvertures en `.jpg` (ce que fait l’import), pas `.png` ; `all`/`insertAll`/`clearAll` de livres et sessions regroupés dans `BackupDao` (une transaction par DAO).
