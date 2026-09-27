# Verso V2 — partie C : états des livres (étape 13), statistiques et Paramètres V2 (étape 14)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Plan principal : `docs/superpowers/plans/2026-09-27-verso-v2.md` (Global Constraints, Review Focus, **Contrat d'architecture**). Design : `docs/superpowers/specs/2026-09-27-verso-v2-design.md`. Maquettes : `docs/design/screens/2.07-bibliotheque-avec-etats.png`, `2.07b-trier-et-afficher.png`, `2.08-details-du-livre.png`, `2.09-parametres.png` et leurs HTML dans `docs/design/html/`.

**Point de départ supposé (étapes 9 à 12 faites selon le contrat)** :

- `BookEntity.stateOverride: String?` et `BookEntity.scrollMode: String?` existent (migration 1 → 2) ; `BookDao.setStateOverride(id, stateOverride)` existe.
- `VersoTheme.typography.x` remplace `VersoTypography.x` partout ; `VersoTheme(theme: AppTheme = <thème du téléphone>, font: ReadingFont = ReadingFont.LITERATA, content)` (tâche 10.1) : `VersoTheme { … }` sans argument reste utilisable dans les tests (clair sous Robolectric) ; les tests de cette partie écrivent `VersoTheme(theme = AppTheme.LIGHT) { … }` quand le thème compte.
- `ReadingSettings`, `ReadingFont`, `ScrollMode`, `ReadingSettingsLimits` (`:core`), `SettingsRepository.readingSettings` / `updateReadingSettings`, `fontFamilyFor(font)` (`ui/theme/Fonts.kt`), `ThemeMode` à cinq valeurs (AUTO, LIGHT, SEPIA, DARK, BLACK).
- `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt` (objet `V2ScreenCatalog` avec `val fixtures: List<V2ScreenFixture>`, `V2ScreenFixture(screen: ScreenFixture, themes: List<AppTheme> = AppTheme.entries)`) et `V2ScreenshotTest.kt` (captures `build/outputs/roborazzi/v2/<id>-<clair|sepia|sombre|noir>.png`) existent (tâche 10.2) ; `AccessibilityTreeTest` couvre tout le catalogue V2 (tâche 11.4). Les entrées ajoutées ici sont des `V2ScreenFixture(ScreenFixture(...))`.
- `docs/acceptance-v2.md` existe (étape 9).

Commandes (Git Bash, depuis la racine du dépôt) :

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_SERIAL=192.168.1.10:5555   # téléphone en adb Wi-Fi ; si « device not found » : adb connect 192.168.1.10:5555
./gradlew :core:test                                   # :core seul
./gradlew :app:testDebugUnitTest --tests '<classe>'    # une classe de :app
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug   # fin de tâche
```

Espaces typographiques dans `strings.xml` : **U+202F** (espace fine insécable) avant `: ; ! ?` et `%`, et à l'intérieur des guillemets « … » ; **U+00A0** dans les durées. Dans les blocs XML ci-dessous, ces caractères sont écrits tels quels ; les recopier sans les remplacer par des espaces ordinaires (`StringsTest` le vérifie).

---

## Étape 13 : états des livres, filtres, « Trier et afficher »

Critères V2 couverts : 11, 12, 13.

### Task 13.1 : `BookStatus.TO_READ` et choix manuel (`:core`)

**Files:**
- Modify: `core/src/main/kotlin/com/maximebier/verso/core/model/LibraryRules.kt`
- Modify: `core/src/test/kotlin/com/maximebier/verso/core/model/LibraryRulesTest.kt`
- Modify (renommage mécanique) : `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryComponents.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/library/LibrarySamples.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/library/LibraryViewModelTest.kt`

**Interfaces:**
- Consumes : rien de nouveau.
- Produces : `enum class BookStatus { TO_READ, IN_PROGRESS, FINISHED }`, `LibraryRules.status(hasReadingLocator: Boolean, progression: Double, override: BookStatus? = null): BookStatus` (contrat).

- [ ] **Step 1 : écrire les tests qui échouent**

Dans `LibraryRulesTest.kt`, remplacer `neverOpenedBookIsNew` et ajouter deux tests :

```kotlin
    @Test
    fun neverOpenedBookIsToRead() {
        assertThat(LibraryRules.status(hasReadingLocator = false, progression = 0.0)).isEqualTo(BookStatus.TO_READ)
        assertThat(LibraryRules.status(hasReadingLocator = false, progression = 0.5)).isEqualTo(BookStatus.TO_READ)
    }

    @Test
    fun manualChoiceWinsOverTheComputedState() {
        assertThat(LibraryRules.status(false, 0.0, override = BookStatus.FINISHED)).isEqualTo(BookStatus.FINISHED)
        assertThat(LibraryRules.status(true, 0.995, override = BookStatus.TO_READ)).isEqualTo(BookStatus.TO_READ)
        assertThat(LibraryRules.status(true, 0.995, override = BookStatus.IN_PROGRESS)).isEqualTo(BookStatus.IN_PROGRESS)
    }

    @Test
    fun noManualChoiceKeepsTheComputedState() {
        assertThat(LibraryRules.status(true, 0.31, override = null)).isEqualTo(BookStatus.IN_PROGRESS)
        assertThat(LibraryRules.status(true, 0.99, override = null)).isEqualTo(BookStatus.FINISHED)
    }
```

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :core:test --tests 'com.maximebier.verso.core.model.LibraryRulesTest'`
Expected : échec de compilation (`Unresolved reference: TO_READ`, paramètre `override` inconnu).

- [ ] **Step 3 : implémenter**

Dans `LibraryRules.kt`, remplacer l'énumération et `status` :

```kotlin
/** État de lecture d'un livre : « À lire », barre + %, « Terminé ». */
enum class BookStatus { TO_READ, IN_PROGRESS, FINISHED }
```

```kotlin
    /**
     * Choix manuel de la fiche ([override]) s'il existe ; sinon jamais ouvert (pas de locator de lecture) → TO_READ,
     * progression ≥ 0,99 → FINISHED, sinon IN_PROGRESS. Le choix manuel tient jusqu'au choix manuel suivant.
     */
    fun status(hasReadingLocator: Boolean, progression: Double, override: BookStatus? = null): BookStatus = override ?: when {
        !hasReadingLocator -> BookStatus.TO_READ
        progression >= FINISHED_PROGRESSION -> BookStatus.FINISHED
        else -> BookStatus.IN_PROGRESS
    }
```

Puis renommer les usages de `:app` :

```bash
grep -rl "BookStatus\.NEW" app/src core/src | xargs sed -i 's/BookStatus\.NEW/BookStatus.TO_READ/g'
grep -rn "BookStatus\.NEW\|NEW ->" app/src core/src   # attendu : aucune ligne
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :core:test :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.library.*'`
Expected : PASS (le libellé reste « Nouveau » jusqu'à la tâche 13.3).

- [ ] **Step 5 : commit**

```bash
git add core/src app/src
git commit -m "Étape 13 (partie) : état « à lire » et choix manuel dans LibraryRules"
```

### Task 13.2 : état d'un livre en base, `setStateOverride`

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/data/BookStatusMapping.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/BookRepository.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryViewModel.kt` (fonction `toLibraryBook`)
- Test: `app/src/test/kotlin/com/maximebier/verso/data/BookRepositoryTest.kt`

**Interfaces:**
- Consumes : `BookDao.setStateOverride(id: Long, stateOverride: String?)` (tâche 9.1), `LibraryRules.status(…, override)` (13.1).
- Produces : `fun BookEntity.status(): BookStatus` (hors contrat, `com.maximebier.verso.data`), `BookRepository.setStateOverride(bookId: Long, status: BookStatus?)` (contrat).

- [ ] **Step 1 : écrire les tests qui échouent**

Ajouter à `BookRepositoryTest.kt` (imports : `com.maximebier.verso.core.model.BookStatus`, `kotlinx.coroutines.flow.first` s'il manque) :

```kotlin
    @Test
    fun manualStateWinsAndSurvivesRereading() = runTest {
        val id = repository.insert(testBook(sha256 = "etat"))
        assertThat(repository.book(id)!!.status()).isEqualTo(BookStatus.TO_READ)

        repository.setStateOverride(id, BookStatus.FINISHED)
        assertThat(repository.book(id)!!.stateOverride).isEqualTo("FINISHED")
        assertThat(repository.book(id)!!.status()).isEqualTo(BookStatus.FINISHED)

        // Relire le livre (nouvelle position de lecture) ne change pas un choix manuel.
        repository.saveReadingPosition(id, """{"href":"ch1.xhtml","type":"application/xhtml+xml"}""", 0.3)
        assertThat(repository.book(id)!!.status()).isEqualTo(BookStatus.FINISHED)

        repository.setStateOverride(id, null)
        assertThat(repository.book(id)!!.status()).isEqualTo(BookStatus.IN_PROGRESS)
    }

    @Test
    fun unknownStoredStateFallsBackToTheComputedOne() = runTest {
        val id = repository.insert(testBook(sha256 = "inconnu"))
        db.bookDao().setStateOverride(id, "EN_PAUSE")
        assertThat(repository.book(id)!!.status()).isEqualTo(BookStatus.TO_READ)
    }
```

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.data.BookRepositoryTest'`
Expected : échec de compilation (`status()` et `setStateOverride` inconnus).

- [ ] **Step 3 : implémenter**

`app/src/main/kotlin/com/maximebier/verso/data/BookStatusMapping.kt` :

```kotlin
package com.maximebier.verso.data

import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.model.LibraryRules
import com.maximebier.verso.data.db.BookEntity

/** État affiché d'un livre : calculé, sauf choix manuel (`stateOverride`). Une valeur inconnue est ignorée. */
fun BookEntity.status(): BookStatus = LibraryRules.status(
    hasReadingLocator = readingLocatorJson != null,
    progression = progression,
    override = stateOverride?.let { stored -> BookStatus.entries.firstOrNull { it.name == stored } },
)
```

Dans `BookRepository.kt`, après `markOpened` (import `com.maximebier.verso.core.model.BookStatus`) :

```kotlin
    /** Choix manuel de la fiche ; null revient à l'état calculé. */
    suspend fun setStateOverride(bookId: Long, status: BookStatus?) = dao.setStateOverride(bookId, status?.name)
```

Dans `LibraryViewModel.kt`, fonction `toLibraryBook`, remplacer la ligne `status = LibraryRules.status(...)` par :

```kotlin
    status = entity.status(),
```

(import `com.maximebier.verso.data.status` ; retirer l'import `LibraryRules` s'il n'est plus utilisé.)

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.data.BookRepositoryTest' --tests 'com.maximebier.verso.ui.library.LibraryViewModelTest'`
Expected : PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 13 (partie) : état manuel enregistré avec le livre"
```

### Task 13.3 : filtres en pastilles et « À lire » dans la bibliothèque

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryFilter.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryComponents.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/library/LibraryViewModelTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/library/LibraryScreenTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/library/LibraryFilterTest.kt` (nouveau)

**Interfaces:**
- Consumes : `BookStatus` (13.1).
- Produces (hors contrat) : `enum class LibraryFilter { ALL, IN_PROGRESS, TO_READ, FINISHED; fun accepts(status: BookStatus): Boolean }` ; `LibraryUiState.filter: LibraryFilter`, `LibraryUiState.filteredBooks: List<LibraryBook>` ; `LibraryViewModel.onFilterChange(filter: LibraryFilter)` ; `LibraryActions.onFilterChange: (LibraryFilter) -> Unit` ; composable `LibraryFilterChips(selected, onSelect, modifier)`.

Décisions de cette tâche (à reporter dans `docs/SPEC.md`, « Historique », au commit de l'étape 13) : le filtre n'est pas enregistré (retour à « Tous » à la réouverture) ; le compteur « 7 livres » compte les livres du filtre choisi ; la carte « Reprendre » reste affichée quel que soit le filtre ; un filtre sans livre affiche « Aucun livre pour ce filtre. » (libellé absent des maquettes).

- [ ] **Step 1 : écrire les tests qui échouent**

`app/src/test/kotlin/com/maximebier/verso/ui/library/LibraryFilterTest.kt` :

```kotlin
package com.maximebier.verso.ui.library

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.model.BookStatus
import org.junit.Test

class LibraryFilterTest {
    @Test
    fun eachFilterAcceptsOnlyItsState() {
        BookStatus.entries.forEach { assertThat(LibraryFilter.ALL.accepts(it)).isTrue() }
        assertThat(BookStatus.entries.filter(LibraryFilter.IN_PROGRESS::accepts)).containsExactly(BookStatus.IN_PROGRESS)
        assertThat(BookStatus.entries.filter(LibraryFilter.TO_READ::accepts)).containsExactly(BookStatus.TO_READ)
        assertThat(BookStatus.entries.filter(LibraryFilter.FINISHED::accepts)).containsExactly(BookStatus.FINISHED)
    }

    @Test
    fun resumeBookIsNotRepeatedInTheFilteredList() {
        val state = LibrarySamples.list.copy(filter = LibraryFilter.IN_PROGRESS)
        assertThat(state.filteredBooks.map { it.title }).contains("Madame Bovary")
        assertThat(state.listedBooks.map { it.title }).doesNotContain("Madame Bovary")
        assertThat(state.listedBooks.map { it.status }.toSet()).containsExactly(BookStatus.IN_PROGRESS)
    }
}
```

Dans `LibraryViewModelTest.kt`, ajouter :

```kotlin
    @Test
    fun filterKeepsOnlyMatchingBooksAndIsNotPersisted() = runTest(dispatcher) {
        insert("Germinal")
        insert("Bel-Ami", locatorJson = LOCATOR_JSON, progression = 0.473)
        insert("Nana", locatorJson = LOCATOR_JSON, progression = 0.995)
        val vm = viewModel()
        vm.state.test {
            awaitUntil { it.books.size == 3 }
            vm.onFilterChange(LibraryFilter.FINISHED)
            val finished = awaitUntil { it.filter == LibraryFilter.FINISHED }
            assertThat(finished.filteredBooks.map { it.title }).containsExactly("Nana")
            vm.onFilterChange(LibraryFilter.TO_READ)
            assertThat(awaitUntil { it.filter == LibraryFilter.TO_READ }.filteredBooks.map { it.title }).containsExactly("Germinal")
        }
        viewModel().state.test {
            assertThat(awaitUntil { it.books.size == 3 }.filter).isEqualTo(LibraryFilter.ALL)
        }
    }
```

Dans `LibraryScreenTest.kt`, remplacer `statesAreWrittenNotOnlyColored` et ajouter deux tests :

```kotlin
    @Test
    fun statesAreWrittenNotOnlyColored() {
        show(LibrarySamples.list.copy(resume = null, books = LibrarySamples.books.drop(3)))
        compose.onAllNodesWithText(text(R.string.library_state_finished)).assertCountEquals(1)
        compose.onAllNodesWithText(text(R.string.library_state_to_read)).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.common_percent, 12)).assertIsDisplayed()
    }

    @Test
    fun filterChipsSelectAndReport() {
        var filter: LibraryFilter? = null
        show(LibrarySamples.list, LibraryActions(onFilterChange = { filter = it }))
        compose.onNodeWithText(text(R.string.library_filter_all)).assertIsSelected()
        compose.onNodeWithText(text(R.string.library_filter_finished)).performScrollTo().performClick()
        assertThat(filter).isEqualTo(LibraryFilter.FINISHED)
    }

    @Test
    fun emptyFilterSaysSo() {
        show(LibrarySamples.list.copy(resume = null, filter = LibraryFilter.FINISHED, books = LibrarySamples.books.take(2)))
        compose.onNodeWithText(text(R.string.library_filter_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.library_book_count, 0, 0)).assertIsDisplayed()
    }
```

(imports : `androidx.compose.ui.test.assertIsSelected`, `androidx.compose.ui.test.performScrollTo`.)

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.library.*'`
Expected : échec de compilation (`LibraryFilter`, `library_state_to_read`, `library_filter_*` inconnus).

- [ ] **Step 3 : implémenter**

`LibraryFilter.kt` :

```kotlin
package com.maximebier.verso.ui.library

import com.maximebier.verso.core.model.BookStatus

/** Filtres « Tous / En cours / À lire / Terminés » (2.07), dans l'ordre des pastilles. Non enregistré. */
enum class LibraryFilter {
    ALL,
    IN_PROGRESS,
    TO_READ,
    FINISHED,
    ;

    fun accepts(status: BookStatus): Boolean = when (this) {
        ALL -> true
        IN_PROGRESS -> status == BookStatus.IN_PROGRESS
        TO_READ -> status == BookStatus.TO_READ
        FINISHED -> status == BookStatus.FINISHED
    }
}
```

Dans `LibraryViewModel.kt` :

1. `LibraryUiState` : ajouter `val filter: LibraryFilter = LibraryFilter.ALL,` après `viewMode`, et remplacer `listedBooks` :

```kotlin
    /** Livres du filtre choisi (compteur « 7 livres »). */
    val filteredBooks: List<LibraryBook>
        get() = books.filter { filter.accepts(it.status) }

    /** Livres de la liste : ceux du filtre, sans celui de la carte « Reprendre », déjà affiché au-dessus (1.02). */
    val listedBooks: List<LibraryBook>
        get() = resume?.let { r -> filteredBooks.filterNot { it.id == r.book.id } } ?: filteredBooks
```

2. `Transient` : ajouter `val filter: LibraryFilter = LibraryFilter.ALL,`.
3. Dans le `combine` de `state`, ajouter `filter = t.filter,` à la construction de `LibraryUiState`.
4. Après `onViewModeChange` :

```kotlin
    fun onFilterChange(filter: LibraryFilter) {
        transient.update { it.copy(filter = filter) }
    }
```

`strings.xml` : remplacer la ligne `library_state_new` (et son commentaire s'il parle de « Nouveau ») par :

```xml
    <string name="library_state_to_read">À lire</string>
```

et ajouter, après le bloc du tri :

```xml
    <!-- V2 · écran 2.07 : filtres par état -->
    <string name="library_filter_group">Filtrer par état</string>
    <string name="library_filter_all">Tous</string>
    <string name="library_filter_in_progress">En cours</string>
    <string name="library_filter_to_read">À lire</string>
    <string name="library_filter_finished">Terminés</string>
    <string name="library_filter_empty">Aucun livre pour ce filtre.</string>
```

`LibraryComponents.kt` :

1. Dans `BookStatusLine`, remplacer la branche `BookStatus.TO_READ` (ex-`NEW`) :

```kotlin
        BookStatus.TO_READ -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VersoIcons.Bookmark, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.library_state_to_read), style = VersoTheme.typography.captionSemiBold, color = colors.textSecondary)
        }
```

2. Ajouter à la fin du fichier (imports : `androidx.compose.foundation.horizontalScroll`, `androidx.compose.foundation.rememberScrollState`) :

```kotlin
/**
 * Pastilles de filtre (2.07) : 40 dp de haut dans une cible de 48 dp ; choisie = fond selection, gras et coche,
 * jamais la couleur seule ; les autres ont un contour. La rangée défile horizontalement (« Terminés » dépasse).
 */
@Composable
fun LibraryFilterChips(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(
        R.string.library_filter_all,
        R.string.library_filter_in_progress,
        R.string.library_filter_to_read,
        R.string.library_filter_finished,
    )
    val groupLabel = stringResource(R.string.library_filter_group)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = groupLabel }
            .selectableGroup()
            .padding(start = 20.dp, end = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryFilter.entries.forEachIndexed { index, filter ->
            FilterChip(label = stringResource(labels[index]), selected = filter == selected, onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = VersoTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = VersoDimens.controlMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val shape = VersoShapes.small
        val chip = if (selected) {
            Modifier.clip(shape).background(colors.selection).padding(start = 10.dp, end = 14.dp)
        } else {
            Modifier.clip(shape).border(1.dp, colors.outline, shape).padding(horizontal = 14.dp)
        }
        Row(
            modifier = Modifier.heightIn(min = 40.dp).then(chip),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(VersoIcons.Check, contentDescription = null, tint = colors.onSelection, modifier = Modifier.size(18.dp))
            }
            Text(
                text = label,
                style = if (selected) VersoTheme.typography.segmentSelected else VersoTheme.typography.segment,
                color = if (selected) colors.onSelection else colors.text,
            )
        }
    }
}
```

`LibraryScreen.kt` :

1. `LibraryActions` : ajouter `val onFilterChange: (LibraryFilter) -> Unit = {},` ; dans `LibraryScreen`, passer `onFilterChange = viewModel::onFilterChange,`.
2. `LibraryHeader` : le compteur devient `pluralStringResource(R.plurals.library_book_count, state.filteredBooks.size, state.filteredBooks.size)`.
3. `LibraryList` : remplacer l'élément `item(key = "sort") { SortSelector(...) }` par :

```kotlin
        item(key = "filters") {
            LibraryFilterChips(selected = state.filter, onSelect = actions.onFilterChange)
        }
        if (state.filteredBooks.isEmpty()) {
            item(key = "filter-empty") { FilterEmptyText(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) }
        }
```

4. `LibraryGrid` : même remplacement, avec `span = { GridItemSpan(maxLineSpan) }` sur les deux éléments, et `LibraryFilterChips(selected = state.filter, onSelect = actions.onFilterChange, modifier = Modifier.padding(start = 4.dp))`, `FilterEmptyText(Modifier.padding(start = 8.dp, top = 16.dp))`.
5. Le tri reste accessible par `SortSelector` jusqu'à la tâche 13.4 : le placer provisoirement **sous** les pastilles (même élément `item(key = "sort")` qu'avant, après `"filters"`), il disparaît en 13.4.
6. Ajouter :

```kotlin
@Composable
private fun FilterEmptyText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.library_filter_empty),
        style = VersoTheme.typography.body,
        color = VersoTheme.colors.textSecondary,
        modifier = modifier,
    )
}
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.library.*' --tests 'com.maximebier.verso.StringsTest'`
Expected : PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 13 (partie) : filtres par état et « À lire »"
```

### Task 13.4 : bouton « Récents ▾ » et feuille « Trier et afficher »

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/VersoBottomSheet.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/VersoIcons.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/library/SortSheet.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryScreen.kt`, `LibraryComponents.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/library/LibraryScreenTest.kt`

**Interfaces:**
- Consumes : `LibrarySort`, `LibraryViewMode`, `VersoSegmentedButton`, `VersoBottomSheet`.
- Produces (hors contrat) : paramètre `fitContent: Boolean = false` de `VersoBottomSheet` ; `VersoIcons.SortArrows`, `VersoIcons.ChevronDown` ; composables `SortButton(label, description, onClick, modifier)` et `SortAndDisplaySheet(sort, viewMode, onSortChange, onViewModeChange, onDismiss)`. `ViewModeToggle` et `SortSelector` sont supprimés.

- [ ] **Step 1 : écrire les tests qui échouent**

Dans `LibraryScreenTest.kt`, remplacer `sortAndViewModeCallbacks` :

```kotlin
    private val sortButtonLabel
        get() = text(R.string.library_sort_button_description, text(R.string.library_sort_state_recent), text(R.string.library_view_state_list))

    @Test
    fun sortButtonOpensTheSheetWithSortAndDisplay() {
        var sort: LibrarySort? = null
        var mode: LibraryViewMode? = null
        show(LibrarySamples.list, LibraryActions(onSortChange = { sort = it }, onViewModeChange = { mode = it }))
        compose.onNodeWithText(text(R.string.library_sort_sheet_title)).assertDoesNotExist()

        compose.onNodeWithContentDescription(sortButtonLabel).assertHasClickAction().performClick()
        compose.onNodeWithText(text(R.string.library_sort_sheet_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.library_sort_title)).performClick()
        assertThat(sort).isEqualTo(LibrarySort.TITLE)
        compose.onNodeWithText(text(R.string.library_view_grid)).performClick()
        assertThat(mode).isEqualTo(LibraryViewMode.GRID)

        compose.onNodeWithContentDescription(text(R.string.common_close)).performClick()
        compose.onNodeWithText(text(R.string.library_sort_sheet_title)).assertDoesNotExist()
    }

    @Test
    fun sortButtonShowsTheCurrentSort() {
        show(LibrarySamples.list.copy(sort = LibrarySort.AUTHOR, viewMode = LibraryViewMode.GRID))
        val label = text(R.string.library_sort_button_description, text(R.string.library_sort_state_author), text(R.string.library_view_state_grid))
        compose.onNodeWithContentDescription(label).assertIsDisplayed()
    }
```

(import `androidx.compose.ui.test.assertHasClickAction`.)

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.library.LibraryScreenTest'`
Expected : échec de compilation (`library_sort_button_description`, `library_sort_sheet_title` inconnus).

- [ ] **Step 3 : implémenter**

`strings.xml`, après le bloc des filtres :

```xml
    <!-- V2 · écran 2.07b : bouton « Récents ▾ » et feuille « Trier et afficher » -->
    <string name="library_sort_sheet_title">Trier et afficher</string>
    <!-- Intitulé TalkBack du bouton : %1$s = library_sort_state_*, %2$s = library_view_state_* -->
    <string name="library_sort_button_description">Trier et afficher, actuellement : %1$s, %2$s</string>
    <string name="library_sort_state_recent">récents</string>
    <string name="library_sort_state_title">titre</string>
    <string name="library_sort_state_author">auteur</string>
    <string name="library_view_state_list">en liste</string>
    <string name="library_view_state_grid">en grille</string>
```

(U+202F avant « : ».) `library_sort_group` (« Trier par »), `library_view_mode_group` (« Affichage »), `library_view_list`, `library_view_grid` et `library_sort_*` sont réutilisées.

`VersoIcons.kt`, après `ChevronRight` (tracés de la maquette 2.07) :

```kotlin
    val ChevronDown: ImageVector = strokeIcon("chevron-down", "m6 9 6 6 6-6")
    val SortArrows: ImageVector = strokeIcon("sort-arrows", "M7 4v16M4 7l3-3 3 3M17 20V4M14 17l3 3 3-3")
```

`VersoBottomSheet.kt` : ajouter le paramètre `fitContent: Boolean = false` après `modifier` (s'il existe déjà d'autres paramètres ajoutés par la partie B, l'ajouter à la suite, avec sa valeur par défaut), documenté « true : hauteur du contenu (feuille « Trier et afficher ») au lieu de l'écran − 88 dp ». Dans le corps :

```kotlin
        modifier = if (fitContent) modifier else modifier.padding(top = 88.dp),
```

et pour la colonne du contenu :

```kotlin
        Column(
            Modifier
                .then(if (fitContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
```

`SortSheet.kt` :

```kotlin
package com.maximebier.verso.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.data.LibrarySort
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSegmentedButton
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/** « Récents », « Titre », « Auteur » : libellé visible du bouton de tri. */
@Composable
fun sortLabel(sort: LibrarySort): String = stringResource(
    when (sort) {
        LibrarySort.RECENT -> R.string.library_sort_recent
        LibrarySort.TITLE -> R.string.library_sort_title
        LibrarySort.AUTHOR -> R.string.library_sort_author
    },
)

/** « Trier et afficher, actuellement : récents, en liste » (intitulé TalkBack, maquette 2.07). */
@Composable
fun sortButtonDescription(sort: LibrarySort, viewMode: LibraryViewMode): String = stringResource(
    R.string.library_sort_button_description,
    stringResource(
        when (sort) {
            LibrarySort.RECENT -> R.string.library_sort_state_recent
            LibrarySort.TITLE -> R.string.library_sort_state_title
            LibrarySort.AUTHOR -> R.string.library_sort_state_author
        },
    ),
    stringResource(if (viewMode == LibraryViewMode.LIST) R.string.library_view_state_list else R.string.library_view_state_grid),
)

/** Bouton « ⇅ Récents ▾ » (2.07) : 48 dp, texte 16 sp gras à l'encre ; TalkBack lit l'intitulé complet. */
@Composable
fun SortButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = VersoDimens.controlMin)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 14.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(VersoIcons.SortArrows, contentDescription = null, tint = colors.text, modifier = Modifier.size(VersoDimens.iconSmall))
        Text(
            text = label,
            style = VersoTheme.typography.bodyStrong,
            color = colors.text,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Icon(VersoIcons.ChevronDown, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
    }
}

/** Feuille « Trier et afficher » (2.07b) : chaque choix s'applique aussitôt, « Fermer » ou le voile la ferment. */
@Composable
fun SortAndDisplaySheet(
    sort: LibrarySort,
    viewMode: LibraryViewMode,
    onSortChange: (LibrarySort) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = VersoTheme.colors
    VersoBottomSheet(
        title = stringResource(R.string.library_sort_sheet_title),
        subtitle = null,
        onDismissRequest = onDismiss,
        fitContent = true,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val sortLabelText = stringResource(R.string.library_sort_group)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sortLabelText, style = VersoTheme.typography.captionBold, color = colors.textSecondary)
                val sorts = LibrarySort.entries
                VersoSegmentedButton(
                    options = sorts.map { sortLabel(it) },
                    selectedIndex = sorts.indexOf(sort),
                    onSelect = { index -> onSortChange(sorts[index]) },
                    groupLabel = sortLabelText,
                )
            }
            val displayLabel = stringResource(R.string.library_view_mode_group)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displayLabel, style = VersoTheme.typography.captionBold, color = colors.textSecondary)
                val modes = LibraryViewMode.entries
                VersoSegmentedButton(
                    options = listOf(stringResource(R.string.library_view_list), stringResource(R.string.library_view_grid)),
                    selectedIndex = modes.indexOf(viewMode),
                    onSelect = { index -> onViewModeChange(modes[index]) },
                    groupLabel = displayLabel,
                )
            }
        }
    }
}
```

`LibraryScreen.kt` :

1. Supprimer `SortSelector`, l'élément `item(key = "sort")` des deux listes, la constante `ToggleWidth` et les imports devenus inutiles (`VersoSegmentedButton`, `ViewModeToggle`).
2. Dans `LibraryContent`, avant le `Scaffold` :

```kotlin
    var sortSheetVisible by rememberSaveable { mutableStateOf(false) }
```

et après le bloc `state.pendingDelete?.let { … }` :

```kotlin
    if (sortSheetVisible) {
        SortAndDisplaySheet(
            sort = state.sort,
            viewMode = state.viewMode,
            onSortChange = actions.onSortChange,
            onViewModeChange = actions.onViewModeChange,
            onDismiss = { sortSheetVisible = false },
        )
    }
```

3. `LibraryHeader(state, onOpenSortSheet, modifier)` ne reçoit plus `actions` ; `LibraryList(state, actions, onOpenSortSheet)` et `LibraryGrid(state, actions, onOpenSortSheet)` reçoivent `onOpenSortSheet: () -> Unit` et l'appellent ainsi : `LibraryHeader(state, onOpenSortSheet = onOpenSortSheet, modifier = …)` ; `LibraryContent` leur passe `{ sortSheetVisible = true }`. Remplacer son corps : même mesure qu'en V1, le bouton de tri à la place de la bascule.

```kotlin
@Composable
private fun LibraryHeader(state: LibraryUiState, onOpenSortSheet: () -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    val titleText = stringResource(R.string.library_title)
    val countText = pluralStringResource(R.plurals.library_book_count, state.filteredBooks.size, state.filteredBooks.size)
    val buttonLabel = sortLabel(state.sort)
    val buttonDescription = sortButtonDescription(state.sort, state.viewMode)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val sortButton = @Composable {
        SortButton(label = buttonLabel, description = buttonDescription, onClick = onOpenSortSheet)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val titleWidthPx = measurer.measure(titleText, VersoTheme.typography.screenTitle).size.width
        val countWidthPx = measurer.measure(countText, CountStyle).size.width
        // Bouton : texte + icônes (20 + 18 dp) + écarts (2 × 6 dp) + marges (14 + 12 dp).
        val buttonWidthPx = measurer.measure(buttonLabel, VersoTheme.typography.bodyStrong).size.width +
            with(density) { 76.dp.roundToPx() }
        val smallGapPx = with(density) { 10.dp.roundToPx() }
        val bigGapPx = with(density) { 8.dp.roundToPx() }

        val titleAndCountFit = titleWidthPx + smallGapPx + countWidthPx <= maxWidthPx
        val everythingFits = titleAndCountFit && titleWidthPx + smallGapPx + countWidthPx + bigGapPx + buttonWidthPx <= maxWidthPx

        when {
            everythingFits -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderTitle(titleText, colors.text, Modifier.alignByBaseline())
                    HeaderCount(countText, colors.textSecondary, Modifier.alignByBaseline())
                }
                sortButton()
            }
            titleAndCountFit -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderTitle(titleText, colors.text, Modifier.alignByBaseline())
                    HeaderCount(countText, colors.textSecondary, Modifier.alignByBaseline())
                }
                sortButton()
            }
            else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderTitle(titleText, colors.text)
                HeaderCount(countText, colors.textSecondary)
                sortButton()
            }
        }
    }
}
```

Marges de l'en-tête dans la liste : `Modifier.padding(start = 24.dp, top = 20.dp, end = 8.dp, bottom = 4.dp)` (maquette : `padding: 20px 8px 4px 24px`).

`LibraryComponents.kt` : supprimer `ViewModeToggle` et `ViewModeSegment` ; vérifier qu'ils ne sont plus utilisés :

```bash
grep -rn "ViewModeToggle\|SortSelector\|ToggleWidth" app/src   # attendu : aucune ligne
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.library.*' --tests 'com.maximebier.verso.ui.reader.TocSheetTest' --tests 'com.maximebier.verso.ui.reader.JournalSheetTest'`
Expected : PASS (les feuilles Sommaire et Journal, sans `fitContent`, sont inchangées).

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 13 (partie) : bouton de tri et feuille « Trier et afficher »"
```

### Task 13.5 : état segmenté dans la fiche

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/details/DetailsViewModel.kt`, `DetailsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/details/DetailsSamples.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/details/DetailsViewModelTest.kt`, `DetailsScreenTest.kt`

**Interfaces:**
- Consumes : `BookEntity.status()`, `BookRepository.setStateOverride` (13.2).
- Produces (hors contrat) : `DetailsUiState.status: BookStatus`, `DetailsViewModel.onStatusChange(status: BookStatus)`, `DetailsActions.onStatusChange: (BookStatus) -> Unit`.

- [ ] **Step 1 : écrire les tests qui échouent**

`DetailsViewModelTest.kt` (imports `com.maximebier.verso.core.model.BookStatus`, `kotlinx.coroutines.test.advanceUntilIdle`) :

```kotlin
    @Test
    fun statusChoiceIsSavedAndWinsOverProgression() = runTest(dispatcher) {
        val id = insertBovary()
        val vm = viewModel(id)
        advanceUntilIdle()
        assertThat(vm.state.value.status).isEqualTo(BookStatus.TO_READ)

        vm.onStatusChange(BookStatus.FINISHED)
        advanceUntilIdle()
        assertThat(books.book(id)!!.stateOverride).isEqualTo("FINISHED")
        assertThat(vm.state.value.status).isEqualTo(BookStatus.FINISHED)

        books.saveReadingPosition(id, """{"href":"ch1.xhtml","type":"application/xhtml+xml"}""", 0.4)
        advanceUntilIdle()
        assertThat(vm.state.value.status).isEqualTo(BookStatus.FINISHED)
    }
```

`DetailsScreenTest.kt` :

```kotlin
    @Test
    fun statusSegmentShowsAndChangesTheState() {
        var chosen: BookStatus? = null
        show(DetailsSamples.bovary, DetailsActions(onStatusChange = { chosen = it }))
        compose.onNodeWithText(text(R.string.details_state_label)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_state_in_progress)).assertIsSelected()
        compose.onNodeWithText(text(R.string.details_state_finished)).performClick()
        assertThat(chosen).isEqualTo(BookStatus.FINISHED)
    }
```

(imports `androidx.compose.ui.test.assertIsSelected`, `com.maximebier.verso.core.model.BookStatus`.) Si `runTest(dispatcher)` n'est pas la forme des autres tests de `DetailsViewModelTest`, reprendre la leur (même dispatcher `StandardTestDispatcher`).

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.details.*'`
Expected : échec de compilation.

- [ ] **Step 3 : implémenter**

`strings.xml`, dans le bloc de la fiche :

```xml
    <!-- V2 · écran 2.08 : état du livre -->
    <string name="details_state_label">État</string>
    <string name="details_state_group">État du livre</string>
    <string name="details_state_to_read">À lire</string>
    <string name="details_state_in_progress">En cours</string>
    <string name="details_state_finished">Terminé</string>
```

`DetailsViewModel.kt` : ajouter `val status: BookStatus = BookStatus.TO_READ,` à `DetailsUiState` ; dans le `copy` de l'observation du livre, `status = book.status(),` (import `com.maximebier.verso.data.status`) ; puis :

```kotlin
    /** Choix manuel de l'état (bouton segmenté) : il l'emporte sur l'état calculé jusqu'au choix suivant. */
    fun onStatusChange(status: BookStatus) {
        if (deleting) return
        viewModelScope.launch { books.setStateOverride(bookId, status) }
    }
```

`DetailsScreen.kt` : `DetailsActions` reçoit `val onStatusChange: (BookStatus) -> Unit = {},` ; `DetailsScreen` passe `onStatusChange = viewModel::onStatusChange,`. Dans `DetailsContent`, entre la rangée couverture / Reprendre et le champ Titre :

```kotlin
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.details_state_label),
                        style = VersoTheme.typography.captionBold,
                        color = colors.textSecondary,
                    )
                    val statuses = listOf(BookStatus.TO_READ, BookStatus.IN_PROGRESS, BookStatus.FINISHED)
                    VersoSegmentedButton(
                        options = listOf(
                            stringResource(R.string.details_state_to_read),
                            stringResource(R.string.details_state_in_progress),
                            stringResource(R.string.details_state_finished),
                        ),
                        selectedIndex = statuses.indexOf(state.status),
                        onSelect = { index -> actions.onStatusChange(statuses[index]) },
                        groupLabel = stringResource(R.string.details_state_group),
                    )
                }
```

`DetailsSamples.kt` : `bovary` reçoit `status = BookStatus.IN_PROGRESS,` ; `neverOpened` reçoit `status = BookStatus.TO_READ`.

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.details.*'`
Expected : PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 13 (partie) : état du livre dans la fiche"
```

### Task 13.6 : captures 2.07, 2.07b, 2.08 (état) et fin de l'étape 13

**Files:**
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/library/LibrarySamples.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/samples/LibrarySamples.kt`, `samples/DetailsSamples.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt`
- Modify: `docs/SPEC.md`, `docs/acceptance-v2.md`

**Interfaces:**
- Consumes : tout ce qui précède.
- Produces : `LibrarySamples.states` (données 2.07), `LibraryStatesSample()`, `DetailsV2Sample()`.

- [ ] **Step 1 : données et échantillons**

Dans l'objet `LibrarySamples` (tests `ui/library`) :

```kotlin
    /** Bibliothèque de la maquette 2.07 : Madame Bovary en carte, puis les états « À lire » et « Terminé ». */
    val states = LibraryUiState(
        loading = false,
        books = listOf(
            bovary,
            book(2, "Vingt mille lieues sous les mers", "Jules Verne", BookStatus.IN_PROGRESS, 0.64),
            book(8, "Le Comte de Monte-Cristo", "Alexandre Dumas", BookStatus.TO_READ, 0.0),
            book(5, "Le Grand Meaulnes", "Alain-Fournier", BookStatus.FINISHED, 1.0),
            book(3, "Bel-Ami", "Guy de Maupassant", BookStatus.IN_PROGRESS, 0.47),
            book(4, "Germinal", "Émile Zola", BookStatus.IN_PROGRESS, 0.12),
            book(6, "Le Rouge et le Noir", "Stendhal", BookStatus.TO_READ, 0.0),
        ),
        resume = resume,
    )
```

Dans `screenshots/samples/LibrarySamples.kt` (mêmes imports que les autres échantillons du fichier) :

```kotlin
/** 2.07 et 2.07b : bibliothèque avec états (la feuille est ouverte par la capture). */
@Composable
internal fun LibraryStatesSample() {
    LibraryContent(LibrarySamples.states, LibraryActions())
}
```

Dans `screenshots/samples/DetailsSamples.kt` :

```kotlin
/** 2.08 : fiche V2 (état ; les statistiques s'ajoutent à la tâche 14.4). */
@Composable
internal fun DetailsV2Sample() {
    DetailsContent(DetailsSamples.bovary, DetailsActions())
}
```

- [ ] **Step 2 : entrées du catalogue**

Dans `V2ScreenCatalog.kt`, ajouter l'action et les entrées :

```kotlin
/** Ouvre la feuille « Trier et afficher » (2.07b) par son bouton. */
private val openSortSheet: ComposeContentTestRule.() -> Unit = {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val label = ctx.getString(
        R.string.library_sort_button_description,
        ctx.getString(R.string.library_sort_state_recent),
        ctx.getString(R.string.library_view_state_list),
    )
    onNodeWithContentDescription(label).performClick()
}
```

```kotlin
        V2ScreenFixture(ScreenFixture("2.07-bibliotheque-avec-etats") { LibraryStatesSample() }),
        V2ScreenFixture(ScreenFixture("2.07b-trier-et-afficher", afterContent = openSortSheet) { LibraryStatesSample() }),
        V2ScreenFixture(ScreenFixture("2.08-details-du-livre") { DetailsV2Sample() }),
```

Type `V2ScreenFixture` de la tâche 10.2 : ces écrans sont capturés dans les quatre palettes (`-clair`, `-sepia`, `-sombre`, `-noir`, plus `-clair-texte-200`) et entrent d’eux-mêmes dans `AccessibilityTreeTest` (étendu en 11.4).

- [ ] **Step 3 : lancer les captures et les comparer**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.screenshots.*'`
Expected : PASS. Ouvrir `app/build/outputs/roborazzi/v2/2.07-bibliotheque-avec-etats-clair.png`, `2.07b-trier-et-afficher-clair.png`, `2.08-details-du-livre-clair.png` (et `-sepia`, `-sombre`, `-noir`, `-clair-texte-200`) à côté de `docs/design/screens/2.07-bibliotheque-avec-etats.png`, `2.07b-trier-et-afficher.png`, `2.08-details-du-livre.png` : pastilles (coche sur « Tous »), bouton « ⇅ Récents ▾ », signet devant « À lire », coche devant « Terminé », feuille avec « Trier par » et « Affichage », segmenté « État » de la fiche. À 200 % : pastilles lisibles en défilant, en-tête replié, segmentés empilés, rien de coupé. `AccessibilityTreeTest` (s'il parcourt `V2ScreenCatalog`) : vert.

- [ ] **Step 4 : fin de l'étape 13**

1. `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` : vert, sortie gardée.
2. `docs/SPEC.md`, « Historique » : ajouter la ligne
   `- 2026-09-27 (étape 13) : filtre de la bibliothèque non enregistré, compteur des livres du filtre choisi, carte « Reprendre » gardée sous tous les filtres, « Aucun livre pour ce filtre. » quand un filtre est vide ; le tri et l'affichage passent dans la feuille « Trier et afficher ».`
   (date du jour réel si elle diffère).
3. Téléphone : `./gradlew :app:installDebug`, puis `adb shell am start -n com.maximebier.verso/.MainActivity`. Vérifier et capturer (`adb exec-out screencap -p > build/acceptance/2.07.png`) : bibliothèque avec pastilles ; « Terminés » ne montre que les livres terminés ; un livre jamais ouvert est « À lire » ; « Récents ▾ » ouvre la feuille, « Titre » puis « Grille » s'appliquent ; fiche : passer un livre en « Terminé », revenir, le filtre « Terminés » le montre ; `adb shell am force-stop com.maximebier.verso` puis relance : tri, affichage et état manuel conservés ; relire ce livre quelques glissés : il reste « Terminé ». Ne toucher à aucun réglage du téléphone.
4. Cocher les critères V2 11, 12 et 13 dans `docs/SPEC.md` et `docs/acceptance-v2.md` (colonne « Claude », avec la date et les captures) si tout est conforme ; sinon noter l'écart et le corriger avant de continuer.
5. Regrouper les commits de l'étape : `git reset --soft <commit précédant la tâche 13.1>` puis `git commit -m "Étape 13 : états des livres, filtres, « Trier et afficher »"`, `git push`.

---

## Étape 14 : statistiques et Paramètres V2

Critères V2 couverts : 14, 15, 16.

### Task 14.1 : `ReadingStats` (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/maximebier/verso/core/stats/ReadingStats.kt`
- Test: `core/src/test/kotlin/com/maximebier/verso/core/stats/ReadingStatsTest.kt`

**Interfaces:**
- Consumes : `DEFAULT_WORDS_PER_MINUTE` (`core/text/RemainingTime.kt`).
- Produces (contrat) : `SessionStat`, `StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED`, `ReadingStats(totalActiveMs, sessionCount, wordsPerMinute)`, `ReadingStats.effectiveWordsPerMinute`, `readingStats(sessions)`.

- [ ] **Step 1 : écrire les tests qui échouent**

```kotlin
package com.maximebier.verso.core.stats

import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import com.maximebier.verso.core.text.remainingMinutes
import org.junit.Test

class ReadingStatsTest {

    private val minute = 60_000L

    @Test
    fun noSessionGivesNoSpeedAndTheDefaultRate() {
        val stats = readingStats(emptyList())
        assertThat(stats.totalActiveMs).isEqualTo(0)
        assertThat(stats.sessionCount).isEqualTo(0)
        assertThat(stats.wordsPerMinute).isNull()
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(DEFAULT_WORDS_PER_MINUTE)
    }

    @Test
    fun speedIsWordsReadOverActiveTime() {
        // 2 h 28 en 5 sessions à 240 mots par minute (maquette 2.08).
        val sessions = List(5) { SessionStat(activeMs = 148 * minute / 5, wordsRead = 240L * 148 / 5) }
        val stats = readingStats(sessions)
        assertThat(stats.totalActiveMs).isEqualTo(148 * minute)
        assertThat(stats.sessionCount).isEqualTo(5)
        assertThat(stats.wordsPerMinute).isEqualTo(240)
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(240)
    }

    @Test
    fun tooLittleActiveTimeGivesNoSpeed() {
        val stats = readingStats(listOf(SessionStat(activeMs = StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED - 1, wordsRead = 400)))
        assertThat(stats.sessionCount).isEqualTo(1)
        assertThat(stats.wordsPerMinute).isNull()
        assertThat(stats.effectiveWordsPerMinute).isEqualTo(DEFAULT_WORDS_PER_MINUTE)
    }

    @Test
    fun noWordReadGivesNoSpeed() {
        assertThat(readingStats(listOf(SessionStat(activeMs = 10 * minute, wordsRead = 0))).wordsPerMinute).isNull()
    }

    @Test
    fun negativeValuesAreIgnored() {
        val stats = readingStats(listOf(SessionStat(activeMs = -5 * minute, wordsRead = -10), SessionStat(10 * minute, 2_000)))
        assertThat(stats.totalActiveMs).isEqualTo(10 * minute)
        assertThat(stats.wordsPerMinute).isEqualTo(200)
    }

    @Test
    fun speedIsNeverZero() {
        assertThat(readingStats(listOf(SessionStat(activeMs = 600 * minute, wordsRead = 1))).wordsPerMinute).isEqualTo(1)
    }

    @Test
    fun finishedBookHasNothingLeftAtAnyRate() {
        val rate = readingStats(listOf(SessionStat(10 * minute, 2_000))).effectiveWordsPerMinute
        assertThat(remainingMinutes(totalWords = 150_000, progression = 1.0, wordsPerMinute = rate)).isEqualTo(0)
    }

    @Test
    fun thresholdMatchesTheDesign() {
        assertThat(StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED).isEqualTo(60_000L)
    }
}
```

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :core:test --tests 'com.maximebier.verso.core.stats.ReadingStatsTest'`
Expected : échec de compilation (`readingStats` inconnu).

- [ ] **Step 3 : implémenter**

```kotlin
package com.maximebier.verso.core.stats

import com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE
import kotlin.math.roundToInt

/** Ce que les statistiques retiennent d'une session du journal. */
data class SessionStat(val activeMs: Long, val wordsRead: Long)

object StatsThresholds {
    /** En dessous d'une minute de lecture active en tout, la vitesse n'est pas mesurée. */
    const val MIN_ACTIVE_MS_FOR_SPEED = 60_000L
}

/** Statistiques d'un livre, calculées à partir de son journal (fiche 2.08). */
data class ReadingStats(val totalActiveMs: Long, val sessionCount: Int, val wordsPerMinute: Int?) {
    /** Vitesse mesurée, ou [DEFAULT_WORDS_PER_MINUTE] (250) sans mesure. */
    val effectiveWordsPerMinute: Int
        get() = wordsPerMinute ?: DEFAULT_WORDS_PER_MINUTE
}

/**
 * Temps actif total, nombre de sessions et vitesse = mots lus / temps actif, arrondie, au moins 1. Pas de vitesse
 * sous [StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED] de temps actif ou sans mot lu. Valeurs négatives comptées 0.
 */
fun readingStats(sessions: List<SessionStat>): ReadingStats {
    val activeMs = sessions.sumOf { it.activeMs.coerceAtLeast(0) }
    val words = sessions.sumOf { it.wordsRead.coerceAtLeast(0) }
    val speed = if (activeMs < StatsThresholds.MIN_ACTIVE_MS_FOR_SPEED || words <= 0) {
        null
    } else {
        (words * 60_000.0 / activeMs).roundToInt().coerceAtLeast(1)
    }
    return ReadingStats(totalActiveMs = activeMs, sessionCount = sessions.size, wordsPerMinute = speed)
}
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :core:test`
Expected : PASS.

- [ ] **Step 5 : commit**

```bash
git add core/src
git commit -m "Étape 14 (partie) : statistiques de lecture"
```

### Task 14.2 : interrupteur des statistiques et statistiques d'un livre

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/SettingsRepository.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/SessionRepository.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/data/SettingsRepositoryTest.kt`, `app/src/test/kotlin/com/maximebier/verso/data/SessionRepositoryTest.kt` (nouveau)

**Interfaces:**
- Consumes : `readingStats`, `SessionStat` (14.1).
- Produces : `SettingsRepository.showStatistics: Flow<Boolean>` et `setShowStatistics(value: Boolean)` (contrat) ; `SessionRepository.observeStats(bookId: Long): Flow<ReadingStats>` (hors contrat).

- [ ] **Step 1 : écrire les tests qui échouent**

Dans `SettingsRepositoryTest.kt` :

```kotlin
    @Test
    fun statisticsAreShownByDefaultAndCanBeHidden() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "stats.preferences_pb") })
        val settings = SettingsRepository(store)
        assertThat(settings.showStatistics.first()).isTrue()
        settings.setShowStatistics(false)
        assertThat(settings.showStatistics.first()).isFalse()
    }
```

`SessionRepositoryTest.kt` :

```kotlin
package com.maximebier.verso.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.data.db.VersoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private lateinit var db: VersoDatabase
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), VersoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessions = SessionRepository(db.sessionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun statsCoverOnlyTheSessionsOfTheBook() = runTest {
        val bookId = db.bookDao().insert(testBook(sha256 = "stats"))
        val otherId = db.bookDao().insert(testBook(sha256 = "autre"))
        sessions.upsert(testSession(bookId, startedAt = 0).copy(activeMs = 10 * 60_000, wordsRead = 2_000))
        sessions.upsert(testSession(bookId, startedAt = 1_000_000).copy(activeMs = 10 * 60_000, wordsRead = 2_800))
        sessions.upsert(testSession(otherId, startedAt = 0).copy(activeMs = 60 * 60_000, wordsRead = 100))

        val stats = sessions.observeStats(bookId).first()
        assertThat(stats.sessionCount).isEqualTo(2)
        assertThat(stats.totalActiveMs).isEqualTo(20 * 60_000L)
        assertThat(stats.wordsPerMinute).isEqualTo(240)
    }

    @Test
    fun bookWithoutSessionHasEmptyStats() = runTest {
        val bookId = db.bookDao().insert(testBook(sha256 = "vide"))
        assertThat(sessions.observeStats(bookId).first().sessionCount).isEqualTo(0)
    }
}
```

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.data.SettingsRepositoryTest' --tests 'com.maximebier.verso.data.SessionRepositoryTest'`
Expected : échec de compilation.

- [ ] **Step 3 : implémenter**

`SettingsRepository.kt` :

```kotlin
    /** Défaut : true. Le journal est toujours tenu ; seul l'affichage des statistiques se désactive. */
    val showStatistics: Flow<Boolean> = preferences.map { it[SHOW_STATISTICS] ?: true }

    suspend fun setShowStatistics(value: Boolean) {
        save { it[SHOW_STATISTICS] = value }
    }
```

et dans le `companion object` : `val SHOW_STATISTICS = booleanPreferencesKey("show_statistics")`.

`SessionRepository.kt` (imports `com.maximebier.verso.core.stats.ReadingStats`, `com.maximebier.verso.core.stats.SessionStat`, `com.maximebier.verso.core.stats.readingStats`, `kotlinx.coroutines.flow.map`) :

```kotlin
    /** Statistiques du livre, recalculées à chaque session écrite. */
    fun observeStats(bookId: Long): Flow<ReadingStats> = dao.observeForBook(bookId).map { list ->
        readingStats(list.map { SessionStat(activeMs = it.activeMs, wordsRead = it.wordsRead) })
    }
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : même commande que l'étape 2.
Expected : PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 14 (partie) : interrupteur et statistiques par livre"
```

### Task 14.3 : temps restant à la vitesse mesurée (bibliothèque, lecture, fiche)

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/details/DetailsViewModel.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/library/LibraryViewModelTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderViewModelTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/details/DetailsViewModelTest.kt`

**Interfaces:**
- Consumes : `SessionRepository.observeStats` (14.2), `ReadingStats.effectiveWordsPerMinute` (14.1), `remainingMinutes(totalWords, progression, wordsPerMinute)`.
- Produces (hors contrat) : paramètre `statsOf: (Long) -> Flow<ReadingStats>` de `LibraryViewModel` et `DetailsViewModel` (défaut : statistiques vides) ; `ReaderUiState.wordsPerMinute: Int` ; `DetailsUiState.stats: ReadingStats?` (null = section masquée) ; paramètre `showStatistics: Flow<Boolean>` de `DetailsViewModel` (défaut `flowOf(true)`).

Décision (à reporter dans « Historique » au commit de l'étape 14) : le temps restant de la bibliothèque, de la barre de lecture et de la fiche utilise la vitesse mesurée même quand l'affichage des statistiques est désactivé (l'interrupteur ne masque que la section « Statistiques » de la fiche).

- [ ] **Step 1 : écrire les tests qui échouent**

`LibraryViewModelTest.kt` — la fabrique `viewModel(...)` reçoit un paramètre `statsOf: (Long) -> Flow<ReadingStats> = { flowOf(readingStats(emptyList())) }` qu'elle passe au constructeur ; puis :

```kotlin
    @Test
    fun resumeCardUsesTheMeasuredSpeed() = runTest(dispatcher) {
        insert("Madame Bovary", lastOpenedAt = 20, locatorJson = LOCATOR_JSON, progression = 0.31, totalWords = 150_000)
        val vm = viewModel(statsOf = { flowOf(ReadingStats(totalActiveMs = 600_000, sessionCount = 2, wordsPerMinute = 100)) })
        vm.state.test {
            val resume = requireNotNull(awaitUntil { it.resume != null }.resume)
            assertThat(resume.remainingMinutes).isEqualTo(remainingMinutes(150_000, 0.31, 100))
        }
    }
```

`ReaderViewModelTest.kt` — la fabrique `factory(...)` passe déjà `SessionRepository(db.sessionDao())` ; ajouter :

```kotlin
    @Test
    fun remainingTimeUsesTheMeasuredSpeed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        SessionRepository(db.sessionDao()).upsert(testSession(id, startedAt = 0).copy(activeMs = 10 * 60_000, wordsRead = 1_000))

        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading && it.wordsPerMinute == 100 }
        viewModel.onReaderReady(FakeReaderController(start))
        runCurrent()
        val expected = remainingMinutes(totalWords = 100_000, progression = 0.30, wordsPerMinute = 100)
        assertThat(viewModel.uiState.first { it.remainingMinutes == expected }.remainingMinutes).isEqualTo(expected)
        store.clear()
    }
```

(imports `com.maximebier.verso.data.testSession`, `com.maximebier.verso.core.text.remainingMinutes` s'ils manquent.)

`DetailsViewModelTest.kt` :

```kotlin
    @Test
    fun statsAreShownWithMeasuredRemainingTimeAndHiddenWhenSwitchedOff() = runTest(dispatcher) {
        val id = insertBovary()
        val stats = ReadingStats(totalActiveMs = 148 * 60_000L, sessionCount = 5, wordsPerMinute = 240)
        val shown = MutableStateFlow(true)
        val vm = DetailsViewModel(bookId = id, books = books, saveScope = this, statsOf = { flowOf(stats) }, showStatistics = shown)
        advanceUntilIdle()
        assertThat(vm.state.value.stats).isEqualTo(stats)
        assertThat(vm.state.value.remainingMinutes).isEqualTo(remainingMinutes(150_000, 0.0, 240))

        shown.value = false
        advanceUntilIdle()
        assertThat(vm.state.value.stats).isNull()
        assertThat(vm.state.value.remainingMinutes).isEqualTo(remainingMinutes(150_000, 0.0, 240))
    }
```

(imports `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.flowOf`, `com.maximebier.verso.core.stats.ReadingStats`, `com.maximebier.verso.core.text.remainingMinutes`.)

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.library.LibraryViewModelTest' --tests 'com.maximebier.verso.ui.reader.ReaderViewModelTest' --tests 'com.maximebier.verso.ui.details.DetailsViewModelTest'`
Expected : échec de compilation.

- [ ] **Step 3 : implémenter**

`LibraryViewModel.kt` :

1. Constructeur : ajouter en dernier paramètre

```kotlin
    /** Statistiques d'un livre (vitesse mesurée du temps restant de la carte « Reprendre »). */
    private val statsOf: (Long) -> Flow<ReadingStats> = { flowOf(readingStats(emptyList())) },
```

2. Dans `state`, remplacer `books.observeLastOpened(),` par :

```kotlin
        books.observeLastOpened().flatMapLatest { entity ->
            if (entity == null) flowOf(null) else statsOf(entity.id).map { stats -> entity to stats }
        },
```

et `resume = lastOpened?.let(::resumeInfoOf),` par `resume = lastOpened?.let { (entity, stats) -> resumeInfoOf(entity, stats.effectiveWordsPerMinute) },`.

3. `resumeInfoOf(entity: BookEntity, wordsPerMinute: Int = DEFAULT_WORDS_PER_MINUTE)` : `remainingMinutes = remainingMinutes(entity.totalWords, entity.progression, wordsPerMinute),`.
4. `Factory` : `statsOf = container.sessions::observeStats,`.
5. Imports : `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.flowOf`, `com.maximebier.verso.core.stats.ReadingStats`, `com.maximebier.verso.core.stats.readingStats`, `com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE`.

`ReaderViewModel.kt` :

1. `ReaderUiState` : ajouter `val wordsPerMinute: Int = DEFAULT_WORDS_PER_MINUTE,` après `remainingMinutes`.
2. Dans `onPositionState`, `remainingMinutes = remainingMinutes(state.totalWords, progression, state.wordsPerMinute),`.
3. Dans `init`, après `viewModelScope.launch { load() }` :

```kotlin
        viewModelScope.launch {
            sessions.observeStats(bookId).collect { stats ->
                val rate = stats.effectiveWordsPerMinute
                _uiState.update { it.copy(wordsPerMinute = rate, remainingMinutes = remainingMinutes(it.totalWords, it.readingProgression, rate)) }
            }
        }
```

(import `com.maximebier.verso.core.text.DEFAULT_WORDS_PER_MINUTE`.)

`DetailsViewModel.kt` :

1. Constructeur : ajouter après `debounceMs`

```kotlin
    private val statsOf: (Long) -> Flow<ReadingStats> = { flowOf(readingStats(emptyList())) },
    private val showStatistics: Flow<Boolean> = flowOf(true),
```

2. `DetailsUiState` : ajouter `val stats: ReadingStats? = null,` (null = section « Statistiques » masquée).
3. Dans `init`, remplacer `books.observeBook(bookId).collect { book ->` par

```kotlin
            combine(books.observeBook(bookId), statsOf(bookId), showStatistics) { book, stats, shown -> Triple(book, stats, shown) }
                .collect { (book, stats, shown) ->
```

et, dans la branche `else`, `remainingMinutes = remainingMinutes(book.totalWords, book.progression, stats.effectiveWordsPerMinute),` et `stats = stats.takeIf { shown },` (le reste du `copy` est inchangé ; fermer la parenthèse supplémentaire du `collect`).
4. `factory(bookId)` : `statsOf = app.container.sessions::observeStats, showStatistics = app.container.settings.showStatistics,`.
5. Imports : `kotlinx.coroutines.flow.Flow`, `flowOf`, `combine`, `com.maximebier.verso.core.stats.ReadingStats`, `com.maximebier.verso.core.stats.readingStats`.

- [ ] **Step 4 : lancer, vérifier le succès**

Run : même commande que l'étape 2, puis `./gradlew :app:testDebugUnitTest`.
Expected : PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 14 (partie) : temps restant à la vitesse mesurée"
```

### Task 14.4 : section « Statistiques » de la fiche et « Voir le journal de lecture »

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/details/DetailsScreen.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/nav/Routes.kt`, `app/src/main/kotlin/com/maximebier/verso/ui/nav/VersoNavHost.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt` (`ReaderDestination`), `ReaderViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/details/DetailsSamples.kt`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/details/DetailsScreenTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes : `DetailsUiState.stats` (14.3), `durationText(minutes)` (`ui/common/BookTexts.kt`), `MetadataRow`, `OutlinedPillButton`, `VersoIcons.History`.
- Produces (hors contrat) : `DetailsActions.onOpenJournal: (Long) -> Unit` ; `DetailsDestination(bookId, onBack, onOpenReader, onOpenJournal)` ; `ReaderRoute(bookId: Long, openJournal: Boolean = false)` ; `ReaderDestination(bookId, onBack, onOpenFailed, openJournal: Boolean = false)` ; paramètre `openJournalOnLoad: Boolean = false` de `ReaderViewModel` et `ReaderViewModel.factory(bookId, openJournal = false)`.

Décision (« Historique », étape 14) : « Voir le journal de lecture » ouvre le livre avec la feuille du journal déjà affichée ; « Reprendre ici » y fonctionne comme depuis la barre (saut explicite, carte « Revenir »). Sans session, la section « Statistiques » n'est pas affichée ; sans vitesse mesurée, « Vitesse moyenne » et « Temps restant estimé » ne le sont pas ; un livre terminé n'affiche pas « Temps restant estimé ».

- [ ] **Step 1 : écrire les tests qui échouent**

`DetailsScreenTest.kt` :

```kotlin
    private val stats = ReadingStats(totalActiveMs = 148 * 60_000L, sessionCount = 5, wordsPerMinute = 240)

    @Test
    fun statisticsSectionShowsTimeSpeedAndRemaining() {
        var journal: Long? = null
        show(DetailsSamples.bovary.copy(stats = stats), DetailsActions(onOpenJournal = { journal = it }))
        val duration = text(R.string.common_duration_hours_minutes, 2, 28)
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.details_stats_reading_time, 5, duration, 5))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.details_stats_speed, 240, 240)).assertExists()
        compose.onNodeWithText(text(R.string.details_stats_remaining, text(R.string.common_duration_hours_minutes, 5, 30))).assertExists()
        compose.onNodeWithText(text(R.string.details_open_journal)).performScrollTo().performClick()
        assertThat(journal).isEqualTo(1L)
    }

    @Test
    fun statisticsSectionIsHiddenWhenSwitchedOffOrEmpty() {
        show(DetailsSamples.bovary.copy(stats = null))
        compose.onNodeWithText(text(R.string.details_stats_title)).assertDoesNotExist()
    }

    @Test
    fun withoutMeasuredSpeedOnlyTheReadingTimeIsShown() {
        show(DetailsSamples.bovary.copy(stats = stats.copy(sessionCount = 1, wordsPerMinute = null)))
        compose.onNodeWithText(text(R.string.details_stats_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_stats_speed_label)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.details_stats_remaining_label)).assertDoesNotExist()
    }

    @Test
    fun sessionlessStatsShowNothing() {
        show(DetailsSamples.bovary.copy(stats = ReadingStats(0, 0, null)))
        compose.onNodeWithText(text(R.string.details_stats_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.details_open_journal)).assertDoesNotExist()
    }
```

(imports `com.maximebier.verso.core.stats.ReadingStats`, `androidx.compose.ui.test.performScrollTo`.)

`ReaderViewModelTest.kt` — la fabrique `factory(...)` reçoit `openJournal: Boolean = false` et passe `openJournalOnLoad = openJournal` ; puis :

```kotlin
    @Test
    fun openingFromTheDetailsJournalShowsTheJournal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id, openJournal = true))[ReaderViewModel::class]
        assertThat(viewModel.uiState.first { !it.loading && it.journalVisible }.journalVisible).isTrue()
        store.clear()
    }
```

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.details.DetailsScreenTest' --tests 'com.maximebier.verso.ui.reader.ReaderViewModelTest'`
Expected : échec de compilation.

- [ ] **Step 3 : implémenter**

`strings.xml`, dans le bloc de la fiche :

```xml
    <!-- V2 · écran 2.08 : statistiques -->
    <string name="details_stats_title">Statistiques</string>
    <string name="details_stats_reading_time_label">Temps de lecture</string>
    <!-- « 2 h 28 en 5 sessions » ; %1$s = durée (common_duration_*), %2$d = nombre de sessions -->
    <plurals name="details_stats_reading_time">
        <item quantity="one">%1$s en %2$d session</item>
        <item quantity="many">%1$s en %2$d sessions</item>
        <item quantity="other">%1$s en %2$d sessions</item>
    </plurals>
    <string name="details_stats_speed_label">Vitesse moyenne</string>
    <plurals name="details_stats_speed">
        <item quantity="one">%1$d mot par minute</item>
        <item quantity="many">%1$d mots par minute</item>
        <item quantity="other">%1$d mots par minute</item>
    </plurals>
    <string name="details_stats_remaining_label">Temps restant estimé</string>
    <!-- « Environ 5 h 30 à votre rythme » ; %1$s = durée (common_duration_*) -->
    <string name="details_stats_remaining">Environ %1$s à votre rythme</string>
    <string name="details_open_journal">Voir le journal de lecture</string>
```

`DetailsScreen.kt` :

1. `DetailsActions` : `val onOpenJournal: (Long) -> Unit = {},`.
2. `DetailsDestination(bookId: Long, onBack: () -> Unit, onOpenReader: (Long) -> Unit, onOpenJournal: (Long) -> Unit)` passe `onOpenJournal` à `DetailsScreen`, qui prend le même paramètre et le relie à `DetailsActions(onOpenJournal = { id -> viewModel.flush(); onOpenJournal(id) }, …)`.
3. Dans `DetailsContent`, entre le champ Auteur et la colonne des `MetadataRow` :

```kotlin
                state.stats?.takeIf { it.sessionCount > 0 }?.let { stats ->
                    StatisticsSection(
                        stats = stats,
                        remainingMinutes = state.remainingMinutes,
                        onOpenJournal = { actions.onOpenJournal(state.bookId) },
                    )
                }
```

4. Ajouter (imports `androidx.compose.ui.res.pluralStringResource`, `androidx.compose.ui.semantics.heading`, `androidx.compose.ui.semantics.semantics`, `com.maximebier.verso.core.stats.ReadingStats`, `com.maximebier.verso.ui.common.durationText`, `com.maximebier.verso.ui.components.OutlinedPillButton`) :

```kotlin
/** Arrondi à la minute, au moins 1 dès qu'il y a eu de la lecture. */
private fun activeMinutes(activeMs: Long): Int = ((activeMs + 30_000) / 60_000).toInt().coerceAtLeast(1)

/** Section « Statistiques » (2.08) : temps de lecture, vitesse moyenne, temps restant à votre rythme, journal. */
@Composable
private fun StatisticsSection(stats: ReadingStats, remainingMinutes: Int, onOpenJournal: () -> Unit) {
    val colors = VersoTheme.colors
    Column {
        Text(
            text = stringResource(R.string.details_stats_title),
            style = VersoTheme.typography.captionBold,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        MetadataRow(
            label = stringResource(R.string.details_stats_reading_time_label),
            value = pluralStringResource(
                R.plurals.details_stats_reading_time,
                stats.sessionCount,
                durationText(activeMinutes(stats.totalActiveMs)),
                stats.sessionCount,
            ),
        )
        stats.wordsPerMinute?.let { speed ->
            MetadataRow(
                label = stringResource(R.string.details_stats_speed_label),
                value = pluralStringResource(R.plurals.details_stats_speed, speed, speed),
            )
            if (remainingMinutes >= 1) {
                MetadataRow(
                    label = stringResource(R.string.details_stats_remaining_label),
                    value = stringResource(R.string.details_stats_remaining, durationText(remainingMinutes)),
                )
            }
        }
        OutlinedPillButton(
            text = stringResource(R.string.details_open_journal),
            onClick = onOpenJournal,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            icon = VersoIcons.History,
        )
    }
}
```

`Routes.kt` : `@Serializable data class ReaderRoute(val bookId: Long, val openJournal: Boolean = false)`.

`VersoNavHost.kt` : dans `composable<DetailsRoute>`, ajouter

```kotlin
                onOpenJournal = { bookId ->
                    if (entry.resumed()) navController.navigate(ReaderRoute(bookId, openJournal = true)) { launchSingleTop = true }
                },
```

et dans `composable<ReaderRoute>`, passer `openJournal = route.openJournal,` à `ReaderDestination`.

`ReaderScreen.kt` : `fun ReaderDestination(bookId: Long, onBack: () -> Unit, onOpenFailed: () -> Unit = onBack, openJournal: Boolean = false)` ; `factory = ReaderViewModel.factory(bookId, openJournal)`.

`ReaderViewModel.kt` :

1. Constructeur, en diff de celui de la tâche 11.2 (dont le dernier paramètre est `settings: SettingsRepository`) :

   ```diff
        private val reportOpenFailure: (title: String) -> Unit = {},
        private val settings: SettingsRepository,
   +    /** Ouvert par « Voir le journal de lecture » de la fiche : feuille du journal affichée au chargement. */
   +    private val openJournalOnLoad: Boolean = false,
    ) : ViewModel() {
   ```
2. Dans `load()`, juste après la mise à jour de `_uiState` qui pose `toc = tocLinks.toTocNodes(anchors)` (et `loading = false`) : `if (openJournalOnLoad) showJournal()`.
3. `fun factory(bookId: Long, openJournal: Boolean = false)` passe `openJournalOnLoad = openJournal`.

`DetailsSamples.kt` : `bovary` reçoit `stats = ReadingStats(totalActiveMs = 148 * 60_000L, sessionCount = 5, wordsPerMinute = 240),` (import `com.maximebier.verso.core.stats.ReadingStats`).

Vérifier les autres appelants de `DetailsDestination` et `ReaderDestination` :

```bash
grep -rn "DetailsDestination(\|ReaderDestination(\|ReaderRoute(" app/src
```

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.details.*' --tests 'com.maximebier.verso.ui.reader.*' --tests 'com.maximebier.verso.ui.nav.*' --tests 'com.maximebier.verso.StringsTest'`
Expected : PASS.

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 14 (partie) : statistiques et journal dans la fiche"
```

### Task 14.5 : Paramètres V2 (section Lecture, interrupteur des statistiques)

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/SettingsRows.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/settings/ReadingSettingsSection.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/VersoIcons.kt` (si `Minus` manque)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/maximebier/verso/ui/settings/SettingsViewModelTest.kt`, `SettingsScreenTest.kt`

**Interfaces:**
- Consumes : `ReadingSettings`, `ReadingFont`, `ScrollMode`, `ReadingSettingsLimits`, `SettingsRepository.readingSettings` / `updateReadingSettings` (étape 9), `showStatistics` / `setShowStatistics` (14.2), `fontFamilyFor(font)` (étape 9), `ThemeMode` à cinq valeurs (étape 10).
- Produces (hors contrat) : `SettingsUiState.readingSettings: ReadingSettings`, `SettingsUiState.showStatistics: Boolean` ; `SettingsViewModel.onFontChange(ReadingFont)`, `onFontSizeChange(Int)`, `onDefaultScrollModeChange(ScrollMode)`, `onShowStatisticsChange(Boolean)` ; paramètres homonymes de `SettingsScreen`, tous avec défaut `{}` ; composables `ChoiceRow(title, value, onClick, modifier)` (`SettingsRows.kt`), `ReadingSettingsSection(...)`.

Cette tâche **remplace** le choix du thème posé par la tâche 10.1 dans Paramètres › Affichage : `ThemeSelector` et `ThemeMode.labelRes()` (`SettingsScreen.kt`) sont supprimés, et `themeModeLabel` ci-dessous les remplace ; `RadioRow` (`SettingsRows.kt`, 10.1) est gardée et réutilisée par `RadioDialog` (avec `horizontalPadding = 0.dp`). Le thème passe dans la section Lecture, derrière la ligne « Thème › Automatique » de la maquette 2.09. Le test V1 `themeSelectorSelectsTheChosenMode` (`SettingsScreenTest`) est remplacé par celui du dialogue Thème ci-dessous. L’étape 9 n’a posé aucun choix de police dans les Paramètres (la police se choisit dans la feuille « Aa » depuis l’étape 11).

Maquette 2.09 : sections dans l'ordre **Lecture**, **Au démarrage**, **Statistiques**, puis Confidentialité et À propos (inchangées). La section « Affichage » disparaît (`settings_section_display` et `settings_display_body` sont retirées de `strings.xml`). « Thème », « Taille du texte » et « Défilement » sont des lignes avec la valeur et un chevron ; un toucher ouvre un dialogue (`VersoDialog`) : boutons radio pour Thème et Défilement, − / + pour la taille ; le choix s'applique aussitôt ; « Fermer » ferme.

- [ ] **Step 1 : écrire les tests qui échouent**

`SettingsViewModelTest.kt` :

```kotlin
    @Test
    fun readingSettingsAndStatisticsSwitchAreWritten() = runTest {
        val vm = SettingsViewModel(settings, sessions)
        vm.state.test {
            val initial = awaitItem()
            assertThat(initial.readingSettings).isEqualTo(ReadingSettings())
            assertThat(initial.showStatistics).isTrue()

            vm.onFontChange(ReadingFont.ATKINSON)
            assertThat(awaitItem().readingSettings.font).isEqualTo(ReadingFont.ATKINSON)
            vm.onFontSizeChange(ReadingSettingsLimits.MAX_FONT_SIZE_SP + 5)
            assertThat(awaitItem().readingSettings.fontSizeSp).isEqualTo(ReadingSettingsLimits.MAX_FONT_SIZE_SP)
            vm.onDefaultScrollModeChange(ScrollMode.PAGES)
            assertThat(awaitItem().readingSettings.defaultScrollMode).isEqualTo(ScrollMode.PAGES)
            vm.onShowStatisticsChange(false)
            assertThat(awaitItem().showStatistics).isFalse()
        }
        assertThat(settings.readingSettings.first().font).isEqualTo(ReadingFont.ATKINSON)
        assertThat(settings.showStatistics.first()).isFalse()
    }
```

(imports `com.maximebier.verso.core.settings.*`.)

`SettingsScreenTest.kt` — ajouter à la classe `private var reading by mutableStateOf(ReadingSettings())` et `private var showStats by mutableStateOf(true)` ; dans `show()`, l'état devient `SettingsUiState(reopenLastBook = reopen, themeMode = theme, confirmingClearJournal = confirming, readingSettings = reading, showStatistics = showStats)` et on ajoute :

```kotlin
                    onFontChange = { reading = reading.copy(font = it); events += "font=$it" },
                    onFontSizeChange = { reading = reading.withFontSize(it); events += "size=$it" },
                    onDefaultScrollModeChange = { reading = reading.copy(defaultScrollMode = it); events += "scroll=$it" },
                    onShowStatisticsChange = { showStats = it; events += "stats=$it" },
```

Remplacer `themeSelectorSelectsTheChosenMode` et `showsAllSectionsAndTheVersion`, ajouter les autres :

```kotlin
    @Test
    fun themeRowOpensAChoiceAndAppliesIt() {
        show()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_theme)).performScrollTo().performClick()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_theme_dark)).performClick()
        assertThat(events).containsExactly("theme=DARK")
        composeRule.onNodeWithText(ctx.getString(R.string.common_close)).performClick()
        composeRule.onNode(hasText(ctx.getString(R.string.settings_theme)) and hasText(ctx.getString(R.string.settings_theme_dark)))
            .assertExists()
    }

    @Test
    fun fontsAreRadioButtonsWithASample() {
        show()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_font_literata)).assertIsSelected()
        composeRule.onAllNodesWithText(ctx.getString(R.string.settings_font_sample)).assertCountEquals(3)
        composeRule.onNodeWithText(ctx.getString(R.string.settings_font_atkinson)).performScrollTo().performClick()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_font_atkinson)).assertIsSelected()
        assertThat(events).containsExactly("font=ATKINSON")
    }

    @Test
    fun textSizeDialogStepsWithinTheLimits() {
        reading = ReadingSettings(fontSizeSp = ReadingSettingsLimits.MAX_FONT_SIZE_SP)
        show()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_text_size)).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(ctx.getString(R.string.settings_text_size_increase)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(ctx.getString(R.string.settings_text_size_decrease)).performClick()
        assertThat(events).containsExactly("size=${ReadingSettingsLimits.MAX_FONT_SIZE_SP - 1}")
    }

    @Test
    fun defaultScrollDialogOffersContinuousAndPages() {
        show()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_scroll)).performScrollTo().performClick()
        composeRule.onNodeWithText(ctx.getString(R.string.settings_scroll_pages)).performClick()
        assertThat(events).containsExactly("scroll=PAGES")
    }

    @Test
    fun statisticsSwitchIsASwitch() {
        show()
        val row = composeRule.onNode(hasText(ctx.getString(R.string.settings_show_statistics)) and isToggleable())
        row.performScrollTo().assertIsOn().performClick()
        assertThat(events).containsExactly("stats=false")
    }

    @Test
    fun showsAllSectionsAndTheVersion() {
        show()
        listOf(
            R.string.settings_section_reading, R.string.settings_reading_help, R.string.settings_font,
            R.string.settings_section_startup, R.string.settings_section_statistics, R.string.settings_show_statistics_summary,
            R.string.settings_section_privacy, R.string.settings_privacy_body, R.string.settings_clear_journal,
            R.string.settings_section_about, R.string.settings_version, R.string.settings_source_code,
            R.string.settings_licenses,
        ).forEach { composeRule.onNodeWithText(ctx.getString(it), substring = true).performScrollTo().assertExists() }
        composeRule.onNodeWithText("1.0.0", substring = true).assertExists()
    }
```

(imports `androidx.compose.ui.test.assertIsNotEnabled`, `assertIsSelected`, `onAllNodesWithText`, `assertCountEquals`, `onNodeWithContentDescription`, `com.maximebier.verso.core.settings.ReadingSettings`, `ReadingSettingsLimits`.)

- [ ] **Step 2 : lancer, vérifier l'échec**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.settings.*'`
Expected : échec de compilation.

- [ ] **Step 3 : implémenter**

`strings.xml` : supprimer `settings_section_display` et `settings_display_body` ; ajouter après `settings_title` :

```xml
    <!-- V2 · écran 2.09 : section Lecture -->
    <string name="settings_section_reading">Lecture</string>
    <string name="settings_reading_help">Pour tous les livres et toute l’application. Pendant la lecture, touchez « Aa » pour changer.</string>
    <string name="settings_font">Police</string>
    <string name="settings_font_literata">Literata</string>
    <string name="settings_font_literata_summary">Par défaut · avec empattements, pensée pour la lecture longue</string>
    <string name="settings_font_atkinson">Atkinson Hyperlegible Next</string>
    <string name="settings_font_atkinson_summary">Sans empattements, lettres très distinctes</string>
    <string name="settings_font_system">Police du système</string>
    <string name="settings_font_system_summary">Celle de votre téléphone</string>
    <string name="settings_font_sample">Nous étions à l’Étude, quand le Proviseur entra</string>
    <string name="settings_text_size">Taille du texte</string>
    <string name="settings_text_size_decrease">Réduire la taille du texte</string>
    <string name="settings_text_size_increase">Agrandir la taille du texte</string>
    <string name="settings_scroll">Défilement</string>
    <string name="settings_scroll_continuous">Continu</string>
    <string name="settings_scroll_pages">Pages</string>
    <!-- V2 · écran 2.09 : section Statistiques -->
    <string name="settings_section_statistics">Statistiques</string>
    <string name="settings_show_statistics">Afficher les statistiques</string>
    <string name="settings_show_statistics_summary">Temps et vitesse de lecture, calculés à partir du journal. Tout reste sur ce téléphone.</string>
```

(U+202F à l'intérieur de « Aa ».) Vérifier que les libellés de thème de l'étape 10 existent : `grep -n "settings_theme_sepia\|settings_theme_black" app/src/main/res/values/strings.xml` ; s'ils manquent, ajouter `<string name="settings_theme_sepia">Sépia</string>` et `<string name="settings_theme_black">Noir</string>` après `settings_theme_dark`.

`VersoIcons.kt` : si `Minus` n'existe pas, ajouter `val Minus: ImageVector = strokeIcon("minus", "M5 12h14")` après `Plus`.

`SettingsRows.kt`, ajouter :

```kotlin
/** Ligne de choix (2.09) : titre à gauche, valeur et chevron à droite ; TalkBack lit « Thème, Automatique ». */
@Composable
fun ChoiceRow(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Row(
        modifier = modifier
            .then(rowModifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(start = 24.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, summary = null)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = VersoTheme.typography.body, color = colors.textSecondary)
            Icon(VersoIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(VersoDimens.iconSmall))
        }
    }
}
```

`ReadingSettingsSection.kt` :

```kotlin
package com.maximebier.verso.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.components.ChoiceRow
import com.maximebier.verso.ui.components.RadioRow
import com.maximebier.verso.ui.components.SettingsParagraph
import com.maximebier.verso.ui.components.SettingsSection
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.fontFamilyFor

/** Dialogue ouvert depuis une ligne de choix de la section Lecture. */
private enum class ReadingDialog { THEME, TEXT_SIZE, SCROLL }

@Composable
fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.AUTO -> R.string.settings_theme_auto
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.SEPIA -> R.string.settings_theme_sepia
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.BLACK -> R.string.settings_theme_black
    },
)

@Composable
private fun scrollModeLabel(mode: ScrollMode): String =
    stringResource(if (mode == ScrollMode.CONTINUOUS) R.string.settings_scroll_continuous else R.string.settings_scroll_pages)

/** Section « Lecture » des Paramètres (2.09) : phrase d'aide, polices, thème, taille et défilement par défaut. */
@Composable
fun ReadingSettingsSection(
    reading: ReadingSettings,
    themeMode: ThemeMode,
    onFontChange: (ReadingFont) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onDefaultScrollModeChange: (ScrollMode) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<ReadingDialog?>(null) }
    SettingsSection(title = stringResource(R.string.settings_section_reading)) {
        SettingsParagraph(text = stringResource(R.string.settings_reading_help))
        FontChoices(selected = reading.font, onSelect = onFontChange)
        ChoiceRow(
            title = stringResource(R.string.settings_theme),
            value = themeModeLabel(themeMode),
            onClick = { dialog = ReadingDialog.THEME },
        )
        ChoiceRow(
            title = stringResource(R.string.settings_text_size),
            value = reading.fontSizeSp.toString(),
            onClick = { dialog = ReadingDialog.TEXT_SIZE },
        )
        ChoiceRow(
            title = stringResource(R.string.settings_scroll),
            value = scrollModeLabel(reading.defaultScrollMode),
            onClick = { dialog = ReadingDialog.SCROLL },
        )
    }
    val close = { dialog = null }
    when (dialog) {
        ReadingDialog.THEME -> RadioDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { themeModeLabel(it) },
            selectedIndex = ThemeMode.entries.indexOf(themeMode),
            onSelect = { onThemeModeChange(ThemeMode.entries[it]) },
            onDismiss = close,
        )
        ReadingDialog.SCROLL -> RadioDialog(
            title = stringResource(R.string.settings_scroll),
            options = ScrollMode.entries.map { scrollModeLabel(it) },
            selectedIndex = ScrollMode.entries.indexOf(reading.defaultScrollMode),
            onSelect = { onDefaultScrollModeChange(ScrollMode.entries[it]) },
            onDismiss = close,
        )
        ReadingDialog.TEXT_SIZE -> TextSizeDialog(reading = reading, onFontSizeChange = onFontSizeChange, onDismiss = close)
        null -> Unit
    }
}

/** Trois polices en boutons radio, chacune montrée sur une phrase du livre (18 sp, interligne 1,45). */
@Composable
private fun FontChoices(selected: ReadingFont, onSelect: (ReadingFont) -> Unit) {
    val colors = VersoTheme.colors
    val legend = stringResource(R.string.settings_font)
    Text(
        text = legend,
        style = VersoTheme.typography.bodyStrong,
        color = colors.text,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp).semantics { heading() },
    )
    val fonts = listOf(
        Triple(ReadingFont.LITERATA, R.string.settings_font_literata, R.string.settings_font_literata_summary),
        Triple(ReadingFont.ATKINSON, R.string.settings_font_atkinson, R.string.settings_font_atkinson_summary),
        Triple(ReadingFont.SYSTEM, R.string.settings_font_system, R.string.settings_font_system_summary),
    )
    Column(Modifier.selectableGroup()) {
        fonts.forEach { (font, name, summary) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = font == selected, role = Role.RadioButton, onClick = { onSelect(font) })
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(
                    selected = font == selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.outline),
                    modifier = Modifier.size(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(name), style = VersoTheme.typography.rowTitle, color = colors.text)
                    Text(stringResource(summary), style = VersoTheme.typography.caption, color = colors.textSecondary)
                    Text(
                        text = stringResource(R.string.settings_font_sample),
                        style = TextStyle(fontFamily = fontFamilyFor(font), fontSize = 18.sp, lineHeight = 26.1.sp),
                        color = colors.text,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Choix unique appliqué aussitôt ; « Fermer » ferme le dialogue. */
@Composable
private fun RadioDialog(title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    VersoDialog(
        title = title,
        onDismissRequest = onDismiss,
        buttons = { VersoTextButton(text = stringResource(R.string.common_close), onClick = onDismiss) },
    ) {
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, label ->
                // Ligne de la tâche 10.1 (bouton radio, libellé en gras quand choisi, 48 dp), sans sa marge : le dialogue a la sienne.
                RadioRow(title = label, selected = index == selectedIndex, onClick = { onSelect(index) }, horizontalPadding = 0.dp)
            }
        }
    }
}

/** Taille du texte : − et + (48 dp, intitulés TalkBack), désactivés aux bornes, valeur au milieu. */
@Composable
private fun TextSizeDialog(reading: ReadingSettings, onFontSizeChange: (Int) -> Unit, onDismiss: () -> Unit) {
    VersoDialog(
        title = stringResource(R.string.settings_text_size),
        onDismissRequest = onDismiss,
        buttons = { VersoTextButton(text = stringResource(R.string.common_close), onClick = onDismiss) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersoIconButton(
                icon = VersoIcons.Minus,
                contentDescription = stringResource(R.string.settings_text_size_decrease),
                onClick = { onFontSizeChange(reading.smaller().fontSizeSp) },
                enabled = reading.canShrink,
            )
            Text(reading.fontSizeSp.toString(), style = VersoTheme.typography.screenTitle, color = VersoTheme.colors.text)
            VersoIconButton(
                icon = VersoIcons.Plus,
                contentDescription = stringResource(R.string.settings_text_size_increase),
                onClick = { onFontSizeChange(reading.larger().fontSizeSp) },
                enabled = reading.canGrow,
            )
        }
    }
}
```

`VersoIconButton` : vérifier sa signature (`grep -n "fun VersoIconButton" -A8 app/src/main/kotlin/com/maximebier/verso/ui/components/*.kt`). S'il n'a pas de paramètre `enabled`, l'ajouter (`enabled: Boolean = true`, transmis à `IconButton(enabled = enabled)`, icône en `colors.outline` quand désactivé) ; les appelants existants ne changent pas.

`SettingsViewModel.kt` :

```kotlin
data class SettingsUiState(
    val reopenLastBook: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val readingSettings: ReadingSettings = ReadingSettings(),
    val showStatistics: Boolean = true,
    val confirmingClearJournal: Boolean = false,
)
```

```kotlin
    val state: StateFlow<SettingsUiState> = combine(
        settings.reopenLastBook,
        settings.themeMode,
        settings.readingSettings,
        settings.showStatistics,
        confirming,
    ) { reopen, theme, reading, stats, confirmingClear ->
        SettingsUiState(
            reopenLastBook = reopen,
            themeMode = theme,
            readingSettings = reading,
            showStatistics = stats,
            confirmingClearJournal = confirmingClear,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onFontChange(value: ReadingFont) {
        viewModelScope.launch { settings.updateReadingSettings { it.copy(font = value) } }
    }

    fun onFontSizeChange(value: Int) {
        viewModelScope.launch { settings.updateReadingSettings { it.withFontSize(value) } }
    }

    fun onDefaultScrollModeChange(value: ScrollMode) {
        viewModelScope.launch { settings.updateReadingSettings { it.copy(defaultScrollMode = value) } }
    }

    fun onShowStatisticsChange(value: Boolean) {
        viewModelScope.launch { settings.setShowStatistics(value) }
    }
```

`SettingsScreen.kt` :

1. Paramètres ajoutés à `SettingsScreen` après `onOpenLicenses`, avant `modifier` :

```kotlin
    onFontChange: (ReadingFont) -> Unit = {},
    onFontSizeChange: (Int) -> Unit = {},
    onDefaultScrollModeChange: (ScrollMode) -> Unit = {},
    onShowStatisticsChange: (Boolean) -> Unit = {},
```

`SettingsDestination` les relie à `viewModel::onFontChange`, `viewModel::onFontSizeChange`, `viewModel::onDefaultScrollModeChange`, `viewModel::onShowStatisticsChange`.

2. Contenu de la colonne défilante, dans l'ordre de la maquette :

```kotlin
            ReadingSettingsSection(
                reading = state.readingSettings,
                themeMode = state.themeMode,
                onFontChange = onFontChange,
                onThemeModeChange = onThemeModeChange,
                onFontSizeChange = onFontSizeChange,
                onDefaultScrollModeChange = onDefaultScrollModeChange,
            )
            SettingsSection(title = stringResource(R.string.settings_section_startup)) {
                SwitchRow(
                    title = stringResource(R.string.settings_reopen_last_book),
                    summary = stringResource(R.string.settings_reopen_last_book_summary),
                    checked = state.reopenLastBook,
                    onCheckedChange = onReopenLastBookChange,
                )
            }
            SettingsSection(title = stringResource(R.string.settings_section_statistics)) {
                SwitchRow(
                    title = stringResource(R.string.settings_show_statistics),
                    summary = stringResource(R.string.settings_show_statistics_summary),
                    checked = state.showStatistics,
                    onCheckedChange = onShowStatisticsChange,
                )
            }
            // Confidentialité et À propos : inchangés.
```

3. Supprimer la section « Affichage », `ThemeSelector` et `ThemeMode.labelRes()` (tâche 10.1 ; `RadioRow` reste) ; retirer les imports devenus inutiles (`VersoSegmentedButton`, `ThemeMode` s'il ne sert plus qu'au paramètre — il reste importé pour `onThemeModeChange`).

- [ ] **Step 4 : lancer, vérifier le succès**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.ui.settings.*' --tests 'com.maximebier.verso.StringsTest' --tests 'com.maximebier.verso.screenshots.*'`
Expected : PASS (la capture V1 `1.09-parametres` change : section Lecture en tête).

- [ ] **Step 5 : commit**

```bash
git add app/src
git commit -m "Étape 14 (partie) : Paramètres V2, section Lecture et statistiques"
```

### Task 14.6 : captures 2.08 et 2.09, fin de l'étape 14

**Files:**
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/samples/SettingsSamples.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt`
- Modify: `docs/SPEC.md`, `docs/acceptance-v2.md`

**Interfaces:**
- Consumes : `DetailsV2Sample()` (13.6, désormais avec statistiques par `DetailsSamples.bovary`), `SettingsScreen`.
- Produces : `SettingsV2Sample()`.

- [ ] **Step 1 : échantillon et entrée du catalogue**

`SettingsSamples.kt` :

```kotlin
/** 2.09 : Paramètres V2 (Literata, Automatique, 20, Continu, statistiques affichées). */
@Composable
internal fun SettingsV2Sample() = SettingsSample(SettingsUiState(reopenLastBook = true, showStatistics = true))
```

`V2ScreenCatalog.kt` : ajouter `V2ScreenFixture(ScreenFixture("2.09-parametres") { SettingsV2Sample() }),` (l'entrée `2.08-details-du-livre` de 13.6 montre maintenant les statistiques).

- [ ] **Step 2 : lancer les captures et les comparer**

Run : `./gradlew :app:testDebugUnitTest --tests 'com.maximebier.verso.screenshots.*'`
Expected : PASS. Comparer `app/build/outputs/roborazzi/v2/2.08-details-du-livre-*.png` et `2.09-parametres-*.png` à `docs/design/screens/2.08-details-du-livre.png` et `2.09-parametres.png` : lignes « Temps de lecture / Vitesse moyenne / Temps restant estimé » avec filets, bouton « Voir le journal de lecture » à contour, puis Importé le, Taille, Format, Fichier d'origine, Supprimer le livre ; Paramètres : section Lecture, polices avec leur phrase dans leur police, lignes Thème / Taille du texte / Défilement avec valeur et chevron, Au démarrage, Statistiques. À 200 % : valeurs des statistiques passées sous leur libellé, phrases des polices sur plusieurs lignes, rien de coupé.

- [ ] **Step 3 : fin de l'étape 14**

1. `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` : vert, sortie gardée.
2. `docs/SPEC.md`, « Historique » :
   `- 2026-09-27 (étape 14) : vitesse mesurée utilisée pour tous les temps restants, même statistiques masquées ; « Voir le journal de lecture » ouvre le livre avec le journal ; section « Statistiques » absente sans session, vitesse et temps restant absents sans vitesse mesurée (moins d'une minute de lecture active) ; Paramètres : « Affichage » remplacé par la section Lecture (thème, taille et défilement par dialogues).`
3. Téléphone : `./gradlew :app:installDebug`, lancer Verso ; ouvrir un livre déjà lu en deux sessions ou plus (sinon lire deux fois une minute, en sortant de l'app entre les deux), revenir, ouvrir sa fiche : temps de lecture, nombre de sessions, vitesse, temps restant « à votre rythme » ; « Voir le journal de lecture » ouvre le livre avec le journal, « Reprendre ici » fonctionne ; Paramètres › désactiver « Afficher les statistiques » : la section disparaît de la fiche, une nouvelle session s'ajoute quand même au journal ; section Lecture conforme à 2.09 (choisir Atkinson, revenir à Literata ; Thème, Taille, Défilement). Captures dans `build/acceptance/`. Aucun réglage du téléphone touché.
4. Cocher les critères V2 14, 15 et 16 dans `docs/SPEC.md` et `docs/acceptance-v2.md` si conformes.
5. `git reset --soft <commit précédant la tâche 14.1>`, `git commit -m "Étape 14 : statistiques et Paramètres V2"`, `git push`.
