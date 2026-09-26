# Prototype Readium 3.4.0 — conclusions

Date : 2026-09-26 · Appareil : Pixel 6a, Android 17, `29081JEGR09520` (1080 × 2400 px, 420 dpi) · Branche : `spike/readium` (commit 025b3d4)
EPUB : Madame Bovary (Gutenberg 14155, `epub3.images`, FR) · Pride and Prejudice (Gutenberg 1342, `epub3.images`, EN)

Toutes les mesures de gestes viennent de gestes **simulés** (`adb shell input swipe` / `input motionevent`), sauf la ligne « humain » signalée comme telle. La campagne a été interrompue à 00:50 : le téléphone a été verrouillé (touche marche) et demande l'empreinte ou le code ; les mesures de la variante B et les phases de la variante A sur Pride and Prejudice n'ont pas pu être faites (voir « Risques restants »).

## Décision

**Variante retenue : A — `readium-navigator-web-reflowable` (Compose).**

Raison en une phrase : c'est la seule qui enchaîne réellement les chapitres en scroll continu (décalage mesuré au pixel près à chaque frontière, dans les deux sens), et elle restaure la position au pixel près après une fermeture brutale ; la variante B remplace l'écran au changement de chapitre (saut par construction) et son `goForward()` ne fait rien en mode scroll.

Variante A retenue par l'orchestrateur le 2026-09-26 (seule à enchaîner les chapitres sans coupure). Pour mémoire, le critère 4 a donné 29/30 taps au centre (un tap perdu, le premier geste après une restauration, voir critère 4) et B ne remplit pas le critère 1 (saut d'écran à chaque chapitre).

## Critères

| Critère | A · Bovary | A · Pride | B · Bovary | B · Pride |
| --- | --- | --- | --- | --- |
| 1. Enchaînement sans saut visible (5 passages) | 5/5 aller + 5/5 retour (fin 0-0 ↔ début 0-1) : 70 drags de 600 px, décalage mesuré entre captures 582–604 px (ligne ≈ 80 px), aucun écran blanc | 5/5 aller + 5/5 retour (h-0 ↔ h-1, frontière avec illustration) : 50 drags, décalage 592–605 px, aucun écran blanc | Non mesuré (téléphone verrouillé). Par construction : au bord, un drag de plus de 100 px déclenche `go(chapitre suivant)` qui remplace l'écran par le haut du chapitre suivant (saut d'au plus un écran, drag perdu). 1 passage vérifié (couverture → 0-0) après correction : `goForward()` ne faisait rien | Non mesuré (téléphone verrouillé) |
| 2. Même paragraphe après fermeture brutale (5 essais) | 5/5, capture identique au pixel près (décalage 0–1 px), dont 3 au milieu de 0-2 (≈ 85 écrans du début du chapitre) et 2 juste après le changement de chapitre | 5/5, identique au pixel près, dont 2 au milieu de h-2 et 3 juste après le changement de chapitre | Non mesuré (dont 0/2 sans extrait) | Non mesuré |
| 3. Locator fiable pendant le scroll (reculs, hors bornes, LOCATION par écran) | 0 recul pendant les 20 flings vers le bas, 0 avance pendant les 10 flings vers le haut, `totalProgression` toujours dans 0..1 ; 2 447 lignes `LOCATION` pendant les 30 flings pour ≈ 42 écrans parcourus (≈ 1 par frame, > 50 par écran) | Pas de phase mesurée ; sur les 50 drags de passage : `LOCATION` à chaque drag de ¼ d'écran, 0 recul, bornes respectées | Non mesuré | Non mesuré |
| 4. Tap au centre (10/10, aucun faux tap pendant un scroll) | 1ʳᵉ série 9/10 (1ᵉʳ tap après une restauration absorbé, voir ci-dessous), 2ᵉ série 10/10 ; 10 taps en bord sans navigation ; 5 scrolls courts sans aucun `TAP` | 10/10 ; 10 taps en bord sans navigation ; 5 scrolls courts sans `TAP` | Non mesuré (1 tap au centre vérifié, `relativeX` 0,498) | Non mesuré |
| Police Atkinson servie (capture) | oui (« 0 » barré, « l » à queue) | oui | oui | non vérifié |

Tap absorbé (A) : juste après une restauration, le premier geste fait émettre au navigateur des `dispatchPreScroll` de −55 362 px (source `UserInput`) pendant ≈ 1 s — la distance du début du chapitre à la position restaurée — sans que le contenu ne bouge. Observé une fois (1ʳᵉ série) : le tap tombé pendant ce défilement interne est traité comme un drag (`PRE_FLING` journalisé, pas de `TAP`). Contournement étape 4 : accepter qu'un tap soit perdu à la réouverture (l'utilisateur retape), ou poser la détection du tap au centre sur `observeGestures` (déplacement < touch slop, durée < 300 ms) plutôt que sur `InputListener`.

## Mesures (écrans par seconde au relâchement du doigt)

Hauteur de référence : 2 400 px (zone de lecture plein écran, bord à bord). Phases simulées : « lecture » = 30 drags linéaires de 250–700 px en 400–1 200 ms (1 sur 8 vers le haut) + 10 drags tenus 400 ms avant de lever ; « fling » = 20 flings vers le bas + 10 vers le haut de 500–1 200 px en 60–200 ms + 10 scrolls rapides tenus 300 ms ; « saut » = 3 salves de 6 drags lents de 1 500 px en 1 300 ms.

| Variante · phase | n | min | médiane | p90 | max | fenêtre 5 s max (écrans) |
| --- | --- | --- | --- | --- | --- | --- |
| A · lecture | 40 | 0,000 | 0,179 | 0,324 | 0,364 | 0,56 |
| A · fling | 40 (dont 10 tenus à 0,000) | 0,000 (0,959 hors tenus) | 1,857 | 3,871 | 4,310 | 8,23 |
| A · saut | 18 | 0,302 | 0,367 | 0,468 | 0,481 | 2,25 |
| B · lecture | non mesuré | – | – | – | – | – |
| B · fling | non mesuré | – | – | – | – | – |
| B · saut | non mesuré | – | – | – | – | – |
| Humain (vrai doigt : essai spontané de la personne qui tenait le téléphone, A, gestes non étiquetés) | 39 | 0,000 | 2,518 | 6,786 | 7,424 | 12,05 (cumul `nestedScroll`) |

- Fenêtre 5 s de la variante A calculée à partir de la progression (`LOCATION`, chapitre 0-2 ≈ 165,6 écrans, étalonné par des drags de 600 px) : le cumul des deltas de `nestedScroll` est faussé par les deltas fantômes de −55 362 px après restauration (1 245 « écrans » en phase lecture). Hors de ces deltas, les deux méthodes concordent (saut : 2,24 contre 2,25).
- Gestes humains : deux groupes nets, ≤ 0,32 écran/s (drags lents ou tenus) et 1,0–7,4 écrans/s (flicks d'exploration).
- Variante A, `onPreFling` (vitesse vue par Readium) comparée à `observeGestures` : écart médian 22 % sur les gestes simulés (`observeGestures` plus bas : l'`UP` injecté par `input swipe` ajoute un échantillon immobile), **6 %** sur les gestes humains.
- **Seuil retenu : `ReadingThresholds.flingScreensPerSecond = 1.0`** (les distributions se chevauchent, donc la règle du brief donne p90 « lecture » + 0,5 = 0,324 + 0,5 = 0,82 ; la règle ne fixe d'arrondi que pour le cas sans chevauchement, et nous arrondissons 0,82 au multiple de 0,5 supérieur, 1,0, pour garder les seuils sur un pas de 0,5). Chevauchement des distributions : oui (les 10 scrolls rapides tenus relâchent à 0 écran/s, comme une lecture). Hors gestes tenus, l'intervalle est 0,32–0,96 (milieu 0,64). Les flicks humains observés commencent à 1,0.
- `navigationWindowScreens = 3.0` sur `navigationWindowMs = 5 000` : la phase « saut » simulée n'a atteint que 2,25 écrans en 5 s (latence d'`adb` entre les drags : elle ne dépasse jamais 3 écrans) ; la phase « lecture » n'a jamais dépassé 0,56 écran sur 5 s → seuil confirmé côté lecture (marge × 5), non vérifié côté saut : c'est lui qui doit rattraper les scrolls rapides tenus, à valider avec un vrai geste.

## API exactes (Readium 3.4.0)

Toutes sous `@OptIn(ExperimentalReadiumApi::class)`.

### API utilisées et mesurées dans le prototype

Extraits copiés tels quels de `spike/readium` (025b3d4), `VariantAReader.kt` et `GestureObserver.kt`. Ce sont les seuls appels de la variante A exercés sur le téléphone (critères 1 à 4, fermeture brutale).

```kotlin
// VariantAReader.kt — préférences et configuration
fun spikeReflowablePreferences(): ReflowableWebPreferences = ReflowableWebPreferences(
    backgroundColor = ReadiumColor(0xFFF5F1E8.toInt()),
    textColor = ReadiumColor(0xFF1F1B16.toInt()),
    fontFamily = SpikeAtkinson,
    fontSize = 19.0 / 16.0,
    lineHeight = 1.6,
    paragraphSpacing = 0.5,
    minMargins = 0.8,
    textAlign = TextAlign.START,
    hyphens = false,
    scroll = true,
)

fun spikeReflowableConfiguration(): ReflowableWebConfiguration = ReflowableWebConfiguration(
    servedAssets = persistentListOf("fonts/.*"),
    fontFamilyDeclarations = FontFamilyDeclarations {
        addFontFamilyDeclaration(SpikeAtkinson) {
            addFontFace {
                addSource("fonts/atkinson_hyperlegible_next.ttf", preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(200..800)
            }
        }
    },
)

// VariantAReader.kt — création de l'état, restauration par initialLocation (critère 2 : 10/10 au pixel près)
val renditionState by produceState<ReflowableWebRenditionState?>(initialValue = null, publication) {
    val factory = ReflowableWebRenditionFactory(
        application = context.applicationContext as Application,
        publication = publication,
        configuration = spikeReflowableConfiguration(),
    ) ?: error("Publication refusée par ReflowableWebRenditionFactory")
    value = factory.createRenditionState(
        initialPreferences = spikeReflowablePreferences(),
        initialLocation = store.load(key)?.let { ReflowableWebGoLocation(it) },
    ).getOrElse { failure -> error(failure.message) }
}

// VariantAReader.kt — position courante (critère 3) et sauvegarde
val controller = state.controller
LaunchedEffect(controller) {
    val readyController = controller ?: return@LaunchedEffect
    snapshotFlow { readyController.location }.collect { location ->
        val locator = location.toLocator()
        lastLocator = locator
        store.save(key, locator)
        // … journal LOCATION
    }
}
LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
    lastLocator?.let { store.save(key, it) }
}

// VariantAReader.kt — tap (critère 4)
val inputListener = remember {
    object : InputListener {
        override fun onTap(event: TapEvent, context: TapContext) {
            val relativeX = event.offset.x / context.viewport.width
            // … journal TAP
            if (relativeX in 0.3f..0.7f) overlayVisible = !overlayVisible
        }
    }
}

// VariantAReader.kt — rendu, taille de la zone, signaux de geste
Box(
    Modifier
        .fillMaxSize()
        .onSizeChanged { viewportHeightPx = it.height.coerceAtLeast(1) }
        .observeGestures { timeMs, velocityY -> /* journal RELEASE : GestureMath.screensPerSecond(velocityY, viewportHeightPx) */ }
        .nestedScroll(scrollObserver),
) {
    ReflowableWebRendition(
        state = state,
        modifier = Modifier.fillMaxSize(),
        windowInsets = WindowInsets.safeDrawing,
        inputListener = inputListener,
    )
}

// GestureObserver.kt — vitesse au relâchement (signal retenu pour GestureSignal)
fun Modifier.observeGestures(onRelease: (timeMs: Long, velocityYPxPerSecond: Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val tracker = VelocityTracker()
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            tracker.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    onRelease(System.currentTimeMillis(), tracker.calculateVelocity().y)
                    break
                }
            }
        }
    }

// GestureObserver.kt — NestedScrollConnection parent (deltas et vitesse vus par Readium ; cumul non fiable, voir Mesures)
class ScrollObserver(
    private val onScroll: (deltaYPx: Float, isInertia: Boolean) -> Unit,
    private val onFling: (velocityYPxPerSecond: Float) -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        onScroll(available.y, source == NestedScrollSource.SideEffect)
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        onFling(available.y)
        return Velocity.Zero
    }
}
```

### API vérifiées seulement dans les sources Readium, non essayées

Relevées dans les sources 3.4.0 (`navigators/web/reflowable/…/ReflowableWebRenditionState.kt`, `ReflowableWebPreferences.kt`) ; **aucune n'est appelée par le prototype**, leur comportement sur appareil n'est pas validé.

```kotlin
// Saut vers un locator (sommaire, carte « Revenir », go() de ReaderController) — suspend
state.controller?.goTo(ReflowableWebGoLocation(locator))
// Implémentation lue : pagerState.scrollToPage(index du href) puis positionnement de chaque WebView ;
// même chemin interne que initialLocation, qui, lui, a été mesuré.

// Haut réel de l'écran (plus juste que location aux frontières de chapitre) — état Compose observable
val viewport: ReflowableWebViewport = controller.viewport
viewport.readingOrder.first()                     // href du premier chapitre visible
viewport.progressions[viewport.readingOrder.first()]?.start   // progression du haut de l'écran

// Couleurs de l'éditeur neutralisées (colonne blanche observée sans elle)
ReflowableWebPreferences(overridePublisherColors = true, optimalLineLength = …, maximalLineLength = …)
```

Pour mémoire, variante B (non retenue) : `EpubNavigatorFactory(publication).createFragmentFactory(initialLocator, initialPreferences = EpubPreferences(…), configuration = EpubNavigatorFragment.Configuration { servedAssets = listOf("fonts/.*"); disablePageTurnsWhileScrolling = true; addFontFamilyDeclaration(…) })`, `currentLocator`, `addInputListener` (à retirer avec `removeInputListener`, sinon doublé à la réouverture), `firstVisibleElementLocator()`, `go(locator, animated = false)`, `evaluateJavascript`. **`goForward()` ne change pas de chapitre** avec `scroll = true` + `disablePageTurnsWhileScrolling = true` (`R2BasicWebView.scrollRight` ne fait rien) : enchaîner avec `go(publication.locatorFromLink(readingOrder[i + 1]))`.

## Conséquences pour `ReaderController` (étape 4)

| Membre | Implémentation avec la variante retenue |
| --- | --- |
| `displayed: StateFlow<Locator?>` | `snapshotFlow { controller.location }.map { it.toLocator() }` dans un `LaunchedEffect(state.controller)`, `stateIn(null)` (mesuré). Aux frontières de chapitre, préférer le haut du `controller.viewport` (non essayé) (premier href visible + `progressions[href].start`) : `location` suit la page du pager la plus visible et bascule sur le chapitre suivant (progression 0) alors que le haut de l'écran montre encore la fin du précédent |
| `gestures: Flow<GestureSignal>` | `observeGestures` autour de `ReflowableWebRendition` → `GestureSignal(timeMs, isFling = GestureMath.screensPerSecond(vitesse, viewportHeightPx) >= thresholds.flingScreensPerSecond)`. Ne pas utiliser le cumul de `nestedScroll` pour la distance (deltas fantômes après restauration) : distance par la progression (`ScreenDistance`) |
| `viewportHeightPx: Int` | `onSizeChanged` sur la `Box` qui contient la rendition (2 400 px ici, bord à bord) |
| `go(locator)` | `state.controller?.goTo(ReflowableWebGoLocation(locator))` (suspend) — **non essayé**, lu dans les sources ; avant la première mise en page, recréer l'état avec `initialLocation` (mesuré) |
| `excerptLocator()` | Non disponible : la variante A n'expose ni texte ni `firstVisibleElementLocator` (`ReflowableWebLocation.toLocator()` n'a que progression / position). Restauration par progression seule : exacte au pixel en V1 (typographie fixe) ; `ReflowableWebGoLocation` accepte déjà `cssSelector` et `textAnchor` si une future version expose le texte. Renvoyer `displayed.value` |
| `VersoReadingPreferences.epub(dark)` | Type réel : `ReflowableWebPreferences` (pas de `publisherStyles` ni `pageMargins` ; ajouter `overridePublisherColors = true` (non essayé) : sans elle, la colonne de texte reste blanche sur les EPUB Gutenberg). Valeurs mesurées à l'écran : `fontSize = 19.0 / 16.0` donne le corps visé ; marges : `minMargins = 0.8` place la colonne à 24 dp des bords de l'écran (64 px) mais le texte touche le bord de la colonne — régler `optimalLineLength` / `maximalLineLength` à l'étape 4 |
| Mort du processus | Pas de fragment à restaurer : l'état est recréé par `createRenditionState(initialLocation = locator sauvegardé)`. Testé 10 fois (fermeture depuis les récents) : réouverture au pixel près. `createDummyFactory()` inutile pour A |

## Risques restants

- **Variante B non mesurée** (critères 1 à 4, phases de gestes) et variante A mesurée sur une seule phase de gestes (Madame Bovary) : téléphone verrouillé par l'utilisateur pendant la campagne. La décision A repose sur les critères 1 et 2, mesurés sur les deux livres.
- **Seuils calibrés sur des gestes simulés.** Les vitesses simulées sont choisies par le script ; seule la ligne « humain » vient d'un vrai doigt (gestes d'exploration, pas de lecture). À faire avant l'étape 5 : une vraie séance de 3 minutes de lecture sur l'APK du prototype (phase « lecture », puis « fling » et « saut »), et recalculer `flingScreensPerSecond` avec la règle. Le défaut 4,0 du plan classerait comme lecture la moitié des flicks humains observés (1,0–7,4).
- **Saut vers un locator et haut réel de l'écran non validés sur appareil** : le prototype n'appelle ni `controller.goTo(ReflowableWebGoLocation)` ni `controller.viewport`. La tâche 4.1 doit les essayer sur le téléphone avant d'en dépendre : `go()` pour le sommaire, le journal et le bouton « Revenir » de la carte (précision d'arrivée, saut vers le chapitre courant, `goTo` appelé pendant un fling), et `viewport` pour la position affichée aux frontières de chapitre. Repli si `goTo` déçoit : recréer l'état avec `initialLocation` (chemin mesuré, 10/10 au pixel près).
- Scroll rapide mais tenu : relâché à 0 écran/s, il n'est rattrapé que par la fenêtre « 3 écrans en 5 s », non vérifiée avec un vrai geste.
- Navigateur expérimental (« APIs are subject to change ») : épingler `readium-navigator-web-reflowable:3.4.0` et isoler tout son usage derrière `ReaderController`.
- Premier geste après une restauration : défilement interne de toute la distance restaurée (−55 362 px en `UserInput`, contenu immobile) ; un tap au centre perdu une fois pendant ce défilement.
- Pas d'extrait de texte en A : si la V2 change la taille de police, la restauration par progression dans un long chapitre (≈ 165 écrans) peut décaler de plusieurs lignes.
- Le texte défile sous la barre d'état (icônes claires sur fond clair) : gérer l'inset haut et la couleur des icônes à l'étape 4.
- `LocatorStore.save` fait un `commit()` synchrone à chaque `location` (≈ 60 par seconde en scroll) : l'étape 5 doit passer par le debounce de 500 ms et `onStop`.
- `BuildConfigTest.applicationIdIsTheFinalOne` échoue sur `spike/readium` (suffixe `.spike` demandé par le brief) : attendu, branche jamais fusionnée.
