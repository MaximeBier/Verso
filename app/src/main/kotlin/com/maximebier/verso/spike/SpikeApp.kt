package com.maximebier.verso.spike

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.readium.r2.shared.publication.Publication

private data class Selection(val variant: String, val book: SpikeBook)

@Composable
fun SpikeApp(activity: FragmentActivity) {
    val context = LocalContext.current.applicationContext
    val library = remember { SpikeLibrary(context) }
    val store = remember { LocatorStore(context) }
    val log = remember { SpikeLog(context) }
    // remember (pas rememberSaveable) : après une fermeture brutale on repart de l'accueil, comme au premier lancement.
    var selection by remember { mutableStateOf<Selection?>(null) }

    val current = selection
    if (current == null) {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Prototype Readium — Verso")
            SpikeBook.entries.forEach { book ->
                listOf("A", "B").forEach { variant ->
                    Button(onClick = { selection = Selection(variant, book) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Variante $variant · ${book.label}")
                    }
                }
            }
            OutlinedButton(onClick = { log.clear() }, modifier = Modifier.fillMaxWidth()) {
                Text("Vider le journal CSV")
            }
        }
        return
    }

    BackHandler { selection = null }
    val publication by produceState<Publication?>(initialValue = null, current.book) {
        value = library.open(current.book)
    }
    val opened = publication
    when {
        opened == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        current.variant == "A" -> VariantAReader(opened, current.book, store, log)
        else -> VariantBReader(activity, opened, current.book, store, log)
    }
}
