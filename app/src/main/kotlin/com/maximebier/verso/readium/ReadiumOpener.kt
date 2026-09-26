package com.maximebier.verso.readium

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import com.maximebier.verso.core.text.countWordsInHtml
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.format.Specification
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.use as useResource
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Ouvre les EPUB avec Readium, hors ligne, et classe les refus (pas un EPUB, DRM, illisible). */
class ReadiumOpener(context: Context) {

    sealed interface Failure {
        data object NotEpub : Failure
        data object Drm : Failure
        data class Unreadable(val cause: String) : Failure
    }

    private val appContext = context.applicationContext
    private val assetRetriever = AssetRetriever(appContext.contentResolver, OfflineHttpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = appContext,
            httpClient = OfflineHttpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    /** Ouvre la publication ; l'appelant la ferme (`close()`). Échec = [OpenFailureException]. */
    suspend fun open(file: File): Result<Publication> = withContext(Dispatchers.IO) {
        guarded { openPublication(file) }
    }

    /** Ouvre, lit titre, auteur, couverture et nombre de mots, puis ferme. */
    suspend fun inspect(file: File): Result<EpubInfo> = withContext(Dispatchers.IO) {
        guarded {
            val publication = openPublication(file)
            try {
                EpubInfo(
                    title = publication.metadata.title?.trim()?.takeIf { it.isNotEmpty() },
                    author = publication.metadata.authors
                        .map { it.name.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                        .takeIf { it.isNotEmpty() },
                    cover = readCover(publication),
                    totalWords = countWords(publication),
                )
            } finally {
                publication.close()
            }
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenFailureException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(OpenFailureException(Failure.Unreadable(e.message ?: e.javaClass.simpleName)))
        }

    private suspend fun openPublication(file: File): Publication {
        if (!hasEpubContainer(file)) throw OpenFailureException(Failure.NotEpub)
        val asset: Asset = assetRetriever
            .retrieve(file, FormatHints(mediaType = MediaType.EPUB))
            .getOrElse { error -> throw OpenFailureException(Failure.Unreadable(error.message)) }
        val format = asset.format
        if (!format.conformsTo(Specification.Epub)) {
            asset.close()
            throw OpenFailureException(Failure.NotEpub)
        }
        if (format.conformsTo(Specification.Lcp) || format.conformsTo(Specification.Adept)) {
            asset.close()
            throw OpenFailureException(Failure.Drm)
        }
        val publication = publicationOpener
            .open(asset, allowUserInteraction = false)
            .getOrElse { error ->
                asset.close()
                throw OpenFailureException(Failure.Unreadable(error.message))
            }
        if (publication.isRestricted) {
            publication.close()
            throw OpenFailureException(Failure.Drm)
        }
        return publication
    }

    /** Test par le contenu : un ZIP qui contient META-INF/container.xml. Le nom du fichier n'intervient pas. */
    private fun hasEpubContainer(file: File): Boolean {
        val zip = try {
            ZipFile(file)
        } catch (e: IOException) {
            return false
        }
        try {
            return zip.getEntry(CONTAINER_ENTRY) != null
        } finally {
            zip.close()
        }
    }

    private suspend fun readCover(publication: Publication): Bitmap? =
        try {
            publication.coverFitting(Size(COVER_MAX_WIDTH, COVER_MAX_HEIGHT))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun countWords(publication: Publication): Long =
        publication.readingOrder
            .filter { it.mediaType?.isHtml == true }
            .sumOf { link ->
                val bytes = publication.get(link)?.useResource { resource -> resource.read().getOrNull() }
                if (bytes == null) 0L else countWordsInHtml(bytes.decodeToString())
            }

    private companion object {
        const val CONTAINER_ENTRY = "META-INF/container.xml"
        const val COVER_MAX_WIDTH = 600
        const val COVER_MAX_HEIGHT = 900
    }
}

data class EpubInfo(val title: String?, val author: String?, val cover: Bitmap?, val totalWords: Long)

class OpenFailureException(val failure: ReadiumOpener.Failure) : Exception(failure.toString())
