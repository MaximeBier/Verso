@file:OptIn(ExperimentalLayoutApi::class)

package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/**
 * Fenêtre de dialogue Verso. Le voile (scrim) est dessiné avec le jeton du thème et non par la plateforme ;
 * un toucher sur le voile ou le bouton retour appelle onDismissRequest (l'appelant choisit l'action sûre).
 * Icône facultative : si présente, elle et le titre sont centrés.
 */
@Composable
fun VersoDialog(
    title: String,
    onDismissRequest: () -> Unit,
    buttons: @Composable FlowRowScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = VersoTheme.colors
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    // Toucher sans sémantique : TalkBack n'y voit pas de bouton sans intitulé (retour ferme le dialogue).
                    .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .shadow(elevation = 12.dp, shape = VersoShapes.sheet)
                        .clip(VersoShapes.sheet)
                        .background(colors.surface)
                        // Bloque la propagation des touchers au voile situé dessous.
                        .pointerInput(Unit) {}
                        .semantics { paneTitle = title }
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(28.dp).align(Alignment.CenterHorizontally),
                        )
                    }
                    Text(
                        text = title,
                        style = VersoTypography.sheetTitle,
                        color = colors.text,
                        textAlign = if (icon != null) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                    CompositionLocalProvider(
                        LocalContentColor provides colors.textSecondary,
                        LocalTextStyle provides VersoTypography.body,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = buttons,
                    )
                }
            }
        }
    }
}
