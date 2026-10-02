package com.maximebier.verso.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.maximebier.verso.importer.IncomingImports
import com.maximebier.verso.ui.collections.CollectionDestination
import com.maximebier.verso.ui.collections.NewCollectionDestination
import com.maximebier.verso.ui.details.DetailsDestination
import com.maximebier.verso.ui.library.LibraryDestination
import com.maximebier.verso.ui.notes.NotesDestination
import com.maximebier.verso.ui.reader.ReaderDestination
import com.maximebier.verso.ui.settings.BackupDestination
import com.maximebier.verso.ui.settings.LicensesDestination
import com.maximebier.verso.ui.settings.SettingsDestination
import com.maximebier.verso.ui.theme.VersoTheme

/** Routes de l’app. Chaque destination délègue à un point d'entrée du fichier de sa fonctionnalité. */
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
        screen<LibraryRoute> { entry ->
            if (!autoReopenHandled) {
                Box(Modifier.fillMaxSize().background(VersoTheme.colors.background))
                return@screen
            }
            LibraryDestination(
                onOpenSettings = { if (entry.resumed()) navController.navigate(SettingsRoute) },
                onOpenDetails = { bookId -> if (entry.resumed()) navController.navigate(DetailsRoute(bookId)) },
                onOpenReader = { bookId -> if (entry.resumed()) navController.openReader(bookId) },
                onNewCollection = { if (entry.resumed()) navController.navigate(NewCollectionRoute()) },
                onOpenCollection = { id -> if (entry.resumed()) navController.navigate(CollectionRoute(id)) },
                onNewCollectionFor = { bookId -> if (entry.resumed()) navController.navigate(NewCollectionRoute(bookId)) },
            )
        }
        screen<NewCollectionRoute> { entry ->
            val route = entry.toRoute<NewCollectionRoute>()
            NewCollectionDestination(
                preselectedBookId = route.preselectedBookId,
                onClose = { if (entry.resumed()) navController.popBackStack() },
                // Depuis la feuille 4.04 (livre présélectionné) : retour à la feuille ; sinon, l’écran de la collection créée.
                onCreated = { id ->
                    if (route.preselectedBookId != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(CollectionRoute(id)) { popUpTo<NewCollectionRoute> { inclusive = true } }
                    }
                },
            )
        }
        screen<CollectionRoute> { entry ->
            val route = entry.toRoute<CollectionRoute>()
            CollectionDestination(
                collectionId = route.collectionId,
                onBack = { if (entry.resumed()) navController.popBackStack() },
                onOpenReader = { bookId -> if (entry.resumed()) navController.openReader(bookId) },
                onOpenDetails = { bookId -> if (entry.resumed()) navController.navigate(DetailsRoute(bookId)) },
            )
        }
        screen<DetailsRoute> { entry ->
            val route = entry.toRoute<DetailsRoute>()
            DetailsDestination(
                bookId = route.bookId,
                onBack = { if (entry.resumed()) navController.popBackStack() },
                onOpenReader = { bookId -> if (entry.resumed()) navController.openReader(bookId) },
                onOpenJournal = { bookId ->
                    if (entry.resumed()) navController.navigate(ReaderRoute(bookId, openJournal = true)) { launchSingleTop = true }
                },
                onOpenNotes = { bookId -> if (entry.resumed()) navController.navigate(NotesRoute(bookId)) },
                onNewCollection = { bookId -> if (entry.resumed()) navController.navigate(NewCollectionRoute(bookId)) },
            )
        }
        screen<NotesRoute> { entry ->
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
        screen<SettingsRoute> { entry ->
            SettingsDestination(
                onBack = { if (entry.resumed()) navController.popBackStack() },
                onOpenLicenses = { if (entry.resumed()) navController.navigate(LicensesRoute) },
                onOpenBackup = { if (entry.resumed()) navController.navigate(BackupRoute) },
            )
        }
        screen<BackupRoute> { entry ->
            BackupDestination(
                onBack = { if (entry.resumed()) navController.popBackStack() },
                // Bibliothèque restaurée : on y arrive directement, Paramètres et Sauvegarde sont dépilés.
                onRestored = { navController.popBackStack<LibraryRoute>(inclusive = false) },
            )
        }
        screen<LicensesRoute> { entry ->
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

/**
 * Destination hors lecture : son contenu reste entre l’encoche et la barre de navigation, qui passent sur les côtés en
 * paysage. Le lecteur gère ses côtés lui-même (texte en plein écran, barres décalées).
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry ->
        Box(
            Modifier
                .fillMaxSize()
                .background(VersoTheme.colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) { content(entry) }
    }
}
