package com.maximebier.verso.ui.nav

import com.maximebier.verso.core.model.LibraryRules
import com.maximebier.verso.data.db.BookEntity

/**
 * Choix de l’écran d’arrivée : rouvrir le dernier livre (moins de 24 h) sauf si un fichier reçu
 * (« Ouvrir avec », « Partager vers » : `IncomingImports.hasPending()`, tâche 3.2) attend son import.
 */
object StartDestination {

    fun bookToReopen(lastOpened: BookEntity?, reopenEnabled: Boolean, now: Long, hasIncomingImport: Boolean): Long? {
        if (hasIncomingImport || lastOpened == null) return null
        return lastOpened.id.takeIf { LibraryRules.shouldReopen(reopenEnabled, lastOpened.lastOpenedAt, now) }
    }
}
