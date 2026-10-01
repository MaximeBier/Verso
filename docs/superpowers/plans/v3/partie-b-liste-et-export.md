# Verso V3 — Partie B : « Notes et surlignages » et export (étape 19)

Plan maître : `docs/superpowers/plans/2026-10-01-verso-v3.md`. Partie A faite (table `highlights`, `HighlightRepository`, `HighlightCoordinator`, `NoteSheet`, `MarkedText`, icônes `Note`, `Download`, chaînes `notes_item_location` et `notes_item_percent`). Commandes Gradle depuis Git Bash avec `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.

Maquette : `docs/design/html/3.06-notes-et-surlignages.html` et son PNG. Mesures : barre de titre 64 dp (retour 48 dp, titre 22 sp 700 interligne 1,25, sous-titre 14 sp `textSecondary`, bouton « Exporter » 48 dp, icône 20 dp, texte 16 sp 700) ; phrase d’aide 14 sp interligne 1,45, marges 4/24/12, filet `divider` dessous ; élément : passage 18 sp interligne 1,55 surligné, note 16 sp interligne 1,5 `textSecondary` précédée de l’icône note 18 dp, emplacement 14 sp `textSecondary`, marges 16/8/16/24, écart 8 dp, bouton ⋮ 48 dp (« Options de cet élément »), filet `divider` sous chaque élément.

Décisions de cette partie (à reporter dans la spec au commit de l’étape) :
- Depuis le lecteur, l’écran est une surcouche (comme la recherche 2.06) ; depuis la fiche, une route `NotesRoute`, et toucher un élément ouvre le lecteur par `ReaderRoute(bookId, highlightId = id)`, qui saute au passage une fois le livre ouvert (saut explicite, carte « Revenir »).
- La ligne de la fiche n’apparaît que si le livre a au moins un surlignage (« Notes et surlignages · 3 »).
- La barre de lecture garde ses cinq outils : la rangée passe à deux colonnes puis une à 200 % (`ReaderToolbar`, V2), rien n’est coupé.

---

### Task 19.1 : fichier Markdown (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/maximebier/verso/core/notes/NotesMarkdown.kt`
- Test: `core/src/test/kotlin/com/maximebier/verso/core/notes/NotesMarkdownTest.kt`

**Interfaces:**
- Produces: `NotesExportItem(chapter: String?, text: String, note: String?)`, `notesMarkdown(title: String, meta: String, items: List<NotesExportItem>): String`.

- [ ] **Step 1 : test qui échoue**

```kotlin
// core/src/test/kotlin/com/maximebier/verso/core/notes/NotesMarkdownTest.kt
package com.maximebier.verso.core.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotesMarkdownTest {
    @Test fun groupsByChapterInBookOrder() {
        val markdown = notesMarkdown(
            title = "Madame Bovary",
            meta = "Gustave Flaubert · 3 éléments · 1 octobre 2026",
            items = listOf(
                NotesExportItem("Première partie, chapitre I", "Il avait les cheveux coupés droit sur le front.", "Premier portrait de Charles."),
                NotesExportItem("Deuxième partie, chapitre I", "la campagne ainsi ressemble à un grand manteau", "Image du manteau :\nla prairie verte."),
                NotesExportItem("Deuxième partie, chapitre I", "On l’aperçoit de loin", null),
            ),
        )
        assertThat(markdown).isEqualTo(
            """
            # Madame Bovary

            Gustave Flaubert · 3 éléments · 1 octobre 2026

            ## Première partie, chapitre I

            > Il avait les cheveux coupés droit sur le front.

            Premier portrait de Charles.

            ## Deuxième partie, chapitre I

            > la campagne ainsi ressemble à un grand manteau

            Image du manteau :
            la prairie verte.

            > On l’aperçoit de loin

            """.trimIndent(),
        )
    }

    @Test fun passagesWithoutChapterComeWithoutHeading() {
        val markdown = notesMarkdown("Livre", "2 éléments · 1 octobre 2026", listOf(NotesExportItem(null, "a", null), NotesExportItem("Chapitre 1", "b", null)))
        assertThat(markdown).isEqualTo("# Livre\n\n2 éléments · 1 octobre 2026\n\n> a\n\n## Chapitre 1\n\n> b\n")
    }

    @Test fun markdownInTheTextIsKeptReadable() {
        // Un passage sur plusieurs lignes reste une seule citation ; un titre qui commence par # n’en devient pas un.
        val markdown = notesMarkdown("# Titre", "0 élément", listOf(NotesExportItem(null, "ligne 1\nligne 2", null)))
        assertThat(markdown).startsWith("# \\# Titre\n")
        assertThat(markdown).contains("> ligne 1\n> ligne 2\n")
    }
}
```

- [ ] **Step 2 : lancer, échec attendu** — `./gradlew :core:test --tests 'com.maximebier.verso.core.notes.NotesMarkdownTest'` → FAIL (compilation).

- [ ] **Step 3 : implémentation**

```kotlin
// core/src/main/kotlin/com/maximebier/verso/core/notes/NotesMarkdown.kt
package com.maximebier.verso.core.notes

/** Un élément exporté : chapitre (forme longue, null hors sommaire), passage, note. */
data class NotesExportItem(val chapter: String?, val text: String, val note: String?)

/**
 * Fichier « <Titre> – notes.md » (spec V3, « Export ») : `# Titre`, la ligne [meta] (auteur · nombre d’éléments · date,
 * formée par l’app), puis un `## Chapitre` à chaque changement de chapitre, chaque passage en citation suivi de sa
 * note. [items] sont déjà dans l’ordre du livre. Lisible tel quel dans un éditeur de texte.
 */
fun notesMarkdown(title: String, meta: String, items: List<NotesExportItem>): String = buildString {
    append("# ").append(escapeHeading(title)).append("\n\n")
    append(meta).append('\n')
    var currentChapter: String? = null
    items.forEach { item ->
        if (item.chapter != null && item.chapter != currentChapter) {
            append("\n## ").append(escapeHeading(item.chapter)).append('\n')
            currentChapter = item.chapter
        }
        // Une ligne vide avant chaque citation.
        append('\n')
        item.text.lines().forEach { line -> append("> ").append(line).append('\n') }
        item.note?.takeIf { it.isNotBlank() }?.let { note -> append('\n').append(note.trim()).append('\n') }
    }
}

/** Un titre qui commence par `#` serait lu comme un titre de niveau supérieur. */
private fun escapeHeading(text: String): String = if (text.startsWith("#")) "\\$text" else text
```

Un élément sans chapitre qui suit un élément avec chapitre reste sous ce chapitre (pas de titre vide) ; dans l’ordre du livre, le texte hors sommaire précède en général le premier chapitre (cas du test `passagesWithoutChapterComeWithoutHeading`).

- [ ] **Step 4 : relancer** — PASS.
- [ ] **Step 5 : commit** — `git add core && git commit -m "19.1 : export Markdown des notes"`

---

### Task 19.2 : `NotesListModel` (liste, menu, note, suppression, export)

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/notes/NotesListModel.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/data/DocumentStore.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/AppContainer.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/notes/NotesListModelTest.kt`

**Interfaces:**
- Consumes: `HighlightRepository`, `BookRepository.observeBook`, `HighlightEntity.chapterPathList()`, `shortLocation`, `longLocation`, `LocationTexts`, `percentOf`, `notesMarkdown`, `NoteSheetState`, `HighlightEvent.Deleted` (partie A).
- Produces:

```kotlin
data class NoteItem(val id: Long, val text: String, val note: String?, val location: String)
data class NotesUiState(
    val bookTitle: String = "",
    val items: List<NoteItem> = emptyList(),
    val loaded: Boolean = false,
    val noteSheet: NoteSheetState? = null,
    /** Nom proposé au sélecteur : « Madame Bovary – notes.md ». */
    val exportFileName: String = "",
)
sealed interface NotesEvent {
    data class Deleted(val row: HighlightEntity) : NotesEvent
    data object Exported : NotesEvent
    data object ExportFailed : NotesEvent
}
/** Textes venus des ressources (testables sans Android). */
class NotesTexts(
    val location: (shortLocation: String?, percent: Int) -> String,
    val longChapter: (path: List<String>) -> String?,
    val exportMeta: (author: String, count: Int, date: String) -> String,
    val exportFileName: (title: String) -> String,
    val exportDate: (epochMs: Long) -> String,
)
class NotesListModel(
    bookId: Long,
    highlights: HighlightRepository,
    books: BookRepository,
    scope: CoroutineScope,
    texts: NotesTexts,
    writeText: suspend (Uri, String) -> Unit,
    clock: () -> Long,
) {
    val state: StateFlow<NotesUiState>
    val events: SharedFlow<NotesEvent>
    fun editNote(id: Long); fun onNoteChange(text: String); fun saveNote(); fun cancelNote()
    fun delete(id: Long); fun undoDelete(row: HighlightEntity)
    fun export(uri: Uri)
    suspend fun awaitIdle()
}
class DocumentStore(context: Context) {
    suspend fun writeText(uri: Uri, text: String)
    fun openOutput(uri: Uri): OutputStream
    fun openInput(uri: Uri): InputStream
    fun displayName(uri: Uri): String?
}
// AppContainer : val documents: DocumentStore
```

Chaînes (section 3.06, à la suite de `notes_item_location`) :

```xml
    <string name="notes_title">Notes et surlignages</string>
    <!-- « Madame Bovary · 3 éléments » -->
    <string name="notes_subtitle">%1$s · %2$s</string>
    <plurals name="notes_item_count">
        <item quantity="one">%d élément</item>
        <item quantity="many">%d éléments</item>
        <item quantity="other">%d éléments</item>
    </plurals>
    <string name="notes_export">Exporter</string>
    <string name="notes_export_hint">L’export crée un fichier Markdown à garder ou à partager.</string>
    <string name="notes_item_options">Options de cet élément</string>
    <string name="notes_go_to_passage">Aller au passage</string>
    <string name="notes_edit_note">Modifier la note</string>
    <string name="notes_add_note">Ajouter une note</string>
    <string name="notes_delete">Supprimer</string>
    <!-- PROPOSÉ : livre sans surlignage -->
    <string name="notes_empty">Aucun surlignage pour l’instant. Sélectionnez un passage pendant la lecture pour le surligner.</string>
    <!-- Nom proposé au sélecteur : « Madame Bovary – notes.md » (tiret demi-cadratin) -->
    <string name="notes_export_file_name">%1$s – notes.md</string>
    <!-- PROPOSÉ : ligne sous le titre du fichier exporté, « Gustave Flaubert · 3 éléments · 1 octobre 2026 » -->
    <string name="notes_export_meta">%1$s · %2$s · %3$s</string>
    <string name="notes_export_meta_no_author">%1$s · %2$s</string>
    <!-- PROPOSÉ : snackbars de l’export -->
    <string name="notes_exported">Notes exportées</string>
    <string name="notes_export_failed">L’export n’a pas pu être enregistré.</string>
```

- [ ] **Step 1 : test qui échoue**

```kotlin
// app/src/test/kotlin/com/maximebier/verso/ui/notes/NotesListModelTest.kt
package com.maximebier.verso.ui.notes

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.data.db.VersoDatabase
import com.maximebier.verso.data.testBook
import com.maximebier.verso.ui.reader.NoteSheetState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NotesListModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).allowMainThreadQueries().build()
    private val highlights = HighlightRepository(db.highlightDao()) { 5L }
    private val books = BookRepository(db.bookDao(), File(context.filesDir, "b"), File(context.filesDir, "c"))
    private val written = mutableMapOf<Uri, String>()
    private var failWrite = false

    private val texts = NotesTexts(
        location = { location, percent -> listOfNotNull(location, "$percent %").joinToString(" · ") },
        shortChapter = { path -> path.takeIf { it.isNotEmpty() }?.joinToString(", ") },
        longChapter = { path -> path.takeIf { it.isNotEmpty() }?.joinToString(", ") },
        exportMeta = { author, count, date -> listOf(author, "$count éléments", date).filter(String::isNotEmpty).joinToString(" · ") },
        exportFileName = { title -> "$title – notes.md" },
        exportDate = { "1 octobre 2026" },
    )

    @After fun tearDown() = db.close()

    private fun row(bookId: Long, progression: Double, text: String, note: String?, path: String) = HighlightEntity(
        bookId = bookId, locatorJson = """{"href":"ch.xhtml","type":"application/xhtml+xml"}""", text = text, note = note,
        progression = progression, chapterPath = path, createdAt = 1, updatedAt = 1,
    )

    private suspend fun TestScope.model(): Pair<NotesListModel, Long> {
        val bookId = db.bookDao().insert(testBook(sha256 = "a").copy(title = "Madame Bovary", author = "Gustave Flaubert"))
        db.highlightDao().insert(row(bookId, 0.30, "la campagne", "Image du manteau", "Deuxième partie\nI"))
        db.highlightDao().insert(row(bookId, 0.01, "Il avait les cheveux", "Premier portrait de Charles.", "Première partie\nI"))
        val model = NotesListModel(
            bookId = bookId, highlights = highlights, books = books, scope = backgroundScope, texts = texts,
            writeText = { uri, text -> if (failWrite) error("disque plein") else written[uri] = text },
            clock = { 0L },
        )
        // Les flux Room émettent depuis leur propre exécuteur : attendre la première liste complète.
        model.state.first { it.items.size == 2 }
        return model to bookId
    }

    @Test fun itemsAreInBookOrderWithLocation() = runTest(UnconfinedTestDispatcher()) {
        val (model) = model()
        val state = model.state.value
        assertThat(state.bookTitle).isEqualTo("Madame Bovary")
        assertThat(state.exportFileName).isEqualTo("Madame Bovary – notes.md")
        assertThat(state.items.map { it.text }).containsExactly("Il avait les cheveux", "la campagne").inOrder()
        assertThat(state.items.first().location).isEqualTo("Première partie, I · 1 %")
    }

    @Test fun exportWritesTheMarkdown() = runTest(UnconfinedTestDispatcher()) {
        val (model) = model()
        val uri = Uri.parse("content://test/notes.md")
        model.events.test {
            model.export(uri)
            assertThat(awaitItem()).isEqualTo(NotesEvent.Exported)
        }
        assertThat(written[uri]).startsWith("# Madame Bovary\n\nGustave Flaubert · 2 éléments · 1 octobre 2026\n\n## Première partie, I\n")
    }

    @Test fun exportFailureIsReported() = runTest(UnconfinedTestDispatcher()) {
        val (model) = model()
        failWrite = true
        model.events.test {
            model.export(Uri.parse("content://test/x.md"))
            assertThat(awaitItem()).isEqualTo(NotesEvent.ExportFailed)
        }
    }

    @Test fun editDeleteAndUndo() = runTest(UnconfinedTestDispatcher()) {
        val (model, bookId) = model()
        val first = model.state.value.items.first()
        model.editNote(first.id)
        assertThat(model.state.value.noteSheet).isEqualTo(NoteSheetState("Il avait les cheveux", "Premier portrait de Charles.", editing = true))
        model.onNoteChange("Portrait.")
        model.saveNote()
        model.awaitIdle()
        assertThat(highlights.get(first.id)!!.note).isEqualTo("Portrait.")
        model.events.test {
            model.delete(first.id)
            val deleted = awaitItem() as NotesEvent.Deleted
            assertThat(highlights.forBook(bookId)).hasSize(1)
            model.undoDelete(deleted.row)
            model.awaitIdle()
            assertThat(highlights.forBook(bookId)).hasSize(2)
        }
    }
}
```

- [ ] **Step 2 : lancer, échec attendu** — `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.notes.NotesListModelTest'` → FAIL.

- [ ] **Step 3 : implémentation**

```kotlin
// app/src/main/kotlin/com/maximebier/verso/data/DocumentStore.kt
package com.maximebier.verso.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fichiers choisis par les sélecteurs d’Android (export des notes, sauvegarde) : aucune permission, l’app n’écrit et ne
 * lit que l’URI que l’utilisateur lui donne. Un fournisseur en ligne (Drive…) envoie lui-même le fichier.
 */
class DocumentStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        openOutput(uri).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    /** « wt » : un fichier existant choisi à nouveau est remplacé, pas complété. */
    fun openOutput(uri: Uri): OutputStream =
        resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())

    fun openInput(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
```

```kotlin
// app/src/main/kotlin/com/maximebier/verso/ui/notes/NotesListModel.kt
package com.maximebier.verso.ui.notes

import android.net.Uri
import com.maximebier.verso.core.notes.NotesExportItem
import com.maximebier.verso.core.notes.notesMarkdown
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.data.HighlightRepository
import com.maximebier.verso.data.chapterPathList
import com.maximebier.verso.data.db.HighlightEntity
import com.maximebier.verso.ui.common.percentOf
import com.maximebier.verso.ui.reader.NoteSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class NoteItem(val id: Long, val text: String, val note: String?, val location: String)

data class NotesUiState(
    val bookTitle: String = "",
    val items: List<NoteItem> = emptyList(),
    val loaded: Boolean = false,
    val noteSheet: NoteSheetState? = null,
    val exportFileName: String = "",
)

sealed interface NotesEvent {
    data class Deleted(val row: HighlightEntity) : NotesEvent
    data object Exported : NotesEvent
    data object ExportFailed : NotesEvent
}

class NotesTexts(
    val location: (shortLocation: String?, percent: Int) -> String,
    val longChapter: (path: List<String>) -> String?,
    val exportMeta: (author: String, count: Int, date: String) -> String,
    val exportFileName: (title: String) -> String,
    val exportDate: (epochMs: Long) -> String,
    val shortChapter: (path: List<String>) -> String?,
)

/**
 * « Notes et surlignages » d’un livre (3.06) : éléments dans l’ordre du livre, menu ⋮ (note, suppression avec
 * « Annuler »), export Markdown. Partagé par la surcouche du lecteur et l’écran ouvert depuis la fiche ; « Aller au
 * passage » reste à l’appelant (saut dans le lecteur).
 */
class NotesListModel(
    private val bookId: Long,
    private val highlights: HighlightRepository,
    books: BookRepository,
    private val scope: CoroutineScope,
    private val texts: NotesTexts,
    private val writeText: suspend (Uri, String) -> Unit,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<NotesEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<NotesEvent> = _events.asSharedFlow()

    private var rows: List<HighlightEntity> = emptyList()
    private var author: String = ""
    private var editing: Long? = null
    private val writes = mutableListOf<Job>()

    init {
        scope.launch {
            combine(books.observeBook(bookId), highlights.observe(bookId)) { book, list -> book to list }.collect { (book, list) ->
                rows = list
                author = book?.author.orEmpty()
                val title = book?.title.orEmpty()
                _state.update { state ->
                    state.copy(
                        bookTitle = title,
                        exportFileName = texts.exportFileName(title),
                        loaded = true,
                        items = list.map { row ->
                            NoteItem(row.id, row.text, row.note, texts.location(texts.shortChapter(row.chapterPathList()), percentOf(row.progression)))
                        },
                    )
                }
            }
        }
    }

    fun editNote(id: Long) {
        val row = rows.firstOrNull { it.id == id } ?: return
        editing = id
        _state.update { it.copy(noteSheet = NoteSheetState(row.text, row.note.orEmpty(), editing = row.note != null)) }
    }

    fun onNoteChange(text: String) = _state.update { it.copy(noteSheet = it.noteSheet?.copy(note = text)) }

    fun saveNote() {
        val id = editing ?: return
        val sheet = _state.value.noteSheet ?: return
        editing = null
        _state.update { it.copy(noteSheet = null) }
        launchWrite { highlights.setNote(id, sheet.note) }
    }

    fun cancelNote() {
        editing = null
        _state.update { it.copy(noteSheet = null) }
    }

    fun delete(id: Long) = launchWrite { highlights.delete(id)?.let { _events.emit(NotesEvent.Deleted(it)) } }

    fun undoDelete(row: HighlightEntity) = launchWrite { highlights.restore(row) }

    fun export(uri: Uri) {
        val title = _state.value.bookTitle
        val current = rows
        scope.launch {
            val markdown = notesMarkdown(
                title = title,
                meta = texts.exportMeta(author, current.size, texts.exportDate(clock())),
                items = current.map { NotesExportItem(texts.longChapter(it.chapterPathList()), it.text, it.note) },
            )
            val event = runCatching { writeText(uri, markdown) }.fold({ NotesEvent.Exported }, { NotesEvent.ExportFailed })
            _events.emit(event)
        }
    }

    private fun launchWrite(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        synchronized(writes) { writes += job }
        job.invokeOnCompletion { synchronized(writes) { writes -= job } }
    }

    suspend fun awaitIdle() = synchronized(writes) { writes.toList() }.joinAll()
}
```

`shortChapter` n’a pas de valeur par défaut : l’oublier ne compile pas.

Fabrique des textes, dans `ui/common/BookTexts.kt` :

```kotlin
/** Textes de « Notes et surlignages » et de son export, lus dans les ressources. */
fun Resources.notesTexts(): NotesTexts {
    val locations = locationTexts()
    return NotesTexts(
        location = { location, percent -> highlightLocation(location, percent) },
        shortChapter = { path -> shortLocation(path, locations) },
        longChapter = { path -> longLocation(path) { part, chapter -> String.format(Locale.FRENCH, getString(R.string.common_location_long), part, chapter) } },
        exportMeta = { author, count, date ->
            val items = getQuantityString(R.plurals.notes_item_count, count, count)
            if (author.isBlank()) getString(R.string.notes_export_meta_no_author, items, date)
            else getString(R.string.notes_export_meta, author, items, date)
        },
        exportFileName = { title -> getString(R.string.notes_export_file_name, title) },
        exportDate = { ms -> formatDate(ms, getString(R.string.common_date_pattern_full)) },
    )
}
```

(Vérifier la signature de `formatDate` dans `BookTexts.kt`. Un titre avec `/` ou `:` : le sélecteur d’Android remplace lui-même les caractères interdits.) `AppContainer` : `val documents: DocumentStore = DocumentStore(appContext)`.

- [ ] **Step 4 : relancer** — PASS.
- [ ] **Step 5 : commit** — `git add app/src && git commit -m "19.2 : liste des notes, menu et export"`

---

### Task 19.3 : écran « Notes et surlignages » (3.06)

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/notes/NotesScreen.kt`
- Modify (test): `screenshots/V3ScreenCatalog.kt`, `samples/NotesSamples.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/notes/NotesScreenTest.kt`

**Interfaces:**
- Consumes: `NotesUiState`, `NoteItem`, `NoteSheet`, `MarkedText`, `VersoIconButton`, `VersoTextButton`, `VersoIcons.{ArrowLeft, Download, Note, MoreVertical}`.
- Produces:

```kotlin
class NotesActions(
    val onBack: () -> Unit,
    val onExport: () -> Unit,               // ouvre le sélecteur (l’écran le lance lui-même : voir NotesScreen)
    val onOpen: (id: Long) -> Unit,         // toucher l’élément, ou « Aller au passage »
    val onEditNote: (id: Long) -> Unit,
    val onDelete: (id: Long) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSaveNote: () -> Unit,
    val onCancelNote: () -> Unit,
)
@Composable fun NotesScreen(state: NotesUiState, actions: NotesActions, modifier: Modifier = Modifier, snackbarHost: @Composable () -> Unit = {})
@Composable fun rememberNotesExport(fileName: String, onChosen: (Uri) -> Unit): () -> Unit   // CreateDocument("text/markdown")
```

- [ ] **Step 1 : test qui échoue**

```kotlin
// app/src/test/kotlin/com/maximebier/verso/ui/notes/NotesScreenTest.kt
package com.maximebier.verso.ui.notes

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesScreenTest {
    @get:Rule val rule = createComposeRule()
    private val calls = mutableListOf<String>()
    private val actions = NotesActions(
        onBack = { calls += "retour" }, onExport = { calls += "exporter" }, onOpen = { calls += "ouvrir $it" },
        onEditNote = { calls += "note $it" }, onDelete = { calls += "supprimer $it" },
        onNoteChange = {}, onSaveNote = {}, onCancelNote = {},
    )
    private val state = NotesUiState(
        bookTitle = "Madame Bovary",
        loaded = true,
        items = listOf(
            NoteItem(1, "Il avait les cheveux coupés droit", "Premier portrait de Charles.", "Première partie, chap. I · 1 %"),
            NoteItem(2, "On l’aperçoit de loin", null, "Deuxième partie, chap. I · 31 %"),
        ),
    )

    private fun show(s: NotesUiState = state) = rule.setContent {
        VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) { NotesScreen(s, actions) }
    }

    @Test fun headerListAndMenu() {
        show()
        rule.onNodeWithText("Notes et surlignages").assertExists()
        rule.onNodeWithText("Madame Bovary · 2 éléments").assertExists()
        rule.onNodeWithText("L’export crée un fichier Markdown à garder ou à partager.").assertExists()
        rule.onNodeWithText("Exporter").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Il avait les cheveux coupés droit").performClick()
        rule.onAllNodesWithContentDescription("Options de cet élément")[1].assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Ajouter une note").performClick()   // élément 2 : sans note
        rule.onAllNodesWithContentDescription("Options de cet élément")[0].performClick()
        rule.onNodeWithText("Aller au passage").performClick()
        rule.onAllNodesWithContentDescription("Options de cet élément")[0].performClick()
        rule.onNodeWithText("Supprimer").performClick()
        assertThat(calls).containsExactly("exporter", "ouvrir 1", "note 2", "ouvrir 1", "supprimer 1").inOrder()
    }

    @Test fun emptyBookHidesExport() {
        show(state.copy(items = emptyList()))
        rule.onNodeWithText("Exporter").assertDoesNotExist()
        rule.onNodeWithText("Aucun surlignage pour l’instant. Sélectionnez un passage pendant la lecture pour le surligner.").assertExists()
    }
}
```

- [ ] **Step 2 : lancer, échec attendu** — FAIL (compilation).

- [ ] **Step 3 : implémentation** (`NotesScreen.kt`)

Structure (mesures de l’en-tête de ce fichier) :

```kotlin
@Composable
fun NotesScreen(state: NotesUiState, actions: NotesActions, modifier: Modifier = Modifier, snackbarHost: @Composable () -> Unit = {}) {
    val colors = VersoTheme.colors
    Box(modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
            item { NotesHeader(state, actions) }
            if (state.items.isEmpty() && state.loaded) {
                item {
                    Text(
                        text = stringResource(R.string.notes_empty),
                        style = VersoTheme.typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.notes_export_hint),
                        style = VersoTheme.typography.caption.copy(lineHeight = 20.3.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 12.dp),
                    )
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                items(state.items, key = { it.id }) { item -> NoteRow(item, actions) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
    }
    state.noteSheet?.let { NoteSheet(it, actions.onNoteChange, actions.onSaveNote, actions.onCancelNote) }
}
```

- `NotesHeader` : `Row` (barre d’état en marge haute `windowInsetsPadding(WindowInsets.statusBars)`, `heightIn(min = 64.dp)`, marges 4/8) avec `VersoIconButton(ArrowLeft, common_back, onBack)`, une `Column(weight 1f)` (titre `screenTitle` en `heading()`, sous-titre `stringResource(R.string.notes_subtitle, title, pluralStringResource(R.plurals.notes_item_count, n, n))` en `caption` `textSecondary`), puis, si `items` n’est pas vide, un bouton texte « Exporter » avec l’icône `Download` 20 dp (`VersoTextButton(text, onClick, icon = VersoIcons.Download)` si `VersoTextButton` accepte une icône ; sinon une `Row` cliquable `heightIn(min = 48.dp)`, `clip(CircleShape)`, `Role.Button`, icône + `Text` en `bodyStrong`). Titre et sous-titre passent à la ligne à 200 % (pas de `maxLines`) ; la maquette montre ce comportement.
- `NoteRow` : `Row(Modifier.fillMaxWidth().padding(end = 8.dp))` ; à gauche une `Column` `weight(1f)` cliquable (`Role.Button`, `onClick = { actions.onOpen(item.id) }`, `padding(start = 24.dp, top = 16.dp, end = 8.dp, bottom = 16.dp)`, `spacedBy(8.dp)`) : `MarkedText(item.text, body 18 sp interligne 27.9 sp)`, la note si présente (`Row` icône `Note` 18 dp `textSecondary` + texte 16 sp interligne 24 sp `textSecondary`), l’emplacement (`caption` `textSecondary`) ; à droite, `Box(Modifier.padding(top = 8.dp))` avec `VersoIconButton(MoreVertical, notes_item_options)` et son `DropdownMenu` (« Aller au passage », « Modifier la note » ou « Ajouter une note », « Supprimer ») aux couleurs `surface`/`text`, chaque entrée `heightIn(min = 48.dp)`. Puis `HorizontalDivider(1.dp, divider)`.
- Le menu ⋮ : reprendre le menu d’un livre de la bibliothèque (`LibraryComponents.kt`, menu ⋮ de la V1) s’il existe un composable réutilisable ; sinon `DropdownMenu` + `DropdownMenuItem` comme lui.

```kotlin
/** Sélecteur « Enregistrer sous » d’Android pour le fichier Markdown ; renvoie la fonction qui l’ouvre. */
@Composable
fun rememberNotesExport(fileName: String, onChosen: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) onChosen(uri)
    }
    val currentName by rememberUpdatedState(fileName)
    return { launcher.launch(currentName) }
}
```

Captures : `3.06-notes-et-surlignages` dans `V3ScreenCatalog` avec les trois éléments de la maquette (`NotesSamples.notesState`), et `3.06-vide` (état vide).

- [ ] **Step 4 : relancer** — `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.notes.*' --tests 'com.maximebier.verso.StringsTest' --tests 'com.maximebier.verso.screenshots.*'` → PASS ; capture regardée à côté du PNG.
- [ ] **Step 5 : commit** — `git add app/src && git commit -m "19.3 : écran Notes et surlignages"`

---

### Task 19.4 : accès depuis la barre de lecture et la fiche, aller au passage

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/notes/NotesViewModel.kt` (route depuis la fiche)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/nav/Routes.kt`, `VersoNavHost.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderBars.kt` (5ᵉ outil), `ReaderScreen.kt`, `ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/details/DetailsScreen.kt`, `DetailsViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderViewModelTest.kt`, `ReaderBarsTest.kt`, `ui/details/DetailsScreenTest.kt`, `DetailsViewModelTest.kt`, `ui/nav/VersoNavHostTest.kt`

**Interfaces:**
- Consumes: `NotesListModel`, `NotesScreen`, `rememberNotesExport`, `DocumentStore`, `notesTexts()` (19.2, 19.3).
- Produces: `NotesRoute(bookId)`, `ReaderRoute.highlightId: Long?`, `ReaderViewModel.{notes: StateFlow<NotesUiState>, notesEvents, notesVisible (dans ReaderUiState), showNotes(), hideNotes(), openHighlight(id: Long)}`, `ReaderViewModel.factory(bookId, openJournal, highlightId)`, `DetailsUiState.highlightCount: Int`, `DetailsDestination(…, onOpenNotes: (Long) -> Unit)`, `NotesDestination(bookId, onBack, onOpenPassage: (bookId: Long, highlightId: Long) -> Unit)`.

Chaînes :

```xml
    <!-- Barre de lecture : 5ᵉ outil (V3) -->
    <string name="reader_notes">Notes</string>
    <!-- Fiche : « Notes et surlignages · 3 » -->
    <string name="details_open_notes">Notes et surlignages · %1$d</string>
```

- [ ] **Step 1 : tests qui échouent**

`ReaderViewModelTest` (harnais existant, `FakeReaderController`, base en mémoire) :

```kotlin
    @Test
    fun openingAHighlightIsAnExplicitJump() = runTest {
        // Livre ouvert à la lecture ; un surlignage loin dans le livre.
        val vm = openedViewModel()                       // nom réel de l’aide du fichier
        val id = db.highlightDao().insert(highlightAt(testLocator(chapter = 3, progression = 0.5, total = 0.8)))
        vm.showNotes()
        assertThat(vm.uiState.value.notesVisible).isTrue()
        vm.openHighlight(id)
        advanceUntilIdle()
        assertThat(vm.uiState.value.notesVisible).isFalse()
        assertThat(fake.goCalls.last().href.toString()).isEqualTo("chapitre-3.xhtml")
        assertThat(vm.uiState.value.returnCard).isNotNull()      // la carte « Revenir » ramène à la lecture
    }

    @Test
    fun highlightIdFromTheRouteJumpsOnceTheBookIsOpen() = runTest {
        val id = db.highlightDao().insert(highlightAt(testLocator(chapter = 3, progression = 0.5, total = 0.8)))
        val vm = viewModel(highlightId = id)            // nouvelle option de l’aide de création
        readerReady(vm)                                  // onReaderReady(fake), nom réel de l’aide
        advanceUntilIdle()
        assertThat(fake.goCalls.map { it.href.toString() }).contains("chapitre-3.xhtml")
    }
```

`highlightAt(locator)` : aide du test qui fabrique un `HighlightEntity` pour le livre du harnais avec `locatorJson = Locators.toJson(locator)`.

`ReaderBarsTest` : le test de l’ordre des outils attend désormais `Sommaire, Journal, Rechercher, Notes, Aa` — choisir la place : après « Rechercher », avant « Réglages » (« Aa » reste le dernier). `DetailsScreenTest` : avec `highlightCount = 3`, `onNodeWithText("Notes et surlignages · 3").performClick()` appelle `onOpenNotes(bookId)` ; avec 0, la ligne est absente. `DetailsViewModelTest` : `highlightCount` suit `observeCount`. `VersoNavHostTest` : la route `NotesRoute` est déclarée (même forme que les tests existants du fichier).

- [ ] **Step 2 : lancer, échec attendu** — FAIL (compilation).

- [ ] **Step 3 : implémentation**

1. `Routes.kt` :

```kotlin
@Serializable data class ReaderRoute(val bookId: Long, val openJournal: Boolean = false, val highlightId: Long? = null)

@Serializable data class NotesRoute(val bookId: Long)
```

2. `ReaderViewModel` : constructeur `openHighlightOnLoad: Long? = null` et `notesTexts: NotesTexts`, `documents: DocumentStore` passés par la fabrique (`factory(bookId, openJournal, highlightId)`) ; `ReaderUiState.notesVisible: Boolean = false`.

```kotlin
    private val notesModel = NotesListModel(
        bookId = bookId, highlights = highlightRepository, books = books, scope = viewModelScope,
        texts = notesTexts, writeText = { uri, text -> writeDocument(uri, text) }, clock = clock,
    )
    val notes: StateFlow<NotesUiState> = notesModel.state
    val notesEvents: SharedFlow<NotesEvent> = notesModel.events

    /** « Notes » de la barre : surcouche 3.06, barre de lecture fermée. */
    fun showNotes() {
        sessionCoordinator?.onInteraction()
        _uiState.update { it.copy(notesVisible = true, barsVisible = false) }
    }

    fun hideNotes() = _uiState.update { it.copy(notesVisible = false) }

    /** Élément touché (ou « Aller au passage ») : surcouche fermée, saut explicite (carte « Revenir »). */
    fun openHighlight(id: Long) {
        viewModelScope.launch {
            val row = highlightRepository.get(id) ?: return@launch
            val target = Locators.fromJson(row.locatorJson) ?: return@launch
            _uiState.update { it.copy(notesVisible = false, barsVisible = false) }
            sessionCoordinator?.onInteraction()
            jumpTo(target)
        }
    }

    fun exportNotes(uri: Uri) = notesModel.export(uri)
    fun editNoteFromList(id: Long) = notesModel.editNote(id)
    fun onListNoteChange(text: String) = notesModel.onNoteChange(text)
    fun saveListNote() = notesModel.saveNote()
    fun cancelListNote() = notesModel.cancelNote()
    fun deleteFromList(id: Long) = notesModel.delete(id)
    fun undoDeleteFromList(row: HighlightEntity) = notesModel.undoDelete(row)
```

`writeDocument` : `private val writeDocument: suspend (Uri, String) -> Unit` du constructeur (fabrique : `container.documents::writeText` ; tests : une fonction qui garde le texte). À la fin de `start()`, après `if (openJournalOnLoad) showJournal()` : `openHighlightOnLoad?.let(::openHighlight)` — `jumpTo` mémorise le saut (`pendingJump`) tant que la surface n’est pas prête, puis le joue dans `onReaderReady` (mécanisme existant).

3. `ReaderBars.readerTools` : ajouter `onNotesClick` (paramètre de `ReaderBars`) et l’outil `ReaderTool(stringResource(R.string.reader_notes), ToolGlyph.Icon(VersoIcons.Note), onNotesClick)` après « Rechercher ». KDoc : « Ordre : Sommaire, Journal, Rechercher, Notes (V3), Réglages ».

4. `ReaderScreen` : `onNotesClick = viewModel::showNotes` ; `BackHandler(enabled = state.notesVisible) { viewModel.hideNotes() }` ; surcouche, au même niveau que `SearchScreen` :

```kotlin
        if (state.notesVisible) {
            val notes by viewModel.notes.collectAsStateWithLifecycle()
            val export = rememberNotesExport(notes.exportFileName, viewModel::exportNotes)
            NotesScreen(
                state = notes,
                actions = NotesActions(
                    onBack = viewModel::hideNotes, onExport = export, onOpen = viewModel::openHighlight,
                    onEditNote = viewModel::editNoteFromList, onDelete = viewModel::deleteFromList,
                    onNoteChange = viewModel::onListNoteChange, onSaveNote = viewModel::saveListNote, onCancelNote = viewModel::cancelListNote,
                ),
            )
        }
```

et la collecte de `notesEvents` dans le même `LaunchedEffect` que les événements de surlignage (snackbar « Surlignage supprimé » + « Annuler », « Notes exportées », « L’export n’a pas pu être enregistré. »). Le `VersoSnackbarHost` du lecteur est dessiné après la surcouche pour rester visible.

5. `NotesViewModel` (route depuis la fiche) : `NotesListModel` + fabrique (`container.highlights`, `container.books`, `resources.notesTexts()`, `container.documents::writeText`, `container.clock`) ; `NotesDestination(bookId, onBack, onOpenPassage)` affiche `NotesScreen` avec `onOpen = { id -> onOpenPassage(bookId, id) }`, son propre `SnackbarHostState` et les mêmes snackbars.

6. `VersoNavHost` :

```kotlin
        composable<NotesRoute> { entry ->
            val route = entry.toRoute<NotesRoute>()
            NotesDestination(
                bookId = route.bookId,
                onBack = { if (entry.resumed()) navController.popBackStack() },
                onOpenPassage = { bookId, highlightId ->
                    if (entry.resumed()) navController.navigate(ReaderRoute(bookId, highlightId = highlightId)) { launchSingleTop = true }
                },
            )
        }
```

`DetailsDestination` reçoit `onOpenNotes = { bookId -> if (entry.resumed()) navController.navigate(NotesRoute(bookId)) }` ; `ReaderDestination` lit `route.highlightId` et le passe à la fabrique (clé du `viewModel` inchangée : `"reader-$bookId"`).

7. Fiche : `DetailsViewModel(…, highlightCountOf: (Long) -> Flow<Int> = { flowOf(0) })` (fabrique : `container.highlights::observeCount`) ; `DetailsUiState.highlightCount: Int = 0` mis à jour dans le `combine` (quatrième flux). `DetailsScreen` : juste après la section statistiques,

```kotlin
                if (state.highlightCount > 0) {
                    OutlinedPillButton(
                        text = stringResource(R.string.details_open_notes, state.highlightCount),
                        onClick = { actions.onOpenNotes(state.bookId) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = VersoIcons.Note,
                    )
                }
```

(`DetailsActions.onOpenNotes: (Long) -> Unit = {}`.)

8. Captures V2 du lecteur (`ReaderBarsSample`, `ReaderToolsSample`) : réenregistrées, la barre a cinq outils ; vérifier la capture à 200 % (deux colonnes, rien de coupé).

- [ ] **Step 4 : relancer** — `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.*'` → PASS.
- [ ] **Step 5 : commit** — `git add app/src && git commit -m "19.4 : Notes et surlignages depuis la barre de lecture et la fiche"`

---

### Task 19.5 : fin d’étape 19

- [ ] Suite complète, lint, `assembleDebug`, captures 3.06 (avec éléments et vide) et barre de lecture à cinq outils regardées.
- [ ] Relecture du diff de l’étape ; corrections.
- [ ] `docs/SPEC.md` : décisions de cette partie (surcouche, `NotesRoute`, ligne de fiche dès un surlignage, cinq outils gardés), critères 8 et 9 cochés après le téléphone ; `docs/acceptance-v3.md`.
- [ ] Commit `Étape 19 : Notes et surlignages et export Markdown`, `git push`.
- [ ] Téléphone (verrou) : ouvrir la liste depuis la barre et depuis la fiche ; ordre du livre, chapitre et pourcentage ; toucher un élément → passage affiché, carte « Revenir » → retour à la lecture ; menu ⋮ (trois actions) ; Exporter → enregistrer dans Téléchargements, ouvrir le fichier dans un lecteur de texte (lisible, groupé par chapitre) ; livre sans surlignage (pas d’« Exporter », phrase d’état vide). Captures 3.06 en clair, sombre, nuit.
