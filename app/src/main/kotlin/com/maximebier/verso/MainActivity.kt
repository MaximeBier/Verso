package com.maximebier.verso

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maximebier.verso.importer.IncomingImports
import com.maximebier.verso.importer.IncomingIntent
import com.maximebier.verso.ui.nav.LibraryRoute
import com.maximebier.verso.ui.nav.VersoNavHost
import com.maximebier.verso.ui.theme.VersoTheme

/** Activité unique. FragmentActivity : le navigateur EPUB de Readium peut être un Fragment. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Fichier reçu par « Ouvrir avec » / « Partager vers ». Après une recréation, l'intent a déjà été traité.
        // Appelé avant setContent : IncomingImports.hasPending() est donc connu dès la première composition.
        if (savedInstanceState == null) handleIncomingIntent(intent)
        // Premier lancement seulement (après une recréation, Navigation restaure la pile), et jamais devant un import.
        val allowAutoReopen = savedInstanceState == null && !IncomingImports.hasPending()
        setContent {
            VersoTheme {
                val navController = rememberNavController()
                VersoNavHost(navController = navController, allowAutoReopen = allowAutoReopen)
                ReturnToLibraryOnIncomingImport(navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        IncomingIntent.parse(intent)?.let(IncomingImports::submit)
    }
}

/**
 * Un fichier reçu d'une autre application ramène à la bibliothèque, qui l'importe et affiche le résultat
 * (priorité sur le livre ouvert, y compris quand Verso tournait déjà).
 */
@Composable
private fun ReturnToLibraryOnIncomingImport(navController: NavHostController) {
    val incoming by IncomingImports.pending.collectAsState()
    val hasIncoming = incoming.isNotEmpty()
    LaunchedEffect(hasIncoming) {
        if (!hasIncoming) return@LaunchedEffect
        if (navController.currentDestination?.hasRoute<LibraryRoute>() == true) return@LaunchedEffect
        if (!navController.popBackStack<LibraryRoute>(inclusive = false)) {
            navController.navigate(LibraryRoute) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }
}
