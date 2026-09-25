# Readium Kotlin toolkit 3.4.0 — notes techniques

Relevé le 2026-09-25 sur le tag `3.4.0` (sources lues). **Non vérifié** = à confirmer par le prototype (étape 1).

## 0. Version et Gradle

- Dernière version : **3.4.0** (2026-09-11).
- Coordonnées : `org.readium.kotlin-toolkit:readium-shared|readium-streamer|readium-navigator:3.4.0`. Navigateur Compose expérimental : `readium-navigator-web-reflowable:3.4.0`.
- Exige **compileSdk 37**, désucrage (`coreLibraryDesugaring`, `desugar_jdk_libs` 2.1.5). Construit avec Kotlin 2.4.20, AGP 9.3.1, Java 11. minSdk 24.
- L'activité hôte du navigateur Fragment doit être une `FragmentActivity` (ou `AppCompatActivity`).
- Pas d'adaptateur à ajouter (pdfium/exoplayer = PDF/audio).

## 1. Ouvrir un EPUB local sans réseau

`HttpClient` n'est appelé que pour des ressources distantes. Client hors ligne :

```kotlin
import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toUrl
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import org.readium.r2.shared.util.http.HttpTry
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.Specification
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
        Try.failure(HttpError.Unreachable(DebugError("Network disabled")))
}

val assetRetriever = AssetRetriever(context.contentResolver, OfflineHttpClient)
val parser = DefaultPublicationParser(context, OfflineHttpClient, assetRetriever, pdfFactory = null)
val opener = PublicationOpener(parser)

val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false)) // toUrl() sans argument n'existe pas en 3.4.0
    .getOrElse { err -> /* AssetRetriever.RetrieveUrlError : FormatNotSupported | Reading | SchemeNotSupported */ }
when {
    !asset.format.conformsTo(Specification.Epub) -> /* pas un EPUB (un ZIP d'images s'ouvrirait comme une BD) */
    asset.format.conformsTo(Specification.Lcp) -> /* DRM LCP */
    asset.format.conformsTo(Specification.Adept) -> /* DRM Adobe */
}
val publication = opener.open(asset, allowUserInteraction = false)
    .getOrElse { /* PublicationOpener.OpenError.FormatNotSupported | .Reading(ReadError) */ }
```

Détection DRM (`EpubDrmSniffer`) :
- LCP : `META-INF/license.lcpl` présent, ou `encryption.xml` avec RetrievalMethod `license.lcpl#/encryption/content_key`.
- Adobe ADEPT : `encryption.xml` KeyInfo contenant `http://ns.adobe.com/adept`, ou `META-INF/rights.xml` dans l'espace de noms ADEPT.
- L'obfuscation de polices seule n'est pas un DRM.
- Un livre DRM s'ouvre quand même : `publication.isRestricted == true`, `publication.protectionScheme` (`ContentProtection.Scheme.Lcp`/`.Adept`) — extensions dans `org.readium.r2.shared.publication.services`. Vérifier le format avant l'ouverture, ou `isRestricted` après.

## 2. Métadonnées

```kotlin
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.positions
publication.metadata.title                    // String?
publication.metadata.authors.map { it.name }
publication.cover()                           // suspend, Bitmap?
publication.tableOfContents                   // List<Link> (children)
publication.readingOrder                      // List<Link>
publication.positions()                       // suspend List<Locator>
publication.locateProgression(total)          // suspend Locator?
```

- Les positions EPUB = taille des entrées de l'archive en tranches de 1024 octets. Ni pages, ni mots.
- Pas d'API de comptage de mots. Retenu : pour chaque lien de `readingOrder`, `publication.get(link)?.read()`, retirer les balises, compter les mots ; une fois à l'import, résultat stocké.

## 3. EpubNavigatorFragment (navigateur stable)

```kotlin
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

val FontFamily.Companion.ATKINSON get() = FontFamily("Atkinson Hyperlegible Next")

val factory = EpubNavigatorFactory(publication)
supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
    initialLocator = savedLocator,
    initialPreferences = EpubPreferences(
        scroll = true, publisherStyles = false, fontFamily = FontFamily.ATKINSON,
        fontSize = 1.0 /*0.1..5.0*/, fontWeight = 1.0 /*0.0..2.5*/, lineHeight = 1.6 /*1.0..2.0*/,
        paragraphSpacing = 0.5 /*0..2*/, pageMargins = 1.0 /*0..4*/, letterSpacing = 0.0 /*0..1*/,
        textAlign = TextAlign.START, hyphens = false,
        theme = Theme.LIGHT, backgroundColor = Color(0xFFF5F1E8.toInt()), textColor = Color(0xFF1F1B16.toInt()),
    ),
    listener = object : EpubNavigatorFragment.Listener {
        override fun onExternalLinkActivated(url: AbsoluteUrl) {}
    },
    configuration = EpubNavigatorFragment.Configuration {
        servedAssets = listOf("fonts/.*")               // src/main/assets/fonts/ (PAS res/font)
        addFontFamilyDeclaration(FontFamily.ATKINSON) {
            addFontFace {
                addSource("fonts/atkinson_hyperlegible_next.ttf", preload = true)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(200..800) // police variable ; « setFontWeightRange » n'existe pas
            }
        }
    },
)
navigator.submitPreferences(newPrefs) // mise à jour à chaud
```

- Constructeur `EpubPreferences`, `servedAssets`, `addFontFamilyDeclaration` : `@ExperimentalReadiumApi` → `@OptIn(ExperimentalReadiumApi::class)`.
- Intégration Compose : `AndroidFragment<EpubNavigatorFragment>(Modifier.fillMaxSize()) { nav -> ... }` (fragment-compose). Il crée le fragment via la `fragmentFactory` du `supportFragmentManager` : la poser avant la composition. Après mort du processus, le fragment restauré ne peut pas être reconstruit sans la publication : installer `EpubNavigatorFragment.createDummyFactory()` avant `super.onCreate` et revenir à la bibliothèque / rouvrir le livre.

## 4. Frontières de chapitre en scroll — risque n° 1

- **Navigateur Fragment** : une WebView par ressource dans un **ViewPager horizontal**. `scroll = true` ne scrolle qu'à l'intérieur d'un chapitre. Passage au suivant : swipe horizontal ou `goForward()` (qui appelle `scrollToStart()`). `Configuration.disablePageTurnsWhileScrolling = true` bloque les swipes. Pas de callback public de fin de chapitre. Issues : kotlin-toolkit #313 « Full vertical scroll ».
- **Navigateur Compose expérimental** `readium-navigator-web-reflowable` (`ReflowableWebRendition`, `ReflowableWebRenditionFactory`, `ReflowableWebPreferences(scroll = true)`) : `VerticalPager` de WebViews, un `RenditionScrollState` transmet drag et fling d'une WebView à l'autre → **chapitres enchaînés en continu**. `servedAssets` et `fontFamilyDeclarations` dans `ReflowableWebConfiguration`. Son `InputListener` n'a que `onTap`. « Still experimental… APIs are subject to change. » Fiabilité du locator : **non vérifié**.

## 5. Locators

```kotlin
navigator.currentLocator: StateFlow<Locator>  // anti-rebond 100 ms
locator.locations.progression / .totalProgression / .position
locator.toJSON().toString(); Locator.fromJSON(JSONObject(s)) // Locator?
navigator.go(locator, animated = false)
```

- `currentLocator.text` est généralement vide. Pour l'extrait : `navigator.firstVisibleElementLocator()` (suspend, expérimental) → `locations.cssSelector` + `text.highlight` = tout le `textContent` du premier bloc visible (à tronquer).
- `go()` essaie dans l'ordre : `text.highlight` (recherche texte), fragment `#id`, puis `progression`. **Sauvegarder un locator fusionné avec le texte de `firstVisibleElementLocator`** : robuste aux changements de taille de police.
- Un changement de police applique le CSS sans repositionner : capturer `firstVisibleElementLocator()` avant `submitPreferences`, puis `go()`.

## 6. Entrées : tap et drag (navigateur Fragment)

```kotlin
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
navigator.addInputListener(object : InputListener {   // @ExperimentalReadiumApi
    override fun onTap(event: TapEvent): Boolean {
        val v = navigator.publicationView
        val cx = event.point.x / v.width
        if (cx in 0.3f..0.7f) { toggleUi(); return true }
        return false
    }
    override fun onDrag(event: DragEvent): Boolean = false // type Start|Move|End, start, offset (px)
})
```

- **Toujours renvoyer `false` depuis `onDrag`** (sinon `preventDefault()` tue le scroll).
- Drag : `Start` après 6 px ; ni horodatage, ni vitesse, ni fling. Drags sur liens ignorés.
- Hauteur de la vue : `navigator.publicationView.height` (px). Distance réelle de scroll : `evaluateJavascript("window.scrollY/window.innerHeight")` (suspend, reflowable) ou variation de `progression` (non vérifié).
- Le fling ne se déduit qu'à partir de la progression qui continue de changer après `DragEvent.End`.

## 8. Recherche (V2)

`publication.search(query, options)` → `SearchIterator?` (`org.readium.r2.shared.publication.services.search`, expérimental).
