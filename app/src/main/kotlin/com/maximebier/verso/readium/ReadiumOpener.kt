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

/** Noms des étapes journalisées par [ReadiumOpener.inspect] (voir `onPhase`), repris par [EpubImporter]. */
const val PHASE_OPEN = "ouverture"
const val PHASE_COVER = "couverture"
const val PHASE_WORDS = "mots"

/** Durée d’une phase d’import (onPhase), en millisecondes. */
private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

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

    /**
     * Ouvre, lit titre, auteur, couverture et nombre de mots, puis ferme.
     *
     * [onPhase] est appelé après chaque étape mesurée (« ouverture », « couverture », « mots ») avec sa
     * durée en millisecondes ; par défaut il ne fait rien, donc ne change aucun comportement existant.
     * Sert uniquement à journaliser la durée des imports (voir [EpubImporter]).
     */
    suspend fun inspect(file: File, onPhase: (phase: String, ms: Long) -> Unit = { _, _ -> }): Result<EpubInfo> =
        withContext(Dispatchers.IO) {
            guarded {
                val openStart = System.nanoTime()
                val publication = openPublication(file)
                onPhase(PHASE_OPEN, elapsedMs(openStart))
                try {
                    val coverStart = System.nanoTime()
                    val cover = readCover(publication)
                    onPhase(PHASE_COVER, elapsedMs(coverStart))
                    val wordsStart = System.nanoTime()
                    val words = countWords(publication)
                    onPhase(PHASE_WORDS, elapsedMs(wordsStart))
                    EpubInfo(
                        title = publication.metadata.title?.trim()?.takeIf { it.isNotEmpty() },
                        author = publication.metadata.authors
                            .map { it.name.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString(", ")
                            .takeIf { it.isNotEmpty() },
                        cover = cover,
                        totalWords = words,
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

    /**
     * Lien `rel=cover` déclaré par la publication (métadonnée EPUB 2 `<meta name="cover">` ou
     * propriété EPUB 3 `cover-image`), parmi les ressources et le fil de lecture.
     */
    private fun coverLink(publication: Publication) =
        (publication.readingOrder + publication.resources).firstOrNull { COVER_REL in it.rels }

    /**
     * Couverture réelle, jamais d'image de repli : si le lien `rel=cover` désigne une ressource
     * qui n'est pas une image (par exemple une page XHTML, cas observé chez Wikisource), on ne
     * tente même pas de la décoder — un décodeur trop permissif pourrait sinon y voir une image.
     */
    private suspend fun readCover(publication: Publication): Bitmap? {
        if (coverLink(publication)?.mediaType?.isBitmap != true) return null
        return try {
            publication.coverFitting(Size(COVER_MAX_WIDTH, COVER_MAX_HEIGHT))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Mots de toutes les ressources HTML de l’ordre de lecture (la synthèse par import est journalisée sous `VersoImport`). */
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
        const val COVER_REL = "cover"
    }
}

data class EpubInfo(val title: String?, val author: String?, val cover: Bitmap?, val totalWords: Long)

class OpenFailureException(val failure: ReadiumOpener.Failure) : Exception(failure.toString())
