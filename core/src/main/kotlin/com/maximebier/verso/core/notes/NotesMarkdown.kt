package com.maximebier.verso.core.notes

/** Un élément exporté : chapitre (forme longue, null hors sommaire), passage, note. */
data class NotesExportItem(val chapter: String?, val text: String, val note: String?)

/**
 * Fichier « <Titre> – notes.md » (spec V3, « Export ») : `# Titre`, la ligne [meta] (auteur · nombre d’éléments · date,
 * formée par l’app), puis un `## Chapitre` à chaque changement de chapitre, chaque passage en citation suivi de sa
 * note. [items] sont déjà dans l’ordre du livre. Lisible tel quel dans un éditeur de texte.
 */
fun notesMarkdown(title: String, meta: String, items: List<NotesExportItem>): String = buildString {
    append("# ").append(escapeHeading(title)).append("\n\n")
    append(meta).append('\n')
    var currentChapter: String? = null
    items.forEach { item ->
        if (item.chapter != null && item.chapter != currentChapter) {
            append("\n## ").append(escapeHeading(item.chapter)).append('\n')
            currentChapter = item.chapter
        }
        // Une ligne vide avant chaque citation.
        append('\n')
        item.text.lines().forEach { line -> append("> ").append(line).append('\n') }
        item.note?.takeIf { it.isNotBlank() }?.let { note -> append('\n').append(note.trim()).append('\n') }
    }
}

/** Un titre qui commence par `#` serait lu comme un titre de niveau supérieur. */
private fun escapeHeading(text: String): String = if (text.startsWith("#")) "\\$text" else text

