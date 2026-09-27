# Verso V2 — partie B : barre de lecture, feuille « Aa », mode pages (étapes 11 et 12)

> Plan principal : `docs/superpowers/plans/2026-09-27-verso-v2.md` (Global Constraints, Review Focus, contrat d'architecture). Design : `docs/superpowers/specs/2026-09-27-verso-v2-design.md`. Maquettes : `docs/design/html/2.01-barre-de-lecture.html`, `2.02-reglages-de-lecture.html`, `2.02-reglages-de-lecture-sombre.html`, `2.05-mode-pages.html` et leurs PNG dans `docs/design/screens/`.

Commande de build (Git Bash, depuis la racine du dépôt), notée `$G` plus bas :

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_SERIAL=192.168.1.10:5555   # téléphone en adb Wi-Fi ; si « device not found » : adb connect 192.168.1.10:5555
G=./gradlew
```

## Point de départ (état attendu après les étapes 9 et 10)

Cette partie consomme, sans les recréer, les noms suivants du contrat :

- `:core` : `ReadingSettings`, `ReadingFont`, `LineSpacing`, `Margins`, `ScrollMode`, `ReadingSettingsLimits` (`com.maximebier.verso.core.settings`).
- `data/ThemeMode.kt` : `AppTheme` (LIGHT, SEPIA, DARK, BLACK) et `ThemeMode` (AUTO, LIGHT, SEPIA, DARK, BLACK).
- `SettingsRepository.readingSettings`, `updateReadingSettings(transform)`, `themeMode`, `setThemeMode(mode)`.
- `BookEntity.scrollMode: String?` (migration 1 → 2 faite en 9.1), `BookDao.setScrollMode(id, scrollMode)`.
- `VersoTheme(theme: AppTheme, font: ReadingFont, content)`, `VersoTheme.typography`, `VersoTheme.theme`, `VersoTheme.colors`, `fontFamilyFor(font)`, `VersoPalette.Light/Sepia/Dark/Black`.
- `ReaderController.submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode)`, implémenté par `FragmentReaderController` (qui applique les préférences Readium sans recréer le fragment ; `style: StateFlow<ReaderStyle?>`) et par `FakeReaderController` (`submitted: MutableList<ReaderStyle>`).
- `data class ReaderStyle(val settings: ReadingSettings, val theme: AppTheme, val scrollMode: ScrollMode)` (`reader/ReaderController.kt`), `ReaderSurface(initialStyle: ReaderStyle, …)`.
- `ReaderViewModel` après 9.3 : paramètre `readingSettings: Flow<ReadingSettings>` (dernier), `ReaderUiState.readingSettings`, `onThemeChanged(theme: AppTheme)` (appelé par `ReaderScreen` avec `VersoTheme.theme`), `submitStyle()` privé, **seul appel à `submit`**.
- `VersoReadingPreferences.epub(settings, theme, scrollMode, fontScale, widthDp)`.
- `V2ScreenCatalog.kt` / `V2ScreenshotTest.kt` (tâche 10.2, type `V2ScreenFixture`) : cette partie y **ajoute** ses écrans.

Règle pour toute la partie : **un seul chemin pousse les réglages vers Readium**, `ReaderViewModel.submitStyle()` (9.3) → `ReaderController.submit`. Aucune tâche de cette partie n'ajoute d'autre appel à `submit` ; les préférences **initiales** du fragment restent construites dans `ReaderSurface` à partir de `initialStyle`.

## Décision reportée dans la spec (étape 12)

La confirmation « 25 secondes de lecture au nouvel endroit » ne peut pas marcher telle quelle en mode pages : on lit une page en 30 à 90 s (pause > `confirmMaxIdleGapMs` = 15 s entre deux tours) et chaque tour avance d'environ un écran (dérive > `confirmMaxDriftScreens` = 1). La machine à états **ne change pas de règles** ; elle reçoit des **seuils propres au mode pages** (`ReadingThresholds.forPages()` : pause jusqu'à 90 s, dérive jusqu'à 2,5 écrans), et une méthode `updateThresholds` pour la bascule en cours de lecture. Effet : après une navigation, deux tours de page au rythme de lecture (au moins 25 s d'écart) confirment la nouvelle position. La tâche 12.4 reporte cette décision dans `docs/SPEC.md` (« Réglages de la V2 » et « Historique »).

---

## Étape 11 : barre de lecture V2 et feuille « Aa »

### Task 11.1 : barre du bas à outils (Sommaire, Journal, Réglages ; place pour Rechercher)

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/AdaptiveGrid.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderBars.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderBarsTest.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/samples/ReaderSamples.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt` (appel de `ReaderBars`, `onSettingsClick` provisoire vers la tâche 11.2)

**Interfaces:**
- Consumes: `VersoTheme.typography` (`captionSemiBold`, `bodyStrong`), `VersoTheme.colors`, `VersoShapes.small`, `VersoIcons.ListBullets`, `VersoIcons.History`.
- Produces:
  - `ReaderBars(visible, state, onBack, onTocClick, onJournalClick, onSettingsClick: () -> Unit, modifier = Modifier, onBottomBarHeightChanged = {})`. L'outil Rechercher n'existe pas encore : l'étape 15 ajoute le paramètre `onSearchClick: () -> Unit` et insère l'outil dans `readerTools`, entre Journal et Réglages (emplacement marqué par un commentaire).
  - `data class ReaderTool(val label: String, val glyph: ToolGlyph, val onClick: () -> Unit)`, `sealed interface ToolGlyph { data class Icon(val vector: ImageVector); data class Text(val text: String) }`, `@Composable internal fun ReaderToolbar(tools: List<ReaderTool>, modifier: Modifier = Modifier)`.
  - `@Composable internal fun fittingColumns(labels: List<String>, style: TextStyle, maxWidth: Dp, gap: Dp, cellPadding: Dp, candidates: List<Int>): Int` et `@Composable internal fun <T> AdaptiveGrid(items: List<T>, columns: Int, gap: Dp, modifier: Modifier = Modifier, cell: @Composable (item: T, modifier: Modifier) -> Unit)` (réutilisés par la feuille 11.3).
  - Chaînes : `reader_settings` (« Réglages »), `reader_settings_glyph` (« Aa »).

- [ ] **Step 1 : chaînes**

Dans `strings.xml`, juste après `reader_journal` :

```xml
    <!-- V2 · écran 2.01 -->
    <string name="reader_settings">Réglages</string>
    <!-- Glyphe de l’outil Réglages et des pastilles de thème (maquettes 2.01, 2.02) ; masqué à TalkBack. -->
    <string name="reader_settings_glyph" translatable="false">Aa</string>
```

- [ ] **Step 2 : tests qui échouent**

Dans `ReaderBarsTest.kt`, remplacer `show(...)` et `buttonsInvokeCallbacksAndAreAtLeast48Dp`, et ajouter deux tests. Le thème de test devient celui du contrat (`VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA)`) ; remplacer aussi les deux autres `VersoTheme(darkTheme = false)` du fichier si l'étape 9 ne l'a pas déjà fait.

```kotlin
    private fun show(
        visible: Boolean,
        onBack: () -> Unit = {},
        onToc: () -> Unit = {},
        onJournal: () -> Unit = {},
        onSettings: () -> Unit = {},
    ) {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                ReaderBars(
                    visible = visible,
                    state = state,
                    onBack = onBack,
                    onTocClick = onToc,
                    onJournalClick = onJournal,
                    onSettingsClick = onSettings,
                )
            }
        }
    }

    @Test
    fun buttonsInvokeCallbacksAndAreAtLeast48Dp() {
        var back = 0
        var toc = 0
        var journal = 0
        var settings = 0
        show(visible = true, onBack = { back++ }, onToc = { toc++ }, onJournal = { journal++ }, onSettings = { settings++ })

        compose.onNodeWithContentDescription(context.getString(R.string.reader_back_to_library))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNode(hasText(context.getString(R.string.reader_toc)) and hasClickAction())
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNode(hasText(context.getString(R.string.reader_journal)) and hasClickAction())
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNode(hasText(context.getString(R.string.reader_settings)) and hasClickAction())
            .assertHeightIsAtLeast(48.dp).performClick()

        assertThat(listOf(back, toc, journal, settings)).containsExactly(1, 1, 1, 1).inOrder()
    }

    @Test
    fun barShowsThreeToolsUntilSearchArrives() {
        show(visible = true)
        // Étape 11 : Sommaire, Journal, Réglages ; Rechercher s'ajoute à l'étape 15 (jamais de commande inactive).
        compose.onAllNodes(hasClickAction() and hasText(context.getString(R.string.reader_settings))).assertCountEquals(1)
        compose.onAllNodes(hasClickAction() and hasText("Rechercher")).assertCountEquals(0)
    }

    @Test
    fun settingsGlyphIsHiddenFromTalkBack() {
        show(visible = true)
        // Le bouton se lit « Réglages, bouton », jamais « Aa ».
        compose.onNode(hasText(context.getString(R.string.reader_settings)) and hasClickAction())
            .assert(hasText(context.getString(R.string.reader_settings_glyph)).not())
    }
```

Imports à ajouter : `androidx.compose.ui.test.assertCountEquals`, `androidx.compose.ui.test.assertWidthIsAtLeast`, `androidx.compose.ui.test.hasClickAction`, `androidx.compose.ui.test.hasText`, `com.maximebier.verso.core.settings.ReadingFont`, `com.maximebier.verso.data.AppTheme`.

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReaderBarsTest"`
Expected: FAIL à la compilation (`onSettingsClick` inconnu, `reader_settings` absent si l'étape 1 n'est pas faite).

- [ ] **Step 3 : grille adaptative**

`AdaptiveGrid.kt` :

```kotlin
package com.maximebier.verso.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

/**
 * Premier nombre de colonnes de [candidates] (du plus grand au plus petit) où chaque libellé tient sur une ligne
 * dans sa cellule. Le texte système agrandi fait passer la grille à moins de colonnes au lieu de couper un mot.
 */
@Composable
internal fun fittingColumns(
    labels: List<String>,
    style: TextStyle,
    maxWidth: Dp,
    gap: Dp,
    cellPadding: Dp,
    candidates: List<Int>,
): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return candidates.firstOrNull { count ->
        val cell = (maxWidth - gap * (count - 1)) / count - cellPadding * 2
        val roomPx = with(density) { cell.toPx() }
        labels.all { measurer.measure(it, style, maxLines = 1).size.width <= roomPx }
    } ?: candidates.last()
}

/** Grille de [columns] colonnes égales ; les cellules d’une rangée ont la même hauteur. */
@Composable
internal fun <T> AdaptiveGrid(
    items: List<T>,
    columns: Int,
    gap: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { item -> cell(item, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
```

- [ ] **Step 4 : barre du bas (2.01)**

Dans `ReaderBars.kt` :

1. Signature de `ReaderBars` (remplace l'actuelle) et passage à `ReaderBottomBar` :

```kotlin
@Composable
fun ReaderBars(
    visible: Boolean,
    state: ReaderBarsState,
    onBack: () -> Unit,
    onTocClick: () -> Unit,
    onJournalClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBottomBarHeightChanged: (Int) -> Unit = {},
) {
```

et dans le second `AnimatedVisibility` :

```kotlin
            ReaderBottomBar(
                state = state,
                tools = readerTools(onTocClick, onJournalClick, onSettingsClick),
                modifier = Modifier.onSizeChanged { onBottomBarHeightChanged(it.height) },
            )
```

2. Remplacer entièrement `ReaderBottomBar` et ajouter les outils :

```kotlin
/** Outil de la barre du bas (2.01) : icône, ou glyphe « Aa », au-dessus du libellé. */
data class ReaderTool(val label: String, val glyph: ToolGlyph, val onClick: () -> Unit)

sealed interface ToolGlyph {
    data class Icon(val vector: ImageVector) : ToolGlyph
    data class Text(val text: String) : ToolGlyph
}

/** Ordre de la maquette 2.01 : Sommaire, Journal, Rechercher, Réglages. */
@Composable
private fun readerTools(
    onTocClick: () -> Unit,
    onJournalClick: () -> Unit,
    onSettingsClick: () -> Unit,
): List<ReaderTool> = buildList {
    add(ReaderTool(stringResource(R.string.reader_toc), ToolGlyph.Icon(VersoIcons.ListBullets), onTocClick))
    add(ReaderTool(stringResource(R.string.reader_journal), ToolGlyph.Icon(VersoIcons.History), onJournalClick))
    // Étape 15 : outil Rechercher (icône VersoIcons.Search, libellé R.string.reader_search) inséré ici.
    add(ReaderTool(stringResource(R.string.reader_settings), ToolGlyph.Text(stringResource(R.string.reader_settings_glyph)), onSettingsClick))
}

@Composable
private fun ReaderBottomBar(state: ReaderBarsState, tools: List<ReaderTool>, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Column(modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                // 2.01 : 16 dp en haut et en bas, 20 dp sur les côtés.
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.common_percent_read, state.readingPercent),
                    style = VersoTheme.typography.bodyStrong,
                    color = colors.text,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = remainingTimeText(state.remainingMinutes),
                    style = VersoTheme.typography.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
            VersoProgressBar(fraction = state.progression, current = true)
            ReaderToolbar(tools)
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBarsIgnoringVisibility))
    }
}

private val ToolGap = 4.dp
private val ToolCellPadding = 4.dp
private val ToolMinHeight = 64.dp
private val ToolIconSize = 22.dp

/**
 * Outils en une rangée de colonnes égales (2.01 : 64 dp de haut, icône 22 dp, texte 14 sp 600). Quand un libellé ne
 * tient plus (texte système agrandi), deux colonnes, puis une, plutôt que de couper « Rechercher ».
 */
@Composable
internal fun ReaderToolbar(tools: List<ReaderTool>, modifier: Modifier = Modifier) {
    val style = VersoTheme.typography.captionSemiBold
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = fittingColumns(
            labels = tools.map { it.label },
            style = style,
            maxWidth = maxWidth,
            gap = ToolGap,
            cellPadding = ToolCellPadding,
            candidates = listOf(tools.size, 2, 1).distinct(),
        )
        AdaptiveGrid(items = tools, columns = columns, gap = ToolGap) { tool, cellModifier ->
            ToolButton(tool, cellModifier)
        }
    }
}

@Composable
private fun ToolButton(tool: ReaderTool, modifier: Modifier) {
    val colors = VersoTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = ToolMinHeight)
            .clip(VersoShapes.small)
            .clickable(role = Role.Button, onClick = tool.onClick)
            .padding(horizontal = ToolCellPadding, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        when (val glyph = tool.glyph) {
            is ToolGlyph.Icon -> Icon(glyph.vector, contentDescription = null, tint = colors.text, modifier = Modifier.size(ToolIconSize))
            // 2.01 : « Aa » 20 sp 700, décoratif ; le bouton se lit par son libellé.
            is ToolGlyph.Text -> Text(
                text = glyph.text,
                style = VersoTheme.typography.bodyStrong.copy(fontSize = 20.sp, lineHeight = 22.sp),
                color = colors.text,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(text = tool.label, style = VersoTheme.typography.captionSemiBold, color = colors.text, textAlign = TextAlign.Center)
    }
}
```

3. Imports à ajouter à `ReaderBars.kt` : `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.BoxWithConstraints`, `androidx.compose.foundation.layout.size`, `androidx.compose.material3.Icon`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.clearAndSetSemantics`, `com.maximebier.verso.ui.theme.VersoShapes`. Retirer ceux devenus inutiles (`FlowRow`, `ExperimentalLayoutApi` sur `ReaderBottomBar`, `OutlinedPillButton`, et `VersoTypography` si l'étape 9 ne l'a pas déjà remplacé par `VersoTheme.typography`).

4. `ReaderScreen.kt`, appel de `ReaderBars` : ajouter `onSettingsClick = viewModel::showReadingSettings,` (créé en 11.2 ; pour que 11.1 compile seule, écrire provisoirement `onSettingsClick = {},` et le remplacer en 11.2).

5. `ReaderSamples.kt`, `ReaderBarsSample` : `ReaderBars(visible = true, state = readerBarsSampleState, onBack = {}, onTocClick = {}, onJournalClick = {}, onSettingsClick = {})`.

- [ ] **Step 5 : tests verts**

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReaderBarsTest"`
Expected: PASS (7 tests).

- [ ] **Step 6 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/ui/reader/AdaptiveGrid.kt app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderBars.kt app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderBarsTest.kt app/src/test/kotlin/com/maximebier/verso/screenshots/samples/ReaderSamples.kt
git commit -m "Étape 11 (partie) : barre du bas à outils"
```

### Task 11.2 : écritures des réglages et du thème depuis le lecteur, défilement du livre

Le branchement des réglages vers Readium existe depuis la tâche 9.3 (partie A) et **n'est pas recréé** : `ReaderStyle`, `ReaderController.submit`, `ReaderViewModel.onThemeChanged(theme: AppTheme)`, `ReaderUiState.readingSettings`, `submitStyle()` (seul appel à `submit`), `FakeReaderController.submitted: MutableList<ReaderStyle>`, `ReaderSurface(initialStyle: ReaderStyle, …)`. Cette tâche l'étend :

- le ViewModel reçoit le `SettingsRepository` (à la place du flux `readingSettings` de 9.3) pour **écrire** les réglages et le thème depuis la feuille « Aa » ;
- `ReaderUiState.scrollMode` porte le défilement du livre (sinon le défaut des Paramètres), et `submitStyle()` l'envoie au lieu de `ScrollMode.CONTINUOUS`.

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderViewModelTest.kt`, `app/src/test/kotlin/com/maximebier/verso/ui/reader/JournalResumeTest.kt` (s'il construit un `ReaderViewModel`)

**Interfaces:**
- Consumes: `SettingsRepository.readingSettings/updateReadingSettings/themeMode/setThemeMode` (9.1, V1), `ReaderStyle`, `onThemeChanged`, `submitStyle()`, `FakeReaderController.submitted` (9.3), `BookEntity.scrollMode` (9.1).
- Produces:
  - Constructeur, en diff de celui de 9.3 :

    ```diff
     class ReaderViewModel(
         private val bookId: Long,
         private val books: BookRepository,
         private val sessions: SessionRepository,
         private val openPublication: suspend (File) -> Result<Publication>,
         private val clock: () -> Long,
         private val locationTexts: LocationTexts,
         private val reportOpenFailure: (title: String) -> Unit = {},
    -    private val readingSettings: Flow<ReadingSettings> = flowOf(ReadingSettings()),
    +    private val settings: SettingsRepository,
     ) : ViewModel() {
    ```

    Appels à mettre à jour : `ReaderViewModel.factory(bookId)` (`settings = container.settings,` à la place de `readingSettings = container.settings.readingSettings,`), la fabrique `factory(…)` de `ReaderViewModelTest` et celle de `JournalResumeTest`. `VersoNavHost` passe par `ReaderViewModel.factory(bookId)` et ne change pas.
  - `ReaderViewModel.themeMode: StateFlow<ThemeMode>`, `updateReadingSettings(transform: (ReadingSettings) -> ReadingSettings)`, `setThemeMode(mode: ThemeMode)`, `showReadingSettings()`, `hideReadingSettings()`.
  - `ReaderUiState.settingsVisible: Boolean = false`, `ReaderUiState.scrollMode: ScrollMode = ScrollMode.CONTINUOUS` (mode du livre, sinon `defaultScrollMode`).

- [ ] **Step 1 : fabrique de test avec un vrai `SettingsRepository`**

Dans `ReaderViewModelTest.kt` :

```kotlin
    @get:Rule val tmp = TemporaryFolder()

    /** Un seul DataStore par test : deux instances sur le même fichier lèveraient une exception. */
    private var settingsRepository: SettingsRepository? = null

    private fun TestScope.testSettings(): SettingsRepository =
        settingsRepository ?: SettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "reader.preferences_pb") }),
        ).also { settingsRepository = it }
```

Dans `setUp()`, ajouter `settingsRepository = null`. Dans `factory(...)`, supprimer le paramètre `readingSettings` ajouté en 9.3 et passer `settings = testSettings(),` (la fabrique devient une extension de `TestScope` si elle ne l'est pas déjà ; ses appels sont tous dans des `runTest`). Imports : `androidx.datastore.preferences.core.PreferenceDataStoreFactory`, `com.maximebier.verso.data.SettingsRepository`, `org.junit.Rule`, `org.junit.rules.TemporaryFolder` ; supprimer `kotlinx.coroutines.flow.flowOf` s'il n'est plus utilisé.

Le test de 9.3 `settingsAndThemeAreSubmittedWithoutMovingTheReadingPosition` pilotait un `MutableStateFlow<ReadingSettings>` : remplacer `val settings = MutableStateFlow(ReadingSettings())` et `factory(id, readingSettings = settings)` par `factory(id)`, et `settings.value = ReadingSettings(fontSizeSp = 24)` par

```kotlin
        testSettings().updateReadingSettings { it.withFontSize(24) }
        testSettings().readingSettings.first { it.fontSizeSp == 24 }
```

Le reste du test (assertions sur `fake.submitted`) ne change pas.

Même changement dans `JournalResumeTest` s'il construit un `ReaderViewModel` (`grep -n "ReaderViewModel(" app/src/test/kotlin/com/maximebier/verso/ui/reader/JournalResumeTest.kt`).

- [ ] **Step 2 : tests qui échouent**

Ajouter à `ReaderViewModelTest` :

```kotlin
    @Test
    fun readingSettingsChangedFromTheReaderAreSavedAndSubmittedWithoutMovingReading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)

        viewModel.onThemeChanged(AppTheme.SEPIA)
        viewModel.onReaderReady(fake)
        runCurrent()
        assertThat(fake.submitted.last()).isEqualTo(ReaderStyle(ReadingSettings(), AppTheme.SEPIA, ScrollMode.CONTINUOUS))

        viewModel.updateReadingSettings { it.larger().copy(lineSpacing = LineSpacing.AIRY) }
        runCurrent()
        testSettings().readingSettings.first { it.fontSizeSp == 21 }
        runCurrent()

        val last = fake.submitted.last()
        assertThat(last.settings.fontSizeSp).isEqualTo(21)
        assertThat(last.settings.lineSpacing).isEqualTo(LineSpacing.AIRY)
        assertThat(viewModel.uiState.value.readingPercent).isEqualTo(30)
        assertThat(viewModel.uiState.value.returnCard).isNull()
        assertThat(fake.goCalls).isEmpty()
        store.clear()
    }

    @Test
    fun readingSettingsSheetOpensWithoutBarsAndCloses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        viewModel.toggleBars()

        viewModel.showReadingSettings()
        assertThat(viewModel.uiState.value.settingsVisible).isTrue()
        assertThat(viewModel.uiState.value.barsVisible).isFalse()

        viewModel.hideReadingSettings()
        assertThat(viewModel.uiState.value.settingsVisible).isFalse()
        store.clear()
    }

    @Test
    fun themeChosenInTheSheetIsSaved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val id = books.insert(testBook(readingLocatorJson = null, progression = 0.0))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }

        viewModel.setThemeMode(ThemeMode.BLACK)
        runCurrent()

        assertThat(testSettings().themeMode.first()).isEqualTo(ThemeMode.BLACK)
        assertThat(viewModel.themeMode.first { it == ThemeMode.BLACK }).isEqualTo(ThemeMode.BLACK)
        store.clear()
    }

    @Test
    fun bookScrollModeIsSubmittedOtherwiseTheDefaultOne() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        testSettings().updateReadingSettings { it.copy(defaultScrollMode = ScrollMode.PAGES) }
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        assertThat(viewModel.uiState.first { !it.loading }.scrollMode).isEqualTo(ScrollMode.PAGES)
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()

        assertThat(fake.submitted.last().scrollMode).isEqualTo(ScrollMode.PAGES)
        store.clear()
    }
```

Imports : `com.maximebier.verso.core.settings.LineSpacing`, `com.maximebier.verso.core.settings.ReadingSettings`, `com.maximebier.verso.core.settings.ScrollMode`, `com.maximebier.verso.data.AppTheme`, `com.maximebier.verso.data.ThemeMode`, `com.maximebier.verso.reader.ReaderStyle`.

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReaderViewModelTest"`
Expected: FAIL à la compilation (`settings`, `updateReadingSettings`, `setThemeMode`, `showReadingSettings`, `scrollMode`… inconnus).

- [ ] **Step 3 : implémentation**

Dans `ReaderViewModel.kt` :

1. `ReaderUiState` : ajouter après `journalVisible` :

```kotlin
    /** Feuille « Réglages de lecture » (2.02) ouverte. */
    val settingsVisible: Boolean = false,
    /** Défilement de ce livre (`books.scrollMode`), à défaut celui des Paramètres (`defaultScrollMode`). */
    val scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
```

2. Constructeur : remplacer `private val readingSettings: Flow<ReadingSettings> = flowOf(ReadingSettings()),` par `private val settings: SettingsRepository,` (diff ci-dessus). Dans le `launch` posé en 9.3 qui collecte les réglages, remplacer `readingSettings.collect` par `settings.readingSettings.collect` ; rien d'autre ne change dans ce bloc (`_uiState.update { it.copy(readingSettings = …) }`, `submitStyle()`, `refreshDistance()`).

3. Champ public, après les champs de 9.3 :

```kotlin
    /** Thème choisi (Automatique, Clair, Sépia, Sombre, Noir), pour la feuille « Aa ». */
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AUTO)
```

4. `start(...)`, avant le `_uiState.update` qui pose `publication = publication` :

```kotlin
        val scrollMode = book.scrollMode?.let { stored -> ScrollMode.entries.firstOrNull { it.name == stored } }
            ?: settings.readingSettings.first().defaultScrollMode
```

et dans cet `update` : `scrollMode = scrollMode,`.

5. `submitStyle()` (9.3) : remplacer `ScrollMode.CONTINUOUS` par `_uiState.value.scrollMode` et supprimer le commentaire « Mode pages : étape 12… » :

```kotlin
    /** Réglages courants vers le moteur : jamais un saut ni un geste pour la machine à états. */
    private fun submitStyle() {
        val reader = controller ?: return
        val current = theme ?: return
        reader.submit(_uiState.value.readingSettings, current, _uiState.value.scrollMode)
    }
```

6. Méthodes publiques, après `hideJournal()` :

```kotlin
    /** « Aa » : la feuille s’ouvre, la barre se referme (maquette 2.02 : texte seul derrière la feuille). */
    fun showReadingSettings() {
        sessionCoordinator?.onInteraction()
        _uiState.update { it.copy(settingsVisible = true, barsVisible = false) }
    }

    fun hideReadingSettings() = _uiState.update { it.copy(settingsVisible = false) }

    /** Police, taille, interligne, marges : communs à tous les livres (spec) ; appliqués en direct par la collecte de 9.3. */
    fun updateReadingSettings(transform: (ReadingSettings) -> ReadingSettings) {
        sessionCoordinator?.onInteraction()
        viewModelScope.launch { settings.updateReadingSettings(transform) }
    }

    /** Thème de toute l’app (même réglage que les Paramètres) ; revient par `onThemeChanged` via `VersoTheme.theme`. */
    fun setThemeMode(mode: ThemeMode) {
        sessionCoordinator?.onInteraction()
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
```

7. `factory(bookId)` : `settings = container.settings,` à la place de `readingSettings = container.settings.readingSettings,`.

8. Imports : ajouter `com.maximebier.verso.data.SettingsRepository`, `com.maximebier.verso.data.ThemeMode`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.stateIn` (s'ils manquent) ; supprimer `kotlinx.coroutines.flow.flowOf` s'il n'est plus utilisé.

9. `ReaderScreen.kt` : l'appel `LaunchedEffect(theme) { viewModel.onThemeChanged(theme) }` de 9.3 (avec `val theme = VersoTheme.theme` depuis 10.1) reste tel quel. Ajouter, après `BackHandler(enabled = state.tocVisible) …` :

```kotlin
    BackHandler(enabled = state.settingsVisible) { viewModel.hideReadingSettings() }
```

et dans l'appel de `ReaderBars` : `onSettingsClick = viewModel::showReadingSettings,`. `initialStyle` de `ReaderSurface` devient `ReaderStyle(state.readingSettings, theme, state.scrollMode)`.

- [ ] **Step 4 : tests verts**

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.*"`
Expected: PASS (le test de 9.3 adapté, les quatre nouveaux, `JournalResumeTest`).

- [ ] **Step 5 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/ui/reader app/src/test/kotlin/com/maximebier/verso/ui/reader
git commit -m "Étape 11 (partie) : réglages et thème écrits depuis le lecteur, défilement du livre"
```

### Task 11.3 : feuille « Réglages de lecture » (2.02)

**Files:**
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingSettingsSheet.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReadingSettingsSheetTest.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/components/VersoIcons.kt` (icône `Minus`)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt`

**Interfaces:**
- Consumes: `ReadingSettings` (`larger()`, `smaller()`, `canGrow`, `canShrink`), `ThemeMode.entries`, `fontFamilyFor`, `VersoPalette`, `VersoSegmentedButton`, `fittingColumns`, `AdaptiveGrid`, les méthodes de 11.2.
- Produces:
  - `data class ReadingSettingsSheetState(val settings: ReadingSettings, val themeMode: ThemeMode, val scrollMode: ScrollMode)`.
  - `@Composable fun ReadingSettingsSheet(state, onFontSelected: (ReadingFont) -> Unit, onSmaller: () -> Unit, onLarger: () -> Unit, onThemeSelected: (ThemeMode) -> Unit, onLineSpacingSelected: (LineSpacing) -> Unit, onMarginsSelected: (Margins) -> Unit, onDismiss: () -> Unit, scrollModeRow: (@Composable () -> Unit)? = null)` — `scrollModeRow` : rangée Défilement, fournie par la tâche 12.4.
  - `@Composable internal fun ReadingSettingsContent(...)` (mêmes paramètres sans `onDismiss`, plus `onClose`), pour les captures.
  - `VersoIcons.Minus`.
  - Chaînes `reader_settings_*` ci-dessous.

- [ ] **Step 1 : chaînes et icône**

`strings.xml`, sous le bloc 2.01 :

```xml
    <!-- V2 · écran 2.02 -->
    <string name="reader_settings_title">Réglages de lecture</string>
    <string name="reader_settings_font">Police</string>
    <string name="reader_settings_font_literata">Literata</string>
    <string name="reader_settings_font_atkinson">Atkinson</string>
    <string name="reader_settings_font_system">Système</string>
    <string name="reader_settings_text_size">Taille du texte</string>
    <string name="reader_settings_text_smaller">Réduire le texte</string>
    <string name="reader_settings_text_larger">Agrandir le texte</string>
    <string name="reader_settings_text_size_value">%1$d</string>
    <string name="reader_settings_theme">Thème</string>
    <string name="reader_settings_theme_auto">Auto</string>
    <string name="reader_settings_theme_light">Clair</string>
    <string name="reader_settings_theme_sepia">Sépia</string>
    <string name="reader_settings_theme_dark">Sombre</string>
    <string name="reader_settings_theme_black">Noir</string>
    <string name="reader_settings_line_spacing">Interligne</string>
    <string name="reader_settings_line_spacing_tight">Serré</string>
    <string name="reader_settings_line_spacing_normal">Normal</string>
    <string name="reader_settings_line_spacing_airy">Aéré</string>
    <string name="reader_settings_margins">Marges</string>
    <string name="reader_settings_margins_narrow">Étroites</string>
    <string name="reader_settings_margins_normal">Normales</string>
    <string name="reader_settings_margins_wide">Larges</string>
```

`VersoIcons.kt`, après `Plus` : `val Minus: ImageVector = strokeIcon("minus", "M5 12h14")`.

- [ ] **Step 2 : tests qui échouent**

`ReadingSettingsSheetTest.kt` (on teste le contenu, `ReadingSettingsContent`, sans la fenêtre modale) :

```kotlin
package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ReadingSettingsLimits
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingSettingsSheetTest {

    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    /** Contenu de la feuille branché sur un état local, comme le fait ReaderScreen avec le ViewModel. */
    private fun show(initial: ReadingSettingsSheetState = ReadingSettingsSheetState(ReadingSettings(), ThemeMode.AUTO, ScrollMode.CONTINUOUS)): () -> ReadingSettingsSheetState {
        var state by mutableStateOf(initial)
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                ReadingSettingsContent(
                    state = state,
                    onFontSelected = { state = state.copy(settings = state.settings.copy(font = it)) },
                    onSmaller = { state = state.copy(settings = state.settings.smaller()) },
                    onLarger = { state = state.copy(settings = state.settings.larger()) },
                    onThemeSelected = { state = state.copy(themeMode = it) },
                    onLineSpacingSelected = { state = state.copy(settings = state.settings.copy(lineSpacing = it)) },
                    onMarginsSelected = { state = state.copy(settings = state.settings.copy(margins = it)) },
                    onClose = {},
                )
            }
        }
        return { state }
    }

    @Test
    fun defaultsAreSelectedAsInTheMockup() {
        show()
        compose.onNodeWithText(s(R.string.reader_settings_font_literata)).assertIsSelected()
        compose.onNodeWithText(s(R.string.reader_settings_theme_auto)).assertIsSelected()
        compose.onNodeWithText("20").assertExists()
        compose.onNodeWithText(s(R.string.reader_settings_line_spacing_normal)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(s(R.string.reader_settings_margins_normal)).performScrollTo().assertIsSelected()
    }

    @Test
    fun everyChoiceIsAppliedAtOnce() {
        val state = show()
        compose.onNodeWithText(s(R.string.reader_settings_font_atkinson)).performClick()
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_larger)).performClick()
        compose.onNodeWithText(s(R.string.reader_settings_theme_sepia)).performClick()
        compose.onNodeWithText(s(R.string.reader_settings_line_spacing_airy)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.reader_settings_margins_wide)).performScrollTo().performClick()

        assertThat(state()).isEqualTo(
            ReadingSettingsSheetState(
                ReadingSettings(font = ReadingFont.ATKINSON, fontSizeSp = 21, lineSpacing = LineSpacing.AIRY, margins = Margins.WIDE),
                ThemeMode.SEPIA,
                ScrollMode.CONTINUOUS,
            ),
        )
        compose.onNodeWithText("21").assertExists()
    }

    @Test
    fun sizeButtonsAreDisabledAtTheLimits() {
        show(ReadingSettingsSheetState(ReadingSettings(fontSizeSp = ReadingSettingsLimits.MAX_FONT_SIZE_SP), ThemeMode.AUTO, ScrollMode.CONTINUOUS))
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_larger)).assertIsNotEnabled()
    }

    @Test
    fun controlsAreAtLeast48DpAndIconButtonsAreLabelled() {
        show()
        compose.onNodeWithContentDescription(s(R.string.common_close)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_smaller)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(s(R.string.reader_settings_text_larger)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.reader_settings_font_system)).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(s(R.string.reader_settings_theme_black)).assertHeightIsAtLeast(48.dp)
    }
}
```

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReadingSettingsSheetTest"`
Expected: FAIL à la compilation (`ReadingSettingsContent` absent).

Note : `onNodeWithText(label)` trouve le nœud fusionné de la carte ou de la pastille (`selectable` fusionne ses enfants), d'où `assertIsSelected` et `performClick` sur le libellé.

- [ ] **Step 3 : implémentation**

`ReadingSettingsSheet.kt` :

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class)

package com.maximebier.verso.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSegmentedButton
import com.maximebier.verso.ui.theme.VersoColors
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoPalette
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.fontFamilyFor

data class ReadingSettingsSheetState(
    val settings: ReadingSettings,
    val themeMode: ThemeMode,
    val scrollMode: ScrollMode,
)

/**
 * Feuille « Réglages de lecture » (2.02) : ouverte à mi-hauteur **sans voile**, le texte reste visible au-dessus et
 * change en direct. En haut Police, Taille, Thème ; en faisant glisser, Interligne, Marges et Défilement.
 */
@Composable
fun ReadingSettingsSheet(
    state: ReadingSettingsSheetState,
    onFontSelected: (ReadingFont) -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onLineSpacingSelected: (LineSpacing) -> Unit,
    onMarginsSelected: (Margins) -> Unit,
    onDismiss: () -> Unit,
    scrollModeRow: (@Composable () -> Unit)? = null,
) {
    val colors = VersoTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Mi-hauteur d’abord (skipPartiallyExpanded = false), dépliable en glissant vers le haut.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        sheetMaxWidth = Dp.Unspecified,
        shape = VersoShapes.sheetTop,
        containerColor = colors.surface,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        // Sans voile (spec) : l’effet se voit sur le texte.
        scrimColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        ReadingSettingsContent(
            state = state,
            onFontSelected = onFontSelected,
            onSmaller = onSmaller,
            onLarger = onLarger,
            onThemeSelected = onThemeSelected,
            onLineSpacingSelected = onLineSpacingSelected,
            onMarginsSelected = onMarginsSelected,
            onClose = onDismiss,
            scrollModeRow = scrollModeRow,
        )
    }
}

@Composable
internal fun ReadingSettingsContent(
    state: ReadingSettingsSheetState,
    onFontSelected: (ReadingFont) -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onLineSpacingSelected: (LineSpacing) -> Unit,
    onMarginsSelected: (Margins) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrollModeRow: (@Composable () -> Unit)? = null,
) {
    val colors = VersoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        // Poignée décorative (comme VersoBottomSheet) : la feuille se glisse, « Fermer » la referme.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 4.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.outline),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = stringResource(R.string.reader_settings_title),
                style = VersoTheme.typography.sheetTitle,
                color = colors.text,
                modifier = Modifier.weight(1f).padding(top = 10.dp).semantics { heading() },
            )
            VersoIconButton(icon = VersoIcons.Close, contentDescription = stringResource(R.string.common_close), onClick = onClose)
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.reader_settings_font))
            FontCards(selected = state.settings.font, onSelect = onFontSelected)
            TextSizeRow(state.settings, onSmaller, onLarger)
            SectionLabel(stringResource(R.string.reader_settings_theme))
            ThemeSwatches(selected = state.themeMode, onSelect = onThemeSelected)
            HorizontalDivider(thickness = 1.dp, color = colors.divider, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledSegments(
                    label = stringResource(R.string.reader_settings_line_spacing),
                    options = listOf(
                        stringResource(R.string.reader_settings_line_spacing_tight),
                        stringResource(R.string.reader_settings_line_spacing_normal),
                        stringResource(R.string.reader_settings_line_spacing_airy),
                    ),
                    selectedIndex = LineSpacing.entries.indexOf(state.settings.lineSpacing),
                    onSelect = { onLineSpacingSelected(LineSpacing.entries[it]) },
                )
                LabeledSegments(
                    label = stringResource(R.string.reader_settings_margins),
                    options = listOf(
                        stringResource(R.string.reader_settings_margins_narrow),
                        stringResource(R.string.reader_settings_margins_normal),
                        stringResource(R.string.reader_settings_margins_wide),
                    ),
                    selectedIndex = Margins.entries.indexOf(state.settings.margins),
                    onSelect = { onMarginsSelected(Margins.entries[it]) },
                )
                scrollModeRow?.invoke()
            }
        }
    }
}

/** Intitulé de section (2.02) : 14 sp 700, couleur secondaire. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = VersoTheme.typography.captionBold,
        color = VersoTheme.colors.textSecondary,
        modifier = modifier.semantics { heading() },
    )
}

/** Segments avec intitulé au-dessus, et un complément à droite (« Pour ce livre », tâche 12.4). */
@Composable
internal fun LabeledSegments(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    trailing: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(label, Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = VersoTheme.typography.caption, color = VersoTheme.colors.textSecondary)
            }
        }
        VersoSegmentedButton(options = options, selectedIndex = selectedIndex, onSelect = onSelect, groupLabel = label)
    }
}

private val CardGap = 8.dp
private val CardPadding = 8.dp

/** Trois cartes « Aa » dans leur police (2.02) ; sélection = bordure accent 2 dp, gras, coche. */
@Composable
private fun FontCards(selected: ReadingFont, onSelect: (ReadingFont) -> Unit) {
    val labels = ReadingFont.entries.map { fontLabel(it) }
    val group = stringResource(R.string.reader_settings_font)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = fittingColumns(labels, VersoTheme.typography.captionBold, maxWidth, CardGap, CardPadding, listOf(3, 1))
        AdaptiveGrid(
            items = ReadingFont.entries,
            columns = columns,
            gap = CardGap,
            modifier = Modifier.semantics { contentDescription = group }.selectableGroup(),
        ) { font, cellModifier ->
            FontCard(font, fontLabel(font), font == selected, { onSelect(font) }, cellModifier)
        }
    }
}

@Composable
private fun fontLabel(font: ReadingFont): String = stringResource(
    when (font) {
        ReadingFont.LITERATA -> R.string.reader_settings_font_literata
        ReadingFont.ATKINSON -> R.string.reader_settings_font_atkinson
        ReadingFont.SYSTEM -> R.string.reader_settings_font_system
    },
)

@Composable
private fun FontCard(font: ReadingFont, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    Box(
        modifier
            .heightIn(min = 80.dp)
            .clip(VersoShapes.small)
            .background(if (selected) colors.background else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.outline, VersoShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(CardPadding),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_glyph),
                fontFamily = fontFamilyFor(font),
                fontSize = 28.sp,
                lineHeight = 28.sp,
                color = colors.text,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = label,
                style = if (selected) VersoTheme.typography.captionBold else VersoTheme.typography.caption.copy(fontWeight = FontWeight(500)),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Icon(
                VersoIcons.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
            )
        }
    }
}

/** « Taille du texte » : − valeur + ; boutons de 48 dp, grisés aux bornes (14 et 32). */
@Composable
private fun TextSizeRow(settings: ReadingSettings, onSmaller: () -> Unit, onLarger: () -> Unit) {
    val colors = VersoTheme.colors
    val label = stringResource(R.string.reader_settings_text_size)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = VersoTheme.typography.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
        Row(
            Modifier.semantics(mergeDescendants = false) { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RoundOutlinedIconButton(VersoIcons.Minus, stringResource(R.string.reader_settings_text_smaller), settings.canShrink, onSmaller)
            Text(
                text = stringResource(R.string.reader_settings_text_size_value, settings.fontSizeSp),
                style = VersoTheme.typography.bookTitleStrong,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            RoundOutlinedIconButton(VersoIcons.Plus, stringResource(R.string.reader_settings_text_larger), settings.canGrow, onLarger)
        }
    }
}

@Composable
private fun RoundOutlinedIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = VersoTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(VersoDimens.controlMin).border(1.dp, colors.outline, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = colors.text, disabledContentColor = colors.textSecondary),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(VersoDimens.iconSmall))
    }
}

private val SwatchGap = 4.dp

/** Cinq pastilles (2.02) dans l’ordre de ThemeMode : Auto, Clair, Sépia, Sombre, Noir. */
@Composable
private fun ThemeSwatches(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = ThemeMode.entries.map { themeLabel(it) }
    val group = stringResource(R.string.reader_settings_theme)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = fittingColumns(labels, VersoTheme.typography.captionBold, maxWidth, SwatchGap, 2.dp, listOf(5, 3, 2))
        AdaptiveGrid(
            items = ThemeMode.entries,
            columns = columns,
            gap = SwatchGap,
            modifier = Modifier.semantics { contentDescription = group }.selectableGroup(),
        ) { mode, cellModifier ->
            ThemeSwatch(mode, themeLabel(mode), mode == selected, { onSelect(mode) }, cellModifier)
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.AUTO -> R.string.reader_settings_theme_auto
        ThemeMode.LIGHT -> R.string.reader_settings_theme_light
        ThemeMode.SEPIA -> R.string.reader_settings_theme_sepia
        ThemeMode.DARK -> R.string.reader_settings_theme_dark
        ThemeMode.BLACK -> R.string.reader_settings_theme_black
    },
)

/** Palette montrée par la pastille ; Auto = diagonale clair / sombre. */
private fun swatchPalette(mode: ThemeMode): VersoColors = when (mode) {
    ThemeMode.AUTO, ThemeMode.LIGHT -> VersoPalette.Light
    ThemeMode.SEPIA -> VersoPalette.Sepia
    ThemeMode.DARK -> VersoPalette.Dark
    ThemeMode.BLACK -> VersoPalette.Black
}

@Composable
private fun ThemeSwatch(mode: ThemeMode, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    val palette = swatchPalette(mode)
    Column(
        modifier
            .heightIn(min = 72.dp)
            .clip(VersoShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 52.dp, height = 40.dp)) {
            val shape = VersoShapes.small
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(palette.background)
                    .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.outline, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (mode == ThemeMode.AUTO) {
                    val dark = VersoPalette.Dark.background
                    Canvas(Modifier.fillMaxSize().clip(shape)) {
                        val triangle = Path().apply {
                            moveTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(triangle, dark)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.reader_settings_glyph),
                        style = VersoTheme.typography.bodyStrong,
                        color = palette.text,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VersoIcons.CheckSwitch, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            text = label,
            style = if (selected) VersoTheme.typography.captionBold else VersoTheme.typography.caption.copy(fontWeight = FontWeight(500)),
            color = colors.text,
            textAlign = TextAlign.Center,
        )
    }
}
```


- [ ] **Step 4 : branchement dans `ReaderScreen`**

Après `journal?.let { … }` :

```kotlin
    if (state.settingsVisible) {
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        ReadingSettingsSheet(
            state = ReadingSettingsSheetState(state.readingSettings, themeMode, state.scrollMode),
            onFontSelected = { font -> viewModel.updateReadingSettings { it.copy(font = font) } },
            onSmaller = { viewModel.updateReadingSettings { it.smaller() } },
            onLarger = { viewModel.updateReadingSettings { it.larger() } },
            onThemeSelected = viewModel::setThemeMode,
            onLineSpacingSelected = { spacing -> viewModel.updateReadingSettings { it.copy(lineSpacing = spacing) } },
            onMarginsSelected = { margins -> viewModel.updateReadingSettings { it.copy(margins = margins) } },
            onDismiss = viewModel::hideReadingSettings,
        )
    }
```

Note : sur Android 12 et plus, changer de thème recrée l'activité (`setApplicationNightMode`, V1) ; `settingsVisible` est dans le ViewModel, la feuille se rouvre donc d'elle-même après la recréation.

- [ ] **Step 5 : tests verts**

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReadingSettingsSheetTest"`
Expected: PASS (4 tests).

- [ ] **Step 6 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingSettingsSheet.kt app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt app/src/main/kotlin/com/maximebier/verso/ui/components/VersoIcons.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/maximebier/verso/ui/reader/ReadingSettingsSheetTest.kt
git commit -m "Étape 11 (partie) : feuille Réglages de lecture"
```

### Task 11.4 : captures 2.01 et 2.02, accessibilité, fin de l'étape 11

**Files:**
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt` (créé en 10.2)
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/samples/ReaderSamples.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/AccessibilityTreeTest.kt`
- Modify: `docs/SPEC.md`, `docs/acceptance-v2.md` (créé à l'étape 9)

**Interfaces:**
- Consumes: `V2ScreenFixture`, `V2ScreenCatalog`, `V2ScreenshotTest` (10.2), `ScreenFixture`, `ScreenVariant`, `VariantRule` (`V1ScreenCatalog.kt`), `SampleReadingText()`, `readerBarsSampleState`.
- Produces: fixtures `2.01-barre-de-lecture` et `2.02-reglages-de-lecture` dans `V2ScreenCatalog.fixtures` ; `AccessibilityTreeTest` étendu à **tous** les écrans de `V2ScreenCatalog` (les étapes 12 à 15 n'ont plus qu'à ajouter leurs fixtures au catalogue).

- [ ] **Step 1 : exemples**

Dans `ReaderSamples.kt`, ajouter :

```kotlin
/** 2.01 : barre V2 (Sommaire, Journal, Réglages ; Rechercher ajouté à l’étape 15). */
@Composable
internal fun ReaderToolsSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
        ReaderBars(
            visible = true,
            state = readerBarsSampleState,
            onBack = {},
            onTocClick = {},
            onJournalClick = {},
            onSettingsClick = {},
        )
    }
}

/** 2.02 : feuille « Réglages de lecture » ouverte à mi-hauteur au-dessus du texte, réglages par défaut. */
@Composable
internal fun ReadingSettingsSheetSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
    }
    ReadingSettingsSheet(
        state = ReadingSettingsSheetState(ReadingSettings(), ThemeMode.AUTO, ScrollMode.CONTINUOUS),
        onFontSelected = {},
        onSmaller = {},
        onLarger = {},
        onThemeSelected = {},
        onLineSpacingSelected = {},
        onMarginsSelected = {},
        onDismiss = {},
    )
}
```

Imports : `com.maximebier.verso.core.settings.ReadingSettings`, `com.maximebier.verso.core.settings.ScrollMode`, `com.maximebier.verso.data.ThemeMode`, `com.maximebier.verso.ui.reader.ReadingSettingsSheet`, `com.maximebier.verso.ui.reader.ReadingSettingsSheetState`.

(L'outil Rechercher n'apparaît sur la capture 2.01 qu'à partir de l'étape 15, qui ajoute `onSearchClick = {}` à `ReaderToolsSample`.)

- [ ] **Step 2 : ajouter les écrans au catalogue V2**

Dans `V2ScreenCatalog.kt` (10.2), ajouter à la liste `fixtures`, après les entrées `2.03-2.04-*` :

```kotlin
        V2ScreenFixture(ScreenFixture("2.01-barre-de-lecture") { ReaderToolsSample() }),
        V2ScreenFixture(ScreenFixture("2.02-reglages-de-lecture") { ReadingSettingsSheetSample() }),
```

(quatre palettes par défaut : clair et sombre se comparent à 2.01, 2.02 et 2.02 sombre ; sépia et noir couvrent le critère 4 pour ces écrans), et les imports `com.maximebier.verso.screenshots.samples.ReaderToolsSample`, `com.maximebier.verso.screenshots.samples.ReadingSettingsSheetSample`. `V2ScreenshotTest` ne change pas.

- [ ] **Step 3 : arbre d'accessibilité**

Dans `AccessibilityTreeTest.parameters()`, remplacer `V1ScreenCatalog.fixtures.flatMap` par `(V1ScreenCatalog.fixtures + V2ScreenCatalog.fixtures.map { it.screen }).flatMap` (l'arbre se vérifie dans les variantes de la V1 : clair, sombre, texte à 200 %). C'est le seul endroit qui étend ce test aux écrans V2 ; les écrans ajoutés ensuite au catalogue y entrent d'eux-mêmes.

- [ ] **Step 4 : tests**

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.screenshots.*"`
Expected: PASS ; `app/build/outputs/roborazzi/v2/2.01-barre-de-lecture-clair.png`, `-sombre.png`, `-sepia.png`, `-noir.png`, `-clair-texte-200.png` et les cinq de 2.02 existent. Les ouvrir et les comparer à `docs/design/screens/2.01-barre-de-lecture.png`, `2.02-reglages-de-lecture.png` et `2.02-reglages-de-lecture-sombre.png` (en-tête, cartes de police, taille, pastilles, segments) ; à 200 %, aucun libellé coupé (pastilles sur deux ou trois colonnes, cartes l'une sous l'autre). Écart visible → corriger le composable, relancer.

- [ ] **Step 5 : fin de l'étape 11**

1. `$G :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → BUILD SUCCESSFUL, lint sans avertissement.
2. Regrouper les commits de l'étape : `git reset --soft <commit précédant la tâche 11.1>` puis

```bash
git commit -m "Étape 11 : barre de lecture V2 et feuille Réglages de lecture"
git push
```

3. Téléphone : `$G :app:installDebug`, `adb shell am start -n com.maximebier.verso/.MainActivity`, ouvrir un livre, toucher le centre, capturer la barre (`adb exec-out screencap -p > build/acceptance/2.01-barre.png`), toucher « Réglages », capturer la feuille ; changer la taille, la police, l'interligne, les marges et le thème : le texte change aussitôt derrière la feuille, le même paragraphe reste à l'écran. Comparer aux PNG 2.01 et 2.02 en clair, puis en sombre en choisissant « Sombre » dans la feuille (réglage de Verso, pas du téléphone) ; remettre ensuite le thème de Verso sur sa valeur d’avant. Choisir Atkinson puis Système dans les cartes Police : toute l’app (barre, feuille, puis bibliothèque au retour) et le texte changent de police ; arrêt forcé (`adb shell am force-stop com.maximebier.verso`) puis relance : le choix est gardé. Remettre Literata.
4. Critères V2 cochés à cette étape (`docs/SPEC.md` et `docs/acceptance-v2.md`) : **2** (police changée partout par les cartes Police, gardée après l’arrêt forcé ; le redémarrage du téléphone reste en colonne Maxime) et **3** (taille, interligne, marges en direct, même paragraphe). Le 6 (barre avec Rechercher) se coche à l’étape 15 ; le 7 (feuille avec Défilement) à l’étape 12 : noter dans `docs/acceptance-v2.md` ce qui est déjà vérifié pour le 7 (Police, Taille, Thème, Interligne, Marges en direct, sans voile).

---

## Étape 12 : mode pages

### Task 12.1 : `pageInChapter` (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/maximebier/verso/core/text/PageLabel.kt`
- Create: `core/src/test/kotlin/com/maximebier/verso/core/text/PageLabelTest.kt`

**Interfaces:**
- Produces: `fun pageInChapter(progression: Double, pageCount: Int): Int` (contrat).

- [ ] **Step 1 : test qui échoue**

```kotlin
package com.maximebier.verso.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageLabelTest {

    @Test
    fun progressionIsTurnedIntoAOneBasedPage() {
        // Readium, en pages : progression = début de la page / largeur du chapitre.
        assertThat(pageInChapter(0.0, 9)).isEqualTo(1)
        assertThat(pageInChapter(1.0 / 9, 9)).isEqualTo(2)
        assertThat(pageInChapter(8.0 / 9, 9)).isEqualTo(9)
    }

    @Test
    fun roundingJustBelowAPageBoundaryStillGivesThatPage() {
        assertThat(pageInChapter(2.0 / 9 - 1e-9, 9)).isEqualTo(3)
    }

    @Test
    fun endOfChapterAndOutOfRangeValuesAreClamped() {
        assertThat(pageInChapter(1.0, 9)).isEqualTo(9)
        assertThat(pageInChapter(1.5, 9)).isEqualTo(9)
        assertThat(pageInChapter(-0.2, 9)).isEqualTo(1)
        assertThat(pageInChapter(Double.NaN, 9)).isEqualTo(1)
    }

    @Test
    fun singlePageChapterIsPageOneOfOne() {
        assertThat(pageInChapter(0.7, 1)).isEqualTo(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pageCountMustBePositive() {
        pageInChapter(0.5, 0)
    }
}
```

Run: `$G :core:test --tests "com.maximebier.verso.core.text.PageLabelTest"`
Expected: FAIL (`pageInChapter` non défini).

- [ ] **Step 2 : implémentation**

```kotlin
package com.maximebier.verso.core.text

import kotlin.math.floor

/** Tolérance d’arrondi : une progression à un milliardième sous le début d’une page est déjà cette page. */
private const val PAGE_EPSILON = 1e-6

/**
 * Page courante, de 1 à [pageCount], à partir de la progression dans le chapitre (mode pages de Readium : début de
 * la page affichée / largeur totale du chapitre). NaN ou hors de 0..1 : bornée. Lève [IllegalArgumentException] si
 * `pageCount < 1`.
 */
fun pageInChapter(progression: Double, pageCount: Int): Int {
    require(pageCount >= 1) { "pageCount doit valoir au moins 1 : $pageCount" }
    val p = if (progression.isNaN()) 0.0 else progression.coerceIn(0.0, 1.0)
    return (floor(p * pageCount + PAGE_EPSILON).toInt() + 1).coerceIn(1, pageCount)
}
```

- [ ] **Step 3 : test vert**

Run: `$G :core:test --tests "com.maximebier.verso.core.text.PageLabelTest"`
Expected: PASS (5 tests).

- [ ] **Step 4 : commit**

```bash
git add core/src/main/kotlin/com/maximebier/verso/core/text/PageLabel.kt core/src/test/kotlin/com/maximebier/verso/core/text/PageLabelTest.kt
git commit -m "Étape 12 (partie) : numéro de page dans le chapitre"
```

### Task 12.2 : tours de page dans la machine à états, seuils du mode pages

**Files:**
- Modify: `core/src/main/kotlin/com/maximebier/verso/core/position/ReadingThresholds.kt`
- Modify: `core/src/main/kotlin/com/maximebier/verso/core/position/ReadingPositionTracker.kt`
- Modify: `core/src/test/kotlin/com/maximebier/verso/core/position/ReadingPositionTrackerTest.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinator.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `ScrollMode` (`:core`).
- Produces:
  - `ReadingThresholds.PAGES_CONFIRM_MAX_IDLE_GAP_MS = 90_000L`, `ReadingThresholds.PAGES_CONFIRM_MAX_DRIFT_SCREENS = 2.5`, `ReadingThresholds.forPages(): ReadingThresholds`, `ReadingThresholds.forScrollMode(mode: ScrollMode): ReadingThresholds` (companion).
  - `ReadingPositionTracker.updateThresholds(thresholds: ReadingThresholds)`.
  - `ReadingPositionCoordinator.updateThresholds(thresholds: ReadingThresholds)`.

- [ ] **Step 1 : tests qui échouent (core)**

Ajouter à `ReadingPositionTrackerTest`, avant la section « Configuration » :

```kotlin
    // ---------- Mode pages : un tour de page est un geste de lecture ----------

    /** Tour de page vers [page] écrans : position affichée à [atMs], fin de geste 100 ms plus tard. */
    private fun pageTurn(atMs: Long, page: Double): Array<ReaderEvent> =
        arrayOf(Displayed(atMs, pos(page)), GestureEnded(atMs + 100, pos(page), isFling = false))

    @Test
    fun slowPageTurnsAreReadingAndReadingFollows() {
        val tracker = tracker(ReadingThresholds.forPages())
        val effects = (1..5).flatMap { i -> tracker.feed(*pageTurn(atMs = 30_000L * i, page = 10.0 + i)) } +
            tracker.onEvent(Tick(160_000))

        assertThat(tracker.state).isEqualTo(following(15.0))
        assertThat(effects).contains(SaveReading(pos(15.0)))
        assertThat(effects.filterIsInstance<ScrollTo>()).isEmpty()
    }

    @Test
    fun turningPagesFastIsANavigationAndGoBackReturnsInOneTap() {
        val tracker = tracker(ReadingThresholds.forPages())
        // Cinq pages en deux secondes : plus de 3 écrans en moins de 5 s.
        (1..5).forEach { i -> tracker.feed(*pageTurn(atMs = 600L + 400L * i, page = 10.0 + i)) }
        tracker.onEvent(Tick(4_000))

        assertThat(tracker.state.reading).isEqualTo(pos(10.0))
        assertThat(tracker.state.showReturnCard).isTrue()
        assertThat(tracker.onEvent(GoBack(5_000))).containsExactly(ScrollTo(pos(10.0)))
        assertThat(tracker.state).isEqualTo(following(10.0))
    }

    @Test
    fun inPagesModeTwoPagesReadAtTheNewPlaceConfirmIt() {
        val tracker = tracker()
        tracker.updateThresholds(ReadingThresholds.forPages())
        tracker.feed(Jumped(1_000, pos(30.0)), Displayed(1_100, pos(30.0)), Tick(2_000))
        assertThat(tracker.state).isEqualTo(away(reading = 10.0, displayed = 30.0))

        // Une page lue en une minute environ, puis la suivante.
        tracker.feed(*pageTurn(atMs = 40_000, page = 31.0))
        assertThat(tracker.state.showReturnCard).isTrue()
        val effects = tracker.feed(*pageTurn(atMs = 100_000, page = 32.0))

        assertThat(tracker.state).isEqualTo(following(32.0))
        assertThat(effects).contains(SaveReading(pos(32.0)))
    }

    @Test
    fun withContinuousThresholdsPageTurnsWouldNeverConfirm() {
        // Justifie ReadingThresholds.forPages() : 60 s entre deux tours dépassent la pause de 15 s, et deux pages
        // dépassent la dérive d’un écran ; la carte ne partirait jamais en lisant.
        val tracker = tracker()
        tracker.feed(Jumped(1_000, pos(30.0)), Displayed(1_100, pos(30.0)), Tick(2_000))
        tracker.feed(*pageTurn(atMs = 40_000, page = 31.0))
        tracker.feed(*pageTurn(atMs = 100_000, page = 32.0))
        tracker.feed(*pageTurn(atMs = 160_000, page = 33.0))

        assertThat(tracker.state.showReturnCard).isTrue()
        assertThat(tracker.state.reading).isEqualTo(pos(10.0))
    }

    @Test
    fun pagesThresholdsOnlyRelaxTheConfirmationWindow() {
        val pages = ReadingThresholds.forPages()
        assertThat(pages).isEqualTo(
            ReadingThresholds().copy(
                confirmMaxIdleGapMs = ReadingThresholds.PAGES_CONFIRM_MAX_IDLE_GAP_MS,
                confirmMaxDriftScreens = ReadingThresholds.PAGES_CONFIRM_MAX_DRIFT_SCREENS,
            ),
        )
        assertThat(ReadingThresholds.forScrollMode(ScrollMode.PAGES)).isEqualTo(pages)
        assertThat(ReadingThresholds.forScrollMode(ScrollMode.CONTINUOUS)).isEqualTo(ReadingThresholds())
    }
```

Import : `com.maximebier.verso.core.settings.ScrollMode`.

Note sur le premier test : `tracker(ReadingThresholds.forPages())` garde `displayedSpeedNavigationScreensPerSecond = 4.0` (valeur par défaut, identique à celle des autres scénarios).

Run: `$G :core:test --tests "com.maximebier.verso.core.position.ReadingPositionTrackerTest"`
Expected: FAIL à la compilation (`forPages`, `updateThresholds` absents).

- [ ] **Step 2 : implémentation (core)**

`ReadingThresholds.kt`, dans la `data class`, après `jumpArrivalMaxWaitMs` :

```kotlin
) {
    companion object {
        /**
         * Mode pages : on lit une page en 30 à 90 s, bien plus que la pause de 15 s du défilement continu, et chaque
         * tour avance d’environ un écran. Après une navigation, deux tours de page au rythme de lecture (au moins
         * [confirmReadingMs] d’écart, au plus 90 s) confirment la nouvelle position ; la dérive admise couvre deux pages.
         */
        const val PAGES_CONFIRM_MAX_IDLE_GAP_MS = 90_000L
        const val PAGES_CONFIRM_MAX_DRIFT_SCREENS = 2.5

        /** Seuils du mode pages : seules la pause et la dérive de la confirmation changent. */
        fun forPages(): ReadingThresholds = ReadingThresholds(
            confirmMaxIdleGapMs = PAGES_CONFIRM_MAX_IDLE_GAP_MS,
            confirmMaxDriftScreens = PAGES_CONFIRM_MAX_DRIFT_SCREENS,
        )

        fun forScrollMode(mode: ScrollMode): ReadingThresholds = when (mode) {
            ScrollMode.CONTINUOUS -> ReadingThresholds()
            ScrollMode.PAGES -> forPages()
        }
    }
}
```

(La parenthèse fermante `)` actuelle du constructeur devient `) {` ; import `com.maximebier.verso.core.settings.ScrollMode`.)

`ReadingPositionTracker.kt` : remplacer

```kotlin
class ReadingPositionTracker(
    initial: BookPosition,
    distance: ScreenDistance,
    private val thresholds: ReadingThresholds = ReadingThresholds(),
) {
```

par

```kotlin
class ReadingPositionTracker(
    initial: BookPosition,
    distance: ScreenDistance,
    thresholds: ReadingThresholds = ReadingThresholds(),
) {
    private var thresholds: ReadingThresholds = thresholds
```

et ajouter après `updateDistance` :

```kotlin
    /** Remplace les seuils (bascule continu ↔ pages) ; l’état (lecture, carte, fenêtres) est gardé. */
    fun updateThresholds(thresholds: ReadingThresholds) {
        this.thresholds = thresholds
    }
```

Mettre à jour le KDoc de `ReadingThresholds.confirmMaxIdleGapMs` : ajouter « En mode pages : [PAGES_CONFIRM_MAX_IDLE_GAP_MS]. »

- [ ] **Step 3 : tests verts (core)**

Run: `$G :core:test`
Expected: PASS (tous les tests de `:core`, les anciens compris).

- [ ] **Step 4 : coordinateur**

Test à ajouter dans `ReadingPositionCoordinatorTest` (en reprenant la construction de coordinateur et de `FakeReaderController` des tests existants du fichier ; nom de l'aide locale à relire dans le fichier) :

```kotlin
    @Test
    fun updateThresholdsReachesTheTracker() = runTest {
        // Même scénario que inPagesModeTwoPagesReadAtTheNewPlaceConfirmIt, à travers le coordinateur.
        val coordinator = ReadingPositionCoordinator(
            initial = Locators.toPosition(testLocator(chapter = 1, progression = 0.1, total = 0.10)),
            distance = ScreenDistance { a, b -> kotlin.math.abs(a.totalProgression - b.totalProgression) * 100.0 },
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            onSave = {},
        )
        val fake = FakeReaderController(testLocator(chapter = 1, progression = 0.1, total = 0.10))
        coordinator.attach(fake)
        runCurrent()
        coordinator.updateThresholds(ReadingThresholds.forPages())

        val away = testLocator(chapter = 2, progression = 0.30, total = 0.30)
        coordinator.onJump(away)
        advanceTimeBy(1_000); runCurrent()
        assertThat(coordinator.state.value.showReturnCard).isTrue()

        advanceTimeBy(39_000)
        fake.displayed.value = testLocator(chapter = 2, progression = 0.31, total = 0.31); runCurrent()
        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false)); runCurrent()
        advanceTimeBy(60_000)
        fake.displayed.value = testLocator(chapter = 2, progression = 0.32, total = 0.32); runCurrent()
        fake.gestures.emit(GestureSignal(timeMs = testScheduler.currentTime, isFling = false)); runCurrent()

        assertThat(coordinator.state.value.showReturnCard).isFalse()
        assertThat(coordinator.state.value.reading.totalProgression).isWithin(1e-9).of(0.32)
        coordinator.detach()
    }
```

(`advanceTimeBy` fait tourner le battement d'horloge d'une seconde du coordinateur : sans effet sur ce scénario.)

Implémentation, dans `ReadingPositionCoordinator`, après `updateDistance` :

```kotlin
    /** Seuils du mode de défilement courant (`ReadingThresholds.forScrollMode`). */
    fun updateThresholds(thresholds: ReadingThresholds) {
        tracker.updateThresholds(thresholds)
        _state.value = tracker.state.toPositionState()
    }
```

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReadingPositionCoordinatorTest"`
Expected: PASS.

- [ ] **Step 5 : commit**

```bash
git add core/src app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinator.kt app/src/test/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinatorTest.kt
git commit -m "Étape 12 (partie) : tours de page comme lecture, seuils du mode pages"
```

### Task 12.3 : mode pages dans le contrôleur et la surface

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderController.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/FragmentReaderController.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderGestures.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/reader/ReaderSurface.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/readium/VersoReadingPreferences.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/maximebier/verso/reader/FragmentReaderControllerTest.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/reader/FakeReaderController.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/readium/VersoReadingPreferencesTest.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/reader/ReaderScriptsTest.kt`

**Interfaces:**
- Consumes: `pageInChapter`, `ScrollMode`, `ReaderController.submit` (9.3).
- Produces:
  - `ReaderController.pageInfo: StateFlow<PageInfo?>` et `data class PageInfo(val page: Int, val pageCount: Int)` (contrat ; dans `ReaderController.kt`).
  - `FragmentReaderController.scrollMode: ScrollMode` (lecture seule), `setScrollMode(mode: ScrollMode)`, `turn(forward: Boolean)`, `bind(navigate, probeEdges, visibleText, turnPage: (forward: Boolean) -> Boolean = { false }, pageCount: suspend () -> Int? = { null })`.
  - `ReaderGestures.MODE_SWITCH_SETTLE_MS = 300L`.
  - `ReaderSurface(…, scrollMode: ScrollMode = ScrollMode.CONTINUOUS, bottomInset: Dp = 0.dp)`.
  - `internal fun jsInt(json: String?): Int?` (dans `ReaderSurface.kt`).
  - Chaînes `reader_page_next` (« Page suivante »), `reader_page_previous` (« Page précédente »).

- [ ] **Step 1 : tests qui échouent (contrôleur)**

Dans `FragmentReaderControllerTest`, `Harness` : ajouter

```kotlin
        val turns = mutableListOf<Boolean>()
        var pageCount: Int? = null
```

et remplacer l'appel `bind(...)` par

```kotlin
            bind(
                navigate = { navigated += it },
                probeEdges = { edges },
                visibleText = { visible },
                turnPage = { forward -> turns += forward; true },
                pageCount = { pageCount },
            )
```

(les propriétés `turns` et `pageCount` sont déclarées **avant** `controller` dans `Harness`, sinon leur initialisation arriverait après `apply { bind(...) }`.)

Tests à ajouter :

```kotlin
    // ---------- Mode pages ----------

    private fun tap(h: Harness, xFraction: Float) {
        h.controller.onPointerDown()
        h.controller.onTapLikeGesture(xFraction)
        h.controller.onReadiumTap(xFraction)
    }

    @Test
    fun inPagesModeSideTapsTurnPagesAndTheCenterTogglesTheBars() = runTest {
        val h = Harness(this)
        h.controller.setScrollMode(ScrollMode.PAGES)

        tap(h, 0.9f)
        tap(h, 0.1f)
        tap(h, 0.5f)

        assertThat(h.turns).containsExactly(true, false).inOrder()
        assertThat(h.centerTaps).isEqualTo(1)
    }

    @Test
    fun inContinuousModeSideTapsDoNothing() = runTest {
        val h = Harness(this)
        tap(h, 0.9f)
        h.controller.turn(forward = true)
        assertThat(h.turns).isEmpty()
        assertThat(h.centerTaps).isEqualTo(0)
    }

    @Test
    fun pageTurnSignalsAReadingGestureOnceTheNewPageIsShown() = runTest {
        val h = Harness(this)
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.gestures.test {
            tap(h, 0.9f)
            advanceTimeBy(100)
            h.controller.onDisplayed(at("ch1.xhtml", 0.2))
            expectNoEvents()

            advanceTimeBy(ReaderGestures.SETTLE_QUIET_MS + ReaderGestures.SETTLE_POLL_MS + 1)
            val signal = awaitItem()
            assertThat(signal.isFling).isFalse()
            assertThat(signal.chapterTurn).isFalse()
        }
    }

    @Test
    fun inPagesModeAFastSwipeIsNeitherAFlingNorAChapterTurn() = runTest {
        val h = Harness(this)
        h.adjacent = at("ch2.xhtml", 0.0)
        h.edges = ChapterEdges(atTop = false, atBottom = true)
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.gestures.test {
            h.controller.onPointerDown()
            runCurrent()
            h.controller.onGestureReleased(velocityYPxPerSecond = -20_000f, dragDyPx = -400f)
            advanceTimeBy(ReaderGestures.SETTLE_MAX_MS)
            val signal = awaitItem()
            assertThat(signal.isFling).isFalse()
            assertThat(signal.chapterTurn).isFalse()
        }
        assertThat(h.navigated).isEmpty()
    }

    @Test
    fun pageInfoCountsThePagesOfTheDisplayedChapterAndDisappearsInContinuousMode() = runTest {
        val h = Harness(this)
        h.pageCount = 9
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.onDisplayed(at("ch1.xhtml", 1.0 / 9))
        runCurrent()
        assertThat(h.controller.pageInfo.value).isEqualTo(PageInfo(page = 2, pageCount = 9))

        h.controller.setScrollMode(ScrollMode.CONTINUOUS)
        assertThat(h.controller.pageInfo.value).isNull()
    }

    @Test
    fun unreadablePageCountLeavesPageInfoEmpty() = runTest {
        val h = Harness(this)
        h.pageCount = null
        h.controller.setScrollMode(ScrollMode.PAGES)
        h.controller.onDisplayed(at("ch1.xhtml", 0.5))
        runCurrent()
        assertThat(h.controller.pageInfo.value).isNull()
    }
```

Import : `com.maximebier.verso.core.settings.ScrollMode`.

`ReaderScriptsTest.kt` :

```kotlin
package com.maximebier.verso.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderScriptsTest {
    @Test
    fun jsIntReadsNumbersAsEvaluateJavascriptReturnsThem() {
        assertThat(jsInt("9")).isEqualTo(9)
        assertThat(jsInt("\"9\"")).isEqualTo(9)
        assertThat(jsInt("9.0")).isEqualTo(9)
        assertThat(jsInt("null")).isNull()
        assertThat(jsInt(null)).isNull()
        assertThat(jsInt("abc")).isNull()
    }
}
```

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.reader.FragmentReaderControllerTest" --tests "com.maximebier.verso.reader.ReaderScriptsTest"`
Expected: FAIL à la compilation.

- [ ] **Step 2 : interface et faux contrôleur**

`ReaderController.kt` : dans l'interface, après `viewportHeightPx` :

```kotlin
    /** Mode pages : page affichée dans le chapitre et nombre de pages ; null en continu ou avant le premier compte. */
    val pageInfo: StateFlow<PageInfo?>
```

et en bas du fichier :

```kotlin
/** « Page 2 sur 9 » : page du chapitre affiché (1..pageCount). */
data class PageInfo(val page: Int, val pageCount: Int)
```

`FakeReaderController.kt` : `override val pageInfo = MutableStateFlow<PageInfo?>(null)`.

- [ ] **Step 3 : contrôleur**

`ReaderGestures.kt`, à la fin de l'objet :

```kotlin
    /**
     * Bascule continu ↔ pages : délai laissé à Readium pour remettre le chapitre en page avant de revenir au texte
     * qui était affiché. À revoir sur le téléphone si le retour tombe à côté.
     */
    const val MODE_SWITCH_SETTLE_MS = 300L
```

`FragmentReaderController.kt` :

1. Champs, après `visibleText` :

```kotlin
    private var turnPage: ((forward: Boolean) -> Boolean)? = null
    private var readPageCount: (suspend () -> Int?)? = null
    private val pageInfoState = MutableStateFlow<PageInfo?>(null)
    private var pageCountJob: Job? = null

    /** Défilement appliqué ; posé par [submit] (via [setScrollMode]). */
    var scrollMode: ScrollMode = ScrollMode.CONTINUOUS
        private set
```

et `override val pageInfo: StateFlow<PageInfo?> = pageInfoState.asStateFlow()` à côté de `displayed`.

2. `bind` :

```kotlin
    fun bind(
        navigate: suspend (Locator) -> Unit,
        probeEdges: suspend () -> ChapterEdges?,
        visibleText: suspend () -> String?,
        turnPage: (forward: Boolean) -> Boolean = { false },
        pageCount: suspend () -> Int? = { null },
    ) {
        this.navigate = navigate
        this.probeEdges = probeEdges
        this.visibleText = visibleText
        this.turnPage = turnPage
        this.readPageCount = pageCount
    }
```

3. Mode :

```kotlin
    /**
     * Continu : défilement vertical, changement de chapitre par un glissé au bord. Pages : tours de page par swipe
     * (natif) ou tap sur les côtés, jamais de fling ni d’enchaînement au bord (Readium passe au chapitre suivant).
     */
    fun setScrollMode(mode: ScrollMode) {
        if (mode == scrollMode) return
        scrollMode = mode
        edgesJob?.cancel()
        edgesAtDown = null
        if (mode == ScrollMode.PAGES) {
            refreshPageInfo()
        } else {
            pageCountJob?.cancel()
            pageInfoState.value = null
        }
    }

    /** Compte des pages du chapitre affiché (JavaScript), puis page courante par la progression. */
    private fun refreshPageInfo() {
        if (displayedState.value == null) return
        pageCountJob?.cancel()
        pageCountJob = scope.launch {
            val count = readPageCount?.invoke()?.takeIf { it >= 1 } ?: return@launch
            ensureActive()
            val current = displayedState.value ?: return@launch
            pageInfoState.value = PageInfo(pageInChapter(current.locations.progression ?: 0.0, count), count)
        }
    }
```

4. Dans `submit(settings, theme, scrollMode)` (écrit à la tâche 9.3) : ajouter **en première ligne** `setScrollMode(scrollMode)`. Le reste du corps de 9.3 (application des préférences à Readium) ne change pas.

5. `onDisplayed` : à la fin, après `displayedState.value = filled`, ajouter `if (scrollMode == ScrollMode.PAGES) refreshPageInfo()`.

6. `onPointerDown` : la condition de lecture des bords devient `if (!touchStoppedScroll && scrollMode == ScrollMode.CONTINUOUS) {`.

7. `onGestureReleased` : remplacer les trois premières lignes de calcul par

```kotlin
        val height = viewportHeightPx
        val continuous = scrollMode == ScrollMode.CONTINUOUS
        val chain = if (continuous) chapterChain(edgesAtDown, dragDyPx, chainThresholdPx) else ChapterChain.NONE
        val target = displayedState.value?.takeIf { chain != ChapterChain.NONE }
            ?.let { adjacentChapter(it, chain == ChapterChain.NEXT) }
        val chapterTurn = target != null
        // En pages, un swipe tourne une page : c’est de la lecture, quelle que soit sa vitesse.
        val isFling = continuous && !chapterTurn && height > 0 &&
            abs(velocityYPxPerSecond) / height >= thresholds.flingScreensPerSecond
```

8. `handleTap` et tour de page :

```kotlin
    private fun handleTap(xFraction: Float) {
        when {
            xFraction in ReaderGestures.CENTER_TAP_RANGE -> onCenterTap()
            scrollMode == ScrollMode.PAGES -> turn(forward = xFraction > ReaderGestures.CENTER_TAP_RANGE.endInclusive)
        }
    }

    /**
     * Tour de page (tap sur un côté, action TalkBack) : Readium tourne la page, puis un geste de lecture est signalé
     * une fois la nouvelle page affichée, comme pour un glissé. Sans effet en continu.
     */
    fun turn(forward: Boolean) {
        if (scrollMode != ScrollMode.PAGES) return
        flushPendingGesture()
        if (turnPage?.invoke(forward) != true) return
        val startedAt = uptimeMs()
        val gesture = PendingGesture(isFling = false, chapterTurn = false, target = null)
        pending = gesture
        gesture.job = scope.launch {
            awaitSettled(startedAt)
            if (pending === gesture) pending = null
            gestureFlow.emit(gesture.signal())
        }
    }
```

9. Imports : `com.maximebier.verso.core.settings.ScrollMode`, `com.maximebier.verso.core.text.pageInChapter`.

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.reader.FragmentReaderControllerTest"`
Expected: PASS (tests existants et nouveaux ; `edgeTapThenCenterTapTogglesOnce` reste vert : il tourne en continu).

- [ ] **Step 4 : préférences Readium**

Test dans `VersoReadingPreferencesTest` :

```kotlin
    @Test
    fun pagesModeIsPaginatedOnOneColumnAndKeepsVersoTypography() {
        val prefs = VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.PAGES)
        assertThat(prefs.scroll).isFalse()
        assertThat(prefs.columnCount).isEqualTo(ColumnCount.ONE)
        assertThat(prefs.textAlign).isEqualTo(TextAlign.START)
        assertThat(prefs.hyphens).isFalse()
        assertThat(prefs.publisherStyles).isFalse()
    }

    @Test
    fun continuousModeScrolls() {
        assertThat(VersoReadingPreferences.epub(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS).scroll).isTrue()
    }
```

Imports : `org.readium.r2.navigator.preferences.ColumnCount`, `org.readium.r2.navigator.preferences.TextAlign`, et ceux de `ReadingSettings`, `AppTheme`, `ScrollMode`.

Implémentation dans `VersoReadingPreferences.epub(...)` : s'assurer que `scroll = scrollMode == ScrollMode.CONTINUOUS` (posé en 9.3) et ajouter

```kotlin
            // Mode pages : une seule colonne, même en paysage ; les lignes ne sont jamais coupées entre deux pages
            // (les colonnes CSS de ReadiumCSS coupent entre deux lignes).
            columnCount = ColumnCount.ONE,
```

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.readium.VersoReadingPreferencesTest"`
Expected: PASS.

- [ ] **Step 5 : surface**

`strings.xml`, nouveau bloc :

```xml
    <!-- V2 · écran 2.05 -->
    <string name="reader_page_of">Page %1$d sur %2$d</string>
    <string name="reader_page_next">Page suivante</string>
    <string name="reader_page_previous">Page précédente</string>
```

`ReaderSurface.kt` :

1. Script et lecture du nombre :

```kotlin
/** Nombre de pages du chapitre affiché en mode pages : largeur du document / largeur de l’écran (une colonne). */
private const val PAGE_COUNT_SCRIPT =
    "Math.max(1, Math.round(document.scrollingElement.scrollWidth / window.innerWidth))"

/** Nombre rendu par `evaluateJavascript` (`"9"`, parfois entre guillemets ou en `9.0`) ; null si illisible. */
internal fun jsInt(json: String?): Int? = json?.trim()?.trim('"')?.toDoubleOrNull()?.toInt()
```

2. Paramètres de `ReaderSurface`, après `onFailed` :

```kotlin
    /** Mode courant du livre, pour les actions TalkBack (le mode initial du fragment vient de `initialStyle.scrollMode`). */
    scrollMode: ScrollMode = ScrollMode.CONTINUOUS,
    /** Espace laissé sous le texte (pied de page du mode pages). */
    bottomInset: Dp = 0.dp,
```

Les préférences initiales du fragment restent celles de 9.3, construites depuis `initialStyle` (dont `scrollMode`, rempli par `ReaderScreen` avec `state.scrollMode` depuis 11.2) ; `remember(publication)` ne change pas (la bascule passe ensuite par `submit`).

3. Mouvement réduit : `val reducedMotion by rememberUpdatedState(rememberReducedMotion())` en tête de la fonction (import `com.maximebier.verso.ui.a11y.rememberReducedMotion`).

4. `controller.bind(...)` dans `LaunchedEffect(navigator)` :

```kotlin
        controller.bind(
            navigate = { nav.go(it, animated = false) },
            probeEdges = { edgesOf(nav.evaluateJavascript(EDGES_SCRIPT)) },
            visibleText = {
                jsString(nav.evaluateJavascript(TOP_TEXT_SCRIPT))?.takeIf { it.isNotBlank() }
                    ?: nav.firstVisibleElementLocator()?.text?.highlight
            },
            turnPage = { forward ->
                if (forward) nav.goForward(animated = !reducedMotion) else nav.goBackward(animated = !reducedMotion)
            },
            pageCount = { jsInt(nav.evaluateJavascript(PAGE_COUNT_SCRIPT)) },
        )
```

5. Modificateur du fragment : après `.windowInsetsPadding(readerContentInsets)`, ajouter `.padding(bottom = bottomInset)` ; et, après `.observeGestures(controller)`, les actions TalkBack :

```kotlin
                .semantics {
                    if (scrollMode == ScrollMode.PAGES) {
                        customActions = listOf(
                            CustomAccessibilityAction(nextPageLabel) { controller.turn(forward = true); true },
                            CustomAccessibilityAction(previousPageLabel) { controller.turn(forward = false); true },
                        )
                    }
                },
```

avec, en tête de la fonction, `val nextPageLabel = stringResource(R.string.reader_page_next)` et `val previousPageLabel = stringResource(R.string.reader_page_previous)`. Le `scrollMode` lu ici est le paramètre ; `ReaderScreen` passe le mode courant du livre (tâche 12.4), la sémantique suit donc la bascule.

6. Imports : `androidx.compose.foundation.layout.padding`, `androidx.compose.ui.res.stringResource`, `androidx.compose.ui.semantics.CustomAccessibilityAction`, `androidx.compose.ui.semantics.customActions`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.unit.Dp`, `androidx.compose.ui.unit.dp`, `com.maximebier.verso.R`, `com.maximebier.verso.core.settings.ScrollMode`.

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.reader.*" --tests "com.maximebier.verso.readium.*"`
Expected: PASS.

- [ ] **Step 6 : commit**

```bash
git add app/src/main/kotlin/com/maximebier/verso/reader app/src/main/kotlin/com/maximebier/verso/readium/VersoReadingPreferences.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/maximebier/verso/reader app/src/test/kotlin/com/maximebier/verso/readium/VersoReadingPreferencesTest.kt
git commit -m "Étape 12 (partie) : mode pages dans le lecteur"
```

### Task 12.4 : défilement par livre, pied de page, bascule, captures 2.05, fin de l'étape 12

**Files:**
- Modify: `app/src/main/kotlin/com/maximebier/verso/data/BookRepository.kt`
- Modify (si absent après 9.1) : `app/src/main/kotlin/com/maximebier/verso/data/db/BookDao.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinator.kt`
- Create: `app/src/main/kotlin/com/maximebier/verso/ui/reader/PageFooter.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingSettingsSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/maximebier/verso/data/BookRepositoryTest.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReaderViewModelTest.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/ui/reader/ReadingSettingsSheetTest.kt`
- Create: `app/src/test/kotlin/com/maximebier/verso/ui/reader/PageFooterTest.kt`
- Modify: `app/src/test/kotlin/com/maximebier/verso/screenshots/V2ScreenCatalog.kt`, `…/samples/ReaderSamples.kt`
- Modify: `docs/SPEC.md`, `docs/acceptance-v2.md`

**Interfaces:**
- Consumes: `BookDao.setScrollMode`, `ReadingThresholds.forScrollMode`, `ReadingPositionCoordinator.updateThresholds`, `ReaderController.pageInfo`, `PageInfo`, `ReaderGestures.MODE_SWITCH_SETTLE_MS`, `LabeledSegments`, `chapterLongLabel`.
- Produces:
  - `BookRepository.setScrollMode(bookId: Long, mode: ScrollMode)` (contrat).
  - `ReaderViewModel.setScrollMode(mode: ScrollMode)`, `ReaderUiState.pageInfo: PageInfo? = null`, `ReaderUiState.displayedChapterPath: List<String> = emptyList()`.
  - `ReadingPositionCoordinator.onRelayout(anchor: Locator, relayout: suspend () -> Unit)`.
  - `@Composable fun PageFooter(chapter: String?, pageInfo: PageInfo?, modifier: Modifier = Modifier)`, `object PageFooterMetrics { val bottom: Dp; val gap: Dp; val side: Dp }`, `@Composable fun pageFooterReserve(): Dp`.
  - Chaînes `reader_settings_scroll` (« Défilement »), `reader_settings_scroll_for_this_book` (« Pour ce livre »), `reader_settings_scroll_continuous` (« Continu »), `reader_settings_scroll_pages` (« Pages »).

- [ ] **Step 1 : dépôt**

Si `BookDao.setScrollMode` n'existe pas encore (`grep -n setScrollMode app/src/main/kotlin/com/maximebier/verso/data/db/BookDao.kt`), l'ajouter :

```kotlin
    @Query("UPDATE books SET scrollMode = :scrollMode WHERE id = :id")
    suspend fun setScrollMode(id: Long, scrollMode: String?)
```

Test dans `BookRepositoryTest` (en reprenant l'aide de création de livre du fichier, `TestBooks`) :

```kotlin
    @Test
    fun scrollModeIsStoredPerBook() = runTest {
        val first = repository.insert(TestBooks.book(sha256 = "a"))
        val second = repository.insert(TestBooks.book(sha256 = "b"))

        repository.setScrollMode(first, ScrollMode.PAGES)

        assertThat(repository.book(first)!!.scrollMode).isEqualTo("PAGES")
        assertThat(repository.book(second)!!.scrollMode).isNull()
    }
```

(Adapter `repository` et `TestBooks.book(...)` aux noms réels de `BookRepositoryTest` et `TestBooks.kt`.)

Implémentation dans `BookRepository` :

```kotlin
    /** Défilement choisi pour ce livre (feuille « Aa ») ; il l’emporte sur le défaut des Paramètres. */
    suspend fun setScrollMode(bookId: Long, mode: ScrollMode) = dao.setScrollMode(bookId, mode.name)
```

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.data.BookRepositoryTest"` → PASS.

- [ ] **Step 2 : tests qui échouent (ViewModel)**

Dans `ReaderViewModelTest` :

```kotlin
    @Test
    fun bookOpensInItsOwnScrollModeOtherwiseInTheDefaultOne() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        testSettings().updateReadingSettings { it.copy(defaultScrollMode = ScrollMode.PAGES) }
        val paged = books.insert(testBook(readingLocatorJson = null, progression = 0.0).copy(sha256 = "pages", scrollMode = "CONTINUOUS"))
        val plain = books.insert(testBook(readingLocatorJson = null, progression = 0.0).copy(sha256 = "defaut"))

        val store = ViewModelStore()
        val first = ViewModelProvider.create(store, factory(paged))[ReaderViewModel::class]
        assertThat(first.uiState.first { !it.loading }.scrollMode).isEqualTo(ScrollMode.CONTINUOUS)
        store.clear()

        val otherStore = ViewModelStore()
        val second = ViewModelProvider.create(otherStore, factory(plain))[ReaderViewModel::class]
        assertThat(second.uiState.first { !it.loading }.scrollMode).isEqualTo(ScrollMode.PAGES)
        otherStore.clear()
    }

    @Test
    fun switchingToPagesIsSavedForThisBookAndComesBackToTheSameTextWithoutReturnCard() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onThemeChanged(AppTheme.LIGHT)
        viewModel.onReaderReady(fake)
        runCurrent()

        viewModel.setScrollMode(ScrollMode.PAGES)
        runCurrent()
        // Readium remet le chapitre en page et affiche un moment son début.
        fake.displayed.value = testLocator(chapter = 2, progression = 0.0, total = 0.25)
        advanceTimeBy(ReaderGestures.MODE_SWITCH_SETTLE_MS + 1)
        runCurrent()

        assertThat(fake.submitted.last().scrollMode).isEqualTo(ScrollMode.PAGES)
        assertThat(fake.goCalls.last().locations.progression).isEqualTo(0.40)
        assertThat(viewModel.uiState.value.scrollMode).isEqualTo(ScrollMode.PAGES)
        assertThat(viewModel.uiState.value.returnCard).isNull()
        assertThat(viewModel.uiState.value.readingPercent).isEqualTo(30)
        books.observeBook(id).first { it?.scrollMode == "PAGES" }
        store.clear()
    }

    @Test
    fun pageInfoAndDisplayedChapterReachTheUiState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val start = testLocator(chapter = 2, progression = 0.40, total = 0.30)
        val id = books.insert(testBook(readingLocatorJson = Locators.toJson(start), progression = 0.30).copy(scrollMode = "PAGES"))
        val store = ViewModelStore()
        val viewModel = ViewModelProvider.create(store, factory(id))[ReaderViewModel::class]
        viewModel.uiState.first { !it.loading }
        val fake = FakeReaderController(start)
        viewModel.onReaderReady(fake)
        runCurrent()

        fake.pageInfo.value = PageInfo(page = 2, pageCount = 9)
        runCurrent()

        assertThat(viewModel.uiState.value.pageInfo).isEqualTo(PageInfo(2, 9))
        assertThat(viewModel.uiState.value.displayedChapterPath).containsExactly("Chapitre II")
        store.clear()
    }
```

Imports : `com.maximebier.verso.reader.PageInfo`, `com.maximebier.verso.reader.ReaderGestures`.

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReaderViewModelTest"`
Expected: FAIL (`setScrollMode`, `pageInfo`, `displayedChapterPath` absents).

- [ ] **Step 3 : coordinateur et ViewModel**

`ReadingPositionCoordinator`, après `onLinkFollowed` :

```kotlin
    /**
     * Mise en page refaite (bascule continu ↔ pages) : annoncée comme un saut vers le texte affiché, pour que les
     * positions intermédiaires de Readium (début du chapitre) ne passent ni pour une navigation ni pour de la
     * lecture ; puis retour à [anchor] une fois la mise en page appliquée.
     */
    fun onRelayout(anchor: Locator, relayout: suspend () -> Unit) {
        dispatch(ReaderEvent.Jumped(clock(), Locators.toPosition(anchor), approximate = false))
        val current = controller
        scope.launch {
            relayout()
            delay(ReaderGestures.MODE_SWITCH_SETTLE_MS)
            current?.go(anchor)
        }
    }
```

(import `com.maximebier.verso.reader.ReaderGestures`.)

`ReaderViewModel` :

1. `ReaderUiState`, après `scrollMode` :

```kotlin
    /** Mode pages : « Page 2 sur 9 » du chapitre affiché. */
    val pageInfo: PageInfo? = null,
    /** Chapitre de la position AFFICHÉE (pied de page du mode pages) ; la barre montre celui de la lecture. */
    val displayedChapterPath: List<String> = emptyList(),
```

2. `start(...)` : créer le coordinateur avec `thresholds = ReadingThresholds.forScrollMode(scrollMode)` (la `val scrollMode` calculée en 11.2 est déjà là).

3. `submitStyle()` (9.3, étendu en 11.2) ne change pas : il envoie toujours `_uiState.value.scrollMode`. Seule la bascule passe par la remise en page (point 5).

4. `onReaderReady`, dans `controllerJob = viewModelScope.launch { … }`, ajouter avant la collecte de `displayed` :

```kotlin
            launch { readerController.pageInfo.collect { info -> _uiState.update { it.copy(pageInfo = info) } } }
```

et dans la collecte de `displayed`, après `_uiState.update { it.copy(initialLocator = locator) }` :

```kotlin
                _uiState.update { state ->
                    state.copy(displayedChapterPath = chapterPathAt(state.toc, Locators.hrefKey(locator), locator.locations.progression))
                }
```

5. Méthode :

```kotlin
    /** Défilement de ce livre (feuille « Aa ») : mémorisé pour ce livre seulement, appliqué sans perdre sa place. */
    fun setScrollMode(mode: ScrollMode) {
        if (_uiState.value.scrollMode == mode) return
        sessionCoordinator?.onInteraction()
        coordinator?.updateThresholds(ReadingThresholds.forScrollMode(mode))
        _uiState.update { it.copy(scrollMode = mode, pageInfo = if (mode == ScrollMode.PAGES) it.pageInfo else null) }
        viewModelScope.launch { books.setScrollMode(bookId, mode) }
        val anchor = controller?.displayed?.value
        val positionCoordinator = coordinator
        if (anchor != null && positionCoordinator != null) {
            // Bascule continu ↔ pages : même texte à l’écran après la remise en page.
            positionCoordinator.onRelayout(withTotalProgression(anchor)) { submitStyle() }
        } else {
            submitStyle()
        }
    }
```

6. Imports : `com.maximebier.verso.core.position.ReadingThresholds`, `com.maximebier.verso.reader.PageInfo`.

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.ReaderViewModelTest"` → PASS (anciens et nouveaux).

- [ ] **Step 4 : pied de page (2.05)**

Test `PageFooterTest.kt` :

```kotlin
package com.maximebier.verso.ui.reader

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.data.AppTheme
import com.maximebier.verso.reader.PageInfo
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageFooterTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun showsChapterAndPageOfChapter() {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                PageFooter(chapter = "Deuxième partie, chapitre I", pageInfo = PageInfo(2, 9))
            }
        }
        compose.onNodeWithText("Deuxième partie, chapitre I").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reader_page_of, 2, 9)).assertIsDisplayed()
        compose.onNodeWithText("Page 2 sur 9").assertIsDisplayed()
    }

    @Test
    fun withoutPageCountOnlyTheChapterIsShown() {
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                PageFooter(chapter = "Préface", pageInfo = null)
            }
        }
        compose.onNodeWithText("Préface").assertIsDisplayed()
    }
}
```

`PageFooter.kt` :

```kotlin
package com.maximebier.verso.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.reader.PageInfo
import com.maximebier.verso.ui.theme.VersoTheme

/** Mesures de la maquette 2.05 : 24 dp sur les côtés, 28 dp sous le texte du pied, 16 dp entre le texte et lui. */
object PageFooterMetrics {
    val side: Dp = 24.dp
    val bottom: Dp = 28.dp
    val gap: Dp = 16.dp
}

/** Hauteur réservée sous le texte en mode pages : une ligne de légende (suit la taille de police) et ses marges. */
@Composable
fun pageFooterReserve(): Dp = with(LocalDensity.current) {
    VersoTheme.typography.caption.lineHeight.toDp()
} + PageFooterMetrics.bottom + PageFooterMetrics.gap

/** Pied de page discret du mode pages (2.05) : chapitre à gauche, « Page 2 sur 9 » à droite. */
@Composable
fun PageFooter(chapter: String?, pageInfo: PageInfo?, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(start = PageFooterMetrics.side, end = PageFooterMetrics.side, bottom = PageFooterMetrics.bottom),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = chapter.orEmpty(),
            style = VersoTheme.typography.caption,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (pageInfo != null) {
            Text(
                text = stringResource(R.string.reader_page_of, pageInfo.page, pageInfo.pageCount),
                style = VersoTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}
```

`ReaderScreen.kt` :

1. Appel de `ReaderSurface` : ajouter

```kotlin
                scrollMode = state.scrollMode,
                bottomInset = if (state.scrollMode == ScrollMode.PAGES) pageFooterReserve() else 0.dp,
```

2. Juste après `ReaderSurface(...)` (dans le `if (publication != null)`, avant `ReaderBars`) :

```kotlin
            if (state.scrollMode == ScrollMode.PAGES) {
                PageFooter(
                    chapter = chapterLongLabel(state.displayedChapterPath),
                    pageInfo = state.pageInfo,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
```

(Le pied est sous la barre de lecture dans l'ordre de dessin : la barre, quand elle est affichée, le recouvre.)

3. Rangée Défilement dans la feuille : dans l'appel de `ReadingSettingsSheet` (11.3), ajouter

```kotlin
            scrollModeRow = {
                LabeledSegments(
                    label = stringResource(R.string.reader_settings_scroll),
                    trailing = stringResource(R.string.reader_settings_scroll_for_this_book),
                    options = listOf(
                        stringResource(R.string.reader_settings_scroll_continuous),
                        stringResource(R.string.reader_settings_scroll_pages),
                    ),
                    selectedIndex = ScrollMode.entries.indexOf(state.scrollMode),
                    onSelect = { viewModel.setScrollMode(ScrollMode.entries[it]) },
                )
            },
```

4. `strings.xml`, dans le bloc 2.02 :

```xml
    <string name="reader_settings_scroll">Défilement</string>
    <string name="reader_settings_scroll_for_this_book">Pour ce livre</string>
    <string name="reader_settings_scroll_continuous">Continu</string>
    <string name="reader_settings_scroll_pages">Pages</string>
```

5. `ReadingSettingsSheetTest` : ajouter

```kotlin
    @Test
    fun scrollModeRowSaysItIsForThisBook() {
        var chosen: ScrollMode? = null
        compose.setContent {
            VersoTheme(theme = AppTheme.LIGHT, font = ReadingFont.LITERATA) {
                ReadingSettingsContent(
                    state = ReadingSettingsSheetState(ReadingSettings(), ThemeMode.AUTO, ScrollMode.CONTINUOUS),
                    onFontSelected = {}, onSmaller = {}, onLarger = {}, onThemeSelected = {},
                    onLineSpacingSelected = {}, onMarginsSelected = {}, onClose = {},
                    scrollModeRow = {
                        LabeledSegments(
                            label = s(R.string.reader_settings_scroll),
                            trailing = s(R.string.reader_settings_scroll_for_this_book),
                            options = listOf(s(R.string.reader_settings_scroll_continuous), s(R.string.reader_settings_scroll_pages)),
                            selectedIndex = 0,
                            onSelect = { chosen = ScrollMode.entries[it] },
                        )
                    },
                )
            }
        }
        compose.onNodeWithText(s(R.string.reader_settings_scroll_for_this_book)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(s(R.string.reader_settings_scroll_pages)).performScrollTo().performClick()
        assertThat(chosen).isEqualTo(ScrollMode.PAGES)
    }
```

(import `androidx.compose.ui.test.assertIsDisplayed`.)

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.ui.reader.*"` → PASS.

- [ ] **Step 5 : captures 2.05 et feuille complète**

`ReaderSamples.kt` :

```kotlin
/** 2.05 : page de texte (imitée, Literata 20 sp) et pied de page « Page 2 sur 9 ». */
@Composable
internal fun PagesModeSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
        PageFooter(
            chapter = "Deuxième partie, chapitre I",
            pageInfo = PageInfo(page = 2, pageCount = 9),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
```

et, dans `ReadingSettingsSheetSample`, passer la même rangée `scrollModeRow` que `ReaderScreen` (avec `onSelect = {}`), pour que la capture 2.02 dépliée montre Défilement. Imports : `com.maximebier.verso.reader.PageInfo`, `com.maximebier.verso.ui.reader.PageFooter`, `com.maximebier.verso.ui.reader.LabeledSegments` (rendre `LabeledSegments` `internal` suffit : même module de test).

`V2ScreenCatalog.fixtures` (10.2) : ajouter `V2ScreenFixture(ScreenFixture("2.05-mode-pages") { PagesModeSample() }),`.

Run: `$G :app:testDebugUnitTest --tests "com.maximebier.verso.screenshots.*"` → PASS ; comparer `v2/2.05-mode-pages-*.png` à `docs/design/screens/2.05-mode-pages.png` (pied à 28 dp du bas, 14 sp, couleur secondaire).

- [ ] **Step 6 : spec**

Dans `docs/SPEC.md`, « Réglages de la V2 », remplacer la ligne « Mode pages : … » par :

```markdown
- Mode pages : un tour de page (swipe, tap sur un côté, action TalkBack « Page suivante » / « Page précédente ») est un geste de lecture ; un feuilletage rapide (plus de 3 écrans en 5 s, seuil V1) est une navigation et affiche la carte « Revenir ». Après une navigation, deux pages lues au nouvel endroit (au moins 25 s d'écart entre les deux tours, au plus 90 s) confirment la nouvelle position (`ReadingThresholds.forPages()`). Une seule colonne, même en paysage. Changer de défilement garde le texte affiché.
```

et ajouter à « Historique » :

```markdown
- 2026-09-27 (étape 12) : mode pages : seuils de confirmation propres (pause jusqu'à 90 s, dérive jusqu'à 2,5 écrans), sans changer les règles de la machine à états ; actions TalkBack pour tourner les pages ; bascule continu ↔ pages annoncée comme un saut vers le texte affiché.
```

- [ ] **Step 7 : fin de l'étape 12**

1. `$G :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` → BUILD SUCCESSFUL, lint sans avertissement.
2. `git reset --soft <commit « Étape 11 : … »>` puis

```bash
git commit -m "Étape 12 : mode pages"
git push
```

3. Téléphone (`$G :app:installDebug`, lancer, ouvrir Madame Bovary) :
   - « Aa » › Défilement › Pages : le même paragraphe reste à l'écran, le pied affiche « Deuxième partie, chapitre I » et « Page x sur y » ; capturer, comparer à `2.05-mode-pages.png`.
   - Tourner dix pages par swipe puis par tap à droite et à gauche : aucune ligne coupée en bas, la page suit ; à la fin d'un chapitre, la page suivante est le début du chapitre suivant.
   - Lire page par page (au moins 30 s par page) : le pourcentage de la barre suit.
   - Feuilleter vite cinq pages : la carte « Revenir » apparaît, un tap ramène à la page de départ, pourcentage inchangé.
   - Après ce feuilletage, lire deux pages au nouvel endroit (30 s chacune) : la carte disparaît et le pourcentage suit.
   - Ouvrir un autre livre : il est en continu ; revenir au premier : il rouvre en pages, à la même page.
   - Toucher le centre : la barre apparaît par-dessus le pied.
   - Si le retour au texte après la bascule tombe à côté, augmenter `ReaderGestures.MODE_SWITCH_SETTLE_MS` et le noter dans l'historique de la spec.
4. Cocher dans `docs/SPEC.md` et `docs/acceptance-v2.md` les critères V2 7 (feuille, Défilement compris), 8, 9 et 10, avec les captures dans `build/acceptance/<horodatage>/`.

## Risques Readium relevés (à vérifier sur le téléphone)

- **Bascule continu ↔ pages** : `submitPreferences(scroll = false)` remet en page la ressource et peut afficher son début un instant ; d'où `onRelayout` (saut annoncé) et `MODE_SWITCH_SETTLE_MS`, délai empirique.
- **Compte des pages** : `scrollWidth / innerWidth` suppose une colonne (d'où `columnCount = ONE`) et la mise en colonnes de ReadiumCSS 1 du navigateur Fragment ; un chapitre très court peut compter 1 page même avec une marge résiduelle.
- **`goForward` / `goBackward`** : non suspendus, appelés sur le fil principal (tap Readium ou action TalkBack) ; en fin de chapitre ils passent à la ressource voisine par le `ViewPager` (sans image de défilement natif : le signal de geste attend alors `SETTLE_QUIET_MS` après la nouvelle position).
- **Swipe horizontal** : géré nativement par `R2WebView` en mode pages ; l'observateur de gestes de Verso le voit comme un glissé et signale une fin de geste sans fling.
