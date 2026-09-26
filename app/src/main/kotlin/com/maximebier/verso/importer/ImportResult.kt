package com.maximebier.verso.importer

import com.maximebier.verso.data.db.BookEntity
import java.io.File

sealed interface ImportResult {
    data class Added(val bookId: Long, val title: String) : ImportResult
    data class Duplicate(val existing: BookEntity, val pending: PendingImport) : ImportResult
    data class Rejected(val reason: RejectReason) : ImportResult
}

enum class RejectReason { NOT_EPUB, DRM, UNREADABLE }

/** Fichier copié et haché, en attente de la décision « Remplacer » / « Ignorer ». */
data class PendingImport(val tempFile: File, val sha256: String, val originalFileName: String, val sizeBytes: Long)
