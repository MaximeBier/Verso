package com.maximebier.verso.ui.a11y

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Vrai quand le réglage Android « Supprimer les animations » est actif. Lu, jamais modifié. */
fun isReducedMotionEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Relit le réglage à chaque retour au premier plan (l'utilisateur a pu le changer entre-temps). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) { mutableStateOf(isReducedMotionEnabled(context)) }
    LifecycleResumeEffect(context) {
        reduced = isReducedMotionEnabled(context)
        onPauseOrDispose { }
    }
    return reduced
}
