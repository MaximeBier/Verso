package com.maximebier.verso.ui.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Livres que le lecteur n’a pas pu ouvrir (fichier illisible, moteur qui refuse le livre), en attente du message
 * de la bibliothèque (`library_open_failed`). Même principe que `IncomingImports` : le lecteur dépose, la
 * bibliothèque prend.
 */
object OpenFailures {

    private val queue = MutableStateFlow<List<String>>(emptyList())

    val pending: StateFlow<List<String>> = queue.asStateFlow()

    fun report(title: String) {
        queue.update { it + title }
    }

    fun take(): String? = queue.getAndUpdate { it.drop(1) }.firstOrNull()
}
