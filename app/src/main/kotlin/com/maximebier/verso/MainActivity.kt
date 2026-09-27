package com.maximebier.verso

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.importer.IncomingImports
import com.maximebier.verso.importer.IncomingIntent
import com.maximebier.verso.ui.nav.LibraryRoute
import com.maximebier.verso.ui.nav.StartDestination
import com.maximebier.verso.ui.nav.VersoNavHost
import com.maximebier.verso.ui.theme.VersoTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Activité unique. FragmentActivity : le navigateur EPUB de Readium peut être un Fragment. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = (application as VersoApplication).container.settings
        // Lu avant la première image : le thème choisi s'affiche d'emblée, sans passer par celui du téléphone.
        val initialThemeMode = runBlocking { settings.themeMode.first() }
        // Le navigateur EPUB est un Fragment sans constructeur vide : restauré par le système (rotation, thème), il
        // n’a pas sa fabrique. Il est recréé à vide puis retiré ; la surface de lecture en crée un vrai.
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        enableEdgeToEdge(initialThemeMode.isDark(systemDark = isSystemNight()))
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) removeRestoredReaders()
        // Fichier reçu par « Ouvrir avec » / « Partager vers ». Après une recréation, l'intent a déjà été traité.
        // Appelé avant setContent : IncomingImports.hasPending() est donc connu dès la première composition.
        // Relancé depuis les récents : l’intent d’origine a déjà été importé, il ne se rejoue pas.
        if (savedInstanceState == null && !launchedFromHistory(intent)) handleIncomingIntent(intent)
        // Premier lancement seulement (après une recréation, Navigation restaure la pile), et jamais devant un import.
        val allowAutoReopen = savedInstanceState == null && !IncomingImports.hasPending()
        // Décidé avant la première image, comme le thème : la bibliothèque ne s’affiche pas avant le livre rouvert.
        val reopenBookId = if (allowAutoReopen) runBlocking { bookToReopen() } else null
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = initialThemeMode)
            val dark = themeMode.isDark(systemDark = isSystemInDarkTheme())
            LaunchedEffect(themeMode) { applyWindowNightMode(themeMode) }
            LaunchedEffect(dark) { enableEdgeToEdge(dark) }
            VersoTheme(darkTheme = dark) {
                val navController = rememberNavController()
                VersoNavHost(navController = navController, reopenBookId = reopenBookId)
                ReturnToLibraryOnIncomingImport(navController)
            }
        }
    }

    private suspend fun bookToReopen(): Long? {
        val container = (application as VersoApplication).container
        return StartDestination.bookToReopen(
            lastOpened = container.books.lastOpened(),
            reopenEnabled = container.settings.reopenLastBook.first(),
            now = container.clock(),
            hasIncomingImport = IncomingImports.hasPending(),
        )
    }

    private fun removeRestoredReaders() {
        val restored = supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>()
        if (restored.isEmpty()) return
        supportFragmentManager.beginTransaction().apply { restored.forEach(::remove) }.commitNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun launchedFromHistory(intent: Intent?): Boolean =
        intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

    private fun handleIncomingIntent(intent: Intent?) {
        IncomingIntent.parse(intent)?.let(IncomingImports::submit)
    }

    private fun isSystemNight(): Boolean =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Icônes des barres système lisibles sur le fond du thème affiché (même voile que le défaut d'AndroidX). */
    private fun enableEdgeToEdge(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { dark },
        )
    }

    /**
     * Android 12+ : le thème choisi s'applique aussi à la fenêtre (fond au lancement, avant Compose), retenu par
     * le système. Changer ce réglage recrée l'activité ; Navigation restaure alors la pile. Avant Android 12,
     * seul le rendu Compose suit le réglage.
     */
    private fun applyWindowNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiModeManager = getSystemService(UiModeManager::class.java) ?: return
        val night = when (mode) {
            ThemeMode.AUTO -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        // Même valeur que la précédente : sans effet (pas de recréation).
        uiModeManager.setApplicationNightMode(night)
    }

    private companion object {
        // Valeurs de SystemBarStyle par défaut (navigation à trois boutons).
        val LightNavigationScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkNavigationScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

/**
 * Un fichier reçu d'une autre application ramène à la bibliothèque, qui l'importe et affiche le résultat
 * (priorité sur le livre ouvert, y compris quand Verso tournait déjà).
 */
@Composable
private fun ReturnToLibraryOnIncomingImport(navController: NavHostController) {
    val returnRequested by IncomingImports.returnToLibrary.collectAsState()
    LaunchedEffect(returnRequested) {
        if (!returnRequested) return@LaunchedEffect
        IncomingImports.onReturnedToLibrary()
        if (navController.currentDestination?.hasRoute<LibraryRoute>() == true) return@LaunchedEffect
        if (!navController.popBackStack<LibraryRoute>(inclusive = false)) {
            navController.navigate(LibraryRoute) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }
}
