package com.maximebier.verso.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fichiers choisis par les sélecteurs d’Android (export des notes, sauvegarde) : aucune permission, l’app n’écrit et ne
 * lit que l’URI que l’utilisateur lui donne. Un fournisseur en ligne (Drive…) envoie lui-même le fichier.
 */
class DocumentStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        openOutput(uri).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    /** « wt » : un fichier existant choisi à nouveau est remplacé, pas complété. */
    fun openOutput(uri: Uri): OutputStream =
        resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())

    fun openInput(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Taille annoncée par le fournisseur du document ; null s’il ne la donne pas. */
    fun size(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    /** Supprime un document créé par le sélecteur (écriture ratée) ; false si le fournisseur refuse. */
    fun delete(uri: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
}

