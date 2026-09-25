package com.maximebier.verso.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.theme.VersoTheme

/** Point d'entrée de DetailsRoute. Corps provisoire (barre seule) : remplacé par la tâche 3.4. */
@Composable
fun DetailsDestination(bookId: Long, onBack: () -> Unit, onOpenReader: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        DetailTopBar(title = stringResource(R.string.details_title), onBack = onBack)
    }
}
