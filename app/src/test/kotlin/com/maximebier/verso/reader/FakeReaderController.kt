package com.maximebier.verso.reader

import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/** Surface de lecture factice : on pousse les positions affichées et les gestes à la main. */
class FakeReaderController(initial: Locator?) : ReaderController {
    override val displayed = MutableStateFlow(initial)
    override val gestures = MutableSharedFlow<GestureSignal>(extraBufferCapacity = 64)
    override var viewportHeightPx: Int = 2_000
    override val pageInfo = MutableStateFlow<PageInfo?>(null)

    /** Locators reçus par `go()`, dans l’ordre. */
    val goCalls = mutableListOf<Locator>()

    /** Réponse de `excerptLocator()` ; à défaut, la position affichée. */
    var excerpt: Locator? = null

    override suspend fun go(locator: Locator) {
        goCalls += locator
        displayed.value = locator
    }

    /** Réglages reçus par `submit()`, dans l’ordre. */
    val submitted = mutableListOf<ReaderStyle>()

    override fun submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode) {
        submitted += ReaderStyle(settings, theme, scrollMode)
    }

    /** Tours de page demandés par `turn()` (vrai = page suivante), dans l’ordre. */
    val turns = mutableListOf<Boolean>()

    override fun turn(forward: Boolean) {
        turns += forward
    }

    /** Marques demandées par `showSearchMatch`, dans l’ordre (null = effacement). */
    val searchMatches = mutableListOf<Locator?>()

    override suspend fun showSearchMatch(locator: Locator?) {
        searchMatches += locator
    }

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

    /** Simule un appui long sur [text], dans le chapitre de la position affichée. */
    fun select(text: String, before: String = "", after: String = "") {
        val base = displayed.value ?: testLocator()
        selecting.value = true
        selection.value = TextSelection(base.copy(text = Locator.Text(before = before, highlight = text, after = after)))
    }

    /** Extrait qui répond après ce délai ; null : jamais (WebView détruite pendant l’appel JavaScript, non annulable). */
    var excerptDelayMs: Long? = 0L

    override suspend fun excerptLocator(): Locator? {
        val wait = excerptDelayMs ?: return suspendCoroutine { }
        if (wait > 0) delay(wait)
        return excerpt ?: displayed.value
    }
}

fun testLocator(chapter: Int = 2, progression: Double = 0.5, total: Double = 0.3): Locator =
    Locator(
        href = Url("chapitre-$chapter.xhtml")!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(progression = progression, totalProgression = total),
    )
