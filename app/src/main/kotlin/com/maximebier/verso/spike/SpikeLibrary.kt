package com.maximebier.verso.spike

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import org.readium.r2.shared.util.http.HttpTry
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Nom de famille CSS sous lequel Atkinson est déclarée aux deux navigateurs. */
internal val SpikeAtkinson = FontFamily("Atkinson Hyperlegible Next")

/** Client HTTP qui refuse tout : Verso n'utilise pas le réseau. */
object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
        Try.failure(HttpError.Unreachable(DebugError("Réseau désactivé dans Verso")))
}

enum class SpikeBook(val assetPath: String, val label: String) {
    BOVARY("spike/madame-bovary.epub", "Madame Bovary (FR, Gutenberg 14155)"),
    PRIDE("spike/pride-and-prejudice.epub", "Pride and Prejudice (EN, Gutenberg 1342)"),
}

class SpikeLibrary(private val context: Context) {
    private val assetRetriever = AssetRetriever(context.contentResolver, OfflineHttpClient)
    private val opener = PublicationOpener(
        DefaultPublicationParser(context, OfflineHttpClient, assetRetriever, pdfFactory = null),
    )

    /** Copie l'EPUB des assets vers filesDir (une fois) puis l'ouvre avec Readium. */
    suspend fun open(book: SpikeBook): Publication = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, book.assetPath.substringAfter('/'))
        if (!file.exists()) {
            context.assets.open(book.assetPath).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false))
            .getOrElse { failure -> error("Asset illisible : ${failure.message}") }
        opener.open(asset, allowUserInteraction = false)
            .getOrElse { failure -> error("Ouverture impossible : ${failure.message}") }
    }
}

/** Sauvegarde synchrone (commit) : survit à une fermeture brutale juste après l'écriture. */
class LocatorStore(context: Context) {
    private val prefs = context.getSharedPreferences("spike-locators", Context.MODE_PRIVATE)

    fun save(key: String, locator: Locator) {
        prefs.edit().putString(key, locator.toJSON().toString()).commit()
    }

    fun load(key: String): Locator? =
        prefs.getString(key, null)?.let { Locator.fromJSON(JSONObject(it)) }
}

/** Journal CSV dans filesDir/spike-log.csv, doublé dans Logcat (étiquette VersoSpike). */
class SpikeLog(context: Context) {
    private val file = File(context.filesDir, "spike-log.csv")

    fun write(line: String) {
        Log.i(TAG, line)
        file.appendText(line + "\n")
    }

    fun clear() {
        file.delete()
    }

    companion object {
        const val TAG = "VersoSpike"
    }
}
