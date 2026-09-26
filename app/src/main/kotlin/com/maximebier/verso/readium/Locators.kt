package com.maximebier.verso.readium

import com.maximebier.verso.core.model.BookPosition
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/**
 * Conversions entre le locator Readium et ce que l’app stocke ou compare.
 * La position est toujours un locator (chapitre, progression, texte), jamais des pixels.
 */
object Locators {

    private val WHITESPACE = Regex("\\s+")

    fun toJson(locator: Locator): String = locator.toJSON().toString()

    fun fromJson(json: String): Locator? =
        try {
            Locator.fromJSON(JSONObject(json))
        } catch (e: JSONException) {
            null
        }

    fun toPosition(locator: Locator): BookPosition =
        BookPosition(
            locatorJson = toJson(locator),
            totalProgression = (locator.locations.totalProgression ?: 0.0).coerceIn(0.0, 1.0),
        )

    /** Extrait lisible (carte Reprendre) : le texte visible enregistré avec la position. */
    fun excerptOf(locator: Locator): String? =
        (locator.text.highlight ?: locator.text.after)
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** `href` sans fragment : même forme que `TocNode.href` et que les liens de l’ordre de lecture. */
    fun hrefKey(locator: Locator): String = locator.href.removeFragment().toString()
}
