package com.maximebier.verso.reader

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

    /** Locators reçus par `go()`, dans l’ordre. */
    val goCalls = mutableListOf<Locator>()

    /** Réponse de `excerptLocator()` ; à défaut, la position affichée. */
    var excerpt: Locator? = null

    override suspend fun go(locator: Locator) {
        goCalls += locator
        displayed.value = locator
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
