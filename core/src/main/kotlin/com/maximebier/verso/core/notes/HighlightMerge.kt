package com.maximebier.verso.core.notes

/** Surlignage existant du même chapitre, situé dans le texte ; span null = introuvable (jamais fusionné). */
data class PlacedHighlight(val id: Long, val span: CharSpan?, val note: String?)

/** Plage finale, note finale, surlignages absorbés (à supprimer dans la même transaction). */
data class MergePlan(val span: CharSpan, val note: String?, val absorbedIds: List<Long>)

/** Jamais deux surlignages empilés : un passage qui en recoupe d’autres les absorbe, notes mises bout à bout. */
object HighlightMerge {
    const val NOTE_SEPARATOR = "\n\n"

    fun plan(span: CharSpan, note: String?, existing: List<PlacedHighlight>): MergePlan {
        var merged = span
        val absorbed = LinkedHashMap<Long, PlacedHighlight>()
        // Jusqu’à stabilité : l’union peut recouper un surlignage que le passage seul ne touchait pas.
        while (true) {
            val more = existing.filter { it.id !in absorbed && it.span?.overlaps(merged) == true }
            if (more.isEmpty()) break
            more.forEach { absorbed[it.id] = it; merged = merged.union(it.span!!) }
        }
        // Notes dans l’ordre du texte ; à début égal, l’existante avant la nouvelle.
        val ordered = absorbed.values.map { it.span!!.start to it.note } + (span.start to note)
        val notes = ordered.withIndex()
            .sortedWith(compareBy({ it.value.first }, { it.index }))
            .map { it.value.second }
        return MergePlan(merged, joinNotes(notes), absorbed.keys.toList())
    }

    fun joinNotes(notes: List<String?>): String? =
        notes.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(NOTE_SEPARATOR)
}

