package com.maximebier.verso.ui.details

import java.time.LocalDateTime
import java.time.ZoneId

/** Fiche de la maquette 1.07 (Madame Bovary, 31 %, 5 h 30 restantes). */
object DetailsSamples {

    val importedAt: Long = LocalDateTime.of(2026, 9, 12, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val bovary = DetailsUiState(
        bookId = 1,
        loaded = true,
        titleField = "Madame Bovary",
        authorField = "Gustave Flaubert",
        savedTitle = "Madame Bovary",
        savedAuthor = "Gustave Flaubert",
        colorSeed = "sha-1",
        coverPath = null,
        percent = 31,
        hasStarted = true,
        remainingMinutes = 330,
        importedAt = importedAt,
        sizeBytes = 1_234_567,
        originalFileName = "madame-bovary.epub",
    )

    val neverOpened = bovary.copy(percent = 0, hasStarted = false, remainingMinutes = 478)

    val deleteDialog = bovary.copy(showDeleteDialog = true)
}
