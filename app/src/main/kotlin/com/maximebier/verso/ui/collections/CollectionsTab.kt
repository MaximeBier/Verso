package com.maximebier.verso.ui.collections

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.CoverStack
import com.maximebier.verso.ui.components.OutlinedPillButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoProgressBar
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme

/** À partir de cette échelle de texte, la pile de couvertures passe au-dessus du texte de la carte. */
const val STACKED_CARD_FONT_SCALE = 1.5f

/** Intentions de l’onglet ; [onOpenCollection] null = cartes non touchables (pas encore d’écran de collection). */
data class CollectionsActions(
    val onOpenCollection: ((Long) -> Unit)? = null,
    val onNewCollection: () -> Unit = {},
)

/** Onglet « Collections » de la bibliothèque (4.01). */
@Composable
fun CollectionsTab(state: CollectionsUiState, actions: CollectionsActions, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (state.loading) return
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "header") {
            TitleWithCount(
                title = stringResource(R.string.collections_title),
                count = pluralStringResource(R.plurals.collections_count, state.cards.size, state.cards.size),
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        items(state.cards, key = { it.id }) { card ->
            CollectionCardItem(card, actions.onOpenCollection, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        item(key = "new") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.cards.isEmpty()) {
                    Text(
                        text = stringResource(R.string.collections_empty),
                        style = VersoTheme.typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                OutlinedPillButton(
                    text = stringResource(R.string.collections_new),
                    onClick = actions.onNewCollection,
                    icon = VersoIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Titre 22 sp et compteur sur la même ligne de base ; le compteur passe dessous s’il ne tient plus (texte agrandi). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TitleWithCount(title: String, count: String, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = VersoTheme.typography.screenTitle,
            color = colors.text,
            modifier = Modifier.alignByBaseline().semantics { heading() },
        )
        Text(text = count, style = VersoTheme.typography.body, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun CollectionCardItem(card: CollectionCard, onOpen: ((Long) -> Unit)?, modifier: Modifier) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val countText = pluralStringResource(R.plurals.library_book_count, card.bookCount, card.bookCount)
    val subtitle = card.author?.let { stringResource(R.string.collections_author_and_count, it, countText) } ?: countText
    val spokenSubtitle = card.author?.let { stringResource(R.string.collections_card_author_and_count, it, countText) } ?: countText
    val description = stringResource(R.string.collections_card_content_description, card.name, spokenSubtitle, card.percent)
    val clickModifier = if (onOpen != null) Modifier.clickable(role = Role.Button) { onOpen(card.id) } else Modifier
    val cardModifier = modifier
        .fillMaxWidth()
        .clip(VersoShapes.card)
        .background(colors.surface)
        .then(clickModifier)
        .clearAndSetSemantics { contentDescription = description }
        .padding(16.dp)
    // Texte très agrandi : la pile passe au-dessus du texte, qui garde toute la largeur (pas de nom coupé en plein mot).
    val stacked = LocalDensity.current.fontScale >= STACKED_CARD_FONT_SCALE
    val texts: @Composable (Modifier) -> Unit = { textModifier ->
        Column(textModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.name, style = typography.bookTitleStrong, color = colors.text)
            Text(subtitle, style = typography.caption, color = colors.textSecondary)
            VersoProgressBar(fraction = card.fraction, current = false, modifier = Modifier.padding(top = 10.dp))
            val percentText = stringResource(R.string.common_percent, card.percent)
            val finishedText = pluralStringResource(R.plurals.collections_finished_of, card.finished, card.finished, card.bookCount)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight(700))) { append(percentText) }
                    append(' ')
                    append(finishedText)
                },
                style = typography.caption,
                color = colors.text,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (stacked) {
        Column(cardModifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverStack(covers = card.covers, fallbackTitle = card.name)
            texts(Modifier.fillMaxWidth())
        }
    } else {
        Row(cardModifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverStack(covers = card.covers, fallbackTitle = card.name)
            texts(Modifier.weight(1f))
        }
    }
}
