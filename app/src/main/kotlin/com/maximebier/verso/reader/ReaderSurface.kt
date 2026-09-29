@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import com.maximebier.verso.core.text.preorder
import com.maximebier.verso.readium.ReadingOrderPositions
import com.maximebier.verso.readium.ReadingStyle
import com.maximebier.verso.readium.VersoReadingPreferences
import com.maximebier.verso.readium.VersoReadingPreferences.applyVerso
import com.maximebier.verso.ui.a11y.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url

private const val LOG_TAG = "VersoReader"

/** Bords du document affiché, lus dans la WebView (marge de 4 px pour l’arrondi du défilement). */
private const val EDGES_SCRIPT =
    "JSON.stringify([window.scrollY <= 4, " +
        "window.scrollY + window.innerHeight >= document.scrollingElement.scrollHeight - 4])"

/**
 * Texte qui commence à la première ligne visible (premier caractère à 12 px sous le haut de l’écran, en partant de la gauche), sur environ
 * 400 caractères : l’extrait de la carte Reprendre est le passage où l’on s’est arrêté, pas le début du paragraphe.
 * Réponse : chaîne JSON, ou `null` si aucun texte sous ce point.
 */
private const val TOP_TEXT_SCRIPT =
    "(function(){var r=null;for(var x=2;x<window.innerWidth;x+=8){" +
        "r=document.caretRangeFromPoint(x,12);if(r&&r.startContainer.nodeType===3)break;r=null;}" +
        "if(!r)return null;" +
        "var n=r.startContainer,s=n.data.substring(r.startOffset);" +
        "var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);w.currentNode=n;" +
        "while(s.length<400&&w.nextNode())s+=' '+w.currentNode.data;" +
        "return s;})()"

/**
 * Mode pages (une colonne) : nombre de pages du fichier affiché (largeur du document / largeur de l’écran), page
 * affichée (défilement horizontal / largeur de l’écran, 1-based) puis, pour chaque ancre de [anchorIds], la page où
 * elle se trouve (`null` si absente). Réponse : `"[pages, page affichée, page…]"`
 * (chaîne JSON), ou `null` si la vue n’est pas mesurée.
 */
internal fun pageLayoutScript(anchorIds: List<String>): String =
    "(function(ids){var e=document.scrollingElement,w=window.innerWidth;if(!w)return null;" +
        "var r=[Math.max(1,Math.round(e.scrollWidth/w)),Math.round(e.scrollLeft/w)+1];" +
        "for(var i=0;i<ids.length;i++){var el=document.getElementById(ids[i]);" +
        "r.push(el?Math.floor((el.getBoundingClientRect().left+e.scrollLeft)/w)+1:null);}" +
        "return JSON.stringify(r);})(" + JSONArray(anchorIds) + ")"

/**
 * Navigateur EPUB classique de Readium (Fragment) : un chapitre à la fois, défilé nativement par la WebView ; un
 * glissé commencé au bord ouvre le chapitre voisin ([chapterChain]). `initialLocator` n’est lu qu’à la création
 * (clé : la publication). `onInternalLink` : lien interne touché (ordre de lecture), appelé juste avant que Readium
 * ne le suive. `initialStyle` : réglages de lecture et palette à la création du lecteur (lus une fois, comme
 * `initialLocator`) ; les changements passent ensuite par [ReaderController.submit], sans recréer le lecteur. La
 * taille choisie suit l’échelle de police d’Android ([ReadingStyle.readingFontScale]). `positions` : positions
 * de l’ordre de lecture déjà calculées (progression totale fine). `onFailed` : le moteur refuse le livre (mise en
 * page fixe, hors V1) ; appelé une fois, surface unie.
 */
@Composable
fun ReaderSurface(
    publication: Publication,
    initialLocator: Locator?,
    initialStyle: ReaderStyle,
    onReady: (ReaderController) -> Unit,
    /** Tap n’importe où sur le texte (affiche ou masque la barre de lecture). */
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    positions: ReadingOrderPositions? = null,
    onInternalLink: (Url) -> Unit = {},
    onFailed: () -> Unit = {},
    /** Espace laissé sous le texte (pied de page du mode pages). */
    bottomInset: Dp = 0.dp,
) {
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val activity = LocalActivity.current as? FragmentActivity
    val densityInfo = LocalDensity.current
    val density = densityInfo.density
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnInternalLink by rememberUpdatedState(onInternalLink)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val background = Color(ReadingStyle.colors(initialStyle.theme).background)

    if (publication.metadata.layout == Layout.FIXED || activity == null) {
        // Livre à mise en page fixe : hors du périmètre V1. Jamais d’écran vide sans issue.
        LaunchedEffect(publication) {
            Log.w(LOG_TAG, "Livre refusé (mise en page fixe) ou hôte sans FragmentActivity")
            currentOnFailed()
        }
        Box(modifier.fillMaxSize().background(background))
        return
    }

    // Chapitres ancrés de chaque fichier, pour « Page x sur y » dans le chapitre (mode pages).
    val anchorIds = remember(publication) { chapterAnchorIds(publication) }
    val scope = rememberCoroutineScope()
    val controller = remember(publication) {
        FragmentReaderController(
            scope = scope,
            readChapterHtml = { href -> readChapterHtml(publication, href) },
            onTap = { currentOnTap() },
            adjacentChapter = { current, next -> adjacentChapter(publication, current, next) },
        ).apply {
            // Mode du fragment créé ci-dessous ; les bascules suivantes passent par submit.
            setScrollMode(initialStyle.scrollMode)
        }
    }
    controller.chainThresholdPx = ReaderGestures.CHAPTER_CHAIN_DRAG_DP * density
    val requestedStyle by controller.style.collectAsState()
    val style = requestedStyle ?: initialStyle
    // Taille réelle voulue par Android pour la taille choisie (échelle non linéaire : dépend de la taille).
    val fontScale = remember(densityInfo, style.settings.fontSizeSp) {
        ReadingStyle.readingFontScale(densityInfo, style.settings.fontSizeSp)
    }

    val fragmentFactory = remember(publication) {
        val listener = object : EpubNavigatorFragment.Listener {
            override fun shouldFollowInternalLink(link: Link, context: HyperlinkNavigator.LinkContext?): Boolean {
                controller.onLinkActivated()
                val url = link.url()
                // Saut explicite (spec) : annoncé avant que Readium ne lance le déplacement.
                if (publication.readingOrder.any { sameResource(it.url(), url) }) currentOnInternalLink(url)
                return true
            }

            // Liens externes ignorés : aucun réseau.
            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                controller.onLinkActivated()
            }
        }
        EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            listener = listener,
            initialPreferences = VersoReadingPreferences.epub(
                initialStyle.settings,
                initialStyle.theme,
                initialStyle.scrollMode,
                fontScale = ReadingStyle.readingFontScale(densityInfo, initialStyle.settings.fontSizeSp),
            ),
            configuration = EpubNavigatorFragment.Configuration { applyVerso(initialStyle.theme) },
        )
    }
    // Posée à chaque composition, donc avant que AndroidFragment instancie le fragment.
    activity.supportFragmentManager.fragmentFactory = fragmentFactory
    LaunchedEffect(controller, positions) {
        controller.setPositions(positions)
    }

    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    // Largeur de la surface, en dp : la marge de 24 dp dépend de la gouttière de ReadiumCSS (paliers selon la largeur).
    var widthDp by remember { mutableFloatStateOf(0f) }
    DisposableEffect(navigator) {
        val nav = navigator ?: return@DisposableEffect onDispose {}
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                controller.onReadiumTap()
                return false
            }
        }
        nav.addInputListener(listener)
        onDispose { nav.removeInputListener(listener) }
    }
    // Défilement natif de la WebView, image par image : Readium ne rapporte la position que 100 ms après l’arrêt.
    val hostView = LocalView.current
    DisposableEffect(hostView, controller) {
        val observer = hostView.viewTreeObserver
        val scrollListener = ViewTreeObserver.OnScrollChangedListener { controller.onScrolled() }
        observer.addOnScrollChangedListener(scrollListener)
        onDispose { if (observer.isAlive) observer.removeOnScrollChangedListener(scrollListener) }
    }
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
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
            pageLayout = { locator ->
                val ids = anchorIds[locator.href.removeFragment().toString()].orEmpty()
                pageLayoutOf(nav.evaluateJavascript(pageLayoutScript(ids)), ids)
            },
        )
        currentOnReady(controller)
        nav.currentLocator.collect(controller::onDisplayed)
    }
    // Préférences soumises au navigateur en dernier ; lues seulement ici, jamais pendant la composition.
    val submitted = remember { mutableStateOf<EpubPreferences?>(null) }
    LaunchedEffect(navigator, style, fontScale, widthDp) {
        val nav = navigator ?: return@LaunchedEffect
        val preferences =
            VersoReadingPreferences.epub(style.settings, style.theme, style.scrollMode, fontScale = fontScale, widthDp = widthDp)
        val previous = submitted.value
        submitted.value = preferences
        if (previous != null && VersoReadingPreferences.changesLayout(previous, preferences)) {
            // Police, taille, interligne, marges, défilement : le texte revient au même locator, sans mouvement pour
            // la machine à états. La bascule continu ↔ pages laisse d’abord Readium remettre le chapitre en page.
            val settleMs = if (previous.scroll != preferences.scroll) ReaderGestures.MODE_SWITCH_SETTLE_MS else 0L
            controller.relayout(settleMs) { nav.submitPreferences(preferences) }
        } else {
            nav.submitPreferences(preferences)
        }
    }

    Box(modifier.fillMaxSize().background(Color(ReadingStyle.colors(style.theme).background))) {
        AndroidFragment<EpubNavigatorFragment>(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(readerContentInsets)
                .padding(bottom = bottomInset)
                .onSizeChanged {
                    controller.viewportHeightPx = it.height
                    widthDp = it.width / density
                }
                .observeGestures(controller),
        ) { fragment ->
            if (navigator !== fragment) navigator = fragment
        }
    }
}

/**
 * Insets donnés au texte : barre d’état et côtés des barres système **même masquées**, et découpe de l’écran, donc
 * constants. Rien en bas : le texte va jusqu’au bas de l’écran (plein écran), sous la barre de navigation masquée.
 * La barre de lecture et les barres système sont une surcouche : les afficher ou les masquer ne change ni la mise en
 * page du texte ni la position de lecture (anomalie G).
 */
@OptIn(ExperimentalLayoutApi::class)
internal val readerContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBarsIgnoringVisibility
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        .union(WindowInsets.displayCutout)

/** Chaîne rendue par `evaluateJavascript` (encodée en JSON : `"\"texte\""`) ; null si `null` ou illisible. */
internal fun jsString(json: String?): String? =
    json?.let { runCatching { JSONTokener(it).nextValue() as? String }.getOrNull() }

/** Réponse de [pageLayoutScript] pour [anchorIds] (`"\"[184,12,1,null,40]\""`) ; null si illisible ou sans page. */
internal fun pageLayoutOf(json: String?, anchorIds: List<String> = emptyList()): PageLayout? {
    val array = runCatching { JSONArray(jsString(json) ?: return null) }.getOrNull() ?: return null
    val count = array.optDouble(0).takeIf { it.isFinite() && it >= 1 }?.toInt() ?: return null
    val current = array.optDouble(1).takeIf { it.isFinite() }?.toInt()
    val anchors = anchorIds.mapIndexedNotNull { index, id ->
        array.optDouble(index + 2).takeIf { it.isFinite() }?.let { AnchorPage(id, it.toInt()) }
    }
    return PageLayout(count, anchors, current)
}

/**
 * Identifiants des ancres du sommaire (chapitres ancrés), par fichier de l’ordre de lecture (href sans fragment),
 * dans l’ordre du sommaire.
 */
internal fun chapterAnchorIds(publication: Publication): Map<String, List<String>> =
    preorder(publication.tableOfContents) { it.children }
        .mapNotNull { link -> link.url().let { url -> url.fragment?.let { url.removeFragment().toString() to it } } }
        .groupBy({ it.first }, { it.second })

/** Réponse de [EDGES_SCRIPT] (`"[true,false]"`, parfois entre guillemets) ; null si illisible. */
internal fun edgesOf(json: String?): ChapterEdges? {
    val values = json?.trim('"')?.removePrefix("[")?.removeSuffix("]")?.split(",")?.map { it.trim() } ?: return null
    if (values.size != 2 || values.any { it != "true" && it != "false" }) return null
    return ChapterEdges(atTop = values[0] == "true", atBottom = values[1] == "true")
}

/** Début du chapitre suivant ou fin du précédent dans l’ordre de lecture ; null au premier ou au dernier. */
internal fun adjacentChapter(publication: Publication, current: Locator, next: Boolean): Locator? {
    val index = publication.readingOrder.indexOfFirst { sameResource(it.url(), current.href) }
    if (index < 0) return null
    val link = publication.readingOrder.getOrNull(if (next) index + 1 else index - 1) ?: return null
    return publication.locatorFromLink(link)?.copyWithLocations(progression = if (next) 0.0 else 1.0)
}

internal fun sameResource(a: Url, b: Url): Boolean =
    a.removeFragment().toString() == b.removeFragment().toString()

/**
 * Observe chaque geste en passe `Initial` sans rien consommer : la WebView reçoit tous les événements. La vitesse
 * et le déplacement au lâcher décident du fling et du changement de chapitre ; un appui bref sans glissement est
 * un tap.
 */
private fun Modifier.observeGestures(controller: FragmentReaderController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            controller.onPointerDown()
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var moved = false
            var upAt: Long? = null
            var upY = down.position.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                upY = change.position.y
                if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!change.pressed) {
                    upAt = change.uptimeMillis
                    break
                }
            }
            val releasedAt = upAt ?: return@awaitEachGesture
            when {
                moved -> controller.onGestureReleased(
                    velocityYPxPerSecond = velocityTracker.calculateVelocity().y,
                    dragDyPx = upY - down.position.y,
                )
                releasedAt - down.uptimeMillis <= ReaderGestures.TAP_MAX_DURATION_MS -> controller.onTapLikeGesture()
            }
        }
    }

internal suspend fun readChapterHtml(publication: Publication, href: Url): String? =
    withContext(Dispatchers.IO) {
        val resource = publication.get(href) ?: return@withContext null
        try {
            resource.read().getOrNull()?.toString(Charsets.UTF_8)
        } finally {
            resource.close()
        }
    }
