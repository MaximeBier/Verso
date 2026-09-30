package com.maximebier.verso.ui.library

import com.maximebier.verso.core.model.BookStatus

/** Filtres « Tous / En cours / À lire / Terminés » (2.07), dans l'ordre des pastilles. Non enregistré. */
enum class LibraryFilter {
    ALL,
    IN_PROGRESS,
    TO_READ,
    FINISHED,
    ;

    fun accepts(status: BookStatus): Boolean = when (this) {
        ALL -> true
        IN_PROGRESS -> status == BookStatus.IN_PROGRESS
        TO_READ -> status == BookStatus.TO_READ
        FINISHED -> status == BookStatus.FINISHED
    }
}
