@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Navigateur simulé d’après le journal du téléphone (anomalie F) : un saut vers un fichier qui n’est pas
 * encore chargé passe par le début du fichier, atterrit juste, puis la WebView se remet en page
 * [relayoutAfterMs] plus tard et le haut de l’écran remonte de [relayoutShift] (en progression dans le
 * fichier). Dans le fichier déjà chargé, le saut est exact. Progression totale : fichier n ⇒ `(n + p) / files`.
 */
internal class FakeReflowEngine(
    private val scope: CoroutineScope,
    private val files: Int,
    var relayoutShift: Double,
    private val relayoutAfterMs: Long = 50,
) {
    lateinit var controller: ReflowableReaderController
    val navigated = mutableListOf<ReflowableWebGoLocation>()
    private var loaded: Url? = null

    fun at(file: Int, progression: Double): Locator = Locator(
        href = Url("f$file.xhtml")!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(progression = progression, totalProgression = (file + progression) / files),
    )

    /** Ouvre le livre à cette position (fichier chargé et mis en page). */
    fun open(file: Int, progression: Double) {
        loaded = Url("f$file.xhtml")!!
        controller.onDisplayed(at(file, progression))
    }

    suspend fun navigate(go: ReflowableWebGoLocation) {
        navigated += go
        val file = go.href.toString().removePrefix("f").removeSuffix(".xhtml").toInt()
        val progression = go.progression?.value ?: 0.0
        val fresh = loaded?.toString() != go.href.toString()
        loaded = go.href
        if (fresh) controller.onDisplayed(at(file, 0.0))
        controller.onDisplayed(at(file, progression))
        if (fresh && relayoutShift != 0.0) {
            scope.launch {
                delay(relayoutAfterMs)
                controller.onDisplayed(at(file, (progression - relayoutShift).coerceAtLeast(0.0)))
            }
        }
    }
}
