@file:OptIn(ExperimentalMaterial3Api::class)

package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.theme.TintDialogNavigationBar
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Feuille modale (Sommaire, Journal, Trier et afficher) : voile du thème, bouton Fermer.
 * [fitContent] : true = hauteur du contenu (feuille « Trier et afficher ») au lieu de l'écran − 88 dp.
 */
@Composable
fun VersoBottomSheet(
    title: String,
    subtitle: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    fitContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VersoTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = if (fitContent) modifier else modifier.padding(top = 88.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dp.Unspecified,
        shape = VersoShapes.sheetTop,
        containerColor = colors.surface,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        // Poignée dessinée dans le contenu, sans sémantique : dans l’emplacement dragHandle, M3 en fait une
        // commande de 32 × 14 dp sans intitulé. La feuille reste glissable ; « Fermer » et le voile la ferment.
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // La feuille vit dans sa propre fenêtre (Dialog) : sans ceci, sa barre de navigation reste
        // blanche quel que soit le thème affiché.
        TintDialogNavigationBar(color = colors.surface, dark = VersoTheme.isDark)
        Column(
            Modifier
                .then(if (fitContent) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.outline),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(
                        text = title,
                        style = VersoTheme.typography.sheetTitle,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = VersoTheme.typography.caption,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                VersoIconButton(
                    icon = VersoIcons.Close,
                    contentDescription = stringResource(R.string.common_close),
                    onClick = onDismissRequest,
                )
            }
            content()
        }
    }
}
