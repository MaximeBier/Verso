# Partie D — Étape 15 (recherche plein texte) et étape 16 (passe d'acceptation V2, README)

Plan parent : `docs/superpowers/plans/2026-09-27-verso-v2.md` (Global Constraints, Review Focus, contrat d'architecture : tout s'applique ici). Design : `docs/superpowers/specs/2026-09-27-verso-v2-design.md`, section « Recherche (étape 15) ». Maquette : `docs/design/screens/2.06-recherche-dans-le-livre.png` et `docs/design/html/2.06-recherche-dans-le-livre.html`.

**Prérequis** : étapes 9 à 14 sur `master`, selon le contrat. En particulier : `AppTheme` (9.3, cinq thèmes en 10.1), `VersoTheme.typography` (9.2), `ReaderController.submit` (9.3) et `pageInfo` (12.3) implémentés par `FragmentReaderController` et `FakeReaderController` (`submitted: MutableList<ReaderStyle>`), `ReaderViewModel(…, settings: SettingsRepository, openJournalOnLoad: Boolean = false)` (11.2, 14.4) avec la fabrique de test `factory(id)` de `ReaderViewModelTest` (11.2), la barre de lecture V2 avec les outils Sommaire, Journal, Réglages (`readerTools`, 11.1), `V2ScreenCatalog` / `V2ScreenshotTest` (10.2, type `V2ScreenFixture`) et `AccessibilityTreeTest` étendu au catalogue V2 (11.4), `docs/acceptance-v2.md` (créé à l'étape 9).

Commandes (Git Bash, depuis la racine du dépôt) :

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_SERIAL=192.168.1.10:5555   # téléphone en adb Wi-Fi ; si « device not found » : adb connect 192.168.1.10:5555
./gradlew :core:test :app:testDebugUnitTest --tests '<motif>'   # un test
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug   # fin de tâche
```

## API Readium vérifiée (3.4.0, `javap` sur les AAR du cache Gradle)

- `org.readium.r2.shared.publication.services.search` : `suspend fun Publication.search(query: String, options: SearchService.Options? = null): SearchIterator?` ; `Publication.isSearchable`. Annotée `@ExperimentalReadiumApi` : `@OptIn(ExperimentalReadiumApi::class)`.
- `SearchIterator` : `suspend fun next(): Try<LocatorCollection?, SearchError>` (null = fin), `resultCount: Int?`, `close()` (non suspendue).
- L'analyseur EPUB (`EpubParser`) enregistre `StringSearchService.createDefaultFactory()` : chaque publication EPUB ouverte par `ReadiumOpener` est cherchable. Algorithme par défaut `IcuAlgorithm` (`android.icu.text.StringSearch` + `RuleBasedCollator`) avec `caseSensitive = false`, `diacriticSensitive = false`, `wholeWord = false` : « riviere » trouve « rivière », « Rivière » aussi.
- `next()` rend **une `LocatorCollection` par fichier de l'ordre de lecture qui contient des résultats** (les fichiers sans résultat sont sautés). Chaque `Locator` : `href`, `title` (titre du sommaire du fichier, `titleMatching`), `locations.progression` et `totalProgression`, `text = Locator.Text(before, highlight, after)` (extrait autour du mot).
- Décorations (navigateur classique, pas d'annotation expérimentale) : `EpubNavigatorFragment : DecorableNavigator`, `suspend fun applyDecorations(decorations: List<Decoration>, group: String)` ; `Decoration(id: String, locator: Locator, style: Decoration.Style, extras: Map<String, Any> = emptyMap())` ; `Decoration.Style.Highlight(tint: Int, isActive: Boolean = false)`. Gabarits : `EpubNavigatorFragment.Configuration.decorationTemplates: HtmlDecorationTemplates` ; `HtmlDecorationTemplates { set(Decoration.Style.Highlight::class, template) }` ; `HtmlDecorationTemplate(layout = Layout.BOXES, width = Width.WRAP, element: (Decoration) -> String, stylesheet: String?)`.
- Le gabarit par défaut de Readium dessine un calque **par-dessus** le texte, en couleur translucide (`z-index: -1` seulement en positionnement expérimental). Un calque translucide sur le texte ferait tomber le contraste sous 7:1. Verso pose donc un calque **opaque** de la couleur `highlight` avec `mix-blend-mode: darken` (Clair, Sépia) ou `lighten` (Sombre, Noir) : comme `highlight` est, canal par canal, entre le fond et le texte dans les quatre palettes (vérifié sur `tokens.json` et figé par un test), le fond sous le mot devient exactement `highlight` et le texte garde exactement sa couleur. Le soulignement est une bordure basse de 2 px à la couleur du texte.

## Noms produits par cette partie

Les noms partagés sont dans le contrat du plan principal (`2026-09-27-verso-v2.md`, « Contrat d'architecture ») ; ceux-ci sont propres à la partie D :

| Nom | Fichier | Rôle |
| --- | --- | --- |
| `SearchSnippet`, `searchSnippet(before, match, after, maxContextChars)`, `SEARCH_CONTEXT_CHARS` | `core/…/core/text/SearchSnippet.kt` | Extrait affiché, coupé aux mots, avec « … » |
| `BookSearch.MIN_QUERY_CHARS` (= 2) | `readium/BookSearch.kt` | Requête minimale |
| `BookSearchModel`, `SearchUiState`, `SearchGroup`, `BookSearchModel.DEBOUNCE_MS` (= 300) | `ui/reader/search/BookSearchModel.kt` | État de la recherche (classe simple détenue par `ReaderViewModel`, pas un `ViewModel` Android) |
| `SearchScreen`, `searchMatchText`, `searchStatusText` | `ui/reader/search/SearchScreen.kt` | Écran 2.06 |
| `SearchMatchDecoration` | `readium/SearchMatchDecoration.kt` | Décoration du mot trouvé dans le texte |
| `FragmentReaderController.bindSearchMatch` | `reader/FragmentReaderController.kt` | Branchement de `showSearchMatch` sur le navigateur |
| `ReaderViewModel.showSearch / hideSearch / openSearchResult / search`, `ReaderUiState.searchVisible` | `ui/reader/ReaderViewModel.kt` | Parcours de recherche |
| `VersoIcons.Search` | `ui/components/VersoIcons.kt` | Icône loupe de 2.01 |

## Libellés

Repris de `2.06-recherche-dans-le-livre.html` (mot pour mot) :

| Clé | Texte | Source |
| --- | --- | --- |
| `reader_search` | Rechercher | 2.01, outil de la barre |
| `search_field` | Rechercher dans le livre | `aria-label` du champ (servi aussi d'indication dans le champ vide) |
| `search_close` | Fermer la recherche | `aria-label` du bouton retour |
| `search_clear` | Effacer la recherche | `aria-label` du bouton ✕ |
| `search_in_progress` | Recherche en cours | `aria-label` de la barre de progression |
| `search_status_running_empty` | Recherche dans tout le livre… | début du statut |
| `search_status_running` (plurals) | Recherche dans tout le livre… %d résultat(s) pour l’instant | statut de la maquette |

**Absents des maquettes, formulés dans leur esprit (à signaler à Maxime au commit de l'étape 15, et à reporter dans `docs/SPEC.md`, « Historique »)** : `search_status_done_none` « Aucun résultat dans le livre », `search_status_done` (plurals) « %d résultat dans le livre » / « %d résultats dans le livre ». En français, la catégorie `one` couvre 0 et 1 : le cas 0 a donc sa propre chaîne, jamais le pluriel.

---

## Étape 15 — Recherche plein texte

Critères V2 de l'étape : 17 (résultats au fur et à mesure, groupés par chapitre, mot marqué par un fond, du gras et un soulignement) et 18 (toucher un résultat : passage affiché avec le mot marqué, carte « Revenir »).

### Task 15.1 : extraits et recherche Readium (`SearchSnippet`, `BookSearch`)

**Files:**
- Create: `core/src/main/kotlin/com/maximebier/verso/core/text/SearchSnippet.kt`
- Create: `core/src/test/kotlin/com/maximebier/verso/core/text/SearchSnippetTest.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/readium/BookSearch.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/readium/BookSearchTest.kt`

**Interfaces:**
- Consumes: `ReadiumOpener.open(file: File): Result<Publication>` (V1) ; EPUB `app/src/test/resources/epub/real/gutenberg-14155-madame-bovary-fr.epub`.
- Produces (contrat) : `class BookSearch(publication: Publication, dispatcher: CoroutineDispatcher = Dispatchers.Default) { fun search(query: String): Flow<List<SearchHit>> }` ; `data class SearchHit(val locator: Locator, val chapter: String?, val before: String, val match: String, val after: String, val progression: Double)`. Hors contrat : `BookSearch.MIN_QUERY_CHARS = 2`, `searchSnippet`, `SearchSnippet`, `SEARCH_CONTEXT_CHARS = 90`.
- Chaque émission de `search` est la **liste cumulée** des résultats trouvés jusque-là, dans l'ordre du livre ; une émission par fichier contenant des résultats. Requête de moins de 2 caractères (après `trim`) ou publication non cherchable : le flux se termine sans rien émettre. Échec de Readium en cours de route : journalisé, le flux se termine avec ce qui a été trouvé. Annulation (collecte arrêtée) : l'itérateur est fermé.

- [ ] **Step 1 : test de l'extrait (`:core`)**

```kotlin
package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchSnippetTest {

    @Test
    fun shortContextIsKeptWholeWithoutEllipsis() {
        val snippet = searchSnippet(before = "vers la ", match = "rivière", after = ".")
        assertThat(snippet).isEqualTo(SearchSnippet(before = "vers la ", match = "rivière", after = "."))
    }

    @Test
    fun longContextIsCutAtAWordBoundaryWithEllipsis() {
        val before = "Il y avait une fois, dans un pays lointain, au fond d’une vallée qu’arrose la Rieule, petite "
        val after = " qui se jette dans l’Andelle, après avoir fait tourner trois moulins vers son embouchure, et où il y a"
        val snippet = searchSnippet(before, "rivière", after, maxContextChars = 40)

        assertThat(snippet.before).startsWith("…")
        assertThat(snippet.before).endsWith("petite ")
        assertThat(snippet.before.length).isAtMost(41)
        assertThat(snippet.before.removePrefix("…").first()).isNotEqualTo(' ')
        assertThat(snippet.after).startsWith(" qui se jette")
        assertThat(snippet.after).endsWith("…")
        assertThat(snippet.after.length).isAtMost(41)
        // Jamais de mot coupé : le dernier mot avant « … » est entier.
        assertThat(after).contains(snippet.after.removeSuffix("…").trimEnd())
    }

    @Test
    fun whitespaceAndLineBreaksAreCollapsed() {
        val snippet = searchSnippet(before = "la\n  petite\t", match = "rivière", after = "\n qui")
        assertThat(snippet).isEqualTo(SearchSnippet(before = "la petite ", match = "rivière", after = " qui"))
    }

    @Test
    fun emptyContextStaysEmpty() {
        assertThat(searchSnippet(before = "", match = "Rivière", after = "")).isEqualTo(SearchSnippet("", "Rivière", ""))
    }
}
```

- [ ] **Step 2 : vérifier l'échec**

Run: `./gradlew :core:test --tests 'com.maximebier.verso.core.text.SearchSnippetTest'`
Expected: FAIL (compilation : `searchSnippet` et `SearchSnippet` inconnus).

- [ ] **Step 3 : implémentation**

```kotlin
package com.maximebier.verso.core.text

/** Longueur maximale du contexte avant et après le mot trouvé, en caractères (maquette 2.06 : deux à trois lignes). */
const val SEARCH_CONTEXT_CHARS = 90

/** Extrait d'un résultat de recherche : contexte avant, mot trouvé, contexte après, prêts à afficher. */
data class SearchSnippet(val before: String, val match: String, val after: String)

private const val ELLIPSIS = "…"
private val WHITESPACE = Regex("\\s+")

/**
 * Espaces fusionnés ; contexte de plus de [maxContextChars] caractères coupé à la frontière de mot la plus proche
 * du mot trouvé, marqué par « … » (au début pour [before], à la fin pour [after]). Un contexte court est gardé tel quel.
 */
fun searchSnippet(
    before: String,
    match: String,
    after: String,
    maxContextChars: Int = SEARCH_CONTEXT_CHARS,
): SearchSnippet {
    val b = before.replace(WHITESPACE, " ")
    val a = after.replace(WHITESPACE, " ")
    return SearchSnippet(
        before = if (b.length <= maxContextChars) b else cutBefore(b, maxContextChars),
        match = match.replace(WHITESPACE, " "),
        after = if (a.length <= maxContextChars) a else cutAfter(a, maxContextChars),
    )
}

/** Garde la fin de [text] : on part de `length − max` et on avance jusqu'au début du mot suivant. */
private fun cutBefore(text: String, max: Int): String {
    var start = text.length - max
    while (start < text.length && text[start] != ' ') start++
    val kept = text.substring(start).trimStart()
    return ELLIPSIS + kept
}

/** Garde le début de [text] : on recule depuis `max` jusqu'à la fin du mot précédent. */
private fun cutAfter(text: String, max: Int): String {
    var end = max
    while (end > 0 && text[end] != ' ') end--
    val kept = text.substring(0, end).trimEnd()
    return kept + ELLIPSIS
}
```

- [ ] **Step 4 : vérifier le succès**

Run: `./gradlew :core:test --tests 'com.maximebier.verso.core.text.SearchSnippetTest'`
Expected: PASS (4 tests).

- [ ] **Step 5 : test de `BookSearch` (Robolectric, vrai EPUB)**

```kotlin
package com.maximebier.verso.readium

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication

@RunWith(AndroidJUnit4::class)
class BookSearchTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var publication: Publication

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tmp.root, "bovary.epub")
        javaClass.getResourceAsStream("/epub/real/gutenberg-14155-madame-bovary-fr.epub")!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        publication = ReadiumOpener(context).open(file).getOrThrow()
    }

    @After
    fun tearDown() {
        publication.close()
    }

    private fun search() = BookSearch(publication, dispatcher = Dispatchers.Unconfined)

    @Test
    fun resultsArriveCumulatedInBookOrder() = runTest {
        val batches = search().search("rivière").toList()

        assertThat(batches).isNotEmpty()
        // Chaque lot contient le précédent : la liste s'allonge.
        batches.zipWithNext().forEach { (previous, next) ->
            assertThat(next.size).isGreaterThan(previous.size)
            assertThat(next.subList(0, previous.size)).isEqualTo(previous)
        }
        val hits = batches.last()
        assertThat(hits.map { it.progression }).isInOrder()
        hits.forEach { hit ->
            assertThat(hit.match.lowercase()).isEqualTo("rivière")
            assertThat(hit.progression).isIn(com.google.common.collect.Range.closed(0.0, 1.0))
            assertThat(hit.locator.text.highlight).isEqualTo(hit.match)
            assertThat(hit.before.length).isAtMost(com.maximebier.verso.core.text.SEARCH_CONTEXT_CHARS + 1)
        }
    }

    @Test
    fun caseAndAccentsAreIgnored() = runTest {
        val accented = search().search("rivière").toList().last()
        val plain = search().search("RIVIERE").toList().last()
        assertThat(plain.map { it.locator.locations.totalProgression }).isEqualTo(accented.map { it.locator.locations.totalProgression })
    }

    @Test
    fun tooShortQueryEmitsNothing() = runTest {
        assertThat(search().search("a").toList()).isEmpty()
        assertThat(search().search("  r  ").toList()).isEmpty()
    }

    @Test
    fun absentWordEmitsNothing() = runTest {
        assertThat(search().search("xylophonique").toList()).isEmpty()
    }

    @Test
    fun collectingOnlyTheFirstBatchCancelsTheSearch() = runTest {
        // Mot très fréquent : premier lot pris, puis la collecte s'arrête ; l'itérateur est fermé (pas d'exception).
        val first = search().search("de").first()
        assertThat(first).isNotEmpty()
    }

    @Test
    fun chapterTitleComesFromTheTableOfContents() = runTest {
        val hits = search().search("Yonville").toList().last()
        assertThat(hits.mapNotNull { it.chapter }).isNotEmpty()
    }
}
```

Si `caseAndAccentsAreIgnored` échoue sous Robolectric seulement (ICU de l'environnement JVM), le vérifier sur le téléphone (étape 15, fin) et, s'il passe là, annoter le test `@Ignore("ICU de Robolectric : vérifié sur le téléphone, voir acceptance-v2.md")` avec la ligne correspondante dans `docs/acceptance-v2.md`. Ne jamais le supprimer.

- [ ] **Step 6 : vérifier l'échec**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.readium.BookSearchTest'`
Expected: FAIL (compilation : `BookSearch` inconnu).

- [ ] **Step 7 : implémentation**

```kotlin
@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.readium

import android.util.Log
import com.maximebier.verso.core.text.searchSnippet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.Try

/** Un résultat : position exacte du mot (saut), titre du sommaire du fichier, extrait prêt à afficher. */
data class SearchHit(
    val locator: Locator,
    val chapter: String?,
    val before: String,
    val match: String,
    val after: String,
    val progression: Double,
)

/**
 * Recherche plein texte dans le livre ouvert, par le service de Readium (ICU : casse et accents ignorés).
 * Chaque émission est la liste cumulée des résultats, un lot par fichier qui en contient.
 */
class BookSearch(
    private val publication: Publication,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun search(query: String): Flow<List<SearchHit>> = flow {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_CHARS) return@flow
        val iterator = publication.search(trimmed) ?: return@flow
        try {
            val hits = mutableListOf<SearchHit>()
            while (true) {
                val page = when (val result = iterator.next()) {
                    is Try.Success -> result.value ?: break
                    is Try.Failure -> {
                        Log.w(TAG, "Recherche interrompue : ${result.value.message}")
                        break
                    }
                }
                val found = page.locators.mapNotNull(::toHit)
                if (found.isEmpty()) continue
                hits += found
                emit(hits.toList())
            }
        } finally {
            iterator.close()
        }
    }.flowOn(dispatcher)

    private fun toHit(locator: Locator): SearchHit? {
        val text = locator.text
        val match = text.highlight?.takeIf { it.isNotBlank() } ?: return null
        val snippet = searchSnippet(before = text.before.orEmpty(), match = match, after = text.after.orEmpty())
        return SearchHit(
            locator = locator,
            chapter = locator.title?.takeIf { it.isNotBlank() },
            before = snippet.before,
            match = snippet.match,
            after = snippet.after,
            progression = (locator.locations.totalProgression ?: 0.0).coerceIn(0.0, 1.0),
        )
    }

    companion object {
        /** En dessous, aucune recherche : une lettre seule donnerait des milliers de résultats inutiles. */
        const val MIN_QUERY_CHARS = 2
        private const val TAG = "BookSearch"
    }
}
```

- [ ] **Step 8 : vérifier le succès**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.readium.BookSearchTest'`
Expected: PASS (6 tests ; voir la consigne ICU du Step 5).

- [ ] **Step 9 : commit**

```bash
git add core/src/main/kotlin/com/maximebier/verso/core/text/SearchSnippet.kt core/src/test/kotlin/com/maximebier/verso/core/text/SearchSnippetTest.kt app/src/main/kotlin/com/maximebier/verso/readium/BookSearch.kt app/src/test/kotlin/com/maximebier/verso/readium/BookSearchTest.kt
git commit -m "Étape 15 (partie) : recherche Readium et extraits"
```

### Task 15.2 : état de la recherche (`BookSearchModel`)

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/search/BookSearchModel.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/ui/reader/search/BookSearchModelTest.kt`

**Interfaces:**
- Consumes: `SearchHit`, `BookSearch.MIN_QUERY_CHARS` (15.1) ; `Locators.hrefKey(locator: Locator): String` (V1).
- Produces:
  ```kotlin
  data class SearchGroup(val title: String?, val hits: List<SearchHit>)
  data class SearchUiState(
      val query: String = "",
      val running: Boolean = false,
      val done: Boolean = false,         // recherche terminée pour `query`
      val groups: List<SearchGroup> = emptyList(),
      val resultCount: Int = 0,
      val progress: Float = 0f,          // 0..1, fichiers parcourus / fichiers de l'ordre de lecture
  )
  class BookSearchModel(
      scope: CoroutineScope,
      search: (String) -> Flow<List<SearchHit>>,
      readingOrderHrefs: () -> List<String>,
      chapterLabel: (SearchHit) -> String?,
  ) {
      val state: StateFlow<SearchUiState>
      fun onQueryChange(query: String)
      fun clear()
      fun cancel()
      companion object { const val DEBOUNCE_MS = 300L }
  }
  ```
- Règles : chaque saisie annule la recherche en cours ; la recherche part [DEBOUNCE_MS] après la dernière frappe ; requête trop courte → état vide, ni `running` ni `done`. Les résultats sont groupés par chapitre **consécutif** (clé : `chapterLabel(hit)`, puis `hit.chapter`, puis `null`). `progress` = rang (1-based) du fichier du dernier résultat dans `readingOrderHrefs()` / nombre de fichiers ; 1 à la fin. La même requête qu'avant (espaces compris) ne relance rien : l'état est gardé quand on rouvre la recherche.

- [ ] **Step 1 : tests**

```kotlin
package com.maximebier.verso.ui.reader.search

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.readium.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

class BookSearchModelTest {

    private val hrefs = listOf("c1.xhtml", "c2.xhtml", "c3.xhtml", "c4.xhtml")

    private fun hit(href: String, total: Double, chapter: String? = "Chapitre $href") = SearchHit(
        locator = Locator(href = Url(href)!!, mediaType = MediaType.XHTML, locations = Locator.Locations(totalProgression = total)),
        chapter = chapter,
        before = "…la ",
        match = "rivière",
        after = " qui…",
        progression = total,
    )

    /** Recherche pilotée par le test : chaque lot est poussé à la main, `null` termine. */
    private class ScriptedSearch {
        val queries = mutableListOf<String>()
        val batches = MutableSharedFlow<List<SearchHit>?>()
        fun search(query: String): Flow<List<SearchHit>> = flow {
            queries += query
            batches.takeWhile { it != null }.collect { emit(it!!) }
        }
    }

    private fun TestScope.model(search: ScriptedSearch, scope: CoroutineScope = backgroundScope) = BookSearchModel(
        scope = scope,
        search = search::search,
        readingOrderHrefs = { hrefs },
        chapterLabel = { it.chapter },
    )

    @Test
    fun searchStartsAfterDebounceAndResultsGrow() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)

        model.onQueryChange("riv")
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS - 1)
        runCurrent()
        assertThat(search.queries).isEmpty()
        assertThat(model.state.value.query).isEqualTo("rivière")

        advanceTimeBy(2)
        runCurrent()
        assertThat(search.queries).containsExactly("rivière")
        assertThat(model.state.value.running).isTrue()

        search.batches.emit(listOf(hit("c1.xhtml", 0.10)))
        runCurrent()
        assertThat(model.state.value.resultCount).isEqualTo(1)
        assertThat(model.state.value.progress).isEqualTo(0.25f)

        search.batches.emit(listOf(hit("c1.xhtml", 0.10), hit("c1.xhtml", 0.12), hit("c3.xhtml", 0.60)))
        runCurrent()
        val state = model.state.value
        assertThat(state.resultCount).isEqualTo(3)
        assertThat(state.groups.map { it.title }).containsExactly("Chapitre c1.xhtml", "Chapitre c3.xhtml").inOrder()
        assertThat(state.groups[0].hits).hasSize(2)
        assertThat(state.progress).isEqualTo(0.75f)
        assertThat(state.done).isFalse()

        search.batches.emit(null)
        runCurrent()
        assertThat(model.state.value.running).isFalse()
        assertThat(model.state.value.done).isTrue()
        assertThat(model.state.value.progress).isEqualTo(1f)
    }

    @Test
    fun newInputCancelsTheRunningSearch() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(listOf(hit("c1.xhtml", 0.10)))
        runCurrent()

        model.onQueryChange("moulin")
        runCurrent()
        assertThat(model.state.value.groups).isEmpty()
        assertThat(model.state.value.running).isFalse()
        // L'ancienne recherche ne reçoit plus rien : ce lot n'arrive pas dans l'état.
        search.batches.emit(listOf(hit("c1.xhtml", 0.10), hit("c2.xhtml", 0.3)))
        runCurrent()
        assertThat(model.state.value.resultCount).isEqualTo(0)

        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(search.queries).containsExactly("rivière", "moulin").inOrder()
    }

    @Test
    fun tooShortQueryNeverSearches() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange(" r ")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS * 3)
        runCurrent()
        assertThat(search.queries).isEmpty()
        assertThat(model.state.value).isEqualTo(SearchUiState(query = " r "))
    }

    @Test
    fun noResultEndsDoneAndEmpty() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("xylophone")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(null)
        runCurrent()
        assertThat(model.state.value.done).isTrue()
        assertThat(model.state.value.resultCount).isEqualTo(0)
    }

    @Test
    fun sameQueryKeepsResultsWithoutSearchingAgain() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        search.batches.emit(listOf(hit("c1.xhtml", 0.10)))
        search.batches.emit(null)
        runCurrent()

        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertThat(search.queries).containsExactly("rivière")
        assertThat(model.state.value.resultCount).isEqualTo(1)
    }

    @Test
    fun clearEmptiesEverything() = runTest(StandardTestDispatcher()) {
        val search = ScriptedSearch()
        val model = model(search)
        model.onQueryChange("rivière")
        advanceTimeBy(BookSearchModel.DEBOUNCE_MS + 1)
        runCurrent()
        model.clear()
        runCurrent()
        assertThat(model.state.value).isEqualTo(SearchUiState())
    }
}
```

- [ ] **Step 2 : vérifier l'échec**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.search.BookSearchModelTest'`
Expected: FAIL (compilation : `BookSearchModel` inconnu).

- [ ] **Step 3 : implémentation**

```kotlin
package com.maximebier.verso.ui.reader.search

import com.maximebier.verso.readium.BookSearch
import com.maximebier.verso.readium.Locators
import com.maximebier.verso.readium.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Résultats consécutifs d'un même chapitre (maquette 2.06 : un intitulé, puis ses extraits). */
data class SearchGroup(val title: String?, val hits: List<SearchHit>)

data class SearchUiState(
    val query: String = "",
    val running: Boolean = false,
    val done: Boolean = false,
    val groups: List<SearchGroup> = emptyList(),
    val resultCount: Int = 0,
    val progress: Float = 0f,
)

/**
 * État de la recherche du livre ouvert, détenu par `ReaderViewModel` (il vit autant que le lecteur : la recherche
 * rouverte retrouve sa requête et ses résultats). Saisie anti-rebond, une seule recherche à la fois.
 */
class BookSearchModel(
    private val scope: CoroutineScope,
    private val search: (String) -> Flow<List<SearchHit>>,
    private val readingOrderHrefs: () -> List<String>,
    private val chapterLabel: (SearchHit) -> String?,
) {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        if (query == _state.value.query && (_state.value.running || _state.value.done || job?.isActive == true)) return
        job?.cancel()
        _state.value = SearchUiState(query = query)
        if (query.trim().length < BookSearch.MIN_QUERY_CHARS) return
        job = scope.launch {
            delay(DEBOUNCE_MS)
            _state.update { it.copy(running = true) }
            search(query).collect { hits -> _state.update { it.withHits(hits) } }
            _state.update { it.copy(running = false, done = true, progress = 1f) }
        }
    }

    fun clear() = onQueryChange("")

    /** Lecteur fermé : la recherche en cours s'arrête (l'état est gardé). */
    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false) }
    }

    private fun SearchUiState.withHits(hits: List<SearchHit>): SearchUiState {
        val groups = mutableListOf<SearchGroup>()
        hits.forEach { hit ->
            val title = chapterLabel(hit) ?: hit.chapter
            val last = groups.lastOrNull()
            if (last != null && last.title == title) {
                groups[groups.lastIndex] = last.copy(hits = last.hits + hit)
            } else {
                groups += SearchGroup(title, listOf(hit))
            }
        }
        val hrefs = readingOrderHrefs()
        val lastIndex = hits.lastOrNull()?.let { hrefs.indexOf(Locators.hrefKey(it.locator)) } ?: -1
        val progress = if (hrefs.isEmpty() || lastIndex < 0) progress else (lastIndex + 1).toFloat() / hrefs.size
        return copy(groups = groups, resultCount = hits.size, progress = progress)
    }

    companion object {
        /** Délai après la dernière frappe avant de chercher. */
        const val DEBOUNCE_MS = 300L
    }
}
```

- [ ] **Step 4 : vérifier le succès**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.search.BookSearchModelTest'`
Expected: PASS (6 tests).

- [ ] **Step 5 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/ui/reader/search/BookSearchModel.kt app/src/test/kotlin/com/maximebier/verso/ui/reader/search/BookSearchModelTest.kt
git commit -m "Étape 15 (partie) : état de la recherche"
```

### Task 15.3 : écran « Recherche dans le livre » (2.06) et outil « Rechercher »

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/search/SearchScreen.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/ui/reader/search/SearchScreenTest.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/screenshots/samples/SearchSample.kt`
- Modify: `app/src/main/res/values/strings.xml` (chaînes du tableau « Libellés », sous `<!-- V2 · écran 2.06 -->`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/VersoIcons.kt` (`Search`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderBars.kt` (outil « Rechercher » entre Journal et Réglages, paramètre `onSearchClick`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderViewModel.kt` (`search`, `searchVisible`, `showSearch`, `hideSearch`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt` (surcouche, retour)
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt` (fixture 2.06)
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderBarsTest.kt` (quatre outils)
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `BookSearchModel`, `SearchUiState`, `SearchGroup` (15.2) ; `BookSearch` (15.1) ; `VersoTheme.colors` (`surface`, `divider`, `highlight`, `accent`, `progressTrack`, `text`, `textSecondary`), `VersoTheme.typography` (`body`, `caption`, `captionBold`) ; `VersoIconButton` (V1) ; outils de la barre de la tâche 11.1 (`ReaderTool`, `ToolGlyph`, `readerTools(...)`, icône et texte dessous).
- Produces:
  ```kotlin
  @Composable fun SearchScreen(
      state: SearchUiState,
      onQueryChange: (String) -> Unit,
      onClear: () -> Unit,
      onClose: () -> Unit,
      onResultClick: (SearchHit) -> Unit,
      modifier: Modifier = Modifier,
      requestFocus: Boolean = true,
  )
  fun searchMatchText(hit: SearchHit, highlight: Color): AnnotatedString
  @Composable fun searchStatusText(state: SearchUiState): String?
  // ReaderViewModel
  val search: StateFlow<SearchUiState>
  fun showSearch(); fun hideSearch(); fun onSearchQueryChange(query: String); fun clearSearch()
  // ReaderUiState
  val searchVisible: Boolean = false
  // ReaderBars
  onSearchClick: () -> Unit
  ```
- Mesures de 2.06 : barre du haut 72 dp, fond `surface`, marges 4 dp à gauche et 8 dp à droite, écart 4 dp ; boutons 48 dp (retour, effacer) ; champ 18 sp ; barre de progression 3 dp (`progressTrack`, remplissage `accent`) ; statut 14 sp `textSecondary`, marges 14 / 24 / 4 dp ; intitulé de chapitre 14 sp gras `textSecondary`, mêmes marges ; résultat : colonne, marges 14 dp × 24 dp, écart 6 dp, filet bas 1 dp `divider` ; extrait 16 sp, interligne 1,55 (24,8 sp) ; pourcentage 14 sp `textSecondary`. Mot trouvé : fond `highlight`, gras, souligné.

- [ ] **Step 1 : chaînes**

Ajouter à `app/src/main/res/values/strings.xml` (U+2026 pour « … », U+2019 pour l'apostrophe) :

```xml
    <!-- V2 · écran 2.06 : recherche dans le livre -->
    <string name="reader_search">Rechercher</string>
    <string name="search_field">Rechercher dans le livre</string>
    <string name="search_close">Fermer la recherche</string>
    <string name="search_clear">Effacer la recherche</string>
    <string name="search_in_progress">Recherche en cours</string>
    <string name="search_status_running_empty">Recherche dans tout le livre…</string>
    <plurals name="search_status_running">
        <item quantity="one">Recherche dans tout le livre… %d résultat pour l’instant</item>
        <item quantity="many">Recherche dans tout le livre… %d résultats pour l’instant</item>
        <item quantity="other">Recherche dans tout le livre… %d résultats pour l’instant</item>
    </plurals>
    <!-- Absents des maquettes (formulés dans leur esprit, signalés à Maxime) ; 0 a sa chaîne : « one » couvre 0 en français. -->
    <string name="search_status_done_none">Aucun résultat dans le livre</string>
    <plurals name="search_status_done">
        <item quantity="one">%d résultat dans le livre</item>
        <item quantity="many">%d résultats dans le livre</item>
        <item quantity="other">%d résultats dans le livre</item>
    </plurals>
```

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.StringsTest'`
Expected: PASS (règles typographiques respectées).

- [ ] **Step 2 : icône**

Dans `VersoIcons` (après `History`), l'icône de 2.01 (cercle r = 7 en 11,11 et trait 20,20 → 16,5,16,5) :

```kotlin
    val Search: ImageVector = strokeIcon("search", "M11 4a7 7 0 1 1 0 14a7 7 0 1 1 0-14z", "m20 20-3.5-3.5")
```

- [ ] **Step 3 : tests de l'écran**

```kotlin
package com.maximebier.verso.ui.reader.search

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.readium.SearchHit
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val hit = SearchHit(
        locator = Locator(href = Url("c1.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(totalProgression = 0.30)),
        chapter = "I",
        before = "…petite ",
        match = "rivière",
        after = " qui se jette…",
        progression = 0.30,
    )

    private fun show(state: SearchUiState, events: MutableList<String> = mutableListOf()) {
        rule.setContent {
            VersoTheme {
                SearchScreen(
                    state = state,
                    onQueryChange = { events += "query:$it" },
                    onClear = { events += "clear" },
                    onClose = { events += "close" },
                    onResultClick = { events += "result:${it.progression}" },
                    requestFocus = false,
                )
            }
        }
    }

    @Test
    fun matchIsBoldUnderlinedOnHighlight() {
        val text = searchMatchText(hit, highlight = Color.Yellow)
        assertThat(text.text).isEqualTo("…petite rivière qui se jette…")
        val span = text.spanStyles.single()
        assertThat(span.start).isEqualTo("…petite ".length)
        assertThat(span.end).isEqualTo("…petite rivière".length)
        assertThat(span.item.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(span.item.textDecoration).isEqualTo(TextDecoration.Underline)
        assertThat(span.item.background).isEqualTo(Color.Yellow)
    }

    @Test
    fun runningSearchShowsCountGroupAndPercent() {
        val state = SearchUiState(query = "rivière", running = true, groups = listOf(SearchGroup("Deuxième partie, chapitre I", listOf(hit))), resultCount = 3, progress = 0.45f)
        show(state)
        rule.onNodeWithText("Recherche dans tout le livre… 3 résultats pour l’instant").assertIsDisplayed()
        rule.onNodeWithText("Deuxième partie, chapitre I").assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.common_percent, 30)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.search_in_progress)).assertIsDisplayed()
    }

    @Test
    fun finishedSearchWithoutResultSaysSo() {
        show(SearchUiState(query = "xylophone", done = true))
        rule.onNodeWithText(context.getString(R.string.search_status_done_none)).assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.search_in_progress)).assertDoesNotExist()
    }

    @Test
    fun oneResultUsesTheSingular() {
        show(SearchUiState(query = "Yonville", done = true, groups = listOf(SearchGroup("I", listOf(hit))), resultCount = 1, progress = 1f))
        rule.onNodeWithText("1 résultat dans le livre").assertIsDisplayed()
    }

    @Test
    fun buttonsAndFieldReportEvents() {
        val events = mutableListOf<String>()
        show(SearchUiState(query = "riv", groups = listOf(SearchGroup("I", listOf(hit))), resultCount = 1, done = true), events)
        rule.onNode(hasSetTextAction()).performTextInput("i")
        rule.onNodeWithContentDescription(context.getString(R.string.search_clear)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.search_close)).performClick()
        rule.onNodeWithText("…petite rivière qui se jette…").performClick()
        assertThat(events.first()).startsWith("query:")
        assertThat(events.drop(1)).containsExactly("clear", "close", "result:0.3").inOrder()
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.search.SearchScreenTest'`
Expected: FAIL (compilation : `SearchScreen` inconnu).

- [ ] **Step 4 : implémentation de l'écran**

```kotlin
package com.maximebier.verso.ui.reader.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.readium.SearchHit
import com.maximebier.verso.ui.common.percentOf
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/** Extrait d'un résultat, le mot trouvé en fond `highlight`, gras et souligné (jamais la couleur seule). */
fun searchMatchText(hit: SearchHit, highlight: Color): AnnotatedString = buildAnnotatedString {
    append(hit.before)
    withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
        append(hit.match)
    }
    append(hit.after)
}

/** Statut sous la barre : en cours (avec le compte), terminé (compte ou « Aucun résultat »), rien sans requête. */
@Composable
fun searchStatusText(state: SearchUiState): String? = when {
    state.running && state.resultCount == 0 -> stringResource(R.string.search_status_running_empty)
    state.running -> pluralStringResource(R.plurals.search_status_running, state.resultCount, state.resultCount)
    state.done && state.resultCount == 0 -> stringResource(R.string.search_status_done_none)
    state.done -> pluralStringResource(R.plurals.search_status_done, state.resultCount, state.resultCount)
    else -> null
}

/** Écran 2.06, en surcouche plein écran du lecteur. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onResultClick: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = true,
) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (requestFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VersoDimens.topBarReader)
                .background(colors.surface)
                .padding(start = 4.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersoIconButton(icon = VersoIcons.ArrowLeft, contentDescription = stringResource(R.string.search_close), onClick = onClose)
            val label = stringResource(R.string.search_field)
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = typography.body.copy(fontSize = 18.sp, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = VersoDimens.controlMin)
                    .focusRequester(focus)
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.query.isEmpty()) {
                            Text(label, style = typography.body.copy(fontSize = 18.sp), color = colors.textSecondary)
                        }
                        inner()
                    }
                },
            )
            if (state.query.isNotEmpty()) {
                VersoIconButton(icon = VersoIcons.Close, contentDescription = stringResource(R.string.search_clear), onClick = onClear)
            }
        }
        if (state.running) SearchProgress(state.progress)

        LazyColumn(Modifier.fillMaxSize()) {
            searchStatusItem(state)
            state.groups.forEach { group ->
                group.title?.let { title ->
                    item(key = "titre-${group.hits.first().progression}-$title") {
                        Text(
                            text = title,
                            style = typography.captionBold,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                }
                items(group.hits, key = { "${it.locator.href}-${it.locator.locations.totalProgression}-${it.before}" }) { hit ->
                    SearchResultRow(hit = hit, onClick = { onResultClick(hit) })
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchStatusItem(state: SearchUiState) {
    item(key = "statut") {
        val status = searchStatusText(state) ?: return@item
        Text(
            text = status,
            style = VersoTheme.typography.caption,
            color = VersoTheme.colors.textSecondary,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Barre de 3 dp : fichiers parcourus. */
@Composable
private fun SearchProgress(progress: Float) {
    val colors = VersoTheme.colors
    val description = stringResource(R.string.search_in_progress)
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(colors.progressTrack)
            .progressSemantics(progress.coerceIn(0f, 1f))
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(colors.accent))
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onClick: () -> Unit) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = VersoDimens.controlMin)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = searchMatchText(hit, colors.highlight), style = typography.body.copy(lineHeight = 24.8.sp), color = colors.text)
            Text(text = stringResource(R.string.common_percent, percentOf(hit.progression)), style = typography.caption, color = colors.textSecondary)
        }
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.search.SearchScreenTest'`
Expected: PASS (5 tests).

- [ ] **Step 5 : outil « Rechercher » dans la barre**

Dans `ReaderBars.kt` (tâche 11.1) : ajouter le paramètre `onSearchClick: () -> Unit` à `ReaderBars`, juste après `onJournalClick`, et le passer à `readerTools(...)`, qui le reçoit aussi après `onJournalClick` ; dans `readerTools`, remplacer la ligne de commentaire « Étape 15 : outil Rechercher … inséré ici. » par

```kotlin
    add(ReaderTool(stringResource(R.string.reader_search), ToolGlyph.Icon(VersoIcons.Search), onSearchClick))
```

Mettre à jour les autres appels de `ReaderBars` (`ReaderScreen`, `ReaderToolsSample` et `ReaderBarsSample` de `ReaderSamples.kt`, `ReaderBarsTest.show(...)`) avec `onSearchClick = {}` (ou `viewModel::showSearch` dans `ReaderScreen`, ci-dessous). Dans `ReaderBarsTest`, ajouter :

```kotlin
    @Test
    fun searchToolIsBetweenJournalAndSettings() {
        var searched = 0
        rule.setContent {
            VersoTheme {
                ReaderBars(
                    visible = true,
                    state = readerBarsSampleState,
                    onBack = {},
                    onTocClick = {},
                    onJournalClick = {},
                    onSearchClick = { searched++ },
                    onSettingsClick = {},
                    onBottomBarHeightChanged = {},
                )
            }
        }
        val labels = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() }
        assertThat(labels.filter { it in setOf("Sommaire", "Journal", "Rechercher", "Réglages") })
            .containsExactly("Sommaire", "Journal", "Rechercher", "Réglages").inOrder()
        rule.onNodeWithText("Rechercher").performClick()
        assertThat(searched).isEqualTo(1)
    }
```

(`readerBarsSampleState` : `screenshots/samples/ReaderSamples.kt`, import `com.maximebier.verso.screenshots.samples.readerBarsSampleState`.) Le libellé de l'outil Réglages est celui de la maquette 2.01 (« Réglages »).

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.ReaderBarsTest'`
Expected: PASS.

- [ ] **Step 6 : le ViewModel ouvre et ferme la recherche**

Test à ajouter à `ReaderViewModelTest` :

```kotlin
    @Test
    fun searchOpensHidesBarsAndKeepsItsQueryWhenReopened() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        viewModel.onReaderReady(FakeReaderController(start))
        runCurrent()
        viewModel.toggleBars()

        viewModel.showSearch()
        assertThat(viewModel.uiState.value.searchVisible).isTrue()
        assertThat(viewModel.uiState.value.barsVisible).isFalse()

        viewModel.onSearchQueryChange("rivière")
        viewModel.hideSearch()
        viewModel.showSearch()
        assertThat(viewModel.search.value.query).isEqualTo("rivière")
        store.clear()
    }
```

Dans `ReaderViewModel` :

```kotlin
// ReaderUiState : ajouter
    /** Écran « Recherche dans le livre » (2.06) affiché par-dessus le texte. */
    val searchVisible: Boolean = false,

// ReaderViewModel : champs
    private var bookSearch: BookSearch? = null

    private val searchModel = BookSearchModel(
        scope = viewModelScope,
        search = { query -> bookSearch?.search(query) ?: emptyFlow() },
        readingOrderHrefs = { _uiState.value.readingOrderHrefs },
        chapterLabel = { hit -> chapterTitleOf(hit.locator) },
    )

    /** Requête et résultats de la recherche ; gardés tant que le lecteur est ouvert. */
    val search: StateFlow<SearchUiState> = searchModel.state

// dans start(book, publication), après `positions = ReadingOrderPositions.load(publication)` :
        bookSearch = BookSearch(publication)

// fonctions publiques
    fun showSearch() = _uiState.update { it.copy(searchVisible = true, barsVisible = false, tocVisible = false, settingsVisible = false) }

    fun hideSearch() = _uiState.update { it.copy(searchVisible = false) }

    fun onSearchQueryChange(query: String) = searchModel.onQueryChange(query)

    fun clearSearch() = searchModel.clear()

// dans onCleared(), avant l'existant :
        searchModel.cancel()
```

(imports : `com.maximebier.verso.readium.BookSearch`, `com.maximebier.verso.ui.reader.search.BookSearchModel`, `com.maximebier.verso.ui.reader.search.SearchUiState`, `kotlinx.coroutines.flow.emptyFlow`). `chapterTitleOf` existe déjà (forme longue « Deuxième partie, chapitre I », ancres du sommaire comprises) ; il reste `private`, `BookSearchModel` le reçoit par la lambda.

Dans `ReaderScreen` :

```kotlin
    val search by viewModel.search.collectAsStateWithLifecycle()
    BackHandler(enabled = state.searchVisible) { viewModel.hideSearch() }
    // … ReaderBars(…, onSearchClick = viewModel::showSearch, …)
    // Tout à la fin du Box principal, après la carte « Revenir » (la recherche la recouvre) :
        if (state.searchVisible) {
            SearchScreen(
                state = search,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::clearSearch,
                onClose = viewModel::hideSearch,
                onResultClick = viewModel::openSearchResult,
            )
        }
```

`openSearchResult` est posé à la tâche 15.4 ; dans cette tâche, le passer comme `onResultClick = {}` et le remplacer en 15.4.

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.ReaderViewModelTest'`
Expected: PASS.

- [ ] **Step 7 : capture 2.06**

`app/src/test/kotlin/com/maximebier/verso/screenshots/samples/SearchSample.kt` :

```kotlin
package com.maximebier.verso.screenshots.samples

import androidx.compose.runtime.Composable
import com.maximebier.verso.readium.SearchHit
import com.maximebier.verso.ui.reader.search.SearchGroup
import com.maximebier.verso.ui.reader.search.SearchScreen
import com.maximebier.verso.ui.reader.search.SearchUiState
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

private fun sampleHit(before: String, after: String, progression: Double) = SearchHit(
    locator = Locator(href = Url("deuxieme-partie-1.xhtml")!!, mediaType = MediaType.XHTML, locations = Locator.Locations(totalProgression = progression)),
    chapter = "I",
    before = before,
    match = "rivière",
    after = after,
    progression = progression,
)

/** Maquette 2.06 : « rivière », trois résultats de la Deuxième partie, chapitre I, recherche en cours. */
@Composable
fun SearchSample() {
    val hits = listOf(
        sampleHit("…au fond d’une vallée qu’arrose la Rieule, petite ", " qui se jette dans l’Andelle, après avoir fait tourner trois moulins…", 0.305),
        sampleHit("…d’où l’on découvre la vallée. La ", " qui la traverse en fait comme deux régions de physionomie distincte…", 0.307),
        sampleHit("…le bourg paresseux, s’écartant de la plaine, a continué naturellement à s’agrandir vers la ", ".", 0.312),
    )
    SearchScreen(
        state = SearchUiState(query = "rivière", running = true, groups = listOf(SearchGroup("Deuxième partie, chapitre I", hits)), resultCount = 3, progress = 0.45f),
        onQueryChange = {},
        onClear = {},
        onClose = {},
        onResultClick = {},
        requestFocus = false,
    )
}
```

Dans `V2ScreenCatalog.fixtures` (10.2), ajouter `V2ScreenFixture(ScreenFixture("2.06-recherche-dans-le-livre") { SearchSample() }),`.

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.screenshots.V2ScreenshotTest' --tests 'com.maximebier.verso.screenshots.AccessibilityTreeTest'`
Expected: PASS ; ouvrir `app/build/outputs/roborazzi/v2/2.06-recherche-dans-le-livre-clair.png` et le comparer à `docs/design/screens/2.06-recherche-dans-le-livre.png` (barre 72 dp, statut, intitulé, trois extraits, pourcentages, mot sur fond). Faire de même en sombre et à 200 % (rien de coupé).

- [ ] **Step 8 : vérification complète et commit**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL, aucun avertissement lint.

```bash
git add -A app/src core/src
git commit -m "Étape 15 (partie) : écran de recherche et outil Rechercher"
```

### Task 15.4 : toucher un résultat — saut, mot marqué dans le texte, carte « Revenir »

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/readium/SearchMatchDecoration.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/readium/SearchMatchDecorationTest.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/readium/VersoReadingPreferences.kt` (`applyVerso` : gabarit de décoration)
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderController.kt` (`showSearchMatch`, contrat)
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/FragmentReaderController.kt` (`bindSearchMatch`, `showSearchMatch`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderSurface.kt` (branchement sur `applyDecorations`)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderViewModel.kt` (`openSearchResult`, effacement)
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt` (`onResultClick = viewModel::openSearchResult`)
- Modify: `app/src/test/kotlin/com/maximebier/verso/reader/FakeReaderController.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/reader/FragmentReaderControllerTest.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `AppTheme` (10.1), `VersoPalette.Light/Sepia/Dark/Black` (générées), `SearchHit` (15.1), `ReaderViewModel.jumpTo(locator)` (V1 : `ReaderEvent.Jumped` puis `go`, via le coordinateur).
- Produces (contrat) : `suspend fun ReaderController.showSearchMatch(locator: Locator?)` ; hors contrat : `object SearchMatchDecoration { const val GROUP = "verso-search"; fun decorations(locator: Locator, theme: AppTheme): List<Decoration>; fun templates(): HtmlDecorationTemplates; fun element(decoration: Decoration): String }`, `FragmentReaderController.bindSearchMatch(show: suspend (Locator?) -> Unit)`, `ReaderViewModel.openSearchResult(hit: SearchHit)`.
- Règles : toucher un résultat ferme la recherche et la barre, fait un saut explicite (`jumpTo` : carte « Revenir » si la cible est à plus d'un écran de la lecture, progression inchangée), puis marque le mot. La marque est effacée au premier geste suivant (`gestures`), à un autre saut (sommaire, journal, « Revenir », lien) ou à la sortie du lecteur.

- [ ] **Step 1 : test de la décoration**

```kotlin
package com.maximebier.verso.readium

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoColors
import com.maximebier.verso.ui.theme.VersoPalette
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

// Robolectric : `Decoration` est Parcelable (classes Android).
@RunWith(AndroidJUnit4::class)
class SearchMatchDecorationTest {

    private val locator = Locator(
        href = Url("c1.xhtml")!!,
        mediaType = MediaType.XHTML,
        text = Locator.Text(before = "petite ", highlight = "rivière", after = " qui"),
    )

    @Test
    fun oneHighlightInTheSearchGroupWithThemeColors() {
        val decoration = SearchMatchDecoration.decorations(locator, AppTheme.SEPIA).single()
        assertThat(decoration.locator).isEqualTo(locator)
        val style = decoration.style as Decoration.Style.Highlight
        assertThat(style.tint).isEqualTo(VersoPalette.Sepia.highlight.toArgb())
        val html = SearchMatchDecoration.element(decoration)
        assertThat(html).contains("background-color: #E9C98C")
        assertThat(html).contains("border-bottom: 2px solid #2E2419")
        assertThat(html).contains("mix-blend-mode: darken")
    }

    @Test
    fun darkThemesLightenInsteadOfDarken() {
        val html = SearchMatchDecoration.element(SearchMatchDecoration.decorations(locator, AppTheme.BLACK).single())
        assertThat(html).contains("mix-blend-mode: lighten")
        assertThat(html).contains("background-color: #4A3A1C")
    }

    /**
     * Le calque est opaque et fondu en « darken » (clair) ou « lighten » (sombre) : le fond sous le mot devient
     * exactement `highlight` et le texte garde sa couleur, seulement si `highlight` est, canal par canal, entre le
     * fond et le texte. Garde-fou si tokens.json change.
     */
    @Test
    fun highlightSitsBetweenBackgroundAndTextInEveryTheme() {
        AppTheme.entries.forEach { theme ->
            val c: VersoColors = SearchMatchDecoration.palette(theme)
            listOf(Color::red, Color::green, Color::blue).forEach { channel ->
                val bg = channel(c.background); val hl = channel(c.highlight); val tx = channel(c.text)
                if (theme.isDark) {
                    assertThat(hl).isAtLeast(bg); assertThat(hl).isAtMost(tx)
                } else {
                    assertThat(hl).isAtMost(bg); assertThat(hl).isAtLeast(tx)
                }
            }
        }
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.readium.SearchMatchDecorationTest'`
Expected: FAIL (compilation : `SearchMatchDecoration` inconnu).

- [ ] **Step 2 : implémentation de la décoration**

```kotlin
package com.maximebier.verso.readium

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.ui.theme.VersoColors
import com.maximebier.verso.ui.theme.VersoPalette
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.shared.publication.Locator

/**
 * Mot trouvé par la recherche, marqué dans le texte par un fond et un soulignement (jamais la couleur seule).
 * Readium dessine les décorations par-dessus le texte : un calque opaque `highlight`, fondu en `darken` (thèmes
 * clairs) ou `lighten` (thèmes sombres), donne exactement le fond `highlight` sous un texte inchangé (7:1 gardé).
 */
object SearchMatchDecoration {
    const val GROUP = "verso-search"
    private const val ID = "verso-search-match"
    private const val EXTRA_BACKGROUND = "background"
    private const val EXTRA_LINE = "line"
    private const val EXTRA_BLEND = "blend"
    private const val CSS_CLASS = "verso-search-match"

    fun palette(theme: AppTheme): VersoColors = when (theme) {
        AppTheme.LIGHT -> VersoPalette.Light
        AppTheme.SEPIA -> VersoPalette.Sepia
        AppTheme.DARK -> VersoPalette.Dark
        AppTheme.BLACK -> VersoPalette.Black
    }

    fun decorations(locator: Locator, theme: AppTheme): List<Decoration> {
        val colors = palette(theme)
        return listOf(
            Decoration(
                id = ID,
                locator = locator,
                style = Decoration.Style.Highlight(tint = colors.highlight.toArgb()),
                extras = mapOf(
                    EXTRA_BACKGROUND to colors.highlight.css(),
                    EXTRA_LINE to colors.text.css(),
                    EXTRA_BLEND to if (theme.isDark) "lighten" else "darken",
                ),
            ),
        )
    }

    /** Gabarit de `Decoration.Style.Highlight` (seul style utilisé par Verso) : une boîte par ligne du mot. */
    fun templates(): HtmlDecorationTemplates = HtmlDecorationTemplates {
        set(
            Decoration.Style.Highlight::class,
            HtmlDecorationTemplate(
                layout = HtmlDecorationTemplate.Layout.BOXES,
                width = HtmlDecorationTemplate.Width.WRAP,
                element = ::element,
                stylesheet = ".$CSS_CLASS { box-sizing: border-box; pointer-events: none; }",
            ),
        )
    }

    fun element(decoration: Decoration): String {
        val background = decoration.extras[EXTRA_BACKGROUND] as? String ?: "transparent"
        val line = decoration.extras[EXTRA_LINE] as? String ?: "currentColor"
        val blend = decoration.extras[EXTRA_BLEND] as? String ?: "normal"
        return "<div class=\"$CSS_CLASS\" style=\"background-color: $background; " +
            "border-bottom: 2px solid $line; mix-blend-mode: $blend;\"></div>"
    }

    private fun Color.css(): String = "#%06X".format(toArgb() and 0xFFFFFF)
}
```

Dans `VersoReadingPreferences.applyVerso(theme)`, ajouter la ligne :

```kotlin
        decorationTemplates = SearchMatchDecoration.templates()
```

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.readium.SearchMatchDecorationTest' --tests 'com.maximebier.verso.readium.VersoReadingPreferencesTest'`
Expected: PASS.

- [ ] **Step 3 : contrôleur — test**

Dans `FragmentReaderControllerTest`, sur le modèle des tests existants (même construction du contrôleur, `bind(...)` déjà appelé par l'aide du fichier) :

```kotlin
    @Test
    fun showSearchMatchGoesToTheBoundDecorator() = runTest {
        val controller = controller()          // aide existante du fichier
        val shown = mutableListOf<Locator?>()
        controller.bindSearchMatch { shown += it }
        val target = testLocator(chapter = 3, progression = 0.2, total = 0.6)

        controller.showSearchMatch(target)
        controller.showSearchMatch(null)

        assertThat(shown).containsExactly(target, null).inOrder()
    }

    @Test
    fun showSearchMatchWithoutNavigatorDoesNothing() = runTest {
        controller().showSearchMatch(testLocator())   // aucune exception, rien à marquer
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.reader.FragmentReaderControllerTest'`
Expected: FAIL (compilation).

- [ ] **Step 4 : contrôleur — implémentation**

`ReaderController.kt`, dans l'interface :

```kotlin
    /** Marque le mot trouvé par la recherche à [locator] (fond et soulignement) ; null efface la marque. */
    suspend fun showSearchMatch(locator: Locator?)
```

`FragmentReaderController.kt` :

```kotlin
    private var searchMatch: (suspend (Locator?) -> Unit)? = null

    /** Branche la marque de recherche sur le navigateur (décorations Readium), une fois celui-ci créé. */
    fun bindSearchMatch(show: suspend (Locator?) -> Unit) {
        searchMatch = show
    }

    override suspend fun showSearchMatch(locator: Locator?) {
        searchMatch?.invoke(locator)
    }
```

`FakeReaderController.kt` :

```kotlin
    /** Marques demandées par `showSearchMatch`, dans l’ordre (null = effacement). */
    val searchMatches = mutableListOf<Locator?>()

    override suspend fun showSearchMatch(locator: Locator?) {
        searchMatches += locator
    }
```

`ReaderSurface.kt`, dans `LaunchedEffect(navigator) { … }`, juste après `controller.bind(...)` (`currentTheme` = `rememberUpdatedState(theme)` du paramètre `theme: AppTheme` de la surface, posé en 10.1 ; le déclarer à côté de `currentOnReady` s'il n'existe pas) :

```kotlin
        controller.bindSearchMatch { locator ->
            val decorations = locator?.let { SearchMatchDecoration.decorations(it, currentTheme) } ?: emptyList()
            nav.applyDecorations(decorations, SearchMatchDecoration.GROUP)
        }
```

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.reader.*'`
Expected: PASS.

- [ ] **Step 5 : ViewModel — test**

Dans `ReaderViewModelTest` :

```kotlin
    @Test
    fun searchResultIsAJumpThatMarksTheWordUntilTheNextGesture() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val effects = mutableListOf<TrackerEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.readingEffects.collect { effects += it } }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()
        viewModel.showSearch()
        val target = testLocator(chapter = 1, progression = 0.2, total = 0.10)
            .copy(text = Locator.Text(before = "petite ", highlight = "rivière", after = " qui"))
        val hit = SearchHit(target, chapter = "I", before = "petite ", match = "rivière", after = " qui", progression = 0.10)

        viewModel.openSearchResult(hit)
        runCurrent()

        assertThat(viewModel.uiState.value.searchVisible).isFalse()
        assertThat(fake.goCalls.last().href).isEqualTo(target.href)
        assertThat(fake.goCalls.last().text.highlight).isEqualTo("rivière")
        assertThat(fake.searchMatches.last()).isEqualTo(fake.goCalls.last())
        // Saut explicite : carte « Revenir », lecture inchangée.
        val withCard = viewModel.uiState.first { it.returnCard != null }
        assertThat(withCard.readingPercent).isEqualTo(30)
        assertThat(effects).isEmpty()

        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false))
        runCurrent()
        assertThat(fake.searchMatches.last()).isNull()
        store.clear()
    }

    @Test
    fun anotherJumpClearsTheSearchMark() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.5, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()
        val target = testLocator(chapter = 1, progression = 0.2, total = 0.10)
        viewModel.openSearchResult(SearchHit(target, "I", "", "rivière", "", 0.10))
        runCurrent()

        viewModel.goBack()
        runCurrent()

        assertThat(fake.searchMatches.last()).isNull()
        store.clear()
    }
```

(imports : `com.maximebier.verso.readium.SearchHit`.)

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.ReaderViewModelTest'`
Expected: FAIL (compilation : `openSearchResult` inconnu).

- [ ] **Step 6 : ViewModel — implémentation**

```kotlin
    /** Mot marqué dans le texte par [openSearchResult] ; effacé au premier geste ou au prochain saut. */
    private var searchMatchShown = false

    /**
     * Résultat de recherche touché : recherche et barre fermées, saut explicite ([jumpTo] : carte « Revenir »,
     * position de lecture inchangée), puis le mot est marqué dans le texte.
     */
    fun openSearchResult(hit: SearchHit) {
        _uiState.update { it.copy(searchVisible = false, barsVisible = false) }
        sessionCoordinator?.onInteraction()
        jumpTo(hit.locator)
        val readerController = controller ?: return
        val target = withTotalProgression(hit.locator)
        searchMatchShown = true
        viewModelScope.launch { readerController.showSearchMatch(target) }
    }

    private fun clearSearchMatch() {
        if (!searchMatchShown) return
        searchMatchShown = false
        val readerController = controller ?: return
        viewModelScope.launch { readerController.showSearchMatch(null) }
    }
```

Et :
- dans `jump(locator, calibrateEntry)`, première ligne : `clearSearchMatch()` (tout saut efface l'ancienne marque ; `openSearchResult` pose la sienne après `jumpTo`) ;
- dans `goBack()` et `onInternalLinkFollowed(url)`, première ligne : `clearSearchMatch()` ;
- dans `onReaderReady`, remplacer `launch { readerController.gestures.collect { cancelCalibration() } }` par `launch { readerController.gestures.collect { cancelCalibration(); clearSearchMatch() } }` ;
- dans `onReaderGone()` : `searchMatchShown = false` (la nouvelle surface n'a pas la décoration).

Dans `ReaderScreen`, `onResultClick = viewModel::openSearchResult`.

Vérifier que `fake.goCalls.last()` et la marque sont le même locator (le saut reçoit la progression totale par `withTotalProgression`, la marque aussi).

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.reader.*'`
Expected: PASS.

- [ ] **Step 7 : vérification complète**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL, aucun avertissement lint.

```bash
git add -A app/src core/src
git commit -m "Étape 15 (partie) : saut vers un résultat et mot marqué"
```

### Fin de l'étape 15 (orchestrateur)

- [ ] **Téléphone** (`./gradlew :app:installDebug`, puis `adb shell am start -n com.maximebier.verso/.MainActivity`), avec Madame Bovary :
  1. Barre affichée → « Rechercher » ; saisir « riviere » (sans accent) : le statut « Recherche dans tout le livre… N résultats pour l’instant » grandit, la barre de 3 dp avance, les résultats sont groupés sous « Deuxième partie, chapitre I » etc., le mot est sur fond, gras et souligné. Capture comparée à `2.06-recherche-dans-le-livre.png`. À la fin : « N résultats dans le livre ». « xylophone » : « Aucun résultat dans le livre ».
  2. Toucher le premier résultat : la recherche se ferme, le passage s'affiche avec « rivière » sur fond et souligné, texte lisible ; la carte « Revenir » apparaît ; « Revenir » ramène à l'écran d'avant en un tap, la marque disparaît. Captures dans les thèmes Clair, Sépia, Sombre, Noir (réglage de l'app, pas du système) : le mot marqué doit être lisible (fond exactement `highlight`, texte inchangé). Si la marque masque le texte ou n'a pas le bon fond sur le téléphone, s'arrêter et le signaler à Maxime avec les captures (ne pas passer à une couleur translucide, qui ferait tomber le contraste sous 7:1).
  3. Rouvrir « Rechercher » : la requête et les résultats sont toujours là. Effacer (✕) : champ vide, liste vide. Retour système : la recherche se ferme.
  4. Mot très fréquent (« de ») : l'interface reste fluide pendant que la liste s'allonge ; fermer la recherche pendant qu'elle tourne puis rouvrir : pas de plantage.
- [ ] Cocher les critères V2 6 (barre complète : Sommaire, Journal, Rechercher, Réglages, chacun avec son texte), 17 et 18 dans `docs/SPEC.md` et `docs/acceptance-v2.md` (preuves : noms des tests, captures dans `build/acceptance/`).
- [ ] `docs/SPEC.md`, « Historique » : ligne « 2026-MM-JJ (étape 15) : recherche par le service de Readium (casse et accents ignorés, deux caractères au moins) ; statuts de fin « N résultats dans le livre » et « Aucun résultat dans le livre » (absents des maquettes) ; le mot trouvé est marqué dans le texte jusqu'au geste suivant ; la recherche rouverte garde sa requête. »
- [ ] Regrouper les commits de l'étape : `git reset --soft <commit précédant l'étape 15>` puis `git commit -m "Étape 15 : recherche plein texte"` (seulement si rien de l'étape n'a été poussé), `git push`.

---

## Étape 16 — Passe d'acceptation V2 et README

Critères V2 de l'étape : tous, plus 19 (texte à 200 % et 48 dp / TalkBack sur les nouveaux écrans) et 20 (toujours aucune permission).

### Task 16.1 : accessibilité automatisée des écrans V2

**Files:**
- Test (lecture seule) : `app/src/test/kotlin/com/maximebier/verso/screenshots/AccessibilityTreeTest.kt` (étendu en 11.4)
- Modify: `app/src/test/kotlin/com/maximebier/verso/NoPermissionsTest.kt` (lecture seule si déjà conforme)

**Interfaces:**
- Consumes: `V1ScreenCatalog.fixtures`, `V2ScreenCatalog.fixtures` (créé en 10.2, complété aux étapes 11 à 15), `AccessibilityChecks.violations(rule)` (V1 : cibles ≥ 48 dp, intitulé des boutons à icône seule, texte non coupé).

- [ ] **Step 1 : vérifier que le test couvre les écrans V2**

`AccessibilityTreeTest` est étendu aux écrans V2 depuis la tâche 11.4 (`(V1ScreenCatalog.fixtures + V2ScreenCatalog.fixtures.map { it.screen }).flatMap`) : ne pas le modifier. Vérifier seulement que chaque écran V2 est au catalogue :

```bash
grep -o 'ScreenFixture("[^"]*"' app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt
```

Expected : `2.01-barre-de-lecture`, `2.02-reglages-de-lecture`, `2.03-2.04-*`, `2.05-mode-pages`, `2.06-recherche-dans-le-livre`, `2.07-bibliotheque-avec-etats`, `2.07b-trier-et-afficher`, `2.08-details-du-livre`, `2.09-parametres`. Un écran absent s'ajoute au catalogue (`V2ScreenFixture(ScreenFixture("<id>") { … })`), jamais ailleurs.

- [ ] **Step 2 : lancer**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.screenshots.AccessibilityTreeTest'`
Expected: PASS pour toutes les fixtures V1 et V2, en clair et à 200 %. Une violation se corrige dans l'écran concerné (jamais en retirant la fixture), avec un test ciblé dans le test de l'écran.

- [ ] **Step 3 : permissions**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.NoPermissionsTest' && bash scripts/check-no-internet.sh`
Expected: PASS ; `check-no-internet.sh` sans erreur (debug et release).

- [ ] **Step 4 : commit**

```bash
git add app/src/test
git commit -m "Étape 16 (partie) : accessibilité des écrans V2"
```

### Task 16.2 : checklist `docs/acceptance-v2.md` et script du téléphone

**Files:**
- Modify: `docs/acceptance-v2.md` (créé à l'étape 9, complété à chaque étape)
- Modify: `scripts/acceptance-phone.sh` (section V2)

- [ ] **Step 1 : compléter la checklist**

`docs/acceptance-v2.md` suit `docs/acceptance-v1.md` : en-tête (colonnes **Auto** / **Claude** / **Maxime**, téléphone en adb Wi-Fi (`ANDROID_SERIAL=192.168.1.10:5555`), fichiers de `app/src/test/resources/epub/real/`), un tableau des 20 critères V2 dans l'ordre de `docs/SPEC.md`, puis une procédure numérotée par critère. Pour chaque ligne :
- **Auto** : les tests réels qui couvrent le critère, relevés par `grep -rn "fun " core/src/test app/src/test | grep -i "<mot-clé>"` (noms exacts de classe et de méthode, et la tâche qui les a créés) ; au minimum : 1 → `VersoDatabaseMigrationTest`, `SettingsRepositoryTest` ; 3 → tests `ReaderViewModelTest` de réglage changé en lecture ; 8 à 10 → tests de `pageInChapter` et de tours de page dans `ReadingPositionTrackerTest` ; 11 à 13 → `LibraryRules` / bibliothèque ; 14 à 15 → `ReadingStatsTest` et fiche ; 17 → `BookSearchTest`, `BookSearchModelTest`, `SearchScreenTest` ; 18 → `ReaderViewModelTest.searchResultIsAJumpThatMarksTheWordUntilTheNextGesture`, `SearchMatchDecorationTest` ; 19 → `AccessibilityTreeTest[2.*]` ; 20 → `NoPermissionsTest`, `scripts/check-no-internet.sh`.
- **Claude** : la procédure faite sur le téléphone sans toucher un réglage système, avec le dossier de captures et le résultat.
- **Maxime** : ce qui exige un réglage système (texte Android à 200 % pour 19, thème du téléphone pour « Automatique » du critère 4, TalkBack pour 19, redémarrage pour 2 et 12 et 13) ; procédure pas à pas, et « remettre le réglage habituel » à la fin.
- **Résultat** : ✅ seulement quand toutes les colonnes de la ligne sont faites et conformes ; sinon ☐ avec l'anomalie décrite (lettre, comme en V1).

Procédures Claude à écrire au minimum (commandes adb exactes, comme en V1) :
- 7 (feuille « Aa ») : `tap_center`, `tap_on "Réglages"`, capture ; glisser la feuille (`input swipe 540 1700 540 900 400`), capture ; comparer à 2.02.
- 8 à 10 (mode pages) : feuille « Aa » › Défilement › « Pages » ; `input swipe 900 1200 150 1200 200` (page suivante) et `input tap 1000 1200` (tap à droite) ; capture du pied de page (2.05) ; huit swipes rapides en moins de 5 s puis `wait_for "Revenir"` ; « Revenir » ; capture.
- 17 et 18 (recherche) : `tap_on "Rechercher"`, `input text riviere`, `pause 3`, capture ; tap sur le premier résultat (`tap_on` sur le texte du premier extrait relevé par `ui_dump`), capture, `wait_for "Revenir"`, `tap_on "Revenir"`, capture.

- [ ] **Step 2 : section V2 du script**

Dans `scripts/acceptance-phone.sh`, la série du téléphone vient désormais de `ANDROID_SERIAL` seule (téléphone en adb Wi-Fi, plus de série USB par défaut) : remplacer la ligne `SERIAL="${ANDROID_SERIAL:-29081JEGR09520}"` par

```bash
if [ -z "${ANDROID_SERIAL:-}" ]; then
  echo "ANDROID_SERIAL absent : export ANDROID_SERIAL=192.168.1.10:5555 (adb Wi-Fi ; si le téléphone ne répond pas : adb connect 192.168.1.10:5555)" >&2
  exit 2
fi
SERIAL="$ANDROID_SERIAL"
```

(`--apk-only` n’interroge pas le téléphone : placer ce contrôle après le traitement de `--apk-only` si le script le lit avant la ligne 17.) PuisDans `scripts/acceptance-phone.sh` : l'en-tête dit « Passe d'acceptation V1 et V2 » ; ajouter, après la section 5 et avant « 6. Bibliothèque… », dans le même bloc `else` (livre ouvert) :

```bash
  echo "== 5b. V2 : barre, feuille « Aa », recherche (critères 6, 7, 17, 18)"
  tap_center
  shot "20-barre-v2"
  tap_on "Réglages" || true
  shot "21-feuille-aa"
  back
  if ! on_screen "Rechercher"; then tap_center; fi
  tap_on "Rechercher" || true
  adb_ shell input text "riviere"
  pause 4
  shot "22-recherche"
  first_result="$(ui_dump | sed -nE 's/.*text="([^"]*[Rr]ivi[eè]re[^"]*)".*/\1/p' | head -n 1)"
  if [ -n "$first_result" ]; then
    tap_on "$first_result" || true
    pause 2
    shot "23-resultat-marque"
    wait_for "Revenir" 10 || true
    tap_on "Revenir" || true
    shot "24-apres-revenir"
  else
    echo "  (aucun résultat lisible dans la hiérarchie : étape 23 ignorée)"
    back
  fi
```

(`tap_on`, `on_screen`, `ui_dump`, `wait_for`, `shot`, `back`, `pause` sont les fonctions V1 du script.)

Run: `bash -n scripts/acceptance-phone.sh && bash scripts/acceptance-phone.sh --apk-only`
Expected: syntaxe valide ; APK et permissions conformes.

Run (téléphone joignable en adb Wi-Fi) : `ANDROID_SERIAL=192.168.1.10:5555 bash scripts/acceptance-phone.sh`
Expected: captures `20` à `24` dans `build/acceptance/<horodatage>/`, comparées à 2.01, 2.02 et 2.06 ; résultats reportés dans `docs/acceptance-v2.md`.

- [ ] **Step 3 : commit**

```bash
git add docs/acceptance-v2.md scripts/acceptance-phone.sh
git commit -m "Étape 16 (partie) : checklist d'acceptation V2 et script du téléphone"
```

### Task 16.3 : README de portfolio et spec

**Files:**
- Modify: `README.md`
- Create: `docs/readme/reglages-de-lecture.png`, `docs/readme/mode-pages.png`, `docs/readme/recherche.png` (captures du téléphone)
- Modify: `docs/readme/bibliotheque.png`, `docs/readme/lecture.png` (recapturées en V2 : Literata, pastilles, barre à quatre outils)
- Modify: `docs/SPEC.md`

- [ ] **Step 1 : captures**

Sur le téléphone, thème de l'app Clair (réglage de Verso, pas du système), Madame Bovary : bibliothèque avec la carte « Reprendre » et les pastilles (2.07), lecture barre affichée (2.01), feuille « Aa » (2.02), mode pages (2.05), recherche « rivière » (2.06). `adb exec-out screencap -p > docs/readme/<nom>.png`, puis réduire à 780 px de large si l'image fait plus (même taille que les captures V1 : `file docs/readme/journal.png` pour la vérifier). Aucune donnée personnelle à l'écran (livres du domaine public uniquement).

- [ ] **Step 2 : README**

- Galerie : six images, dans cet ordre : `bibliotheque.png` (« Bibliothèque avec états et carte Reprendre »), `lecture.png` (« Lecture avec la barre affichée »), `reglages-de-lecture.png` (« Réglages de lecture en direct »), `mode-pages.png` (« Mode pages »), `recherche.png` (« Recherche dans le livre »), `carte-revenir.png` (« Carte Revenir après un scroll accidentel »), en `width="200"`.
- Section « Ce que fait la V1 » renommée « Ce que fait Verso » ; la ligne Lecture devient : « **Lecture confortable**, réglée d’après la recherche : Literata par défaut (Atkinson Hyperlegible Next ou la police du téléphone au choix, dans toute l’app), taille, interligne et marges réglables en direct, aligné à gauche sans césure, thèmes Clair, Sépia, Sombre et Noir, en scroll continu ou en pages, mémorisé par livre. »
- Ajouter après la ligne « Journal de lecture » : « **États et statistiques** : livres à lire, en cours ou terminés, filtres et tri ; temps de lecture, vitesse moyenne et temps restant à votre rythme. » et « **Recherche plein texte** : résultats au fil de la recherche, groupés par chapitre, mot marqué par un fond, du gras et un soulignement, carte « Revenir » après le saut. »
- La ligne « Journal de lecture » et les lignes Import, Progression, Accessible, Privé restent.
- Section Architecture : ajouter à `:core` « réglages de lecture, état d’un livre, statistiques, extraits de recherche ».
- Le README se lit en une minute : pas plus de 70 lignes.

Run: `wc -l README.md && grep -o 'docs/readme/[a-z-]*\.png' README.md | xargs -I{} test -f {} && echo images-ok`
Expected: 70 lignes au plus ; `images-ok`.

- [ ] **Step 3 : spec**

Dans `docs/SPEC.md` : cocher chaque critère V2 dont la ligne de `docs/acceptance-v2.md` est ✅ (ne cocher que ceux-là) ; ajouter à « Historique » : « 2026-MM-JJ (étape 16) : passe d'acceptation V2 ; README mis à jour. » avec les écarts acceptés relevés pendant la passe (un par écart). Mettre à jour `CLAUDE.md` seulement si tous les critères V2 sont cochés : « **V2 terminée** (date) : ne pas coder la V3 ou la V4 sans décision explicite. »

- [ ] **Step 4 : vérification complète, commit de l'étape**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add README.md docs/readme docs/SPEC.md docs/acceptance-v2.md CLAUDE.md
git commit -m "Étape 16 (partie) : README et spec"
```

### Fin de l'étape 16 (orchestrateur)

- [ ] Regrouper les commits de l'étape : `git reset --soft <commit précédant l'étape 16>` puis `git commit -m "Étape 16 : passe d'acceptation V2 et README"` (seulement si rien de l'étape n'a été poussé), `git push`, `./gradlew :app:installDebug`, lancement, capture.
- [ ] Rapport à Maxime : critères cochés, critères en attente de sa vérification (colonne Maxime, avec le numéro de procédure), écarts acceptés.
