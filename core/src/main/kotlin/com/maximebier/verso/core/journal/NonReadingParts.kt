package com.maximebier.verso.core.journal

/**
 * Parties d’un EPUB qui ne sont pas de la lecture (sommaire de l’éditeur, mentions légales, index, notes, bibliographie,
 * colophon), d’après les landmarks qu’il déclare (`epub:type` en EPUB 3, `<guide>` en EPUB 2). Leurs mots et leur
 * temps ne comptent pas dans la vitesse. Un EPUB qui ne déclare rien n’est pas touché.
 */
object NonReadingParts {
    /** Types de landmarks (vocabulaire structurel de l’IDPF, sans l’espace de noms). */
    val TYPES: Set<String> = setOf(
        "toc", "copyright-page", "index", "endnotes", "rearnotes", "footnotes", "notes", "bibliography", "colophon",
    )

    /** [rel] : relation d’un landmark, `http://idpf.org/epub/vocab/structure/#index` ou `index`. */
    fun isNonReading(rel: String): Boolean = rel.substringAfterLast('#').substringAfterLast('/') in TYPES

    /** Ressources (href sans ancre) des landmarks hors lecture ; [landmarks] : href et relations de chaque landmark. */
    fun hrefs(landmarks: List<Pair<String, List<String>>>): Set<String> =
        landmarks.filter { (_, rels) -> rels.any(::isNonReading) }.map { (href, _) -> href.substringBefore('#') }.toSet()
}
