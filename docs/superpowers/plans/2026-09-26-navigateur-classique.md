# Navigateur classique de Readium — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** remplacer le navigateur Compose de Readium (fling à 30 images/s) par `EpubNavigatorFragment` (défilement natif, 60 images/s), chapitres enchaînés par un glissé de plus au bord.

**Architecture :** `ReaderController` reste la frontière. Son implémentation passe sur le Fragment dans `ReaderSurface.kt`. Le changement de chapitre est décidé par une fonction pure, et il est signalé à la machine à états (`GestureSignal.chapterTurn` → `GestureEnded.chapterTurn`) pour compter comme lecture.

**Tech Stack :** Kotlin, Compose, `androidx.fragment.compose.AndroidFragment`, Readium 3.4.0 (`readium-navigator`), Robolectric, Truth.

**Spec :** `docs/superpowers/specs/2026-09-26-navigateur-classique-design.md`

## Global Constraints

- Aucune ligne `Co-Authored-By` ni mention de Claude dans les commits.
- Position de lecture : toujours un locator Readium, jamais des pixels. Seuils : constantes nommées.
- Textes en français. Aucune couleur en dur (couleurs issues de `ReadingStyle.colors`).
- Réglages imposés : Atkinson, 19 sp (1,1875), interligne 1,6, espacement de paragraphe 0,8 rem, alignement à gauche, sans césure, CSS de l'éditeur écarté, graisse 380 et espacement +0,01 em en sombre, marge latérale 24 dp.
- Aucune permission réseau. Liens externes ignorés.
- Tout sur `master`, un commit par tâche, push et installation sur le téléphone à la fin.
- JDK : `JAVA_HOME=C:/Users/Maxime/AppData/Local/Programs/jdk17` avant `./gradlew`.

## Review Focus

1. Changement de chapitre en mode FOLLOWING : ni carte « Revenir », ni retour en arrière de la lecture.
2. L'élan d'un fling qui atteint le bord ne change jamais de chapitre : seul un glissé commencé au bord compte.
3. Recréation de l'activité (rotation, changement de thème) pendant la lecture : pas de plantage, pas d'écran vide.
4. Livre à mise en page fixe : toujours refusé avec « Impossible d'ouvrir », puisque le Fragment accepterait de l'ouvrir.
5. Premier et dernier fichiers : un glissé au bord ne fait rien et ne plante pas.

---

### Tâche 1 : la machine à états accepte un changement de chapitre comme lecture

**Fichiers :**
- Modifier : `core/src/main/kotlin/com/maximebier/verso/core/position/ReadingPositionTracker.kt` (`GestureEnded`, `onGestureEnded`)
- Modifier : `app/src/main/kotlin/com/maximebier/verso/reader/ReaderController.kt` (`GestureSignal`)
- Modifier : `app/src/main/kotlin/com/maximebier/verso/ui/reader/ReadingPositionCoordinator.kt:77`
- Tests : `core/src/test/kotlin/com/maximebier/verso/core/position/ReadingPositionTrackerTest.kt`

**Interfaces :**
- Produit : `ReaderEvent.GestureEnded(timeMs, position, isFling, chapterTurn: Boolean = false)` et `GestureSignal(timeMs, isFling, chapterTurn: Boolean = false)`.

Règle : `GestureEnded` avec `chapterTurn = true`, hors saut en cours.
- En FOLLOWING : la navigation détectée par la vitesse des `Displayed` est annulée (`navigating = false`, fenêtre remise à zéro sur la position), l'affiché devient la position, puis `settle` commet la lecture (`SaveReading` + `ReadingMoved`).
- En AWAY : la navigation est annulée, le point d'arrivée devient la position et la carte reste.

- [ ] Étape 1 : tests (échouent).
  - `chapterTurnWhileFollowingMovesReadingWithoutReturnCard` : lecture en fin de chapitre, puis `Displayed` à +1,2 écran 30 ms plus tard (vitesse de navigation), puis `GestureEnded(chapterTurn = true)`. Attendu : `showReturnCard == false`, `reading == nouvelle position`, effets contenant `SaveReading(nouvelle)`.
  - `chapterTurnWhileAwayKeepsTheCard` : en AWAY après un `Jumped`, un changement de chapitre garde la carte et ne touche pas la lecture.
- [ ] Étape 2 : `./gradlew :core:test`, les deux tests échouent.
- [ ] Étape 3 : implémenter.
  - Ajouter le champ `chapterTurn` à `GestureEnded`.
  - Dans `onGestureEnded`, après la branche du saut en cours : si `chapterTurn`, alors `navigating = false`, `resetWindow(timeMs, position)`, `lastMotion = Stamped(timeMs, position)`, `displayed = position`, `displayedAtMs = timeMs`. En FOLLOWING, `motionPending = true` puis `settle(effects)`. En AWAY, `anchor = Stamped(timeMs, position)` et `movedSinceGesture = false`. Puis `return`.
  - Ajouter le champ `chapterTurn` à `GestureSignal` ; le coordinateur le transmet : `ReaderEvent.GestureEnded(signal.timeMs, lastDisplayed, signal.isFling, signal.chapterTurn)`.
- [ ] Étape 4 : `./gradlew :core:test :app:testDebugUnitTest`, tout passe.
- [ ] Étape 5 : commit « Lecteur : un changement de chapitre compte comme lecture ».

### Tâche 2 : décision de changement de chapitre (fonction pure)

**Fichiers :**
- Créer : `app/src/main/kotlin/com/maximebier/verso/reader/ChapterChain.kt`
- Modifier : `app/src/main/kotlin/com/maximebier/verso/reader/ReaderGestures.kt` (ajout de `CHAPTER_CHAIN_DRAG_DP = 40f`)
- Test : `app/src/test/kotlin/com/maximebier/verso/reader/ChapterChainTest.kt`

**Interfaces :**
- Produit : `data class ChapterEdges(val atTop: Boolean, val atBottom: Boolean)`, `enum class ChapterChain { NEXT, PREVIOUS, NONE }`, `fun chapterChain(edgesAtDown: ChapterEdges?, dragDyPx: Float, thresholdPx: Float): ChapterChain`. `dragDyPx` vaut `up.y − down.y` : négatif quand le doigt monte, donc quand le texte avance.

```kotlin
fun chapterChain(edgesAtDown: ChapterEdges?, dragDyPx: Float, thresholdPx: Float): ChapterChain = when {
    edgesAtDown == null -> ChapterChain.NONE
    edgesAtDown.atBottom && dragDyPx <= -thresholdPx -> ChapterChain.NEXT
    edgesAtDown.atTop && dragDyPx >= thresholdPx -> ChapterChain.PREVIOUS
    else -> ChapterChain.NONE
}
```

- [ ] Étape 1 : tests.
  - Au bord bas avec un glissé de −thresholdPx : `NEXT`. Au bord haut avec +thresholdPx : `PREVIOUS`.
  - Sous le seuil : `NONE`. Mauvais sens : `NONE`. Bords inconnus (`null`) : `NONE`.
  - Pas au bord à l'appui (cas d'un fling qui atteint le bord ensuite) : `NONE`.
  - Chapitre plus court que l'écran (aux deux bords) : le sens décide.
- [ ] Étape 2 : lancer les tests, ils échouent. Étape 3 : implémenter. Étape 4 : les tests passent.
- [ ] Étape 5 : commit « Lecteur : décision de changement de chapitre au bord ».

### Tâche 3 : réglages Readium pour le navigateur classique

**Fichiers :**
- Modifier : `app/src/main/kotlin/com/maximebier/verso/readium/VersoReadingPreferences.kt` : remplacer `reflowableWeb` et `reflowableWebConfiguration` par `epub(dark: Boolean, fontSizeSp: Double = ReadingStyle.READING_FONT_SIZE_SP): EpubPreferences` et `EpubNavigatorFragment.Configuration.applyVerso()`.
- Modifier : `app/src/main/kotlin/com/maximebier/verso/readium/ReadingStyle.kt` : retirer `webMinMargins` et `WEB_BASE_MIN_MARGINS_DP`.
- Test : `app/src/test/kotlin/com/maximebier/verso/readium/VersoReadingPreferencesTest.kt` (réécrit).

```kotlin
fun epub(dark: Boolean, fontSizeSp: Double = ReadingStyle.READING_FONT_SIZE_SP): EpubPreferences {
    val colors = ReadingStyle.colors(dark)
    return EpubPreferences(
        backgroundColor = Color(colors.background),
        textColor = Color(colors.text),
        fontFamily = ATKINSON,
        fontSize = ReadingStyle.fontSizeFactor(fontSizeSp),
        fontWeight = ReadingStyle.fontWeightFactor(dark),
        hyphens = false,
        letterSpacing = ReadingStyle.readiumLetterSpacing(dark),
        lineHeight = ReadingStyle.LINE_HEIGHT,
        pageMargins = ReadingStyle.fragmentPageMargins(),
        paragraphSpacing = ReadingStyle.paragraphSpacingRem(),
        publisherStyles = false,
        scroll = true,
        textAlign = TextAlign.START,
        theme = if (dark) Theme.DARK else Theme.LIGHT,
    )
}

fun EpubNavigatorFragment.Configuration.applyVerso() {
    servedAssets = listOf(ReadingStyle.SERVED_ASSETS_PATTERN)
    disablePageTurnsWhileScrolling = true
    addFontFamilyDeclaration(ATKINSON) {
        addFontFace { addSource(ReadingStyle.FONT_ASSET_REGULAR, preload = true); setFontStyle(FontStyle.NORMAL); setFontWeight(ReadingStyle.FONT_WEIGHT_AXIS) }
        addFontFace { addSource(ReadingStyle.FONT_ASSET_ITALIC); setFontStyle(FontStyle.ITALIC); setFontWeight(ReadingStyle.FONT_WEIGHT_AXIS) }
    }
}
```

- [ ] Étape 1 : tests.
  - Clair : défilement, alignement, sans césure, police, taille, interligne, espacement, marges 1,2, `publisherStyles = false`, couleurs.
  - Sombre : graisse 0,95, espacement des lettres 0,02, thème sombre.
  - Configuration : Atkinson servie, avec deux fontes.
- [ ] Étape 2 : implémenter. Adapter les appelants plus tard (tâche 5) ; en attendant, la compilation de `ReaderSurface.kt` casse, donc les tâches 3 à 5 forment un seul commit, « Lecteur : navigateur classique de Readium ».

### Tâche 4 : contrôleur du Fragment

**Fichiers :**
- Modifier : `app/src/main/kotlin/com/maximebier/verso/reader/ReaderSurface.kt` : `ReflowableReaderController` devient `FragmentReaderController`.
- Test : `app/src/test/kotlin/com/maximebier/verso/reader/ReflowableReaderControllerTest.kt`, renommé `FragmentReaderControllerTest.kt`.

**Interfaces :**
- Consomme : `ChapterEdges`, `chapterChain`, `ReaderGestures.CHAPTER_CHAIN_DRAG_DP`, `GestureSignal(…, chapterTurn)`.
- Produit :

```kotlin
internal class FragmentReaderController(
    scope: CoroutineScope,
    readChapterHtml: suspend (Url) -> String?,
    onCenterTap: () -> Unit,
    private val adjacentChapter: (current: Locator, next: Boolean) -> Locator?,
    thresholds: ReadingThresholds = ReadingThresholds(),
    uptimeMs: () -> Long = SystemClock::uptimeMillis,
    wallClockMs: () -> Long = System::currentTimeMillis,
    textDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReaderController {
    fun bind(navigate: suspend (Locator) -> Unit, probeEdges: suspend () -> ChapterEdges?, visibleText: suspend () -> String?)
    var chainThresholdPx: Float
    fun onDisplayed(locator: Locator)
    fun setPositions(positions: ReadingOrderPositions?)
    fun onPointerDown(); fun onDragStarted(); fun onPointerUp()
    fun onGestureReleased(velocityYPxPerSecond: Float, dragDyPx: Float)
    fun onTapLikeGesture(xFraction: Float); fun onReadiumTap(xFraction: Float); fun onLinkActivated()
}
```

À faire :
- **Retirer** la retenue et le recalage (`holding`, `held`, `goGeneration`, `awaitJumpSettled`, `releaseHold`, `pointerUpAt`), `onViewport` et `goLocationOf`.
- **`go`** devient : `flushPendingGesture()`, puis `navigate(locator)`.
- **`onPointerDown`** lance aussi `probeEdges()`, rangé dans `edgesAtDown` (remis à `null` à chaque appui).
- **`onGestureReleased`** calcule `chapterChain(edgesAtDown, dragDyPx, chainThresholdPx)`. Si c'est `NEXT` ou `PREVIOUS` et que `adjacentChapter(displayed, next)` n'est pas nul, il lance `navigate(cible)` ; le signal émis après stabilisation porte alors `isFling = false, chapterTurn = true`. Sinon, le comportement actuel est inchangé.
- **`excerptLocator`** prend d'abord `visibleText()` (limité à `EXCERPT_MAX_CHARS`, espaces normalisés) dans `text.after`, et retombe sur l'approximation actuelle si le texte manque.
- **Tests :**
  - garder les tests des gestes, des taps et de l'extrait (en adaptant le harnais : `bind(navigate = { navigated += it }, probeEdges = { edges }, visibleText = { visible })`) ;
  - supprimer les 7 tests de retenue et de recalage ainsi que les 2 tests de `goLocationOf` ;
  - ajouter : glissé au bord bas → une navigation vers le chapitre suivant et un signal `chapterTurn = true` ; fling qui atteint le bord sans avoir commencé au bord → aucune navigation ; dernier chapitre (`adjacentChapter` nul) → aucune navigation et un signal ordinaire ; extrait visible pris en priorité.

### Tâche 5 : surface Fragment, recréation, nettoyage

**Fichiers :**
- Modifier : `app/src/main/kotlin/com/maximebier/verso/reader/ReaderSurface.kt` (le composable).
- Modifier : `app/src/main/kotlin/com/maximebier/verso/MainActivity.kt` (fragment restauré).
- Supprimer : `app/src/main/kotlin/com/maximebier/verso/reader/ScreenTop.kt`, `app/src/test/kotlin/com/maximebier/verso/reader/ScreenTopTest.kt`, `app/src/test/kotlin/com/maximebier/verso/reader/FakeReflowEngine.kt`.
- Modifier : `app/build.gradle.kts` et `gradle/libs.versions.toml` (retirer `readium-navigator-web-reflowable`).
- Modifier : `app/src/test/kotlin/com/maximebier/verso/reader/ReaderEngineIntegrationTest.kt`, `ReaderSurfaceFailureTest.kt`.

Composable :
- Livre refusé si `publication.metadata.presentation.layout == EpubLayout.FIXED` ou si `EpubNavigatorFactory` échoue : `onFailed()` une fois, fond uni.
- Fabrique posée dans `remember(publication)` avant `AndroidFragment<EpubNavigatorFragment>`, avec :
  - `createFragmentFactory(initialLocator, listener, initialPreferences = epub(dark), configuration = Configuration { applyVerso() })` ;
  - `listener` : `EpubNavigatorFragment.Listener` avec `shouldFollowInternalLink` (appelle `onLinkActivated`, puis `onInternalLink(url)` pour les liens de l'ordre de lecture, et renvoie `true`) et `onExternalLinkActivated` (appelle `onLinkActivated` et ne fait rien d'autre).
- Fragment prêt :
  - `addInputListener` : `onTap` → `onReadiumTap(x / largeur)`, et `onDrag` renvoie toujours `false` ;
  - `bind` : `navigate = { fragment.go(it, animated = false) }` ; `probeEdges` par JavaScript (`window.scrollY <= 4` et `scrollY + innerHeight >= scrollHeight - 4`) ; `visibleText = { fragment.firstVisibleElementLocator()?.text?.highlight }` ;
  - `currentLocator.collect(controller::onDisplayed)`, puis `onReady(controller)`.
- `LaunchedEffect(dark)` : `fragment.submitPreferences(epub(dark))`.
- Modifier du fragment : `windowInsetsPadding(readerContentInsets)` (insets constants, correctif G), fond de lecture en dessous.
- `observeGestures` inchangé, sauf que `onGestureReleased` reçoit `dragDyPx = up.y − down.y`. `chainThresholdPx` vaut `CHAPTER_CHAIN_DRAG_DP × densité`.
- `adjacentChapter` : index dans `readingOrder` du `href` courant, sans fragment ; `locatorFromLink(voisin)` avec `progression = 0.0` pour le suivant et `1.0` pour le précédent.
- `MainActivity.onCreate` : `supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()` avant `super.onCreate`. Ensuite, si `savedInstanceState != null`, retirer (`commitNow`) tout `EpubNavigatorFragment` restauré, pour que la surface en recrée un avec la vraie fabrique.

- [ ] Étapes :
  - tests adaptés : le livre en mise en page fixe est toujours refusé ; Alice est acceptée par `EpubNavigatorFactory` ; les positions et les cibles du sommaire restent valides ;
  - `./gradlew :app:testDebugUnitTest :core:test`, tout passe ;
  - commit « Lecteur : navigateur classique de Readium » (tâches 3 à 5).

### Tâche 6 : spec, téléphone, livraison

- [ ] Mettre à jour `docs/SPEC.md` :
  - le moteur EPUB passe au navigateur classique ;
  - le point de vigilance n° 1 décrit les chapitres enchaînés par un glissé au bord ;
  - la partie « Lecture » mentionne le changement de chapitre ;
  - l'historique note le 2026-09-26.
  Même commit que la vérification.
- [ ] Installer (`installDebug`) et mesurer 4 flings (`dumpsys gfxinfo framestats`) : aucun écart de plus de 25 ms en fling.
- [ ] Vérifier :
  - le passage au chapitre suivant et au précédent, sans carte « Revenir » ;
  - un fling qui arrive au bord ne change pas de chapitre ;
  - le sommaire, puis « Revenir » ;
  - la fermeture brutale suivie de la réouverture ;
  - le thème Clair / Sombre changé pendant la lecture et la rotation : pas de plantage ;
  - le tap au centre.
- [ ] Commit, puis push et installation.
