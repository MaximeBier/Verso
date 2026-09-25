package com.maximebier.verso.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.theme.VersoTheme

/** Point d'entrée de LicensesRoute. Corps provisoire (barre seule) : remplacé par la tâche 6.3. */
@Composable
fun LicensesDestination(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        DetailTopBar(title = stringResource(R.string.settings_licenses), onBack = onBack)
    }
}
