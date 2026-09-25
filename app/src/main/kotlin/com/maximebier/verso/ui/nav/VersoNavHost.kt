package com.maximebier.verso.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.maximebier.verso.ui.details.DetailsDestination
import com.maximebier.verso.ui.library.LibraryDestination
import com.maximebier.verso.ui.reader.ReaderDestination
import com.maximebier.verso.ui.settings.LicensesDestination
import com.maximebier.verso.ui.settings.SettingsDestination

/** Les 5 routes de la V1. Chaque destination délègue à un point d'entrée du fichier de sa fonctionnalité. */
@Composable
fun VersoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
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
}
