package com.maximebier.verso.screenshots.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.maximebier.verso.ui.reader.SelectionBar
import com.maximebier.verso.ui.theme.VersoTheme

/** 3.04 : texte de lecture, barre « Texte sélectionné » en bas. */
@Composable
internal fun SelectionBarSample() {
    Box(Modifier.fillMaxSize().background(VersoTheme.colors.background)) {
        SampleReadingText()
        SelectionBar(
            selectionText = "la campagne ainsi ressemble à un grand manteau…",
            onHighlight = {},
            onNote = {},
            onCopy = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
