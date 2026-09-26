@file:OptIn(ExperimentalReadiumApi::class)

package com.maximebier.verso.reader

import org.readium.navigator.web.reflowable.resource.ReflowableWebViewport
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

/**
 * Première page visible du pager de Readium : son chapitre et la part de la page (0 à 1) passée
 * au-dessus du haut de l’écran. 0 quand la page est calée en haut, proche de 1 quand seule sa fin dépasse.
 */
internal data class PagerTop(val href: Url, val hiddenFraction: Double)

/**
 * Locator du haut réel de l’écran, continu et monotone quand on franchit une frontière de fichier.
 *
 * Le navigateur enchaîne les fichiers dans un pager de WebViews. `location` suit la page la plus
 * visible (elle bascule au milieu du passage) et `viewport.progressions[href].start` est le haut de la
 * **WebView**, pas celui de l’écran : quand on remonte, le fichier précédent entre par le haut calé
 * sur sa fin, et le haut de sa WebView est encore un écran plus haut que le haut de l’écran (saut
 * d’environ un écran en arrière en une frame, que le suivi prenait pour une navigation). Pendant le
 * passage, seule la page bouge : Readium ne recalcule rien.
 *
 * On place donc le haut de l’écran dans la fenêtre de la première WebView visible grâce au décalage
 * du pager ([pagerTop]) : `start + part cachée × (end − start)`. Si ce décalage manque, ou si le
 * viewport n’a pas encore rattrapé le pager (page entrée sans nouveau calcul de Readium), on garde
 * la position précédente plutôt que d’inventer un saut ; avec une seule page visible, son haut est
 * celui de l’écran.
 *
 * `position` (tranche Readium) n’est pas recalculée ; `totalProgression` reste celle de `location`
 * jusqu’au calcul fin de `ReadingOrderPositions`.
 */
internal fun screenTop(
    location: Locator,
    viewport: ReflowableWebViewport,
    pagerTop: PagerTop?,
    previous: Locator?,
): Locator {
    val topHref = viewport.readingOrder.firstOrNull() ?: return location
    val range = viewport.progressions[topHref] ?: return location
    val start = range.start.value
    val end = range.endInclusive.value
    val progression = when {
        pagerTop != null && sameResource(pagerTop.href, topHref) ->
            start + pagerTop.hiddenFraction.coerceIn(0.0, 1.0) * (end - start)
        viewport.readingOrder.size == 1 && pagerTop == null -> start
        // Viewport en retard sur le pager, ou décalage du pager inconnu pendant un passage.
        previous != null && viewport.readingOrder.any { sameResource(it, previous.href) } -> return previous
        else -> start
    }
    if (sameResource(topHref, location.href) && progression == location.locations.progression) return location
    return Locator(
        href = topHref,
        mediaType = location.mediaType,
        locations = Locator.Locations(
            progression = progression,
            totalProgression = location.locations.totalProgression,
        ),
    )
}

private fun sameResource(a: Url, b: Url): Boolean =
    a.removeFragment().toString() == b.removeFragment().toString()
