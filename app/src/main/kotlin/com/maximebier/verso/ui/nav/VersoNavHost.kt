package com.maximebier.verso.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.importer.IncomingImports
import com.maximebier.verso.ui.details.DetailsDestination
import com.maximebier.verso.ui.library.LibraryDestination
import com.maximebier.verso.ui.reader.ReaderDestination
import com.maximebier.verso.ui.settings.LicensesDestination
import com.maximebier.verso.ui.settings.SettingsDestination
import kotlinx.coroutines.flow.first

/** Les 5 routes de la V1. Chaque destination délègue à un point d'entrée du fichier de sa fonctionnalité. */
@Composable
fun VersoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    allowAutoReopen: Boolean = false,
) {
    NavHost(navController = navController, startDestination = LibraryRoute, modifier = modifier) {
        composable<LibraryRoute> {
            LibraryDestination(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenDetails = { bookId -> navController.navigate(DetailsRoute(bookId)) },
                onOpenReader = { bookId -> navController.navigate(ReaderRoute(bookId)) },
            )
        }
        composable<DetailsRoute> { entry ->
            val route = entry.toRoute<DetailsRoute>()
            DetailsDestination(
                bookId = route.bookId,
                onBack = { navController.popBackStack() },
                onOpenReader = { bookId -> navController.navigate(ReaderRoute(bookId)) },
            )
        }
        composable<ReaderRoute> { entry ->
            val route = entry.toRoute<ReaderRoute>()
            ReaderDestination(bookId = route.bookId, onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsDestination(
                onBack = { navController.popBackStack() },
                onOpenLicenses = { navController.navigate(LicensesRoute) },
            )
        }
        composable<LicensesRoute> {
            LicensesDestination(onBack = { navController.popBackStack() })
        }
    }

    // Ouverture automatique du dernier livre (spec, « Ouverture au lancement ») : une seule fois par lancement,
    // jamais si un fichier reçu attend son import. La bibliothèque reste dessous dans la pile arrière.
    val context = LocalContext.current
    var autoReopenHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(allowAutoReopen) {
        if (!allowAutoReopen || autoReopenHandled) return@LaunchedEffect
        autoReopenHandled = true
        val container = (context.applicationContext as VersoApplication).container
        val bookId = StartDestination.bookToReopen(
            lastOpened = container.books.lastOpened(),
            reopenEnabled = container.settings.reopenLastBook.first(),
            now = container.clock(),
            // Revérifié ici : un « Ouvrir avec » peut arriver (onNewIntent) pendant la lecture de la base.
            hasIncomingImport = IncomingImports.hasPending(),
        )
        if (bookId != null) navController.navigate(ReaderRoute(bookId))
    }
}
