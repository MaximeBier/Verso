package com.maximebier.verso.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.currentInFile
import com.maximebier.verso.ui.components.VersoBottomSheet
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

enum class ChapterStatus { READ, CURRENT, UNREAD }

sealed interface TocRow {
    data class PartHeader(val title: String) : TocRow

    /** `index` : rang du nœud dans le préordre de tout l’arbre (sert au saut, côté ViewModel). */
    data class Chapter(val index: Int, val title: String, val indentLevel: Int, val status: ChapterStatus) : TocRow
}

data class TocSummary(val parts: Int, val chapters: Int)

/** Lignes montrées avant le chapitre en cours à l’ouverture (maquette : 3). */
private const val CURRENT_ROW_CONTEXT = 3

fun <T> preorder(roots: List<T>, children: (T) -> List<T>): List<T> =
    roots.flatMap { listOf(it) + preorder(children(it), children) }

private data class FlatNode(val index: Int, val depth: Int, val node: TocNode)

private fun flatten(toc: List<TocNode>): List<FlatNode> {
    val result = mutableListOf<FlatNode>()
    fun visit(nodes: List<TocNode>, depth: Int) {
        nodes.forEach { node ->
            result += FlatNode(result.size, depth, node)
            visit(node.children, depth + 1)
        }
    }
    visit(toc, 0)
    return result
}

/**
 * Lignes du sommaire. Chapitre en cours : parmi les chapitres (feuilles) du fichier de la position, celui que
 * choisit `currentInFile` à `currentProgression` (progression dans ce fichier ; même règle que la barre du haut,
 * `chapterPathAt`) ; à défaut, le dernier chapitre d’un fichier antérieur dans l’ordre de lecture. Ceux qui le
 * précèdent sont « Lu ».
 */
fun buildTocRows(
    toc: List<TocNode>,
    readingOrderHrefs: List<String>,
    currentHref: String?,
    currentProgression: Double? = null,
): List<TocRow> {
    val flat = flatten(toc)
    val currentReadingIndex = currentHref?.let { readingOrderHrefs.indexOf(it) } ?: -1
    val leaves = flat.filter { it.node.children.isEmpty() }
    val current = if (currentReadingIndex < 0) {
        null
    } else {
        val inFile = leaves.filter { readingOrderHrefs.indexOf(it.node.href) == currentReadingIndex }
        currentInFile(inFile, currentProgression) { it.node }
            ?: leaves.lastOrNull { readingOrderHrefs.indexOf(it.node.href) in 0 until currentReadingIndex }
    }
    var passedCurrent = current == null
    return flat.map { item ->
        if (item.node.children.isNotEmpty()) {
            TocRow.PartHeader(item.node.title)
        } else {
            val status = when {
                item == current -> ChapterStatus.CURRENT.also { passedCurrent = true }
                passedCurrent -> ChapterStatus.UNREAD
                else -> ChapterStatus.READ
            }
            TocRow.Chapter(item.index, item.node.title, (item.depth - 1).coerceAtLeast(0), status)
        }
    }
}

fun tocSummary(toc: List<TocNode>): TocSummary {
    val flat = flatten(toc)
    return TocSummary(
        parts = toc.count { it.children.isNotEmpty() },
        chapters = flat.count { it.node.children.isEmpty() },
    )
}

/**
 * Feuille « Sommaire » (1.12). Feuille, poignée, en-tête (titre, sous-titre, « Fermer ») et voile
 * viennent du composant partagé `VersoBottomSheet` (2.2) ; seule la liste est propre au sommaire.
 */
@Composable
fun TocSheet(
    bookTitle: String,
    summary: TocSummary,
    rows: List<TocRow>,
    readingPercent: Int,
    onChapterClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    VersoBottomSheet(
        title = stringResource(R.string.toc_title),
        subtitle = tocSubtitle(bookTitle, summary),
        onDismissRequest = onDismiss,
    ) {
        TocChapterList(
            rows = rows,
            readingPercent = readingPercent,
            onChapterClick = onChapterClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Liste des chapitres (sémantique « Chapitres »), ouverte sur le chapitre en cours précédé de 3 lignes. */
@Composable
fun TocChapterList(
    rows: List<TocRow>,
    readingPercent: Int,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentRow = rows.indexOfFirst { it is TocRow.Chapter && it.status == ChapterStatus.CURRENT }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentRow - CURRENT_ROW_CONTEXT).coerceAtLeast(0))
    val chaptersLabel = stringResource(R.string.toc_chapters_group)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = chaptersLabel },
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(rows) { row ->
            when (row) {
                is TocRow.PartHeader -> PartHeaderRow(row.title)
                is TocRow.Chapter -> ChapterRow(row, readingPercent) { onChapterClick(row.index) }
            }
        }
    }
}

@Composable
private fun tocSubtitle(bookTitle: String, summary: TocSummary): String {
    val chapters = pluralStringResource(R.plurals.toc_chapter_count, summary.chapters, summary.chapters)
    val counts = if (summary.parts > 0) {
        stringResource(
            R.string.toc_subtitle_counts,
            pluralStringResource(R.plurals.toc_part_count, summary.parts, summary.parts),
            chapters,
        )
    } else {
        chapters
    }
    return stringResource(R.string.toc_subtitle, bookTitle, counts)
}

@Composable
private fun PartHeaderRow(title: String) {
    Text(
        text = title,
        style = VersoTypography.captionBold,
        color = VersoTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ChapterRow(row: TocRow.Chapter, readingPercent: Int, onClick: () -> Unit) {
    val colors = VersoTheme.colors
    val current = row.status == ChapterStatus.CURRENT
    val indent = (16 * row.indentLevel).dp
    Box(Modifier.fillMaxWidth().padding(horizontal = if (current) 12.dp else 0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(VersoShapes.small)
                .then(if (current) Modifier.background(colors.selection) else Modifier)
                .semantics(mergeDescendants = true) { selected = current }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = (if (current) 12.dp else 24.dp) + indent, end = if (current) 12.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = row.title,
                style = if (current) VersoTypography.bodyStrong else VersoTypography.body,
                color = if (current) colors.onSelection else colors.text,
                modifier = Modifier.weight(1f),
            )
            when (row.status) {
                ChapterStatus.READ -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(VersoIcons.Check, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.toc_chapter_read), style = VersoTypography.caption, color = colors.textSecondary)
                }
                ChapterStatus.CURRENT -> Text(
                    text = stringResource(R.string.toc_chapter_current, readingPercent),
                    style = VersoTypography.captionBold,
                    color = colors.onSelection,
                )
                ChapterStatus.UNREAD -> Unit
            }
        }
    }
}
