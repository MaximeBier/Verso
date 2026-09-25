package com.maximebier.verso.ui.library

import androidx.compose.runtime.Composable

/**
 * Point d'entrée de la route LibraryRoute. Corps provisoire (bibliothèque vide, import inactif) :
 * la tâche 3.3 le remplace par la bibliothèque complète en gardant cette signature.
 */
@Composable
fun LibraryDestination(onOpenSettings: () -> Unit, onOpenDetails: (Long) -> Unit, onOpenReader: (Long) -> Unit) {
    EmptyLibraryScreen(onOpenSettings = onOpenSettings, onImport = {})
}
