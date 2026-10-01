package com.maximebier.verso.backup

import android.net.Uri
import android.util.Log
import com.maximebier.verso.core.backup.BackupFormat
import com.maximebier.verso.data.DocumentStore
import com.maximebier.verso.data.LastBackup
import com.maximebier.verso.data.SettingsRepository
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Ce que l’écran « Sauvegarde » demande (remplacé par un double dans les tests du ViewModel). */
interface Backups {
    val lastBackup: Flow<LastBackup?>
    fun suggestedFileName(): String
    /** true si le zip est entièrement écrit et la carte « Dernière sauvegarde » mise à jour. */
    suspend fun create(uri: Uri): Boolean
    suspend fun prepareRestore(uri: Uri): RestorePreparation
    suspend fun confirmRestore(): RestoreResult
    suspend fun cancelRestore()
}

/** Sauvegarde et restauration à travers les documents choisis par les sélecteurs Android. Aucun réseau dans Verso. */
class BackupService(
    private val writer: BackupWriter,
    private val restorer: BackupRestorer,
    private val documents: DocumentStore,
    private val settings: SettingsRepository,
    private val clock: () -> Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : Backups {

    override val lastBackup: Flow<LastBackup?> = settings.lastBackup

    override fun suggestedFileName(): String =
        BackupFormat.fileName(Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate())

    override suspend fun create(uri: Uri): Boolean = withContext(NonCancellable + Dispatchers.IO) {
        try {
            val written = documents.openOutput(uri).use { writer.write(it) }
            settings.setLastBackup(
                LastBackup(
                    at = clock(),
                    // Le fournisseur peut rapporter une taille pas encore à jour (1 ko pour 6 Mo sur le téléphone) : la plus grande des deux.
                    sizeBytes = maxOf(documents.size(uri) ?: 0L, written),
                    fileName = documents.displayName(uri) ?: suggestedFileName(),
                ),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // IOException, SecurityException, IllegalStateException du fournisseur
            Log.w(TAG, "Sauvegarde impossible", e)
            documents.delete(uri)
            false
        }
    }

    override suspend fun prepareRestore(uri: Uri): RestorePreparation = try {
        withContext(Dispatchers.IO) { documents.openInput(uri).use { restorer.prepare(it) } }
    } catch (e: IOException) {
        Log.w(TAG, "Document de sauvegarde illisible", e)
        restorer.discard()
        RestorePreparation.Failed
    } catch (e: SecurityException) {
        Log.w(TAG, "Document de sauvegarde refusé", e)
        restorer.discard()
        RestorePreparation.Failed
    }

    override suspend fun confirmRestore(): RestoreResult = restorer.apply()

    override suspend fun cancelRestore() = restorer.discard()

    companion object {
        const val MIME_TYPE = "application/zip"
        /** Certains fournisseurs (Drive, partages réseau) présentent un zip comme octet-stream. */
        val RESTORE_MIME_TYPES: Array<String> = arrayOf("application/zip", "application/octet-stream")
        private const val TAG = "BackupService"
    }
}
