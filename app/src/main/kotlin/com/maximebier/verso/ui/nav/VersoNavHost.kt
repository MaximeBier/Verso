package com.maximebier.verso.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.maximebier.verso.importer.IncomingImports
import com.maximebier.verso.ui.details.DetailsDestination
import com.maximebier.verso.ui.library.LibraryDestination
import com.maximebier.verso.ui.notes.NotesDestination
import com.maximebier.verso.ui.reader.ReaderDestination
import com.maximebier.verso.ui.settings.LicensesDestination
import com.maximebier.verso.ui.settings.SettingsDestination
import com.maximebier.verso.ui.theme.VersoTheme

/** Les 5 routes de la V1. Chaque destination délègue à un point d'entrée du fichier de sa fonctionnalité. */
@Composable
fun VersoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    reopenBookId: Long? = null,
) {
    // Ouverture automatique du dernier livre (spec, « Ouverture au lancement ») : une seule fois par lancement,
    // jamais si un fichier reçu attend son import. La bibliothèque reste dessous dans la pile arrière, mais n’est pas
    // dessinée avant : le lancement montre directement le livre.
    var autoReopenHandled by rememberSaveable { mutableStateOf(reopenBookId == null) }
    NavHost(navController = navController, startDestination = LibraryRoute, modifier = modifier) {
        composable<LibraryRoute> { entry ->
            if (!autoReopenHandled) {
                Box(Modifier.fillMaxSize().background(VersoTheme.colors.background))
                return@composable
            }
            LibraryDestination(
                onOpenSettings = { if (entry.resumed()) navController.navigate(SettingsRoute) },
                onOpenDetails = { bookId -> if (entry.resumed()) navController.navigate(DetailsRoute(bookId)) },
                onOpenReader = { bookId -> if (entry.resumed()) navController.openReader(bookId) },
            )
        }
        composable<DetailsRoute> { entry ->
            val route = entry.toRoute<DetailsRoute>()
            DetailsDestination(
                bookId = route.bookId,
                onBack = { if (entry.resumed()) navController.popBackStack() },
                onOpenReader = { bookId -> if (entry.resumed()) navController.openReader(bookId) },
                onOpenJournal = { bookId ->
                    if (entry.resumed()) navController.navigate(ReaderRoute(bookId, openJournal = true)) { launchSingleTop = true }
                },
                onOpenNotes = { bookId -> if (entry.resumed()) navController.navigate(NotesRoute(bookId)) },
            )
        }
        composable<NotesRoute> { entry ->
            val route = entry.toRoute<NotesRoute>()
            NotesDestination(
                bookId = route.bookId,
                onBack = { if (entry.resumed()) navController.popBackStack() },
                // Aller au passage : le livre s’ouvre et saute au surlignage (carte « Revenir »).
                onOpenPassage = { bookId, highlightId ->
                    if (entry.resumed()) navController.navigate(ReaderRoute(bookId, highlightId = highlightId)) { launchSingleTop = true }
                },
            )
        }
        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            ReaderDestination(
                bookId = route.bookId,
                onBack = { if (entry.resumed()) navController.popBackStack() },
                // Échec d’ouverture : toujours la bibliothèque, qui affiche le message (même ouvert depuis la fiche).
                onOpenFailed = { navController.popBackStack<LibraryRoute>(inclusive = false) },
                openJournal = route.openJournal,
                highlightId = route.highlightId,
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsDestination(
                onBack = { if (entry.resumed()) navController.popBackStack() },
                onOpenLicenses = { if (entry.resumed()) navController.navigate(LicensesRoute) },
            )
        }
        composable<LicensesRoute> { entry ->
            LicensesDestination(onBack = { if (entry.resumed()) navController.popBackStack() })
        }
    }

    LaunchedEffect(Unit) {
        if (autoReopenHandled) return@LaunchedEffect
        // Revérifié ici : un « Ouvrir avec » peut être arrivé (onNewIntent) depuis la décision.
        if (reopenBookId != null && !IncomingImports.hasPending()) navController.openReader(reopenBookId)
        autoReopenHandled = true
    }
}

/**
 * Écran au premier plan : un second tap pendant une transition (retour ou ouverture déjà en cours) est ignoré,
 * sinon il dépilerait la bibliothèque elle-même (écran vide) ou empilerait un second écran.
 */
private fun NavBackStackEntry.resumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

/** Un seul lecteur à la fois en haut de la pile. */
private fun NavHostController.openReader(bookId: Long) = navigate(ReaderRoute(bookId)) { launchSingleTop = true }
