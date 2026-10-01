package com.maximebier.verso.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.maximebier.verso.ui.collections.COVER_STACK_SIZE
import com.maximebier.verso.ui.collections.CoverRef
import com.maximebier.verso.ui.theme.VersoShapes

/** Décalage d’une couverture à la suivante (4.01) : 14 dp vers la droite, 6 dp vers le haut. */
private const val STACK_STEP_X = 14
private const val STACK_STEP_Y = 6

/**
 * Pile décorative (le nom est écrit à côté), 84 × 92 dp : 3 couvertures au plus, dans l’ordre de lecture, la
 * première derrière à gauche et la dernière devant (4.01). Collection vide : une vignette à l’initiale du nom.
 */
@Composable
fun CoverStack(covers: List<CoverRef>, fallbackTitle: String, modifier: Modifier = Modifier) {
    val shown = covers.ifEmpty { listOf(CoverRef(fallbackTitle, null, fallbackTitle)) }
    Box(modifier.size(width = 84.dp, height = 92.dp).clearAndSetSemantics {}) {
        shown.take(COVER_STACK_SIZE).forEachIndexed { index, cover ->
            BookCover(
                title = cover.title,
                coverPath = cover.coverPath,
                seed = cover.seed,
                size = CoverSize.STACK,
                // Bas des couvertures alignés comme sur la maquette : la première à 12 dp du haut, la troisième à 0.
                modifier = Modifier
                    .offset(x = (STACK_STEP_X * index).dp, y = (STACK_STEP_Y * (COVER_STACK_SIZE - 1 - index)).dp)
                    .shadow(2.dp, VersoShapes.cover),
            )
        }
    }
}
