package com.maximebier.verso.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * [ModalBottomSheet][androidx.compose.material3.ModalBottomSheet] vit dans sa propre fenêtre Android
 * (Dialog), pas celle de `MainActivity` : sa barre de navigation garde le blanc par défaut du système
 * tant qu'on ne la teinte pas nous-mêmes, comme `MainActivity.enableEdgeToEdge` le fait pour la fenêtre
 * principale. À appeler depuis l'intérieur du contenu de la feuille (même fenêtre que son voile).
 */
@Composable
fun TintDialogNavigationBar(color: Color, dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        // Pas d'équivalent SystemBarStyle/enableEdgeToEdge pour une fenêtre de Dialog arbitraire
        // (réservé à ComponentActivity) : couleur posée directement, comme le fait AndroidX en interne.
        @Suppress("DEPRECATION")
        window.navigationBarColor = color.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
    }
}
