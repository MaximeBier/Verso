# Verso V3 — Partie A : surlignages et notes (étapes 17 et 18)

Plan maître : `docs/superpowers/plans/2026-10-01-verso-v3.md` (contrat d’architecture, contraintes globales, Review Focus). Les commandes Gradle se lancent depuis Git Bash avec `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (abrégé `G=… ./gradlew` ci-dessous : `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` une fois par shell).

Maquettes : `docs/design/html/3.04-texte-selectionne.html` (barre de sélection) et `3.05-ajouter-une-note.html` (feuille de note).

Rappels utiles du code existant :
- `FragmentReaderController` (`app/.../reader/FragmentReaderController.kt`) : gestes (`onPointerDown`, `onGestureReleased`, `onTapLikeGesture`, `onReadiumTap`), remise en page, marque de recherche (`bindSearchMatch`).
- `ReaderSurface` (`app/.../reader/ReaderSurface.kt`) : crée le fragment Readium (`EpubNavigatorFragment.Configuration { applyVerso(theme) }`), branche le contrôleur (`controller.bind(…)`), applique la marque de recherche (`applyDecorations(…, SearchMatchDecoration.GROUP)`).
- Readium 3.4.0 (vérifié dans le JS du navigateur) : un clic avec une sélection non vide n’envoie jamais `onTap` ; un clic sur une décoration d’un groupe qui a un écouteur (`addDecorationListener`) appelle `onDecorationActivated` et, s’il renvoie `true`, **pas** `onTap`. Les décorations sont repérées par leurs rectangles : `pointer-events: none` du gabarit ne gêne pas.
- `SelectableNavigator` : `suspend fun currentSelection(): Selection?` (`Selection(locator, rect)`, `locator.text` = highlight/before/after), `fun clearSelection()`. `EpubNavigatorFragment.Configuration.selectionActionModeCallback: ActionMode.Callback?`.

---

## Étape 17 : surlignages

### Task 17.1 : passage dans le texte et fusion (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/maximebier/verso/core/notes/TextQuotes.kt`
- Create: `core/src/main/kotlin/com/maximebier/verso/core/notes/HighlightMerge.kt`
- Test: `core/src/test/kotlin/com/maximebier/verso/core/notes/TextQuotesTest.kt`
- Test: `core/src/test/kotlin/com/maximebier/verso/core/notes/HighlightMergeTest.kt`

**Interfaces:**
- Consumes: rien.
- Produces: `TextQuote`, `CharSpan`, `TextQuotes.{CONTEXT_CHARS, PREVIEW_CHARS, normalize, collapse, locate, quoteAt, preview}`, `PlacedHighlight`, `MergePlan`, `HighlightMerge.{NOTE_SEPARATOR, plan, joinNotes}` (contrat du plan maître ; `collapse`, `preview` et `PREVIEW_CHARS` ajoutés ici).

- [ ] **Step 1 : tests qui échouent**

```kotlin
// core/src/test/kotlin/com/maximebier/verso/core/notes/TextQuotesTest.kt
package com.maximebier.verso.core.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextQuotesTest {
    private val chapter = TextQuotes.normalize(
        "L’eau qui court au bord de l’herbe sépare d’une raie blanche la couleur des prés, et la campagne ainsi " +
            "ressemble à un grand manteau. Plus loin, la campagne ainsi ressemble à un tapis.",
    )

    @Test fun normalizeCollapsesAllWhitespaceAndTrims() {
        assertThat(TextQuotes.normalize("  un\n\tdeux  trois quatre  ")).isEqualTo("un deux trois quatre")
    }

    @Test fun collapseKeepsEdges() {
        assertThat(TextQuotes.collapse("\n un \n")).isEqualTo(" un ")
    }

    @Test fun locatesSingleOccurrence() {
        val span = TextQuotes.locate(chapter, TextQuote(highlight = "raie blanche"))!!
        assertThat(chapter.substring(span.start, span.end)).isEqualTo("raie blanche")
    }

    @Test fun highlightIsNormalizedBeforeSearch() {
        val span = TextQuotes.locate(chapter, TextQuote(highlight = " raie\nblanche "))!!
        assertThat(chapter.substring(span.start, span.end)).isEqualTo("raie blanche")
    }

    @Test fun contextPicksTheRightOccurrence() {
        val second = TextQuotes.locate(
            chapter,
            TextQuote(highlight = "la campagne ainsi ressemble", before = "Plus loin, ", after = " à un tapis"),
        )!!
        assertThat(second.start).isEqualTo(chapter.lastIndexOf("la campagne ainsi ressemble"))
        val first = TextQuotes.locate(
            chapter,
            TextQuote(highlight = "la campagne ainsi ressemble", before = "des prés, et ", after = " à un grand"),
        )!!
        assertThat(first.start).isEqualTo(chapter.indexOf("la campagne ainsi ressemble"))
    }

    @Test fun absentPassageIsNull() {
        assertThat(TextQuotes.locate(chapter, TextQuote(highlight = "Yonville"))).isNull()
        assertThat(TextQuotes.locate(chapter, TextQuote(highlight = "   "))).isNull()
    }

    @Test fun quoteAtKeepsContextWithinBounds() {
        val span = CharSpan(0, 5)
        val quote = TextQuotes.quoteAt(chapter, span)
        assertThat(quote.highlight).isEqualTo("L’eau")
        assertThat(quote.before).isEmpty()
        assertThat(quote.after).hasLength(TextQuotes.CONTEXT_CHARS)
    }

    @Test fun previewCutsOnAWordWithEllipsis() {
        val text = "la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert"
        assertThat(TextQuotes.preview(text)).isEqualTo("la campagne ainsi ressemble à un grand manteau…")
        assertThat(TextQuotes.preview("court")).isEqualTo("court")
    }
}
```

```kotlin
// core/src/test/kotlin/com/maximebier/verso/core/notes/HighlightMergeTest.kt
package com.maximebier.verso.core.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HighlightMergeTest {
    @Test fun noOverlapKeepsTheNewPassageAlone() {
        val plan = HighlightMerge.plan(CharSpan(10, 20), "nouvelle", listOf(PlacedHighlight(1, CharSpan(20, 30), "a")))
        assertThat(plan).isEqualTo(MergePlan(CharSpan(10, 20), "nouvelle", emptyList()))
    }

    @Test fun overlapMergesAndJoinsNotesInTextOrder() {
        val plan = HighlightMerge.plan(
            CharSpan(15, 40),
            "nouvelle",
            listOf(PlacedHighlight(1, CharSpan(10, 20), "première"), PlacedHighlight(2, CharSpan(35, 50), null)),
        )
        assertThat(plan.span).isEqualTo(CharSpan(10, 50))
        assertThat(plan.note).isEqualTo("première\n\nnouvelle")
        assertThat(plan.absorbedIds).containsExactly(1L, 2L)
    }

    @Test fun containedAndContainingPassagesMerge() {
        val inside = HighlightMerge.plan(CharSpan(12, 14), null, listOf(PlacedHighlight(1, CharSpan(10, 20), "garde")))
        assertThat(inside).isEqualTo(MergePlan(CharSpan(10, 20), "garde", listOf(1L)))
        val around = HighlightMerge.plan(CharSpan(0, 30), null, listOf(PlacedHighlight(1, CharSpan(10, 20), "garde")))
        assertThat(around).isEqualTo(MergePlan(CharSpan(0, 30), "garde", listOf(1L)))
    }

    @Test fun mergeIsTransitive() {
        // Le nouveau recoupe 1 ; l’union recoupe alors 2, qui ne touchait pas le passage seul.
        val plan = HighlightMerge.plan(
            CharSpan(18, 25),
            null,
            listOf(PlacedHighlight(1, CharSpan(10, 20), null), PlacedHighlight(2, CharSpan(5, 12), "deux")),
        )
        assertThat(plan.span).isEqualTo(CharSpan(5, 25))
        assertThat(plan.absorbedIds).containsExactly(1L, 2L)
        assertThat(plan.note).isEqualTo("deux")
    }

    @Test fun adjacentPassagesDoNotMerge() {
        val plan = HighlightMerge.plan(CharSpan(20, 25), null, listOf(PlacedHighlight(1, CharSpan(10, 20), null)))
        assertThat(plan.absorbedIds).isEmpty()
    }

    @Test fun unplacedHighlightIsNeverMerged() {
        val plan = HighlightMerge.plan(CharSpan(0, 100), null, listOf(PlacedHighlight(1, null, "perdue ?")))
        assertThat(plan.absorbedIds).isEmpty()
    }

    @Test fun joinNotesDropsBlanksAndDuplicates() {
        assertThat(HighlightMerge.joinNotes(listOf(" a ", null, "  ", "b", "a"))).isEqualTo("a\n\nb")
        assertThat(HighlightMerge.joinNotes(listOf(null, " "))).isNull()
    }
}
```

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :core:test --tests 'com.maximebier.verso.core.notes.*'`
Expected: FAIL (compilation : `TextQuotes`, `HighlightMerge` inconnus).

- [ ] **Step 3 : implémentation**

```kotlin
// core/src/main/kotlin/com/maximebier/verso/core/notes/TextQuotes.kt
package com.maximebier.verso.core.notes

/** Passage et son contexte, comme `Locator.Text` de Readium (highlight, before, after). */
data class TextQuote(val highlight: String, val before: String = "", val after: String = "")

/** Plage de caractères [start, end) ; start < end. */
data class CharSpan(val start: Int, val end: Int) {
    init {
        require(start in 0 until end) { "Plage vide ou négative : [$start, $end)" }
    }

    fun overlaps(other: CharSpan): Boolean = start < other.end && other.start < end

    fun union(other: CharSpan): CharSpan = CharSpan(minOf(start, other.start), maxOf(end, other.end))
}

/**
 * Passages retrouvés dans le texte brut d’un chapitre (espaces normalisés) : c’est sur ces plages que se décide le
 * chevauchement de deux surlignages (spec V3, « Chevauchement »).
 */
object TextQuotes {
    /** Caractères de contexte gardés avant et après un passage reconstruit. */
    const val CONTEXT_CHARS = 32

    /** Longueur maximale du début de passage montré dans la barre de sélection (3.04). */
    const val PREVIEW_CHARS = 48

    // \s ne couvre ni U+00A0 ni U+202F : ajoutés explicitement.
    private val SPACES = Regex("[\\s\\u00A0\\u202F\\u2007]+")

    /** Espaces (dont insécables et retours à la ligne) réduits à une espace, bords coupés. */
    fun normalize(text: String): String = collapse(text).trim()

    /** Comme [normalize], sans couper les bords (contexte d’un passage). */
    fun collapse(text: String): String = SPACES.replace(text, " ")

    /**
     * Plage du passage dans [chapterText] (déjà normalisé). Plusieurs occurrences : celle dont le contexte (fin de
     * `before`, début de `after`) correspond sur le plus de caractères ; à égalité, la première.
     */
    fun locate(chapterText: String, quote: TextQuote): CharSpan? {
        val needle = normalize(quote.highlight)
        if (needle.isEmpty()) return null
        val before = collapse(quote.before)
        val after = collapse(quote.after)
        var best: CharSpan? = null
        var bestScore = -1
        var from = chapterText.indexOf(needle)
        while (from >= 0) {
            val end = from + needle.length
            val score = commonSuffix(chapterText.substring(0, from), before) +
                commonPrefix(chapterText.substring(end), after)
            if (score > bestScore) {
                best = CharSpan(from, end)
                bestScore = score
            }
            from = chapterText.indexOf(needle, from + 1)
        }
        return best
    }

    /** Passage de [chapterText] à [span], avec [CONTEXT_CHARS] de contexte de chaque côté. */
    fun quoteAt(chapterText: String, span: CharSpan): TextQuote = TextQuote(
        highlight = chapterText.substring(span.start, span.end),
        before = chapterText.substring((span.start - CONTEXT_CHARS).coerceAtLeast(0), span.start),
        after = chapterText.substring(span.end, (span.end + CONTEXT_CHARS).coerceAtMost(chapterText.length)),
    )

    /** Début du passage pour la barre de sélection : coupé sur un mot, suivi de « … » s’il est plus long. */
    fun preview(text: String, maxChars: Int = PREVIEW_CHARS): String {
        val normalized = normalize(text)
        if (normalized.length <= maxChars) return normalized
        val cut = normalized.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
        return normalized.substring(0, cut).trimEnd() + "…"
    }

    private fun commonSuffix(a: String, b: String): Int {
        var n = 0
        while (n < a.length && n < b.length && a[a.length - 1 - n] == b[b.length - 1 - n]) n++
        return n
    }

    private fun commonPrefix(a: String, b: String): Int {
        var n = 0
        while (n < a.length && n < b.length && a[n] == b[n]) n++
        return n
    }
}
```

```kotlin
// core/src/main/kotlin/com/maximebier/verso/core/notes/HighlightMerge.kt
package com.maximebier.verso.core.notes

/** Surlignage existant du même chapitre, situé dans le texte ; span null = introuvable (jamais fusionné). */
data class PlacedHighlight(val id: Long, val span: CharSpan?, val note: String?)

/** Plage finale, note finale, surlignages absorbés (à supprimer dans la même transaction). */
data class MergePlan(val span: CharSpan, val note: String?, val absorbedIds: List<Long>)

/** Jamais deux surlignages empilés : un passage qui en recoupe d’autres les absorbe, notes mises bout à bout. */
object HighlightMerge {
    const val NOTE_SEPARATOR = "\n\n"

    fun plan(span: CharSpan, note: String?, existing: List<PlacedHighlight>): MergePlan {
        var merged = span
        val absorbed = LinkedHashMap<Long, PlacedHighlight>()
        // Jusqu’à stabilité : l’union peut recouper un surlignage que le passage seul ne touchait pas.
        while (true) {
            val more = existing.filter { it.id !in absorbed && it.span?.overlaps(merged) == true }
            if (more.isEmpty()) break
            more.forEach { absorbed[it.id] = it; merged = merged.union(it.span!!) }
        }
        // Notes dans l’ordre du texte ; à début égal, l’existante avant la nouvelle.
        val ordered = absorbed.values.map { it.span!!.start to it.note } + (span.start to note)
        val notes = ordered.withIndex()
            .sortedWith(compareBy({ it.value.first }, { it.index }))
            .map { it.value.second }
        return MergePlan(merged, joinNotes(notes), absorbed.keys.toList())
    }

    fun joinNotes(notes: List<String?>): String? =
        notes.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(NOTE_SEPARATOR)
}
```

Note : `HighlightMergeTest.mergeIsTransitive` attend `absorbedIds` `containsExactly(1L, 2L)` sans ordre imposé (Truth `containsExactly` sans `inOrder`) : l’ordre d’absorption n’a pas d’importance.

- [ ] **Step 4 : relancer**

Run: `./gradlew :core:test --tests 'com.maximebier.verso.core.notes.*'`
Expected: PASS.

- [ ] **Step 5 : commit**

```bash
git add core/src/main/kotlin/com/maximebier/verso/core/notes core/src/test/kotlin/com/maximebier/verso/core/notes
git commit -m "17.1 : passage dans le texte du chapitre et fusion des surlignages"
```

---

### Task 17.2 : table `highlights`, migration 2 → 3, dépôt

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/data/db/HighlightEntity.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/data/db/HighlightDao.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/data/HighlightRepository.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/db/VersoDatabase.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/AppContainer.kt`
- Create (généré par Room au build) : `app/schemas/com.maximebier.verso.data.db.VersoDatabase/3.json`
- Test: `app/src/test/kotlin/com/maximebier/verso/data/db/HighlightDaoTest.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/data/HighlightRepositoryTest.kt`
- Modify (test): `app/src/test/kotlin/com/maximebier/verso/data/db/VersoDatabaseMigrationTest.kt`

**Interfaces:**
- Consumes: `BookEntity`, `TestBooks` (tests).
- Produces: `HighlightEntity`, `HighlightDao`, `HighlightRepository`, `chapterPathList()`, `toChapterPathColumn()`, `VersoDatabase.MIGRATION_2_3`, `AppContainer.highlights` (contrat).

- [ ] **Step 1 : tests qui échouent**

Lire d’abord `app/src/test/kotlin/com/maximebier/verso/data/TestBooks.kt` (fabrique de `BookEntity`) et `BookDaoTest.kt` (création de la base en mémoire) et reprendre leurs aides telles quelles.

```kotlin
// app/src/test/kotlin/com/maximebier/verso/data/db/HighlightDaoTest.kt
package com.maximebier.verso.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.testBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlightDaoTest {
    private lateinit var db: VersoDatabase
    private lateinit var dao: HighlightDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.highlightDao()
    }

    @After fun tearDown() = db.close()

    private fun highlight(bookId: Long, progression: Double, note: String? = null) = HighlightEntity(
        bookId = bookId,
        locatorJson = """{"href":"ch.xhtml"}""",
        text = "passage $progression",
        note = note,
        progression = progression,
        chapterPath = "Deuxième partie\nI",
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test fun listIsInBookOrder() = runTest {
        val bookId = db.bookDao().insert(testBook(sha256 = "a"))
        dao.insert(highlight(bookId, 0.31))
        dao.insert(highlight(bookId, 0.01))
        dao.insert(highlight(bookId, 0.30))
        assertThat(dao.observeForBook(bookId).first().map { it.progression }).containsExactly(0.01, 0.30, 0.31).inOrder()
        assertThat(dao.observeCount(bookId).first()).isEqualTo(3)
    }

    @Test fun deletingTheBookDeletesItsHighlights() = runTest {
        val bookId = db.bookDao().insert(testBook(sha256 = "a"))
        dao.insert(highlight(bookId, 0.5))
        db.bookDao().deleteById(bookId)
        assertThat(dao.forBook(bookId)).isEmpty()
    }

    @Test fun replaceMergedIsAtomic() = runTest {
        val bookId = db.bookDao().insert(testBook(sha256 = "a"))
        val a = dao.insert(highlight(bookId, 0.1, "a"))
        val b = dao.insert(highlight(bookId, 0.2, "b"))
        val id = dao.replaceMerged(highlight(bookId, 0.1, "a\n\nb"), listOf(a, b))
        assertThat(dao.forBook(bookId).map { it.id }).containsExactly(id)
        assertThat(dao.byId(id)!!.note).isEqualTo("a\n\nb")
    }
}
```

Si l’aide de `TestBooks.kt` porte un autre nom que `testBook(sha256 = …)`, utiliser le sien (même chose dans les tests suivants).

```kotlin
// app/src/test/kotlin/com/maximebier/verso/data/HighlightRepositoryTest.kt
package com.maximebier.verso.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.VersoDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlightRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 100L
    private val repository = HighlightRepository(db.highlightDao()) { now }

    @After fun tearDown() = db.close()

    private suspend fun newHighlight(): HighlightEntity {
        val bookId = db.bookDao().insert(testBook(sha256 = "a"))
        val row = HighlightEntity(
            bookId = bookId, locatorJson = "{}", text = "passage", note = null, progression = 0.3,
            chapterPath = "", createdAt = now, updatedAt = now,
        )
        return repository.get(repository.saveMerged(row, emptyList()))!!
    }

    @Test fun setNoteTrimsAndBlankBecomesNull() = runTest {
        val row = newHighlight()
        now = 200L
        repository.setNote(row.id, "  Premier portrait.  ")
        assertThat(repository.get(row.id)!!.note).isEqualTo("Premier portrait.")
        assertThat(repository.get(row.id)!!.updatedAt).isEqualTo(200L)
        repository.setNote(row.id, "   ")
        assertThat(repository.get(row.id)!!.note).isNull()
    }

    @Test fun deleteThenRestoreKeepsTheSameRow() = runTest {
        val row = newHighlight()
        val deleted = repository.delete(row.id)!!
        assertThat(repository.get(row.id)).isNull()
        repository.restore(deleted)
        assertThat(repository.get(row.id)).isEqualTo(row)
        assertThat(repository.delete(999L)).isNull()
    }

    @Test fun chapterPathColumnRoundTrips() {
        assertThat(listOf("Deuxième partie", "I").toChapterPathColumn()).isEqualTo("Deuxième partie\nI")
        assertThat(listOf("Titre\nsur deux lignes").toChapterPathColumn().let { HighlightEntityFixture.withPath(it).chapterPathList() })
            .containsExactly("Titre sur deux lignes")
        assertThat(HighlightEntityFixture.withPath("").chapterPathList()).isEmpty()
    }
}

private object HighlightEntityFixture {
    fun withPath(path: String) = HighlightEntity(
        bookId = 1, locatorJson = "{}", text = "", note = null, progression = 0.0, chapterPath = path, createdAt = 0, updatedAt = 0,
    )
}
```

Ajout à `VersoDatabaseMigrationTest` : la base V1 du test existant passe par 1 → 2 → 3 (Room enchaîne les migrations). Ajouter :

```kotlin
    @Test
    fun highlightsTableExistsAfterMigration() = runTest {
        createVersion1Database()
        val db = VersoDatabase.build(context, name)
        try {
            val id = db.highlightDao().insert(
                HighlightEntity(
                    bookId = 1, locatorJson = "{}", text = "passage", note = "note", progression = 0.3,
                    chapterPath = "", createdAt = 1, updatedAt = 1,
                ),
            )
            assertThat(db.highlightDao().byId(id)!!.note).isEqualTo("note")
            assertThat(db.bookDao().byId(1)!!.title).isEqualTo("Madame Bovary")
        } finally {
            db.close()
        }
    }
```

et mettre à jour son KDoc (« ouverte par la V3 : Room joue MIGRATION_1_2 puis MIGRATION_2_3 »).

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.data.db.HighlightDaoTest' --tests 'com.maximebier.verso.data.HighlightRepositoryTest' --tests 'com.maximebier.verso.data.db.VersoDatabaseMigrationTest'`
Expected: FAIL (compilation).

- [ ] **Step 3 : implémentation**

```kotlin
// app/src/main/kotlin/com/maximebier/verso/data/db/HighlightEntity.kt
package com.maximebier.verso.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un surlignage, avec sa note éventuelle. Supprimé avec son livre (cascade). */
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locatorJson: String,      // locator Readium de la plage (texte, contexte, href, progressions)
    val text: String,             // passage, espaces normalisés
    val note: String?,            // null = sans note
    val progression: Double,      // totalProgression du début du passage : ordre du livre et pourcentage
    val chapterPath: String,      // chemin du sommaire, titres séparés par '\n' ; "" = hors sommaire
    val createdAt: Long,
    val updatedAt: Long,
)
```

```kotlin
// app/src/main/kotlin/com/maximebier/verso/data/db/HighlightDao.kt
package com.maximebier.verso.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(highlight: HighlightEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(highlights: List<HighlightEntity>)

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun byId(id: Long): HighlightEntity?

    /** Ordre du livre ; à progression égale, ordre de création. */
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY progression, id")
    suspend fun forBook(bookId: Long): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY progression, id")
    fun observeForBook(bookId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT COUNT(*) FROM highlights WHERE bookId = :bookId")
    fun observeCount(bookId: Long): Flow<Int>

    @Query("SELECT * FROM highlights ORDER BY id")
    suspend fun all(): List<HighlightEntity>

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM highlights WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM highlights")
    suspend fun clearAll()

    /** Surlignage fusionné : insère [merged] (id 0) ou le met à jour, et supprime les absorbés, d’un seul coup. */
    @Transaction
    suspend fun replaceMerged(merged: HighlightEntity, absorbedIds: List<Long>): Long {
        val others = absorbedIds.filter { it != merged.id }
        if (others.isNotEmpty()) deleteByIds(others)
        return if (merged.id == 0L) insert(merged) else merged.id.also { update(merged) }
    }
}
```

`VersoDatabase.kt` :

```kotlin
@Database(entities = [BookEntity::class, SessionEntity::class, HighlightEntity::class], version = 3, exportSchema = true)
abstract class VersoDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        // … NAME, MIGRATION_1_2 inchangés …

        /** V3 : surlignages et notes, supprimés avec leur livre. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `highlights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER NOT NULL, `locatorJson` TEXT NOT NULL, `text` TEXT NOT NULL, `note` TEXT, " +
                        "`progression` REAL NOT NULL, `chapterPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_bookId` ON `highlights` (`bookId`)")
            }
        }

        fun build(context: Context, name: String = NAME): VersoDatabase =
            Room.databaseBuilder(context.applicationContext, VersoDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
```

Après le premier build, comparer le `CREATE TABLE` de `app/schemas/…/3.json` (`createSql` de `highlights`) au SQL ci-dessus : ils doivent être identiques (sinon Room refuse la base migrée — le test de migration le montre).

```kotlin
// app/src/main/kotlin/com/maximebier/verso/data/HighlightRepository.kt
package com.maximebier.verso.data

import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.data.db.HighlightDao
import com.maximebier.verso.data.db.HighlightEntity
import kotlinx.coroutines.flow.Flow

/** Surlignages et notes d’un livre (V3). */
class HighlightRepository(private val dao: HighlightDao, private val clock: () -> Long) {

    fun observe(bookId: Long): Flow<List<HighlightEntity>> = dao.observeForBook(bookId)

    fun observeCount(bookId: Long): Flow<Int> = dao.observeCount(bookId)

    suspend fun forBook(bookId: Long): List<HighlightEntity> = dao.forBook(bookId)

    suspend fun get(id: Long): HighlightEntity? = dao.byId(id)

    suspend fun saveMerged(merged: HighlightEntity, absorbedIds: List<Long>): Long = dao.replaceMerged(merged, absorbedIds)

    /** Note coupée ; vide ou blanche = sans note. */
    suspend fun setNote(id: Long, note: String?) {
        val row = dao.byId(id) ?: return
        dao.update(row.copy(note = note?.trim()?.takeIf(String::isNotEmpty), updatedAt = clock()))
    }

    suspend fun delete(id: Long): HighlightEntity? {
        val row = dao.byId(id) ?: return null
        dao.deleteById(id)
        return row
    }

    /** « Annuler » après une suppression : la même ligne, même id. Sans effet si le livre a été supprimé entre-temps. */
    suspend fun restore(row: HighlightEntity) {
        runCatching { dao.insert(row) }
    }
}

/** Titres du sommaire enregistrés avec le surlignage. */
fun HighlightEntity.chapterPathList(): List<String> = if (chapterPath.isEmpty()) emptyList() else chapterPath.split('\n')

/** Un titre sur plusieurs lignes est ramené à une ligne : '\n' sépare les niveaux. */
fun List<String>.toChapterPathColumn(): String = joinToString("\n") { TextQuotes.normalize(it) }
```

`AppContainer.kt` : après `sessions`,

```kotlin
    val highlights: HighlightRepository = HighlightRepository(database.highlightDao(), System::currentTimeMillis)
```

(`clock` est déclaré plus bas : utiliser `System::currentTimeMillis` ici, ou déplacer la déclaration de `clock` au-dessus et passer `clock`.)

- [ ] **Step 4 : relancer**

Même commande. Expected: PASS ; `app/schemas/…/3.json` créé.

- [ ] **Step 5 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/data app/src/main/kotlin/com/maximebier/verso/AppContainer.kt app/schemas app/src/test/kotlin/com/maximebier/verso/data
git commit -m "17.2 : table des surlignages, migration 2 → 3"
```

---

### Task 17.3 : la machine à états ignore la sélection

**Files:**
- Modify: `core/src/main/kotlin/com/maximebier/verso/core/position/ReadingPositionTracker.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinator.kt`
- Test: `core/src/test/kotlin/com/maximebier/verso/core/position/ReadingPositionTrackerTest.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `ReaderController.selecting: StateFlow<Boolean>` (déclaré en 17.4 ; dans cette tâche, l’ajouter à l’interface et à `FakeReaderController` comme `MutableStateFlow(false)`, et à `FragmentReaderController` comme `MutableStateFlow(false).asStateFlow()` provisoire, complété en 17.4).
- Produces: `ReaderEvent.SelectionStarted(timeMs)`, `ReaderEvent.SelectionEnded(timeMs, position)`.

Relecture dédiée de cette tâche (machine à états).

- [ ] **Step 1 : tests qui échouent** (dans `ReadingPositionTrackerTest`, mêmes aides `tracker()`, `pos()`, `feed()`, `awayAt15()` ; importer `ReaderEvent.SelectionStarted` et `ReaderEvent.SelectionEnded`)

```kotlin
    @Test
    fun selectionWithAutoScrollNeverMovesReading() {
        val t = tracker()
        val effects = t.feed(
            SelectionStarted(1_000),
            // Poignée tirée en bas de l’écran : le texte défile tout seul de 3 écrans.
            Displayed(1_200, pos(11.0)),
            Displayed(1_400, pos(13.0)),
            GestureEnded(1_500, pos(13.0), isFling = false),
            Tick(3_000),
            SelectionEnded(3_200, pos(13.0)),
            Tick(6_000),
        )
        assertThat(effects).isEmpty()
        assertThat(t.state.reading).isEqualTo(pos(10.0))
        assertThat(t.state.displayed).isEqualTo(pos(13.0))
        assertThat(t.state.showReturnCard).isFalse()
    }

    @Test
    fun readingResumesFromWhereTheSelectionLeftTheText() {
        val t = tracker()
        t.feed(SelectionStarted(1_000), Displayed(1_200, pos(10.5)), SelectionEnded(2_000, pos(10.5)))
        val effects = t.feed(
            Displayed(3_000, pos(10.8)),
            GestureEnded(3_100, pos(10.8), isFling = false),
            Tick(5_000),
        )
        // Seul le glissé d’après compte comme lecture : de 10,5 à 10,8, pas de 10 à 10,8.
        assertThat(effects).contains(ReadingMoved(pos(10.5), pos(10.8)))
        assertThat(t.state.reading).isEqualTo(pos(10.8))
    }

    @Test
    fun selectionKeepsTheReturnCardAsItWas() {
        val t = awayAt15()
        val effects = t.feed(SelectionStarted(5_000), Displayed(5_100, pos(15.4)), SelectionEnded(6_000, pos(15.4)), Tick(9_000))
        assertThat(effects).isEmpty()
        assertThat(t.state.showReturnCard).isTrue()
        assertThat(t.state.reading).isEqualTo(pos(10.0))
    }

    @Test
    fun pendingReadingIsSettledWhenTheSelectionStarts() {
        val t = tracker()
        // Glissé de lecture pas encore validé (repos pas atteint), puis appui long.
        val effects = t.feed(Displayed(1_000, pos(10.4)), GestureEnded(1_100, pos(10.4), isFling = false), SelectionStarted(1_200))
        assertThat(effects).contains(SaveReading(pos(10.4)))
        assertThat(t.state.reading).isEqualTo(pos(10.4))
    }
```

Vérifier sur le test existant le plus proche que `ReadingMoved(pos(10.5), pos(10.8))` est bien la forme émise par un glissé de lecture (sinon adapter l’assertion à ce qu’émet un glissé de lecture ordinaire, en gardant l’intention : le départ est 10,5).

Dans `ReadingPositionCoordinatorTest` (même harnais que les tests existants, avec `FakeReaderController`) :

```kotlin
    @Test
    fun selectionIsForwardedToTheTracker() = runTest {
        // Harnais du fichier : coordinateur branché sur un FakeReaderController affichant la lecture.
        val (coordinator, fake) = attachedCoordinator()
        fake.selecting.value = true
        fake.displayed.value = testLocator(progression = 0.9, total = 0.6)   // défilement pendant la sélection
        fake.gestures.emit(GestureSignal(timeMs = clockMs, isFling = false))
        fake.selecting.value = false
        advanceTimeBy(10_000)
        assertThat(coordinator.state.value.showReturnCard).isFalse()
        assertThat(coordinator.state.value.reading).isEqualTo(initialPosition)
    }
```

(`attachedCoordinator`, `clockMs`, `initialPosition` : reprendre les noms réels du harnais existant du fichier.)

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :core:test --tests 'com.maximebier.verso.core.position.ReadingPositionTrackerTest'`
Expected: FAIL (compilation : `SelectionStarted` inconnu).

- [ ] **Step 3 : implémentation**

Dans `ReaderEvent` :

```kotlin
    /** Une sélection de texte commence (appui long) : ni lecture ni navigation jusqu’à [SelectionEnded]. */
    data class SelectionStarted(override val timeMs: Long) : ReaderEvent

    /**
     * Fin de la sélection (action de la barre, toucher qui l’annule). [position] : affiché à cet instant, qui devient
     * le point de départ du prochain mouvement, sans effet (ni lecture, ni carte, ni mots lus).
     */
    data class SelectionEnded(override val timeMs: Long, val position: BookPosition) : ReaderEvent
```

Dans le KDoc de la classe, ajouter une règle :

```
 * - Sélection (`SelectionStarted` … `SelectionEnded`) : le mouvement en attente est validé au début (l’appui long
 *   suit un arrêt). Ensuite, `Displayed` ne fait que suivre l’affiché (défilement automatique des poignées),
 *   `GestureEnded` est ignoré, le repos n’est pas évalué. À la fin, l’affiché devient le point de départ, sans effet.
```

Champ et traitement :

```kotlin
    /** Sélection de texte en cours : ni lecture ni navigation. */
    private var selecting = false

    fun onEvent(event: ReaderEvent): List<TrackerEffect> {
        val effects = mutableListOf<TrackerEffect>()
        if (selecting) {
            onEventWhileSelecting(event, effects)
            pruneHistory(event.timeMs)
            return effects
        }
        settleIfResting(event.timeMs, effects)
        when (event) {
            // … branches existantes …
            is ReaderEvent.SelectionStarted -> {
                if (motionPending || navigating) settle(effects)
                flingAwaitingPositionSinceMs = null
                selecting = true
            }
            // Fin sans début (surface recréée) : simple nouveau point de départ.
            is ReaderEvent.SelectionEnded -> restartFrom(event.timeMs, event.position)
        }
        pruneHistory(event.timeMs)
        return effects
    }

    private fun onEventWhileSelecting(event: ReaderEvent, effects: MutableList<TrackerEffect>) {
        when (event) {
            is ReaderEvent.Displayed -> {
                displayed = event.position
                displayedAtMs = event.timeMs
            }
            is ReaderEvent.SelectionEnded -> {
                selecting = false
                restartFrom(event.timeMs, event.position)
            }
            // Saut ou carte touchée pendant une sélection (barre masquée, peu probable) : la sélection est finie.
            is ReaderEvent.Jumped, is ReaderEvent.StayHere, is ReaderEvent.GoBack -> {
                selecting = false
                restartFrom(event.timeMs, displayed)
                effects += onEvent(event)
            }
            is ReaderEvent.GestureEnded, is ReaderEvent.Tick, is ReaderEvent.Rest, is ReaderEvent.SelectionStarted -> Unit
        }
    }

    /** L’affiché devient le point de départ du prochain mouvement : aucune lecture, aucune navigation en attente. */
    private fun restartFrom(timeMs: Long, position: BookPosition) {
        displayed = position
        displayedAtMs = timeMs
        lastMotion = Stamped(timeMs, position)
        motionPending = false
        navigating = false
        navigationStartedAtMs = null
        flingAwaitingPositionSinceMs = null
        displayedSinceGestureEnd = false
        movedSinceGesture = false
        resetWindow(timeMs, position)
        if (mode == TrackerMode.AWAY) {
            anchor = Stamped(timeMs, position)
            confirmWindowStartMs = null
        }
    }
```

Vérifier en lisant `resetWindow` qu’elle vide la fenêtre glissante (`samples`) et y pose la position donnée ; sinon remplacer l’appel par `samples.clear(); samples.addLast(Stamped(timeMs, position))`. Le `when` existant devient exhaustif avec les deux nouvelles branches (le compilateur le signale s’il en manque une).

Dans `ReadingPositionCoordinator.attach`, ajouter un troisième travail, et un état gardé entre deux surfaces :

```kotlin
    /** Sélection annoncée au tracker et pas encore close (une surface retirée pendant la sélection ne la ferme pas). */
    private var selectionOpen = false

    // dans attach(), avant la liste des jobs :
    if (selectionOpen && !readerController.selecting.value) {
        selectionOpen = false
        dispatch(ReaderEvent.SelectionEnded(clock(), lastDisplayed))
    }
    // dans la liste des jobs :
            scope.launch {
                readerController.selecting.collect { active ->
                    if (active && !selectionOpen) {
                        selectionOpen = true
                        dispatch(ReaderEvent.SelectionStarted(clock()))
                    } else if (!active && selectionOpen) {
                        selectionOpen = false
                        dispatch(ReaderEvent.SelectionEnded(clock(), lastDisplayed))
                    }
                }
            },
```

(L’ordre des collectes `displayed` puis `selecting` dans la liste garde `lastDisplayed` à jour avant la fin de sélection dans le cas courant.)

- [ ] **Step 4 : relancer**

Run: `./gradlew :core:test --tests 'com.maximebier.verso.core.position.*'` puis `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.ReadingPositionCoordinatorTest'`
Expected: PASS, et tous les tests existants du tracker restent verts.

- [ ] **Step 5 : relecture ciblée** (machine à états) : un sous-agent relecteur lit le diff de `ReadingPositionTracker.kt` contre la règle du KDoc et la spec (« Sélectionner … n’est ni lecture ni navigation ») ; corriger avant de continuer.

- [ ] **Step 6 : commit**

```bash
git add core/src app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinator.kt app/src/main/kotlin/com/maximebier/verso/reader app/src/test
git commit -m "17.3 : la machine à états ignore la sélection"
```

---

### Task 17.4 : sélection et surlignages dans le contrôleur

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderController.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/FragmentReaderController.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderGestures.kt`
- Modify (test): `app/src/test/kotlin/com/maximebier/verso/reader/FakeReaderController.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/reader/FragmentReaderControllerTest.kt`

**Interfaces:**
- Consumes: `TextQuotes.normalize` (17.1).
- Produces (ajouts à `ReaderController`, contrat) :

```kotlin
    /** Sélection de texte en cours (appui long), du début à la fin de l’ActionMode d’Android. */
    val selecting: StateFlow<Boolean>

    /** Passage sélectionné, relu pendant la sélection ([ReaderGestures.SELECTION_POLL_MS]) ; null sans sélection. */
    val selection: StateFlow<TextSelection?>

    /** Passage sélectionné relu dans la WebView à l’instant (au moment d’une action de la barre). */
    suspend fun currentSelection(): TextSelection?

    /** Efface la sélection (après une action de la barre). */
    fun clearSelection()

    /** Surlignages dessinés dans le texte ; remplace la liste précédente. */
    suspend fun showHighlights(marks: List<HighlightMark>)

    /** Id du surlignage touché dans le texte. */
    val highlightTaps: Flow<Long>
```

```kotlin
/** Passage sélectionné ; [locator] porte `text.highlight/before/after` et la progression totale. */
data class TextSelection(val locator: Locator) {
    val text: String get() = TextQuotes.normalize(locator.text.highlight.orEmpty())
}

/** Surlignage à dessiner. */
data class HighlightMark(val id: Long, val locator: Locator)
```

`FragmentReaderController` gagne aussi : `fun onSelectionStarted()`, `fun onSelectionEnded()`, `fun onHighlightActivated(id: Long)`, `fun bindSelection(read: suspend () -> Selection?, clear: () -> Unit)` (type `org.readium.r2.navigator.Selection`), `fun bindHighlights(show: suspend (List<HighlightMark>) -> Unit)`.

Relecture dédiée de cette tâche (moteur de lecture).

- [ ] **Step 1 : tests qui échouent** (dans `FragmentReaderControllerTest`, harnais existant ; ajouter au `Harness` : `var selectionText: String? = null`, `var cleared = 0`, et après `bind(...)` :

```kotlin
            bindSelection(
                read = { selectionText?.let { Selection(at("ch1.xhtml", 0.4).copy(text = Locator.Text(highlight = it)), null) } },
                clear = { cleared++ },
            )
```

(import `org.readium.r2.navigator.Selection`, constructeur `Selection(Locator, RectF?)`).

```kotlin
    @Test
    fun selectionGesturesAreNeverSignalled() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.2))
        h.controller.gestures.test {
            // Appui long : le doigt se pose, la sélection commence, le doigt glisse pour l’étendre puis se lève.
            h.controller.onPointerDown()
            h.selectionText = "la campagne"
            h.controller.onSelectionStarted()
            h.controller.onGestureReleased(velocityYPxPerSecond = 0f, dragDyPx = 120f)
            // Toucher qui annule la sélection : ni geste, ni barre de lecture.
            h.controller.onPointerDown()
            h.controller.onSelectionEnded()
            h.controller.onTapLikeGesture()
            h.controller.onReadiumTap()
            advanceTimeBy(5_000)
            expectNoEvents()
        }
        assertThat(h.taps).isEqualTo(0)
    }

    @Test
    fun selectionTextIsPolledWhileSelecting() = runTest {
        val h = Harness(this)
        h.selectionText = "la campagne"
        h.controller.onSelectionStarted()
        assertThat(h.controller.selecting.value).isTrue()
        advanceTimeBy(ReaderGestures.SELECTION_POLL_MS + 1)
        assertThat(h.controller.selection.value?.text).isEqualTo("la campagne")
        h.selectionText = "la campagne ainsi"
        advanceTimeBy(ReaderGestures.SELECTION_POLL_MS + 1)
        assertThat(h.controller.selection.value?.text).isEqualTo("la campagne ainsi")
        h.controller.clearSelection()
        assertThat(h.cleared).isEqualTo(1)
        assertThat(h.controller.selecting.value).isFalse()
        assertThat(h.controller.selection.value).isNull()
    }

    @Test
    fun currentSelectionCarriesTotalProgression() = runTest {
        val h = Harness(this)
        h.controller.setPositions(positions)
        h.selectionText = "passage"
        val selection = h.controller.currentSelection()!!
        assertThat(selection.locator.locations.totalProgression).isNotNull()
    }

    @Test
    fun tapOnAHighlightOpensItInsteadOfTheBars() = runTest {
        val h = Harness(this)
        h.controller.onDisplayed(at("ch1.xhtml", 0.2))
        h.controller.highlightTaps.test {
            h.controller.onPointerDown()
            h.controller.onTapLikeGesture()
            h.controller.onHighlightActivated(42L)
            assertThat(awaitItem()).isEqualTo(42L)
            advanceTimeBy(ReaderGestures.TAP_FALLBACK_DELAY_MS * 2)
        }
        assertThat(h.taps).isEqualTo(0)
    }

    @Test
    fun ordinaryTapStillTogglesTheBarsAfterTheGuard() = runTest {
        val h = Harness(this)
        h.controller.onSelectionStarted()
        h.controller.onSelectionEnded()
        advanceTimeBy(ReaderGestures.SELECTION_DISMISS_TAP_MS + 1)
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture()
        h.controller.onReadiumTap()
        assertThat(h.taps).isEqualTo(1)
    }
```

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.reader.FragmentReaderControllerTest'`
Expected: FAIL (compilation).

- [ ] **Step 3 : implémentation**

`ReaderGestures.kt`, à la fin :

```kotlin
    /** Pendant une sélection, intervalle de relecture du passage sélectionné (barre 3.04). */
    const val SELECTION_POLL_MS = 300L

    /**
     * Après la fin d’une sélection, un tap signalé par Readium dans ce délai est celui qui l’a annulée (le JavaScript
     * peut le voir une fois la sélection déjà vide) : il n’affiche pas la barre de lecture.
     */
    const val SELECTION_DISMISS_TAP_MS = 600L
```

`ReaderController.kt` : ajouter les membres et les deux classes du bloc Interfaces (import `com.maximebier.verso.core.notes.TextQuotes`, `kotlinx.coroutines.flow.Flow`).

`FragmentReaderController.kt` :

```kotlin
    private val selectingState = MutableStateFlow(false)
    private val selectionState = MutableStateFlow<TextSelection?>(null)
    private val highlightTapFlow = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    private var readSelection: (suspend () -> Selection?)? = null
    private var clearNativeSelection: (() -> Unit)? = null
    private var highlightsSink: (suspend (List<HighlightMark>) -> Unit)? = null
    private var selectionPoll: Job? = null

    /** Le doigt posé pendant une sélection : tout le geste lui appartient (poignées, toucher qui l’annule). */
    private var selectionGesture = false

    /** Fin de la dernière sélection (garde contre le tap qui l’annule, [ReaderGestures.SELECTION_DISMISS_TAP_MS]). */
    private var selectionEndedAt: Long = Long.MIN_VALUE

    override val selecting: StateFlow<Boolean> = selectingState.asStateFlow()
    override val selection: StateFlow<TextSelection?> = selectionState.asStateFlow()
    override val highlightTaps: Flow<Long> = highlightTapFlow.asSharedFlow()

    fun bindSelection(read: suspend () -> Selection?, clear: () -> Unit) {
        readSelection = read
        clearNativeSelection = clear
    }

    fun bindHighlights(show: suspend (List<HighlightMark>) -> Unit) {
        highlightsSink = show
    }

    override suspend fun showHighlights(marks: List<HighlightMark>) {
        highlightsSink?.invoke(marks)
    }

    override suspend fun currentSelection(): TextSelection? =
        readSelection?.invoke()
            ?.takeIf { !it.locator.text.highlight.isNullOrBlank() }
            ?.let { TextSelection(withTotalProgression(it.locator)) }

    /** ActionMode d’Android créé : une sélection commence (menu natif vidé par la surface). */
    fun onSelectionStarted() {
        if (selectingState.value) return
        cancelRelayout(publishScreen = true)
        pendingTap?.cancel()
        pendingTap = null
        dropPendingGesture()
        selectingState.value = true
        selectionPoll?.cancel()
        selectionPoll = scope.launch {
            while (true) {
                currentSelection()?.let { selectionState.value = it }
                delay(ReaderGestures.SELECTION_POLL_MS)
            }
        }
    }

    /** ActionMode détruit : sélection effacée (action, toucher ailleurs, retour). */
    fun onSelectionEnded() {
        if (!selectingState.value) return
        selectionPoll?.cancel()
        selectionPoll = null
        selectionState.value = null
        selectingState.value = false
        selectionEndedAt = uptimeMs()
    }

    override fun clearSelection() {
        clearNativeSelection?.invoke()
        onSelectionEnded()
    }

    /** Surlignage touché (Readium : décoration activée, pas de `onTap`) ; le tap vu par l’app n’est pas rattrapé. */
    fun onHighlightActivated(id: Long) {
        pendingTap?.cancel()
        pendingTap = null
        if (selectingState.value) return
        highlightTapFlow.tryEmit(id)
    }
```

Modifier les gestes existants :

```kotlin
    fun onPointerDown() {
        selectionGesture = selectingState.value
        if (selectionGesture) return   // poignées et toucher qui annule : ni lecture, ni bords, ni tap
        // … corps existant inchangé …
    }

    fun onGestureReleased(velocityYPxPerSecond: Float, dragDyPx: Float) {
        // Appui long : la sélection a commencé pendant ce geste (glissé qui l’étend) — rien à signaler.
        if (selectionGesture || selectingState.value) return
        // … corps existant inchangé …
    }

    fun onTapLikeGesture() {
        if (selectionGesture || selectingState.value) return
        if (touchStoppedScroll) return
        // … reste inchangé …
    }

    fun onReadiumTap() {
        val now = uptimeMs()
        if (selectingState.value || now - selectionEndedAt < ReaderGestures.SELECTION_DISMISS_TAP_MS) return
        // … reste inchangé (le `val now` existant est réutilisé) …
    }
```

`FakeReaderController` :

```kotlin
    override val selecting = MutableStateFlow(false)
    override val selection = MutableStateFlow<TextSelection?>(null)
    override val highlightTaps = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    /** Réponse de `currentSelection()` ; à défaut, `selection.value`. */
    var nativeSelection: TextSelection? = null
    var selectionCleared = 0
    val shownHighlights = mutableListOf<List<HighlightMark>>()

    override suspend fun currentSelection(): TextSelection? = nativeSelection ?: selection.value

    override fun clearSelection() {
        selectionCleared++
        nativeSelection = null
        selection.value = null
        selecting.value = false
    }

    override suspend fun showHighlights(marks: List<HighlightMark>) {
        shownHighlights += marks
    }

    /** Simule un appui long sur [text] (dans le chapitre de la position affichée). */
    fun select(text: String, before: String = "", after: String = "") {
        val base = displayed.value ?: testLocator()
        val chosen = TextSelection(base.copy(text = Locator.Text(before = before, highlight = text, after = after)))
        selecting.value = true
        selection.value = chosen
    }
```

- [ ] **Step 4 : relancer**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.reader.*'`
Expected: PASS (nouveaux tests et tous les tests existants du contrôleur).

- [ ] **Step 5 : relecture ciblée** (moteur de lecture) sur le diff de `FragmentReaderController.kt` : gestes pendant une sélection, garde du tap, interaction avec la remise en page et le changement de chapitre au bord.

- [ ] **Step 6 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/reader app/src/test/kotlin/com/maximebier/verso/reader
git commit -m "17.4 : sélection et surlignages dans le contrôleur du lecteur"
```

---

### Task 17.5 : sélection et décorations dans la surface Readium

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/readium/HighlightDecoration.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/reader/SelectionActionModeCallback.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/readium/SearchMatchDecoration.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/readium/VersoReadingPreferences.kt` (`applyVerso` reçoit le rappel de sélection)
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderSurface.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/readium/HighlightDecorationTest.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/reader/SelectionActionModeCallbackTest.kt`

**Interfaces:**
- Consumes: `FragmentReaderController.{onSelectionStarted, onSelectionEnded, onHighlightActivated, bindSelection, bindHighlights}` (17.4), `HighlightMark`.
- Produces: `HighlightDecoration.{GROUP, decorations, idOf}` ; `SearchMatchDecoration.decoration(id: String, locator: Locator, theme: AppTheme): Decoration` (extrait de `decorations`) ; `applyVerso(theme: AppTheme, selectionCallback: ActionMode.Callback? = null)`.

- [ ] **Step 1 : tests qui échouent**

```kotlin
// app/src/test/kotlin/com/maximebier/verso/readium/HighlightDecorationTest.kt
package com.maximebier.verso.readium

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.reader.HighlightMark
import com.maximebier.verso.reader.testLocator
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration

@RunWith(AndroidJUnit4::class)
class HighlightDecorationTest {
    @Test fun oneDecorationPerHighlightWithBackgroundAndUnderline() {
        val marks = listOf(HighlightMark(7, testLocator()), HighlightMark(9, testLocator(progression = 0.8)))
        AppTheme.entries.forEach { theme ->
            val decorations = HighlightDecoration.decorations(marks, theme)
            assertThat(decorations.map { HighlightDecoration.idOf(it.id) }).containsExactly(7L, 9L).inOrder()
            val style = decorations.first().style as Decoration.Style.Highlight
            assertThat(style.tint).isEqualTo(ReadingStyle.palette(theme).highlight.toArgb())
            assertThat(SearchMatchDecoration.element(decorations.first())).contains("border-bottom: 2px solid")
        }
    }

    @Test fun foreignIdsAreIgnored() {
        assertThat(HighlightDecoration.idOf("verso-search-match")).isNull()
        assertThat(HighlightDecoration.idOf("highlight-abc")).isNull()
    }
}
```

```kotlin
// app/src/test/kotlin/com/maximebier/verso/reader/SelectionActionModeCallbackTest.kt
package com.maximebier.verso.reader

import android.view.ActionMode
import android.view.Menu
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import androidx.appcompat.view.menu.MenuBuilder

@RunWith(AndroidJUnit4::class)
class SelectionActionModeCallbackTest {
    @Test fun nativeMenuIsEmptiedAndSelectionReported() {
        var started = 0
        var ended = 0
        val callback = SelectionActionModeCallback(onStarted = { started++ }, onEnded = { ended++ })
        val menu: Menu = MenuBuilder(RuntimeEnvironment.getApplication()).apply { add("Copier"); add("Partager") }
        val mode: ActionMode? = null
        assertThat(callback.onCreateActionMode(mode, menu)).isTrue()
        assertThat(menu.size()).isEqualTo(0)
        menu.add("Tout sélectionner")
        assertThat(callback.onPrepareActionMode(mode, menu)).isTrue()
        assertThat(menu.size()).isEqualTo(0)
        callback.onDestroyActionMode(mode)
        assertThat(started to ended).isEqualTo(1 to 1)
    }
}
```

Si `androidx.appcompat` n’est pas une dépendance du module (vérifier `app/build.gradle.kts`), remplacer le `MenuBuilder` par un `Menu` factice minimal écrit dans le test (classe anonyme qui implémente `Menu` avec une `MutableList` d’éléments ; seules `add(CharSequence)`, `clear()` et `size()` servent, les autres lèvent `UnsupportedOperationException`).

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.readium.HighlightDecorationTest' --tests 'com.maximebier.verso.reader.SelectionActionModeCallbackTest'`
Expected: FAIL (compilation).

- [ ] **Step 3 : implémentation**

`SearchMatchDecoration.kt` : extraire la construction d’une décoration ; `decorations(locator, theme)` devient `listOf(decoration(ID, locator, theme))`.

```kotlin
    /** Une décoration aux couleurs du thème (fond `highlight`, soulignement `text`) ; gabarit commun avec les surlignages. */
    fun decoration(id: String, locator: Locator, theme: AppTheme): Decoration {
        val colors = palette(theme)
        return Decoration(
            id = id,
            locator = locator,
            style = Decoration.Style.Highlight(tint = colors.highlight.toArgb()),
            extras = mapOf(
                EXTRA_BACKGROUND to colors.highlight.css(),
                EXTRA_LINE to colors.text.css(),
                EXTRA_BLEND to if (theme.isDark) "lighten" else "darken",
            ),
        )
    }
```

```kotlin
// app/src/main/kotlin/com/maximebier/verso/readium/HighlightDecoration.kt
package com.maximebier.verso.readium

import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.reader.HighlightMark
import org.readium.r2.navigator.Decoration

/**
 * Surlignages dessinés dans le texte (V3) : même rendu que le mot trouvé par la recherche, fond `highlight` et
 * soulignement (jamais la couleur seule), par le gabarit de [SearchMatchDecoration]. Groupe à part, avec un écouteur :
 * le toucher d’un surlignage arrive par `onDecorationActivated`, pas comme un tap.
 */
object HighlightDecoration {
    const val GROUP = "verso-highlights"
    private const val PREFIX = "highlight-"

    fun decorations(marks: List<HighlightMark>, theme: AppTheme): List<Decoration> =
        marks.map { SearchMatchDecoration.decoration("$PREFIX${it.id}", it.locator, theme) }

    fun idOf(decorationId: String): Long? = decorationId.removePrefix(PREFIX)
        .takeIf { decorationId.startsWith(PREFIX) }
        ?.toLongOrNull()
}
```

```kotlin
// app/src/main/kotlin/com/maximebier/verso/reader/SelectionActionModeCallback.kt
package com.maximebier.verso.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

/**
 * Menu de sélection d’Android remplacé par la barre de Verso (3.04) : le menu reste vide (aucune barre flottante),
 * et le début et la fin de la sélection sont signalés. Readium garde la sélection et ses poignées.
 */
internal class SelectionActionModeCallback(
    private val onStarted: () -> Unit,
    private val onEnded: () -> Unit,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu): Boolean {
        menu.clear()
        onStarted()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu): Boolean {
        menu.clear()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode?) {
        onEnded()
    }
}
```

`VersoReadingPreferences.applyVerso(theme: AppTheme, selectionCallback: ActionMode.Callback? = null)` : ajouter `selectionActionModeCallback = selectionCallback` à côté de `decorationTemplates`.

`ReaderSurface.kt` :

1. Création du fragment : `configuration = EpubNavigatorFragment.Configuration { applyVerso(initialStyle.theme, SelectionActionModeCallback(controller::onSelectionStarted, controller::onSelectionEnded)) }`.
2. État des surlignages, appliqué comme la marque de recherche et redessiné au changement de thème :

```kotlin
    // Surlignages du livre ; redessinés aux couleurs du thème quand il change.
    var highlightMarks by remember { mutableStateOf<List<HighlightMark>>(emptyList()) }
    LaunchedEffect(navigator, highlightMarks, style.theme) {
        val nav = navigator ?: return@LaunchedEffect
        nav.applyDecorations(HighlightDecoration.decorations(highlightMarks, style.theme), HighlightDecoration.GROUP)
    }
    DisposableEffect(navigator) {
        val nav = navigator ?: return@DisposableEffect onDispose {}
        val listener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val id = HighlightDecoration.idOf(event.decoration.id) ?: return false
                controller.onHighlightActivated(id)
                return true
            }
        }
        nav.addDecorationListener(HighlightDecoration.GROUP, listener)
        onDispose { nav.removeDecorationListener(listener) }
    }
```

3. Dans le `LaunchedEffect(navigator)` qui appelle `controller.bind(...)`, avant `currentOnReady(controller)` :

```kotlin
        controller.bindSelection(read = { nav.currentSelection() }, clear = { nav.clearSelection() })
        controller.bindHighlights { highlightMarks = it }
```

4. Surface retirée pendant une sélection (rotation) : dans le `DisposableEffect(navigator)` ci-dessus, `onDispose` appelle aussi `controller.onSelectionEnded()`.

- [ ] **Step 4 : relancer**

Même commande, puis `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.readium.*' --tests 'com.maximebier.verso.reader.*'`. Expected: PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/readium app/src/main/kotlin/com/maximebier/verso/reader app/src/test
git commit -m "17.5 : sélection Android remplacée et surlignages dessinés dans Readium"
```

À vérifier sur le téléphone en fin d’étape (non testable sur la JVM) : aucun menu flottant d’Android à l’appui long, poignées présentes, sélection en continu et en pages ; un toucher sur un surlignage ne fait pas apparaître la barre de lecture. Si une barre flottante vide apparaît malgré le menu vide, retourner `false` dans `onCreateActionMode` **après** avoir signalé le début, et vérifier que les poignées restent (sinon garder `true` et le noter dans la spec).

---

### Task 17.6 : `HighlightCoordinator` (surligner, copier, toucher)

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/HighlightCoordinator.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderViewModel.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/reader/HighlightCoordinatorTest.kt`

**Interfaces:**
- Consumes: `HighlightRepository` (17.2), `ReaderController.{selection, currentSelection, clearSelection, showHighlights, highlightTaps}` (17.4), `TextQuotes`, `HighlightMerge` (17.1), `Locators`, `readChapterHtml(publication, href)` et `ChapterText.plainText` (existants, `reader/`).
- Produces:

```kotlin
data class HighlightUiState(
    /** Début du passage sélectionné (barre 3.04) ; null = pas de barre. */
    val selectionText: String? = null,
    /** Feuille « Ajouter une note » (3.05) ; null = fermée. Rempli en 18.1. */
    val noteSheet: NoteSheetState? = null,
    /** Feuille d’un surlignage touché ; null = fermée. */
    val actions: HighlightActionsState? = null,
)
data class NoteSheetState(val passage: String, val note: String, val editing: Boolean)   // 18.1
data class HighlightActionsState(val id: Long, val passage: String, val note: String?, val location: String?)
sealed interface HighlightEvent {
    data class Copied(val text: String) : HighlightEvent
    data class Deleted(val row: HighlightEntity) : HighlightEvent   // 18.1
}

class HighlightCoordinator(
    private val bookId: Long,
    private val highlights: HighlightRepository,
    private val scope: CoroutineScope,
    /** Texte brut normalisé du fichier d’un locator (`TextQuotes.normalize(ChapterText.plainText(html))`), null si illisible. */
    private val chapterText: suspend (Locator) -> String?,
    private val chapterPath: (Locator) -> List<String>,
    /** « Deuxième partie, chap. I · 30 % » d’un surlignage (titre de la feuille). */
    private val locationLabel: (HighlightEntity) -> String?,
    private val withTotalProgression: (Locator) -> Locator,
    private val clock: () -> Long,
) {
    val state: StateFlow<HighlightUiState>
    val events: SharedFlow<HighlightEvent>
    fun attach(controller: ReaderController)
    fun detach()
    fun highlightSelection()
    fun copySelection()
    fun copyHighlight()
    fun dismissActions()
    suspend fun awaitIdle()   // tests : attend la fin des écritures lancées
}
// ReaderViewModel :
val highlights: StateFlow<HighlightUiState>
val highlightEvents: SharedFlow<HighlightEvent>
fun highlightSelection(); fun copySelection(); fun copyHighlight(); fun dismissHighlightActions()
```

- [ ] **Step 1 : tests qui échouent**

```kotlin
// app/src/test/kotlin/com/maximebier/verso/ui/reader/HighlightCoordinatorTest.kt
package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.reader.FakeReaderController
import com.maximebier.verso.reader.testLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HighlightCoordinatorTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = HighlightRepository(db.highlightDao()) { 1_000L }

    /** Texte du chapitre 2 de [testLocator]. */
    private val chapter = TextQuotes.normalize(
        "L’eau qui court au bord de l’herbe sépare d’une raie blanche la couleur des prés et celle des sillons, et " +
            "la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert.",
    )

    @After fun tearDown() = db.close()

    private suspend fun TestScope.setUp(textAvailable: Boolean = true): Triple<HighlightCoordinator, FakeReaderController, Long> {
        val bookId = db.bookDao().insert(testBook(sha256 = "a"))
        val fake = FakeReaderController(testLocator())
        val coordinator = HighlightCoordinator(
            bookId = bookId,
            highlights = repository,
            scope = backgroundScope,
            chapterText = { if (textAvailable) chapter else null },
            chapterPath = { listOf("Deuxième partie", "I") },
            locationLabel = { "Deuxième partie, chap. I · 30 %" },
            withTotalProgression = { it },
            clock = { 1_000L },
        )
        coordinator.attach(fake)
        return Triple(coordinator, fake, bookId)
    }

    @Test fun selectionShowsItsBeginningInTheBar() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake) = setUp()
        fake.select("la campagne ainsi ressemble à un grand manteau déplié qui a un collet de velours vert.")
        assertThat(coordinator.state.value.selectionText).isEqualTo("la campagne ainsi ressemble à un grand manteau…")
        fake.clearSelection()
        assertThat(coordinator.state.value.selectionText).isNull()
    }

    @Test fun highlightCreatesTheRowAndClearsTheSelection() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche", before = "sépare d’une ", after = " la couleur")
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val row = repository.forBook(bookId).single()
        assertThat(row.text).isEqualTo("raie blanche")
        assertThat(row.note).isNull()
        assertThat(row.chapterPathList()).containsExactly("Deuxième partie", "I").inOrder()
        assertThat(fake.selectionCleared).isEqualTo(1)
        // Les flux Room émettent depuis leur propre exécuteur : attendre en temps réel que la marque soit poussée.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (fake.shownHighlights.lastOrNull()?.map { it.id } != listOf(row.id)) delay(10) }
        }
    }

    @Test fun overlappingHighlightMergesIntoOne() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche la couleur")
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        repository.setNote(repository.forBook(bookId).single().id, "première")
        fake.select("la couleur des prés")
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val row = repository.forBook(bookId).single()
        assertThat(row.text).isEqualTo("raie blanche la couleur des prés")
        assertThat(row.note).isEqualTo("première")
    }

    @Test fun unreadableChapterStillHighlights() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp(textAvailable = false)
        fake.select("raie blanche")
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        assertThat(repository.forBook(bookId).single().text).isEqualTo("raie blanche")
    }

    @Test fun copyEmitsThePassageWithoutCreatingAnything() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        coordinator.events.test {
            fake.select(" raie\nblanche ")
            coordinator.copySelection()
            assertThat(awaitItem()).isEqualTo(HighlightEvent.Copied("raie blanche"))
        }
        assertThat(repository.forBook(bookId)).isEmpty()
        assertThat(fake.selectionCleared).isEqualTo(1)
    }

    @Test fun tappingAHighlightOpensItsSheet() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val id = repository.forBook(bookId).single().id
        fake.highlightTaps.emit(id)
        coordinator.awaitIdle()
        assertThat(coordinator.state.value.actions)
            .isEqualTo(HighlightActionsState(id, "raie blanche", null, "Deuxième partie, chap. I · 30 %"))
        coordinator.events.test {
            coordinator.copyHighlight()
            assertThat(awaitItem()).isEqualTo(HighlightEvent.Copied("raie blanche"))
        }
        assertThat(coordinator.state.value.actions).isNull()
    }
}
```

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.HighlightCoordinatorTest'`
Expected: FAIL (compilation).

- [ ] **Step 3 : implémentation**

```kotlin
// app/src/main/kotlin/com/maximebier/verso/ui/reader/HighlightCoordinator.kt
package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.notes.HighlightMerge
import com.maximebier.verso.core.notes.PlacedHighlight
import com.maximebier.verso.core.notes.TextQuote
import com.maximebier.verso.core.notes.TextQuotes
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.toChapterPathColumn
import com.maximebier.verso.reader.HighlightMark
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.reader.TextSelection
import com.maximebier.verso.readium.Locators
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.publication.Locator

data class HighlightUiState(
    val selectionText: String? = null,
    val noteSheet: NoteSheetState? = null,
    val actions: HighlightActionsState? = null,
)

/** Feuille de note (3.05) : passage, texte du champ ; [editing] = note d’un surlignage existant. */
data class NoteSheetState(val passage: String, val note: String, val editing: Boolean)

/** Feuille d’un surlignage touché : passage, note, « Deuxième partie, chap. I · 30 % ». */
data class HighlightActionsState(val id: Long, val passage: String, val note: String?, val location: String?)

sealed interface HighlightEvent {
    data class Copied(val text: String) : HighlightEvent
    data class Deleted(val row: HighlightEntity) : HighlightEvent
}

/**
 * Surlignages et notes du livre ouvert (V3) : barre de sélection (3.04), création et fusion, toucher d’un
 * surlignage. Séparé de [ReaderViewModel] comme `SessionCoordinator`. Rien ici ne touche la position de lecture :
 * la machine à états voit la sélection par `ReaderController.selecting` (17.3).
 */
class HighlightCoordinator(
    private val bookId: Long,
    private val highlights: HighlightRepository,
    private val scope: CoroutineScope,
    private val chapterText: suspend (Locator) -> String?,
    private val chapterPath: (Locator) -> List<String>,
    private val locationLabel: (HighlightEntity) -> String?,
    private val withTotalProgression: (Locator) -> Locator,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow(HighlightUiState())
    val state: StateFlow<HighlightUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HighlightEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HighlightEvent> = _events.asSharedFlow()

    private var controller: ReaderController? = null
    private var jobs: List<Job> = emptyList()
    private val writes = mutableListOf<Job>()

    /** Une écriture à la fois : deux « Surligner » rapides ne fusionnent pas sur une liste périmée. */
    private val writeLock = Mutex()

    fun attach(readerController: ReaderController) {
        if (controller === readerController) return
        detach()
        controller = readerController
        jobs = listOf(
            scope.launch {
                readerController.selection.collect { selection ->
                    _state.update { it.copy(selectionText = selection?.text?.takeIf(String::isNotEmpty)?.let(TextQuotes::preview)) }
                }
            },
            scope.launch {
                highlights.observe(bookId).collect { rows ->
                    val marks = rows.mapNotNull { row -> Locators.fromJson(row.locatorJson)?.let { HighlightMark(row.id, it) } }
                    readerController.showHighlights(marks)
                }
            },
            scope.launch { readerController.highlightTaps.collect(::openActions) },
        )
    }

    fun detach() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        controller = null
        _state.update { it.copy(selectionText = null) }
    }

    /** « Surligner » : surlignage créé (ou fusionné) aussitôt, sélection effacée, sans snackbar. */
    fun highlightSelection() {
        val reader = controller ?: return
        launchWrite {
            val selection = reader.currentSelection()
            reader.clearSelection()
            if (selection != null) save(selection.locator, note = null)
        }
    }

    /** « Copier » : passage dans le presse-papiers (par l’écran), sélection effacée. */
    fun copySelection() {
        val reader = controller ?: return
        scope.launch {
            val text = reader.currentSelection()?.text
            reader.clearSelection()
            if (!text.isNullOrEmpty()) _events.emit(HighlightEvent.Copied(text))
        }
    }

    fun copyHighlight() {
        val actions = _state.value.actions ?: return
        _state.update { it.copy(actions = null) }
        _events.tryEmit(HighlightEvent.Copied(actions.passage))
    }

    fun dismissActions() = _state.update { it.copy(actions = null) }

    private suspend fun openActions(id: Long) {
        val row = highlights.get(id) ?: return
        _state.update { it.copy(actions = HighlightActionsState(row.id, row.text, row.note, locationLabel(row))) }
    }

    /**
     * Enregistre le passage de [selected] avec [note], fusionné avec les surlignages qu’il recoupe dans son chapitre
     * (même fichier). Passage introuvable dans le texte brut, ou texte illisible : ajouté tel quel.
     */
    internal suspend fun save(selected: Locator, note: String?): Long = writeLock.withLock {
        val now = clock()
        val quote = TextQuote(
            highlight = selected.text.highlight.orEmpty(),
            before = selected.text.before.orEmpty(),
            after = selected.text.after.orEmpty(),
        )
        val href = Locators.hrefKey(selected)
        val text = chapterText(selected)
        val span = text?.let { TextQuotes.locate(it, quote) }
        if (text == null || span == null) {
            return@withLock highlights.saveMerged(newRow(withTotalProgression(selected), TextQuotes.normalize(quote.highlight), note, now, now), emptyList())
        }
        val sameChapter = highlights.forBook(bookId).filter { row ->
            Locators.fromJson(row.locatorJson)?.let(Locators::hrefKey) == href
        }
        val placed = sameChapter.map { row ->
            val locator = Locators.fromJson(row.locatorJson)
            val rowQuote = TextQuote(
                highlight = locator?.text?.highlight ?: row.text,
                before = locator?.text?.before.orEmpty(),
                after = locator?.text?.after.orEmpty(),
            )
            PlacedHighlight(row.id, TextQuotes.locate(text, rowQuote), row.note)
        }
        val plan = HighlightMerge.plan(span, note, placed)
        val merged = TextQuotes.quoteAt(text, plan.span)
        // Locator reconstruit : Readium retrouve le passage par son texte (sans sélecteur CSS, qui ne couvrirait pas
        // une plage sur plusieurs paragraphes) ; progression du début du passage dans le fichier.
        val locator = withTotalProgression(
            Locator(
                href = selected.href,
                mediaType = selected.mediaType,
                title = selected.title,
                locations = Locator.Locations(progression = plan.span.start.toDouble() / text.length),
                text = Locator.Text(before = merged.before, highlight = merged.highlight, after = merged.after),
            ),
        )
        val createdAt = sameChapter.filter { it.id in plan.absorbedIds }.minOfOrNull { it.createdAt } ?: now
        highlights.saveMerged(newRow(locator, merged.highlight, plan.note, createdAt, now), plan.absorbedIds)
    }

    private fun newRow(locator: Locator, text: String, note: String?, createdAt: Long, now: Long) = HighlightEntity(
        bookId = bookId,
        locatorJson = Locators.toJson(locator),
        text = text,
        note = note?.trim()?.takeIf(String::isNotEmpty),
        progression = (locator.locations.totalProgression ?: 0.0).coerceIn(0.0, 1.0),
        chapterPath = chapterPath(locator).toChapterPathColumn(),
        createdAt = createdAt,
        updatedAt = now,
    )

    private fun launchWrite(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        synchronized(writes) { writes += job }
        job.invokeOnCompletion { synchronized(writes) { writes -= job } }
    }

    /** Tests et fermeture : attend les écritures lancées. */
    suspend fun awaitIdle() {
        val pending = synchronized(writes) { writes.toList() }
        pending.joinAll()
    }
}
```

Note : `TextSelection` n’est pas importée si inutilisée ; retirer l’import si lint le signale.

`ReaderViewModel` :

1. Constructeur : `private val highlightRepository: HighlightRepository` (après `settings`), passé par la fabrique (`container.highlights`).
2. Cache du texte des chapitres et coordinateur, créé dans `start()` après `positions` :

```kotlin
    private val chapterTexts = HashMap<String, String>()
    private var highlightCoordinator: HighlightCoordinator? = null
    private val highlightState = MutableStateFlow(HighlightUiState())
    /** Barre de sélection, feuilles de note et de surlignage (V3). */
    val highlights: StateFlow<HighlightUiState> = highlightState.asStateFlow()
    private val highlightEventsFlow = MutableSharedFlow<HighlightEvent>(extraBufferCapacity = 8)
    val highlightEvents: SharedFlow<HighlightEvent> = highlightEventsFlow.asSharedFlow()

    // dans start(), après `positions = …` :
        val coordinatorForHighlights = HighlightCoordinator(
            bookId = bookId,
            highlights = highlightRepository,
            scope = viewModelScope,
            chapterText = { locator -> chapterPlainText(publication, locator) },
            chapterPath = { locator -> chapterPathAt(_uiState.value.toc, Locators.hrefKey(locator), locator.locations.progression) },
            locationLabel = ::highlightLocation,
            withTotalProgression = ::withTotalProgression,
            clock = clock,
        )
        highlightCoordinator = coordinatorForHighlights
        viewModelScope.launch { coordinatorForHighlights.state.collect { highlightState.value = it } }
        viewModelScope.launch { coordinatorForHighlights.events.collect { highlightEventsFlow.emit(it) } }
```

```kotlin
    /** Texte brut normalisé du fichier d’un locator, lu une fois par fichier. */
    private suspend fun chapterPlainText(publication: Publication, locator: Locator): String? {
        val key = Locators.hrefKey(locator)
        chapterTexts[key]?.let { return it }
        val html = readChapterHtml(publication, locator.href.removeFragment()) ?: return null
        val text = withContext(Dispatchers.Default) { TextQuotes.normalize(ChapterText.plainText(html)) }
        chapterTexts[key] = text
        return text
    }

    /** « Deuxième partie, chap. I · 30 % » d’un surlignage (même forme que la liste 3.06). */
    private fun highlightLocation(row: HighlightEntity): String? {
        val location = shortLocation(row.chapterPathList(), locationTexts)
        return highlightLocationText(location, percentOf(row.progression))
    }
```

`highlightLocationText(location: String?, percent: Int): String?` est une fonction passée par la fabrique comme `locationTexts` (ressource `notes_item_location` « %1$s · %2$d %% » ou, sans chapitre, `common_percent_read`) : ajouter au constructeur `private val highlightLocationText: (String?, Int) -> String? = { location, percent -> listOfNotNull(location, "$percent %").joinToString(" · ") }`, et dans la fabrique `highlightLocationText = { location, percent -> app.resources.highlightLocation(location, percent) }` avec, dans `ui/common/BookTexts.kt` :

```kotlin
/** « Deuxième partie, chap. I · 30 % » (3.06), ou « 30 % » hors sommaire. */
fun Resources.highlightLocation(location: String?, percent: Int): String =
    if (location == null) getString(R.string.notes_item_percent, percent) else getString(R.string.notes_item_location, location, percent)
```

et dans `strings.xml` (section `<!-- ===================== Notes et surlignages (3.06) ===================== -->`, créée ici) :

```xml
    <!-- « Deuxième partie, chap. I · 30 % » -->
    <string name="notes_item_location">%1$s · %2$d %%</string>
    <string name="notes_item_percent">%1$d %%</string>
```

Dans ces deux chaînes, l’espace avant `%%` est une U+202F réelle, comme dans `toc_chapter_current` (`StringsTest` le vérifie).

(`ChapterText` est `internal` dans `reader/`, même module : accessible.) `ChapterText.plainText` remplace déjà les espaces multiples ; `TextQuotes.normalize` ajoute les insécables.

3. Branchement : dans `onReaderReady`, `highlightCoordinator?.attach(readerController)` ; dans `onReaderGone`, `highlightCoordinator?.detach()`. Méthodes publiques :

```kotlin
    fun highlightSelection() { sessionCoordinator?.onInteraction(); highlightCoordinator?.highlightSelection() }
    fun copySelection() { sessionCoordinator?.onInteraction(); highlightCoordinator?.copySelection() }
    fun copyHighlight() = highlightCoordinator?.copyHighlight()
    fun dismissHighlightActions() = highlightCoordinator?.dismissActions()
```

4. Un toucher sur un surlignage ne doit pas laisser la barre de lecture ouverte par-dessus la feuille : dans `HighlightCoordinator.openActions`, rien de plus ; dans `ReaderViewModel`, à la collecte de `state`, si `actions != null` alors `_uiState.update { it.copy(barsVisible = false) }`.

5. `ReaderViewModelTest` : le constructeur gagne `highlightRepository` ; mettre à jour la fabrique de test du fichier (base en mémoire déjà créée par le harnais : `HighlightRepository(db.highlightDao()) { 0L }`).

- [ ] **Step 4 : relancer**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.HighlightCoordinatorTest' --tests 'com.maximebier.verso.ui.reader.ReaderViewModelTest'`
Expected: PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/ui app/src/main/res/values/strings.xml app/src/test/kotlin/com/maximebier/verso/ui/reader
git commit -m "17.6 : surligner, copier, toucher un surlignage"
```

---

### Task 17.7 : barre de sélection (3.04), presse-papiers, feuille d’un surlignage, captures

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/SelectionBar.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/HighlightSheets.kt` (feuille d’un surlignage ; la feuille de note s’y ajoute en 18.2)
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/components/MarkedText.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/VersoIcons.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create (test): `app/src/test/kotlin/com/maximebier/verso/screenshots/V3ScreenCatalog.kt`, `V3ScreenshotTest.kt`, `samples/NotesSamples.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/reader/SelectionBarTest.kt`

**Interfaces:**
- Consumes: `ReaderViewModel.{highlights, highlightEvents, highlightSelection, copySelection, copyHighlight, dismissHighlightActions}` (17.6), `VersoBottomSheet`, `VersoSnackbarHost`, `VersoIconButton`, `ActionRow` (existants).
- Produces:

```kotlin
@Composable fun SelectionBar(selectionText: String, onHighlight: () -> Unit, onNote: (() -> Unit)?, onCopy: () -> Unit, modifier: Modifier = Modifier)
@Composable fun HighlightActionsSheet(state: HighlightActionsState, onCopy: () -> Unit, onEditNote: (() -> Unit)?, onDelete: (() -> Unit)?, onDismiss: () -> Unit)
@Composable fun MarkedText(text: String, style: TextStyle, modifier: Modifier = Modifier)   // fond highlight + soulignement 2 dp
VersoIcons.Highlighter, VersoIcons.Note, VersoIcons.Copy, VersoIcons.Download, VersoIcons.Upload
data class V3ScreenFixture(val screen: ScreenFixture, val themes: List<AppTheme> = AppTheme.entries)
```

Chaînes (section `<!-- ===================== Texte sélectionné (3.04) ===================== -->`) :

```xml
    <string name="selection_toolbar">Texte sélectionné</string>
    <!-- « Sélection : « la campagne ainsi ressemble à un grand manteau… » » -->
    <string name="selection_label">Sélection : « %1$s »</string>
    <string name="selection_highlight">Surligner</string>
    <string name="selection_note">Note</string>
    <string name="selection_copy">Copier</string>
    <!-- PROPOSÉ : confirmation sous Android 12 et moins (Android 13 et plus affiche la sienne) -->
    <string name="selection_copied">Passage copié</string>
    <!-- ===================== Surlignage touché ===================== -->
    <!-- PROPOSÉ : titre de la feuille d’un surlignage touché dans le texte -->
    <string name="highlight_sheet_title">Surlignage</string>
    <string name="highlight_action_copy">Copier</string>
```

Dans le fichier, écrire les espaces fines U+202F réelles : `Sélection : « %1$s »` (caractère, pas l’échappement : `StringsTest` lit le texte).

- [ ] **Step 1 : tests qui échouent**

```kotlin
// app/src/test/kotlin/com/maximebier/verso/ui/reader/SelectionBarTest.kt
package com.maximebier.verso.ui.reader

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.core.settings.ReadingFont
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SelectionBarTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsTheStartOfTheSelectionAndThreeActions() {
        val clicks = mutableListOf<String>()
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                SelectionBar(
                    selectionText = "la campagne ainsi ressemble à un grand manteau…",
                    onHighlight = { clicks += "surligner" },
                    onNote = { clicks += "note" },
                    onCopy = { clicks += "copier" },
                )
            }
        }
        rule.onNodeWithText("Sélection : « la campagne ainsi ressemble à un grand manteau… »").assertExists()
        listOf("Surligner", "Note", "Copier").forEach {
            rule.onNodeWithText(it).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        }
        assertThat(clicks).containsExactly("surligner", "note", "copier").inOrder()
    }

    @Test fun noteIsHiddenUntilItsSheetExists() {
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                SelectionBar(selectionText = "passage", onHighlight = {}, onNote = null, onCopy = {})
            }
        }
        rule.onNodeWithText("Note").assertDoesNotExist()
    }

    @Config(qualifiers = "+w390dp-h844dp", fontScale = 2.0f)
    @Test fun labelsAreNotCutAtDoubleTextSize() {
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                SelectionBar(selectionText = "la campagne ainsi ressemble à un grand manteau…", onHighlight = {}, onNote = {}, onCopy = {})
            }
        }
        // Le libellé complet reste dans l’arbre (aucune ellipse ni maxLines).
        rule.onNodeWithText("Surligner").assertExists()
        rule.onNodeWithText("Sélection", substring = true).assertExists()
    }
}
```

Vérifier dans `ReaderBarsTest` ou `AccessibilityTreeTest` comment le projet teste le texte à 200 % (qualificatif Robolectric ou `Density(fontScale = 2f)` via `CompositionLocalProvider`) et reprendre la même méthode à la place de `@Config(fontScale = …)` si elle diffère.

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.SelectionBarTest'`
Expected: FAIL (compilation).

- [ ] **Step 3 : implémentation**

Icônes (`VersoIcons`, tracés des maquettes 3.04, 3.06, 3.07) :

```kotlin
    val Highlighter: ImageVector = strokeIcon("highlighter", "m9 11-6 6v3h9l3-3", "m22 12-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4")
    val Note: ImageVector = strokeIcon("note", "M20 15a2 2 0 0 1-2 2H8l-4 4V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2z", "M8 9h8M8 13h5")
    val Copy: ImageVector = strokeIcon("copy", "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M4 16V6a2 2 0 0 1 2-2h10")
    val Download: ImageVector = strokeIcon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Upload: ImageVector = strokeIcon("upload", "M12 20V9M7 14l5-5 5 5M5 4h14")
```

(Le `rect x=8 y=8 w=12 h=12 rx=2` de « Copier » est écrit en tracé.)

```kotlin
// app/src/main/kotlin/com/maximebier/verso/ui/components/MarkedText.kt
package com.maximebier.verso.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.maximebier.verso.ui.theme.VersoTheme

/** Passage surligné (3.05, 3.06) : fond `highlight` et soulignement, jamais la couleur seule. */
@Composable
fun MarkedText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(background = colors.highlight, textDecoration = TextDecoration.Underline)) { append(text) }
        },
        style = style,
        color = colors.text,
        modifier = modifier,
    )
}
```

(Le soulignement de Compose fait environ 1 dp ; la maquette en a 2 px. Écart accepté si la capture le montre lisible ; sinon, dessiner la ligne avec `Modifier.drawBehind` et `onTextLayout` — à décider sur la capture, et noter la décision.)

```kotlin
// app/src/main/kotlin/com/maximebier/verso/ui/reader/SelectionBar.kt
package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Barre « Texte sélectionné » (3.04) : début du passage, puis Surligner, Note, Copier en colonnes égales de 64 dp.
 * Remplace le menu d’Android ; posée en bas de l’écran, par-dessus le texte. [onNote] null : bouton absent.
 */
@Composable
fun SelectionBar(
    selectionText: String,
    onHighlight: () -> Unit,
    onNote: (() -> Unit)?,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    val toolbarLabel = stringResource(R.string.selection_toolbar)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(20.dp, VersoShapes.sheetTop, clip = false)
            .clip(VersoShapes.sheetTop)
            .background(colors.surface)
            .border(1.dp, colors.divider, VersoShapes.sheetTop)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp)
            .semantics { isTraversalGroup = true; contentDescription = toolbarLabel },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.selection_label, selectionText),
            style = VersoTheme.typography.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        val tools = buildList {
            add(ReaderTool(stringResource(R.string.selection_highlight), ToolGlyph.Icon(VersoIcons.Highlighter), onHighlight))
            if (onNote != null) add(ReaderTool(stringResource(R.string.selection_note), ToolGlyph.Icon(VersoIcons.Note), onNote))
            add(ReaderTool(stringResource(R.string.selection_copy), ToolGlyph.Icon(VersoIcons.Copy), onCopy))
        }
        // Même rangée adaptative que la barre de lecture (2.01) : colonnes égales, deux puis une à 200 %.
        ReaderToolbar(tools)
    }
}
```

`ReaderToolbar` est `internal` dans le même paquet : réutilisé tel quel (64 dp, icône 22 dp, libellé 14 sp 600 — mêmes mesures que 3.04 ; l’écart entre colonnes de la maquette est 8 dp contre 4 dp : accepté, à confirmer sur la capture). `contentDescription` sur un groupe avec du texte : vérifier avec `AccessibilityTreeTest` que TalkBack lit bien les boutons un à un ; si le groupe masque ses enfants, remplacer par `Modifier.semantics { paneTitle = toolbarLabel }`.

```kotlin
// app/src/main/kotlin/com/maximebier/verso/ui/reader/HighlightSheets.kt
package com.maximebier.verso.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.ActionRow
import com.maximebier.verso.ui.components.MarkedText
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Surlignage touché dans le texte : passage, note, puis « Modifier la note » (« Ajouter une note » sans note),
 * « Supprimer », « Copier ». [onEditNote] et [onDelete] null : lignes absentes (branchées à l’étape 18).
 */
@Composable
fun HighlightActionsSheet(
    state: HighlightActionsState,
    onCopy: () -> Unit,
    onEditNote: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    VersoBottomSheet(
        title = stringResource(R.string.highlight_sheet_title),
        subtitle = state.location,
        onDismissRequest = onDismiss,
        fitContent = true,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            MarkedText(
                text = state.passage,
                style = VersoTheme.typography.body,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            state.note?.let { note ->
                Text(
                    text = note,
                    style = VersoTheme.typography.body,
                    color = VersoTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            onEditNote?.let {
                ActionRow(
                    title = stringResource(if (state.note == null) R.string.highlight_add_note else R.string.highlight_edit_note),
                    summary = null,
                    icon = VersoIcons.Note,
                    onClick = it,
                )
            }
            onDelete?.let {
                ActionRow(
                    title = stringResource(R.string.highlight_delete),
                    summary = null,
                    icon = VersoIcons.Trash,
                    onClick = it,
                    iconTint = VersoTheme.colors.danger,
                )
            }
            ActionRow(title = stringResource(R.string.highlight_action_copy), summary = null, icon = VersoIcons.Copy, onClick = onCopy)
        }
    }
}
```

(Lire la signature réelle de `ActionRow` dans `SettingsRows.kt` — `title`, `summary`, `icon`, `onClick`, `iconTint` — et d’`ActionRow` : l’icône y est en fin de ligne ; si c’est gênant pour ces actions, accepter tel quel à l’étape 17 et le noter pour la relecture. Les chaînes `highlight_add_note`, `highlight_edit_note`, `highlight_delete` arrivent en 18.2.)

`ReaderScreen.kt` :

```kotlin
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val copiedMessage = stringResource(R.string.selection_copied)
    LaunchedEffect(viewModel) {
        viewModel.highlightEvents.collect { event ->
            when (event) {
                is HighlightEvent.Copied -> {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(event.text, event.text)))
                    // Android 13 et plus confirme lui-même la copie.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbarHostState.showSnackbar(copiedMessage)
                }
                is HighlightEvent.Deleted -> Unit   // 18.2
            }
        }
    }
```

Dans la `Box` principale, après la carte « Revenir » et avant la recherche :

```kotlin
        highlights.selectionText?.let { text ->
            SelectionBar(
                selectionText = text,
                onHighlight = viewModel::highlightSelection,
                onNote = null,   // feuille de note : 18.2
                onCopy = viewModel::copySelection,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        VersoSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        )
```

et après la feuille de réglages :

```kotlin
    highlights.actions?.let { actions ->
        HighlightActionsSheet(
            state = actions,
            onCopy = viewModel::copyHighlight,
            onEditNote = null,   // 18.2
            onDelete = null,     // 18.2
            onDismiss = viewModel::dismissHighlightActions,
        )
    }
```

(`ClipEntry` : `androidx.compose.ui.platform.ClipEntry` ; `LocalClipboard` : `androidx.compose.ui.platform.LocalClipboard`. Si la version de Compose du projet n’a que `LocalClipboardManager`, utiliser `LocalClipboardManager.current.setText(AnnotatedString(event.text))`.)

La barre de lecture et la barre de sélection ne coexistent pas : dans `ReaderViewModel`, à la collecte de l’état du coordinateur, `selectionText != null` ferme aussi la barre de lecture (`barsVisible = false`).

Captures : créer `V3ScreenCatalog`/`V3ScreenshotTest` en copiant `V2ScreenCatalog`/`V2ScreenshotTest` (mêmes paramètres, même dossier de sortie, préfixe `3.`), avec un premier écran `3.04-texte-selectionne` : `SampleReadingText` (échantillon V2 existant) puis `SelectionBar` alignée en bas, `selectionText = "la campagne ainsi ressemble à un grand manteau…"`. Échantillons V3 dans `samples/NotesSamples.kt`.

- [ ] **Step 4 : relancer**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.SelectionBarTest' --tests 'com.maximebier.verso.StringsTest' --tests 'com.maximebier.verso.screenshots.*'`
Expected: PASS ; captures 3.04 (cinq palettes) à regarder à côté de `docs/design/screens/3.04-texte-selectionne.png`.

- [ ] **Step 5 : commit**

```bash
git add app/src docs
git commit -m "17.7 : barre de sélection, presse-papiers, feuille d’un surlignage"
```

---

### Task 17.8 : fin d’étape 17

- [ ] `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` vert ; captures 3.04 regardées.
- [ ] Relecture de tout le diff de l’étape (sous-agent relecteur, modèle le plus capable) ; corrections.
- [ ] `docs/acceptance-v3.md` créé sur le modèle de `docs/acceptance-v2.md` (14 critères, colonnes Agent / Maxime).
- [ ] `docs/SPEC.md` : décisions « Passage dans le texte du chapitre », « Chapitre enregistré », « Machine à états » et « Barre de sélection » du plan maître reportées dans « Décisions de la V3 » ; ligne d’« Historique » ; critères 1, 2, 3, 5, 7 cochés après le téléphone.
- [ ] Commit `Étape 17 : surlignages`, `git push`.
- [ ] Téléphone (verrou) : appui long en continu et en pages (barre à la place du menu d’Android, début de la sélection affiché) ; Surligner (fond et soulignement dans les cinq thèmes, présent après redémarrage de l’app) ; Copier (coller dans le champ de recherche de Verso) ; surligner par-dessus un surlignage (un seul reste) ; poignée tirée jusqu’au défilement automatique puis toucher ailleurs (pas de carte, la barre de lecture ne s’ouvre pas, position inchangée : pourcentage de la barre identique avant/après) ; toucher un surlignage (feuille, pas de barre de lecture). Captures comparées au PNG 3.04 en clair, sombre, nuit.

---

## Étape 18 : notes

### Task 18.1 : note, modification, suppression et « Annuler » dans le coordinateur

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/HighlightCoordinator.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderViewModel.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/reader/HighlightCoordinatorTest.kt`

**Interfaces:**
- Consumes: 17.6.
- Produces (coordinateur, et relais du même nom dans `ReaderViewModel`) :

```kotlin
fun noteForSelection()          // « Note » de la barre : feuille 3.05, rien n’est créé
fun onNoteChange(text: String)
fun saveNote()                  // « Enregistrer »
fun cancelNote()                // « Annuler » ou la croix : rien n’est créé ni modifié
fun editNote()                  // « Modifier la note » / « Ajouter une note » depuis la feuille d’un surlignage
fun deleteHighlight()           // « Supprimer » : immédiat, HighlightEvent.Deleted(row)
fun undoDelete(row: HighlightEntity)
```

- [ ] **Step 1 : tests qui échouent** (ajoutés à `HighlightCoordinatorTest`)

```kotlin
    @Test fun noteSheetCreatesNothingUntilSaved() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "", editing = false))
        assertThat(fake.selectionCleared).isEqualTo(1)   // sélection effacée, passage gardé par la feuille
        coordinator.onNoteChange("Image du manteau")
        coordinator.cancelNote()
        coordinator.awaitIdle()
        assertThat(coordinator.state.value.noteSheet).isNull()
        assertThat(repository.forBook(bookId)).isEmpty()
    }

    @Test fun saveCreatesTheHighlightWithItsNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        coordinator.onNoteChange("  Image du manteau  ")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.forBook(bookId).single().note).isEqualTo("Image du manteau")
        assertThat(coordinator.state.value.noteSheet).isNull()
    }

    @Test fun blankNoteStillCreatesTheHighlight() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        coordinator.noteForSelection()
        coordinator.awaitIdle()
        coordinator.onNoteChange("   ")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.forBook(bookId).single().note).isNull()
    }

    @Test fun editingAnExistingNote() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val id = repository.forBook(bookId).single().id
        fake.highlightTaps.emit(id)
        coordinator.awaitIdle()
        coordinator.editNote()
        assertThat(coordinator.state.value.actions).isNull()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "", editing = false))
        coordinator.onNoteChange("Premier portrait.")
        coordinator.saveNote()
        coordinator.awaitIdle()
        assertThat(repository.get(id)!!.note).isEqualTo("Premier portrait.")
        // Deuxième passage : la feuille s’ouvre avec la note, en modification.
        fake.highlightTaps.emit(id)
        coordinator.awaitIdle()
        coordinator.editNote()
        assertThat(coordinator.state.value.noteSheet).isEqualTo(NoteSheetState("raie blanche", "Premier portrait.", editing = true))
    }

    @Test fun deleteThenUndoRestoresTheHighlight() = runTest(UnconfinedTestDispatcher()) {
        val (coordinator, fake, bookId) = setUp()
        fake.select("raie blanche")
        coordinator.highlightSelection()
        coordinator.awaitIdle()
        val row = repository.forBook(bookId).single()
        fake.highlightTaps.emit(row.id)
        coordinator.awaitIdle()
        coordinator.events.test {
            coordinator.deleteHighlight()
            val deleted = awaitItem() as HighlightEvent.Deleted
            assertThat(repository.forBook(bookId)).isEmpty()
            coordinator.undoDelete(deleted.row)
            coordinator.awaitIdle()
            assertThat(repository.forBook(bookId)).containsExactly(row)
        }
    }
```

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.HighlightCoordinatorTest'`
Expected: FAIL (compilation).

- [ ] **Step 3 : implémentation** (dans `HighlightCoordinator`)

```kotlin
    /** Cible de la feuille de note ouverte : passage sélectionné (rien n’existe encore) ou surlignage existant. */
    private sealed interface NoteTarget {
        data class Selection(val locator: Locator) : NoteTarget
        data class Existing(val id: Long) : NoteTarget
    }

    private var noteTarget: NoteTarget? = null

    fun noteForSelection() {
        val reader = controller ?: return
        scope.launch {
            val selection = reader.currentSelection() ?: return@launch
            reader.clearSelection()
            noteTarget = NoteTarget.Selection(selection.locator)
            _state.update { it.copy(noteSheet = NoteSheetState(selection.text, "", editing = false)) }
        }
    }

    fun editNote() {
        val actions = _state.value.actions ?: return
        noteTarget = NoteTarget.Existing(actions.id)
        _state.update {
            it.copy(actions = null, noteSheet = NoteSheetState(actions.passage, actions.note.orEmpty(), editing = actions.note != null))
        }
    }

    fun onNoteChange(text: String) = _state.update { state -> state.copy(noteSheet = state.noteSheet?.copy(note = text)) }

    fun saveNote() {
        val sheet = _state.value.noteSheet ?: return
        val target = noteTarget ?: return
        noteTarget = null
        _state.update { it.copy(noteSheet = null) }
        launchWrite {
            when (target) {
                is NoteTarget.Selection -> save(target.locator, note = sheet.note)
                is NoteTarget.Existing -> highlights.setNote(target.id, sheet.note)
            }
        }
    }

    fun cancelNote() {
        noteTarget = null
        _state.update { it.copy(noteSheet = null) }
    }

    fun deleteHighlight() {
        val actions = _state.value.actions ?: return
        _state.update { it.copy(actions = null) }
        launchWrite {
            highlights.delete(actions.id)?.let { _events.emit(HighlightEvent.Deleted(it)) }
        }
    }

    fun undoDelete(row: HighlightEntity) = launchWrite { highlights.restore(row) }
```

`save(…, note = "")` donne une note nulle (`newRow` coupe et vide). `ReaderViewModel` : relais `noteForSelection()`, `onNoteChange(text)`, `saveNote()`, `cancelNote()`, `editNote()`, `deleteHighlight()`, `undoDeleteHighlight(row)` ; `sessionCoordinator?.onInteraction()` dans `noteForSelection`, `saveNote`, `deleteHighlight`.

- [ ] **Step 4 : relancer** — même commande. Expected: PASS.

- [ ] **Step 5 : commit** — `git commit -am "18.1 : note, modification, suppression avec annulation"`

---

### Task 18.2 : feuille « Ajouter une note » (3.05), actions complètes, snackbar « Annuler »

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/HighlightSheets.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify (test): `screenshots/V3ScreenCatalog.kt`, `samples/NotesSamples.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/reader/NoteSheetTest.kt`

**Interfaces:**
- Consumes: 18.1.
- Produces: `@Composable fun NoteSheet(state: NoteSheetState, onNoteChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit)` — réutilisée par l’écran 3.06 (19).

Chaînes (section `<!-- ===================== Ajouter une note (3.05) ===================== -->`) :

```xml
    <string name="note_sheet_title">Ajouter une note</string>
    <!-- PROPOSÉ : même feuille sur une note existante -->
    <string name="note_sheet_title_edit">Modifier la note</string>
    <string name="note_field_label">Votre note</string>
    <string name="note_cancel">Annuler</string>
    <string name="note_save">Enregistrer</string>
    <!-- Feuille d’un surlignage touché -->
    <string name="highlight_add_note">Ajouter une note</string>
    <string name="highlight_edit_note">Modifier la note</string>
    <string name="highlight_delete">Supprimer</string>
    <!-- PROPOSÉ : snackbar après « Supprimer » -->
    <string name="highlight_deleted">Surlignage supprimé</string>
    <string name="highlight_deleted_undo">Annuler</string>
```

- [ ] **Step 1 : tests qui échouent**

```kotlin
// app/src/test/kotlin/com/maximebier/verso/ui/reader/NoteSheetTest.kt
package com.maximebier.verso.ui.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsThePassageAndSavesTheTypedNote() {
        var note = ""
        var saved = 0
        var cancelled = 0
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                NoteSheet(
                    state = NoteSheetState("la campagne ainsi ressemble", note, editing = false),
                    onNoteChange = { note = it },
                    onSave = { saved++ },
                    onCancel = { cancelled++ },
                )
            }
        }
        rule.onNodeWithText("Ajouter une note").assertExists()
        rule.onNodeWithText("la campagne ainsi ressemble").assertExists()
        rule.onNodeWithText("Votre note").performTextReplacement("Image du manteau")
        assertThat(note).isEqualTo("Image du manteau")
        rule.onNodeWithText("Enregistrer").performClick()
        rule.onNodeWithText("Annuler").performClick()
        rule.onNodeWithContentDescription("Fermer").performClick()
        assertThat(saved).isEqualTo(1)
        assertThat(cancelled).isEqualTo(2)
    }

    @Test fun editingTitle() {
        rule.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                NoteSheet(NoteSheetState("passage", "note", editing = true), {}, {}, {})
            }
        }
        rule.onNodeWithText("Modifier la note").assertExists()
    }
}
```

(Si le champ n’est pas trouvé par son libellé « Votre note » — selon l’implémentation, le libellé est un `Text` séparé —, chercher le champ par `hasSetTextAction()` : `rule.onNode(hasSetTextAction())`.)

- [ ] **Step 2 : lancer, échec attendu**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.NoteSheetTest'`
Expected: FAIL (compilation).

- [ ] **Step 3 : implémentation** (dans `HighlightSheets.kt`)

```kotlin
/**
 * « Ajouter une note » (3.05) : passage surligné sur fond de page, champ « Votre note » (bord accent 2 dp), puis
 * Annuler et Enregistrer à droite. La croix et le voile annulent. Le champ prend le focus à l’ouverture.
 */
@Composable
fun NoteSheet(
    state: NoteSheetState,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = VersoTheme.colors
    val focus = remember { FocusRequester() }
    VersoBottomSheet(
        title = stringResource(if (state.editing) R.string.note_sheet_title_edit else R.string.note_sheet_title),
        subtitle = null,
        onDismissRequest = onCancel,
        fitContent = true,
    ) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MarkedText(
                text = state.passage,
                // 16 sp, interligne 1,55 (3.05).
                style = VersoTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 24.8.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(VersoShapes.small)
                    .background(colors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val label = stringResource(R.string.note_field_label)
                Text(text = label, style = VersoTheme.typography.captionBold, color = colors.textSecondary)
                BasicTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    textStyle = VersoTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 23.2.sp, color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = label }
                        .clip(VersoShapes.small)
                        .background(colors.background)
                        .border(2.dp, colors.accent, VersoShapes.small)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                VersoTextButton(text = stringResource(R.string.note_cancel), onClick = onCancel)
                PrimaryButton(text = stringResource(R.string.note_save), onClick = onSave)
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
```

Imports à ajouter : `BasicTextField`, `FocusRequester`, `focusRequester`, `SolidColor`, `imePadding`, `border`, `background`, `clip`, `Row`, `Arrangement`, `Alignment`, `fillMaxWidth`, `remember`, `LaunchedEffect`, `semantics`, `contentDescription`, `sp`, `VersoShapes`, `PrimaryButton`, `VersoTextButton`. Vérifier les signatures de `PrimaryButton(text, onClick, modifier, icon?)` et `VersoTextButton(text, onClick, …)` dans `Buttons.kt` et le nom des styles (`captionBold`, `body`) dans `Type.kt`. `VersoShapes.small` doit valoir 12 dp (3.05 : `border-radius: 12px`) ; sinon prendre la forme de 12 dp existante.

Le `contentDescription = label` sur le champ fait lire « Votre note » par TalkBack (le libellé visible est un `Text` à part). Vérifier dans `NoteSheetTest` que `onNodeWithText("Votre note")` ne trouve pas deux nœuds ; sinon chercher le champ par `hasSetTextAction()`.

`ReaderScreen.kt` :

```kotlin
    val deletedMessage = stringResource(R.string.highlight_deleted)
    val undoLabel = stringResource(R.string.highlight_deleted_undo)
    // dans le when des événements :
                is HighlightEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteHighlight(event.row)
                }
```

Barre de sélection : `onNote = viewModel::noteForSelection`. Feuille d’un surlignage : `onEditNote = viewModel::editNote`, `onDelete = viewModel::deleteHighlight`. Feuille de note :

```kotlin
    highlights.noteSheet?.let { sheet ->
        NoteSheet(state = sheet, onNoteChange = viewModel::onNoteChange, onSave = viewModel::saveNote, onCancel = viewModel::cancelNote)
    }
```

`BackHandler(enabled = highlights.selectionText != null) { viewModel.clearSelection() }` — ajouter `fun clearSelection() = controller?.clearSelection()` au `ReaderViewModel` : le retour efface la sélection au lieu de quitter le livre.

Captures : `3.05-ajouter-une-note` (texte d’échantillon, puis `NoteSheet` avec le passage et la note de la maquette ; la feuille modale se capture comme `ReadingSettingsSheetSample` le fait en V2 — reprendre sa méthode).

- [ ] **Step 4 : relancer**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.NoteSheetTest' --tests 'com.maximebier.verso.StringsTest' --tests 'com.maximebier.verso.screenshots.*'`
Expected: PASS ; capture 3.05 regardée à côté du PNG.

- [ ] **Step 5 : commit** — `git add app/src && git commit -m "18.2 : feuille de note, suppression avec annulation"`

### Task 18.3 : fin d’étape 18

- [ ] Suite complète, lint, `assembleDebug`, captures.
- [ ] Relecture du diff de l’étape ; corrections.
- [ ] `docs/SPEC.md` : critères 4 et 6 cochés après le téléphone, libellés proposés listés pour Maxime ; `docs/acceptance-v3.md` à jour.
- [ ] Commit `Étape 18 : notes`, `git push`.
- [ ] Téléphone (verrou) : Note → feuille avec le passage, clavier ouvert ; Enregistrer (surlignage et note), Annuler et la croix (rien) ; toucher un surlignage → Ajouter / Modifier la note, Supprimer puis Annuler (rétabli) ; note longue à 200 % (la feuille défile). Captures 3.05 en clair, sombre, nuit.
