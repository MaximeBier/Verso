package com.maximebier.verso.readium

import com.maximebier.verso.core.text.anchorProgression
import com.maximebier.verso.reader.ChapterText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

/**
 * Progression, dans son fichier, des entrées du sommaire qui pointent vers une ancre (`fichier.xhtml#id`).
 *
 * Calculé avant l’affichage, sans le DOM : la progression d’une ancre est estimée comme la part du texte du
 * fichier qui la précède (une mesure dans la WebView serait plus juste : reportée, relecture REV-007), sur le même texte brut ([ChapterText.plainText]) que l’extrait de la carte
 * Reprendre, qui fait la conversion inverse (progression du moteur → caractère). Calculé une fois par livre.
 */
object TocAnchors {

    /** Début d’une balise portant un attribut `id` ; groupe 2 : la valeur. `data-id` n’en est pas un. */
    private val ID_ATTRIBUTE = Regex("<[A-Za-z][^>]*?\\sid\\s*=\\s*([\"'])([^\"']+)\\1")
    private const val MARKER = '\uE000'

    /** Texte d’un fichier : sa longueur et le nombre de caractères avant chaque ancre trouvée. */
    data class Offsets(val fileChars: Int, val charsBefore: Map<String, Int>)

    /** Ancre d’une entrée du sommaire : début estimé dans son fichier et longueur du texte de ce fichier. */
    data class Anchor(val progression: Double, val fileChars: Int)

    /** Caractères de texte avant chaque `id` de [ids] trouvé dans [html] (ids absents omis), et au total. */
    fun offsets(html: String, ids: Collection<String>): Offsets {
        val wanted = ids.toSet()
        val starts = sortedMapOf<Int, String>()
        val seen = mutableSetOf<String>()
        ID_ATTRIBUTE.findAll(html).forEach { match ->
            val id = match.groupValues[2]
            if (id in wanted && seen.add(id)) starts[match.range.first] = id
        }
        // Un marqueur (caractère privé) devant chaque balise, puis un seul passage de texte brut.
        val marked = buildString(html.length + starts.size) {
            var from = 0
            starts.keys.forEach { index ->
                append(html, from, index).append(MARKER)
                from = index
            }
            append(html, from, html.length)
        }
        val text = ChapterText.plainText(marked)
        val before = mutableListOf<Int>()
        var length = 0
        text.forEach { char -> if (char == MARKER) before += length else length++ }
        return Offsets(fileChars = length, charsBefore = starts.values.zip(before).toMap())
    }

    /**
     * Ancres des liens de [links] (et de leurs enfants), par href complet (`Link.url()` avec fragment).
     * Chaque fichier n’est lu qu’une fois ; un fichier illisible ou une ancre absente est omis.
     */
    suspend fun load(publication: Publication, links: List<Link>): Map<String, Anchor> =
        withContext(Dispatchers.IO) {
            val anchored = flatten(links).map { it.url() }.filter { it.fragment != null }
            anchored.groupBy { it.removeFragment() }.flatMap { (resource, urls) ->
                val html = readHtml(publication, resource) ?: return@flatMap emptyList()
                val offsets = offsets(html, urls.mapNotNull { it.fragment })
                urls.mapNotNull { url ->
                    offsets.charsBefore[url.fragment]?.let { chars ->
                        url.toString() to Anchor(anchorProgression(chars, offsets.fileChars), offsets.fileChars)
                    }
                }
            }.toMap()
        }

    private suspend fun readHtml(publication: Publication, href: Url): String? {
        val resource = publication.get(href) ?: return null
        return try {
            resource.read().getOrNull()?.toString(Charsets.UTF_8)
        } finally {
            resource.close()
        }
    }

    private fun flatten(links: List<Link>): List<Link> = links.flatMap { listOf(it) + flatten(it.children) }
}
