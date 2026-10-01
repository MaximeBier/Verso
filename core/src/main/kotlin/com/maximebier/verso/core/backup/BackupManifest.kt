package com.maximebier.verso.core.backup

import java.time.LocalDate

/** Format du zip de sauvegarde : JSON (pas une copie de la base), EPUB et couvertures. */
object BackupFormat {
    /**
     * Écrit dans le champ `format` de `donnees.json` ; une sauvegarde d’un numéro supérieur est refusée.
     * 1 : V3. 2 : collections (V4) ; le format 1 se lit toujours, sans collection.
     */
    const val VERSION = 2
    const val DATA_ENTRY = "donnees.json"
    const val SETTINGS_ENTRY = "reglages.json"
    const val BOOKS_FOLDER = "livres"
    const val COVERS_FOLDER = "couvertures"
    const val BOOK_EXTENSION = ".epub"

    /** Couvertures : JPEG depuis la V2 (EpubImporter), PNG accepté. */
    val COVER_EXTENSIONS: List<String> = listOf(".jpg", ".png")

    private val SHA256 = Regex("[0-9a-f]{64}")

    fun isSha256(value: String): Boolean = SHA256.matches(value)

    fun bookEntry(sha256: String): String = "$BOOKS_FOLDER/$sha256$BOOK_EXTENSION"

    fun coverEntry(coverFile: String): String = "$COVERS_FOLDER/$coverFile"

    /** Nom de couverture permis pour ce livre : `<sha256>.jpg` ou `<sha256>.png`. */
    fun isCoverFileOf(sha256: String, coverFile: String): Boolean = COVER_EXTENSIONS.any { coverFile == sha256 + it }

    /** Empreinte d’une entrée `livres/<sha256>.epub` ; null pour toute autre entrée. */
    fun bookSha256(entry: String): String? {
        val prefix = "$BOOKS_FOLDER/"
        if (!entry.startsWith(prefix) || !entry.endsWith(BOOK_EXTENSION)) return null
        return entry.removePrefix(prefix).removeSuffix(BOOK_EXTENSION).takeIf(::isSha256)
    }

    /**
     * Entrée que la restauration extrait. Tout le reste est ignoré : jamais de chemin choisi par le fichier
     * (`../`, majuscules, autres extensions), donc jamais d’écriture hors du dossier temporaire.
     */
    fun isKnownEntry(entry: String): Boolean =
        entry == DATA_ENTRY || entry == SETTINGS_ENTRY || bookSha256(entry) != null || isCoverEntry(entry)

    private fun isCoverEntry(entry: String): Boolean {
        val prefix = "$COVERS_FOLDER/"
        if (!entry.startsWith(prefix)) return false
        val file = entry.removePrefix(prefix)
        return COVER_EXTENSIONS.any { extension -> file.endsWith(extension) && isSha256(file.removeSuffix(extension)) }
    }

    /** « verso-sauvegarde-2026-09-20.zip ». */
    fun fileName(date: LocalDate): String = "verso-sauvegarde-$date.zip"

    fun checkVersion(format: Int): BackupCheck = when {
        format > VERSION -> BackupCheck.NewerFormat
        format < 1 -> BackupCheck.Invalid("format $format")
        else -> BackupCheck.Valid
    }
}

sealed interface BackupCheck {
    data object Valid : BackupCheck
    data object NewerFormat : BackupCheck
    data class Invalid(val reason: String) : BackupCheck
}

data class BackupBookRef(val id: Long, val sha256: String, val coverFile: String?)

/** Session ou surlignage : son id et le livre auquel il appartient. */
data class BackupItemRef(val id: Long, val bookId: Long)

/** Collection : son id et ses livres dans l’ordre de lecture. */
data class BackupCollectionRef(val id: Long, val bookIds: List<Long>)

/** Ce que la restauration a lu : données de `donnees.json` et entrées extraites du zip. */
data class BackupManifest(
    val format: Int,
    val books: List<BackupBookRef>,
    val sessions: List<BackupItemRef>,
    val highlights: List<BackupItemRef>,
    val entries: Set<String>,
    val collections: List<BackupCollectionRef> = emptyList(),
) {
    /** Format d’abord (une sauvegarde plus récente est refusée telle quelle), puis fichiers et identifiants. */
    fun check(): BackupCheck {
        val version = BackupFormat.checkVersion(format)
        if (version != BackupCheck.Valid) return version
        if (BackupFormat.DATA_ENTRY !in entries) return BackupCheck.Invalid("${BackupFormat.DATA_ENTRY} absent")
        if (BackupFormat.SETTINGS_ENTRY !in entries) return BackupCheck.Invalid("${BackupFormat.SETTINGS_ENTRY} absent")
        val bookIds = HashSet<Long>()
        val fingerprints = HashSet<String>()
        for (book in books) {
            if (book.id <= 0 || !bookIds.add(book.id)) return BackupCheck.Invalid("livre ${book.id} invalide ou en double")
            if (!BackupFormat.isSha256(book.sha256)) return BackupCheck.Invalid("empreinte mal formée (livre ${book.id})")
            if (!fingerprints.add(book.sha256)) return BackupCheck.Invalid("empreinte en double (livre ${book.id})")
            if (BackupFormat.bookEntry(book.sha256) !in entries) return BackupCheck.Invalid("EPUB absent (livre ${book.id})")
            val cover = book.coverFile
            if (cover != null && (!BackupFormat.isCoverFileOf(book.sha256, cover) || BackupFormat.coverEntry(cover) !in entries)) {
                return BackupCheck.Invalid("couverture absente (livre ${book.id})")
            }
        }
        return checkItems("session", sessions, bookIds)
            ?: checkItems("surlignage", highlights, bookIds)
            ?: checkCollections(bookIds)
            ?: BackupCheck.Valid
    }

    private fun checkCollections(bookIds: Set<Long>): BackupCheck? {
        val seen = HashSet<Long>()
        for (collection in collections) {
            if (collection.id <= 0 || !seen.add(collection.id)) return BackupCheck.Invalid("collection ${collection.id} invalide ou en double")
            if (collection.bookIds.toSet().size != collection.bookIds.size) return BackupCheck.Invalid("livre en double (collection ${collection.id})")
            val missing = collection.bookIds.firstOrNull { it !in bookIds }
            if (missing != null) return BackupCheck.Invalid("collection ${collection.id} sans livre $missing")
        }
        return null
    }

    private fun checkItems(kind: String, items: List<BackupItemRef>, bookIds: Set<Long>): BackupCheck? {
        val seen = HashSet<Long>()
        for (item in items) {
            if (item.id <= 0 || !seen.add(item.id)) return BackupCheck.Invalid("$kind ${item.id} invalide ou en double")
            if (item.bookId !in bookIds) return BackupCheck.Invalid("$kind ${item.id} sans livre ${item.bookId}")
        }
        return null
    }
}

