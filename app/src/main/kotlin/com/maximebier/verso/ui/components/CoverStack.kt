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

/** Zone de la pile, la même pour toutes les collections (4.01). */
private const val STACK_WIDTH = 84
private const val STACK_HEIGHT = 92

/**
 * Pile décorative (le nom est écrit à côté), 84 × 92 dp : 3 couvertures au plus, dans l’ordre de lecture, la
 * première derrière à gauche et la dernière devant (4.01). Collection vide : une vignette à l’initiale du nom.
 */
@Composable
fun CoverStack(covers: List<CoverRef>, fallbackTitle: String, modifier: Modifier = Modifier) {
    val shown = covers.ifEmpty { listOf(CoverRef(fallbackTitle, null, fallbackTitle)) }
    val stack = shown.take(COVER_STACK_SIZE)
    // Pile centrée dans sa zone fixe : avec une ou deux couvertures, le vide se partage des deux côtés au lieu de
    // s’accumuler avant le texte ; les titres restent alignés d’une carte à l’autre.
    val steps = stack.size - 1
    val startX = (STACK_WIDTH - CoverSize.STACK.widthDp - STACK_STEP_X * steps) / 2f
    val startY = (STACK_HEIGHT - CoverSize.STACK.heightDp - STACK_STEP_Y * steps) / 2f
    Box(modifier.size(width = STACK_WIDTH.dp, height = STACK_HEIGHT.dp).clearAndSetSemantics {}) {
        stack.forEachIndexed { index, cover ->
            BookCover(
                title = cover.title,
                coverPath = cover.coverPath,
                seed = cover.seed,
                size = CoverSize.STACK,
                // Bas des couvertures alignés comme sur la maquette : la dernière, devant, est la plus haute.
                modifier = Modifier
                    .offset(x = (startX + STACK_STEP_X * index).dp, y = (startY + STACK_STEP_Y * (steps - index)).dp)
                    .shadow(2.dp, VersoShapes.cover),
            )
        }
    }
}
