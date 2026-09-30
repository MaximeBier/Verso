package com.maximebier.verso.ui.library

import com.maximebier.verso.core.model.BookStatus
import com.maximebier.verso.data.LibraryViewMode
import com.maximebier.verso.data.db.BookEntity
import com.maximebier.verso.importer.ImportResult
import com.maximebier.verso.importer.PendingImport
import com.maximebier.verso.importer.RejectReason
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Données d'exemple des maquettes 1.02 à 1.06 (vignettes générées : aucune couverture sur disque). */
object LibrarySamples {

    private fun book(id: Long, title: String, author: String, status: BookStatus, progression: Double) = LibraryBook(
        id = id, title = title, author = author, coverPath = null, colorSeed = "sha-$id",
        status = status, progression = progression, percent = (progression * 100).toInt(),
    )

    val bovary = book(1, "Madame Bovary", "Gustave Flaubert", BookStatus.IN_PROGRESS, 0.31)

    val books = listOf(
        bovary,
        book(2, "Vingt mille lieues sous les mers", "Jules Verne", BookStatus.IN_PROGRESS, 0.64),
        book(3, "Bel-Ami", "Guy de Maupassant", BookStatus.IN_PROGRESS, 0.47),
        book(4, "Germinal", "Émile Zola", BookStatus.IN_PROGRESS, 0.12),
        book(5, "Le Grand Meaulnes", "Alain-Fournier", BookStatus.FINISHED, 1.0),
        book(6, "Le Rouge et le Noir", "Stendhal", BookStatus.TO_READ, 0.0),
        book(7, "Les Misérables", "Victor Hugo", BookStatus.IN_PROGRESS, 0.08),
    )

    val resume = ResumeInfo(
        book = bovary,
        chapter = "Deuxième partie, chapitre I",
        excerpt = "Yonville-l’Abbaye (ainsi nommé à cause d’une ancienne abbaye de Capucins dont les ruines n’existent même plus)",
        remainingMinutes = 330,
    )

    /** Bibliothèque de la maquette 2.07 : Madame Bovary en carte, puis les états « À lire » et « Terminé ». */
    val states = LibraryUiState(
        loading = false,
        books = listOf(
            bovary,
            book(2, "Vingt mille lieues sous les mers", "Jules Verne", BookStatus.IN_PROGRESS, 0.64),
            book(8, "Le Comte de Monte-Cristo", "Alexandre Dumas", BookStatus.TO_READ, 0.0),
            book(5, "Le Grand Meaulnes", "Alain-Fournier", BookStatus.FINISHED, 1.0),
            book(3, "Bel-Ami", "Guy de Maupassant", BookStatus.IN_PROGRESS, 0.47),
            book(4, "Germinal", "Émile Zola", BookStatus.IN_PROGRESS, 0.12),
            book(6, "Le Rouge et le Noir", "Stendhal", BookStatus.TO_READ, 0.0),
        ),
        resume = resume,
    )

    val list = LibraryUiState(loading = false, books = books, resume = resume)
    val grid = list.copy(viewMode = LibraryViewMode.GRID)
    val empty = LibraryUiState(loading = false)

    private val germinalEntity = BookEntity(
        id = 4, title = "Germinal", author = "Émile Zola", filePath = "/livres/g.epub", sha256 = "sha-4",
        coverPath = null, sizeBytes = 1_200_000, originalFileName = "germinal.epub",
        importedAt = LocalDateTime.of(2026, 9, 3, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        lastOpenedAt = null, readingLocatorJson = null, progression = 0.12, totalWords = 180_000,
    )

    val duplicate = list.copy(
        dialog = ImportDialog.Duplicate(
            ImportResult.Duplicate(germinalEntity, PendingImport(File("/cache/import/x.tmp"), "sha-4", "germinal.epub", 1_200_000)),
        ),
    )

    val rejected = list.copy(dialog = ImportDialog.Rejected(RejectReason.NOT_EPUB, "notes-de-cours.pdf"))

    val snackbar = list.copy(snackbar = ImportSnackbar(6, "Le Rouge et le Noir"))
}
