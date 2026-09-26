package com.maximebier.verso.importer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/** Extrait l'URI d'un EPUB reçu par « Ouvrir avec » (VIEW) ou « Partager vers » (SEND). */
object IncomingIntent {

    const val EPUB_MIME_TYPE = "application/epub+zip"

    private val readableSchemes = setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE)

    fun parse(intent: Intent?): Uri? {
        if (intent == null) return null
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> streamExtra(intent)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            else -> null
        }
        return uri?.takeIf { it.scheme in readableSchemes }
    }

    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
}

/**
 * Fichiers reçus d'une autre application, en attente d'import par la bibliothèque.
 * Partagée par tout le processus : MainActivity y dépose, LibraryViewModel y puise.
 */
object IncomingImports {

    private val queue = MutableStateFlow<List<Uri>>(emptyList())

    val pending: StateFlow<List<Uri>> = queue.asStateFlow()

    fun submit(uri: Uri) {
        queue.update { it + uri }
    }

    fun take(): Uri? = queue.getAndUpdate { it.drop(1) }.firstOrNull()

    fun hasPending(): Boolean = queue.value.isNotEmpty()

    fun clear() {
        queue.value = emptyList()
    }
}
