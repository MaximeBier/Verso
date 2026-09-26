package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.reader.ReaderController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écrit la position de lecture en base. Étape 4 : écriture immédiate de chaque demande.
 * La tâche 5.4 ajoute l’anti-rebond (`debounceMs`) et l’extrait, sans changer cette signature.
 */
class PositionSaver(
    private val bookId: Long,
    private val save: suspend (bookId: Long, locatorJson: String, progression: Double) -> Unit,
    private val scope: CoroutineScope,
    private val debounceMs: Long = ReadingThresholds().saveDebounceMs,
) {
    private var controller: ReaderController? = null
    private var lastRequested: BookPosition? = null

    fun attach(controller: ReaderController) {
        this.controller = controller
    }

    fun requestSave(position: BookPosition) {
        lastRequested = position
        scope.launch { save(bookId, position.locatorJson, position.totalProgression) }
    }

    /** Mise en arrière-plan : réécrit la dernière demande, même si l’écran est en train de se fermer. */
    fun flush() {
        val position = lastRequested ?: return
        scope.launch {
            withContext(NonCancellable) { save(bookId, position.locatorJson, position.totalProgression) }
        }
    }
}
