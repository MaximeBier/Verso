package com.maximebier.verso.data

import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.core.model.LibraryRules
import com.maximebier.verso.data.db.BookEntity

/** État affiché d'un livre : calculé, sauf choix manuel (`stateOverride`). Une valeur inconnue est ignorée. */
fun BookEntity.status(): BookStatus = LibraryRules.status(
    hasReadingLocator = readingLocatorJson != null,
    progression = progression,
    override = stateOverride?.let { stored -> BookStatus.entries.firstOrNull { it.name == stored } },
)
