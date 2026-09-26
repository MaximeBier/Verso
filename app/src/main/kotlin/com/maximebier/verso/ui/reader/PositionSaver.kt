package com.maximebier.verso.ui.reader

import com.maximebier.verso.core.model.BookPosition
import com.maximebier.verso.core.position.ReadingThresholds
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.readium.Locators
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator

/**
 * Écrit la position de LECTURE en base : anti-rebond de 500 ms, écriture immédiate à la mise en
 * arrière-plan, locator enrichi du texte visible quand la lecture est à l’écran.
 * La position affichée n’est jamais écrite.
 */
class PositionSaver(
    private val bookId: Long,
    private val save: suspend (bookId: Long, locatorJson: String, progression: Double) -> Unit,
    private val scope: CoroutineScope,
    private val debounceMs: Long = ReadingThresholds().saveDebounceMs,
) {
    private var controller: ReaderController? = null
    private var pending: BookPosition? = null
    private var debounceJob: Job? = null
    private val writeMutex = Mutex()

    fun attach(controller: ReaderController) {
        this.controller = controller
    }

    /** Position de lecture à écrire (effet `SaveReading` de la machine à états). */
    fun requestSave(position: BookPosition) {
        pending = position
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            writePending()
        }
    }

    /** Mise en arrière-plan (`onStop`) : écrit tout de suite ce qui attend, sans être annulable. */
    fun flush() {
        debounceJob?.cancel()
        debounceJob = null
        scope.launch {
            withContext(NonCancellable) { writePending() }
        }
    }

    private suspend fun writePending() {
        writeMutex.withLock {
            val position = pending ?: return
            pending = null
            val locator = Locators.fromJson(position.locatorJson)
            val json = if (locator != null) Locators.toJson(enrich(locator)) else position.locatorJson
            save(bookId, json, position.totalProgression)
        }
    }

    /** Ajoute texte visible et sélecteur seulement si la position de lecture est celle à l’écran. */
    private suspend fun enrich(target: Locator): Locator {
        val reader = controller ?: return target
        val shown = reader.displayed.value ?: return target
        if (!sameSpot(target, shown)) return target
        val excerpt = reader.excerptLocator() ?: return target
        if (Locators.hrefKey(excerpt) != Locators.hrefKey(target)) return target
        return target.copy(
            text = excerpt.text,
            locations = target.locations.copy(
                otherLocations = target.locations.otherLocations + excerpt.locations.otherLocations,
            ),
        )
    }

    private fun sameSpot(a: Locator, b: Locator): Boolean =
        Locators.hrefKey(a) == Locators.hrefKey(b) &&
            abs((a.locations.progression ?: 0.0) - (b.locations.progression ?: 0.0)) <= SAME_SPOT_TOLERANCE

    companion object {
        /** Écart de progression dans le chapitre en dessous duquel lecture et affichage coïncident. */
        const val SAME_SPOT_TOLERANCE = 0.000_5
    }
}
