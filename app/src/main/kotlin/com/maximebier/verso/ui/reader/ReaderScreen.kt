package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.theme.VersoTheme

/** Point d'entrée de ReaderRoute. Corps provisoire (retour seul) : remplacé par la tâche 4.3. */
@Composable
fun ReaderDestination(bookId: Long, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        DetailTopBar(title = "", onBack = onBack, backContentDescription = stringResource(R.string.reader_back_to_library))
    }
}
