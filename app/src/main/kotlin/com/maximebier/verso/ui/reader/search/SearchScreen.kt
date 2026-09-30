package com.maximebier.verso.ui.reader.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.readium.SearchHit
import com.maximebier.verso.ui.common.percentOf
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme

/** Extrait d'un résultat, le mot trouvé en fond `highlight`, gras et souligné (jamais la couleur seule). */
fun searchMatchText(hit: SearchHit, highlight: Color): AnnotatedString = buildAnnotatedString {
    append(hit.before)
    withStyle(SpanStyle(background = highlight, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
        append(hit.match)
    }
    append(hit.after)
}

/** Statut sous la barre : en cours (avec le compte), terminé (compte ou « Aucun résultat »), rien sans requête. */
@Composable
fun searchStatusText(state: SearchUiState): String? = when {
    state.running && state.resultCount == 0 -> stringResource(R.string.search_status_running_empty)
    state.running -> pluralStringResource(R.plurals.search_status_running, state.resultCount, state.resultCount)
    state.done && state.resultCount == 0 -> stringResource(R.string.search_status_done_none)
    state.done -> pluralStringResource(R.plurals.search_status_done, state.resultCount, state.resultCount)
    else -> null
}

/** Écran 2.06, en surcouche plein écran du lecteur. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onResultClick: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = true,
) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (requestFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    // Écran posé sur le lecteur : un toucher dans une zone vide ne doit pas passer au texte (barre, tour de page).
    Column(
        modifier.fillMaxSize().background(colors.background).pointerInput(Unit) {}.windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = VersoDimens.topBarReader)
                .background(colors.surface)
                .padding(start = 4.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersoIconButton(icon = VersoIcons.ArrowLeft, contentDescription = stringResource(R.string.search_close), onClick = onClose)
            val label = stringResource(R.string.search_field)
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = typography.body.copy(fontSize = 18.sp, color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = VersoDimens.controlMin)
                    .focusRequester(focus)
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.query.isEmpty()) {
                            Text(label, style = typography.body.copy(fontSize = 18.sp), color = colors.textSecondary)
                        }
                        inner()
                    }
                },
            )
            if (state.query.isNotEmpty()) {
                VersoIconButton(icon = VersoIcons.Close, contentDescription = stringResource(R.string.search_clear), onClick = onClear)
            }
        }
        if (state.running) SearchProgress(state.progress)

        LazyColumn(Modifier.fillMaxSize()) {
            searchStatusItem(state)
            state.groups.forEach { group ->
                group.title?.let { title ->
                    item(key = "titre-${group.hits.first().progression}-$title") {
                        Text(
                            text = title,
                            style = typography.captionBold,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                }
                items(group.hits, key = { "${it.locator.href}-${it.locator.locations.totalProgression}-${it.before}" }) { hit ->
                    SearchResultRow(hit = hit, onClick = { onResultClick(hit) })
                }
            }
        }
    }
}

private fun LazyListScope.searchStatusItem(state: SearchUiState) {
    item(key = "statut") {
        val status = searchStatusText(state) ?: return@item
        Text(
            text = status,
            style = VersoTheme.typography.caption,
            color = VersoTheme.colors.textSecondary,
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Barre de 3 dp : fichiers parcourus. */
@Composable
private fun SearchProgress(progress: Float) {
    val colors = VersoTheme.colors
    val description = stringResource(R.string.search_in_progress)
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(colors.progressTrack)
            .progressSemantics(progress.coerceIn(0f, 1f))
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(colors.accent))
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onClick: () -> Unit) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = VersoDimens.controlMin)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = searchMatchText(hit, colors.highlight), style = typography.body.copy(lineHeight = 24.8.sp), color = colors.text)
            Text(text = stringResource(R.string.common_percent, percentOf(hit.progression)), style = typography.caption, color = colors.textSecondary)
        }
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
    }
}
