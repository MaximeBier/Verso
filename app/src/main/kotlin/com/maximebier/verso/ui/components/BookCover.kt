package com.maximebier.verso.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import java.io.File
import java.util.Locale
import java.util.zip.CRC32

/** Tailles de couverture de v1-ui-reference §3.13 : 1 initiale sous 56 dp de large, 2 au-delà. */
enum class CoverSize(val widthDp: Int, val heightDp: Int, val letters: Int, val monogramSp: Int) {
    LIST(48, 72, 1, 22),
    COMPACT(56, 84, 2, 18),
    RESUME(64, 96, 2, 18),
    DETAILS(96, 144, 2, 22),
    STACK(52, 78, 2, 16),
    SMALL(36, 54, 2, 14),
    ROW(40, 60, 2, 14),
    CARD(44, 66, 1, 22),
}

/** Règles pures des vignettes générées (testées dans CoverArtTest). */
object CoverArt {
    private val articles = setOf("le", "la", "les", "un", "une", "des")
    private val elisions = listOf("l’", "l'")

    /** Initiales des premiers mots significatifs (articles initiaux ignorés), en capitales. */
    fun monogram(title: String, letters: Int): String {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val unelided = words.map { word ->
            val prefix = elisions.firstOrNull { word.lowercase(Locale.FRENCH).startsWith(it) && word.length > it.length }
            if (prefix != null) word.substring(prefix.length) else word
        }
        val significant = unelided.filter { it.lowercase(Locale.FRENCH) !in articles }.ifEmpty { words }
        return significant
            .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
            .take(letters)
            .joinToString("") { it.toString().uppercase(Locale.FRENCH) }
    }

    /** Index stable dans la palette : CRC32 de la graine (l'empreinte SHA-256 du fichier, pour que corriger le titre ne change pas la couleur). */
    fun colorIndex(seed: String, paletteSize: Int): Int {
        val crc = CRC32()
        crc.update(seed.toByteArray(Charsets.UTF_8))
        return Math.floorMod(crc.value, paletteSize.toLong()).toInt()
    }
}

/** Couverture décorative (le titre est toujours affiché à côté) : image de l'EPUB, sinon monogramme sur couleur sourde. */
@Composable
fun BookCover(title: String, coverPath: String?, seed: String, size: CoverSize, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val monogramSize = with(LocalDensity.current) { size.monogramSp.dp.toSp() }
    val palette = VersoTheme.coverPalette
    Box(
        modifier = modifier
            .size(size.widthDp.dp, size.heightDp.dp)
            .clip(VersoShapes.cover)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(colors.progressTrack),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(palette[CoverArt.colorIndex(seed, palette.size)]),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = CoverArt.monogram(title, size.letters),
                    color = colors.onCover,
                    // Vignette de taille fixe en dp : les initiales, décoratives, ne suivent pas la taille de texte d’Android.
                    style = VersoTheme.typography.body.copy(fontSize = monogramSize, lineHeight = monogramSize, fontWeight = FontWeight(600)),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Couverture de la grille (pleine largeur, 2:3) ; vignette générée : titre + filet + auteur (§3.4b). */
@Composable
fun GridBookCover(title: String, author: String, coverPath: String?, seed: String, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val palette = VersoTheme.coverPalette
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(VersoShapes.cover)
            .clearAndSetSemantics {},
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(colors.progressTrack),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette[CoverArt.colorIndex(seed, palette.size)])
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val titleSp = if (title.length <= 10) 28 else 22
                Text(
                    text = title,
                    color = colors.onCover,
                    style = VersoTheme.typography.body.copy(fontSize = titleSp.sp, lineHeight = (titleSp * 1.2f).sp, fontWeight = FontWeight(600)),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.size(width = 24.dp, height = 2.dp).background(colors.onCover))
                if (author.isNotBlank()) {
                    Text(
                        text = author,
                        color = colors.onCover,
                        style = VersoTheme.typography.caption.copy(fontWeight = FontWeight(500)),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
