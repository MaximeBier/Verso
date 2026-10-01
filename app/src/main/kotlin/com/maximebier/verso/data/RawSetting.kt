package com.maximebier.verso.data

/** Une clé DataStore et sa valeur typée : Boolean, Int, Long, Float, Double, String ou Set<String>. */
data class RawSetting(val key: String, val value: Any) {
    init {
        require(isSupported(value)) { "Type de réglage non pris en charge : ${value::class.simpleName}" }
    }

    companion object {
        fun isSupported(value: Any): Boolean =
            value is Boolean || value is Int || value is Long || value is Float || value is Double || value is String ||
                (value is Set<*> && value.all { it is String })
    }
}

/** Carte « Dernière sauvegarde » (3.07) : propre à ce téléphone, jamais dans la sauvegarde. */
data class LastBackup(val at: Long, val sizeBytes: Long, val fileName: String)
