package com.maximebier.verso.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/**
 * Parcourt l'arbre sémantique (toutes les fenêtres : écran, dialogues, menus) et liste les violations :
 * cible < 48 dp, commande sans intitulé TalkBack, texte coupé, défilement horizontal.
 */
object AccessibilityChecks {

    private val interactive = hasClickAction() or isToggleable() or isSelectable()

    private const val LINE_ROUNDING_PX = 2f

    fun violations(rule: ComposeContentTestRule): List<String> =
        touchTargets(rule) + labels(rule) + clippedTexts(rule) + horizontalScroll(rule)

    /**
     * Toute commande fait au moins 48 × 48 dp (taille de mise en page, marges d'interaction M3 comprises). La taille
     * du nœud et non boundsInRoot : ces bornes sont rognées par la fenêtre, si bien qu'une ligne à moitié sortie de
     * l'écran (ou composée hors de l'écran par une liste) passerait pour trop petite.
     */
    fun touchTargets(rule: ComposeContentTestRule): List<String> {
        val minPx = with(rule.density) { 48.dp.toPx() } - 0.5f
        return rule.onAllNodes(interactive).fetchSemanticsNodes().mapNotNull { node ->
            val size = node.size
            if (size.width < minPx || size.height < minPx) {
                "Cible < 48 dp : ${describe(node)} (${size.width} × ${size.height} px)"
            } else {
                null
            }
        }
    }

    /** Toute commande a un texte, une contentDescription ou un texte éditable (bouton à icône seule compris). */
    fun labels(rule: ComposeContentTestRule): List<String> =
        rule.onAllNodes(interactive).fetchSemanticsNodes()
            .filterNot { it.hasLabel() }
            .map { "Commande sans intitulé TalkBack : ${describe(it)}" }

    /**
     * Aucun texte coupé : pas de débordement en largeur, pas de mot coupé en plein milieu par un retour à la ligne,
     * pas de débordement en hauteur sans points de suspension.
     */
    fun clippedTexts(rule: ComposeContentTestRule): List<String> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.mapNotNull { result ->
                    val lastLine = result.lineCount - 1
                    when {
                        result.linesOverflowWidth() -> "Texte coupé en largeur : ${describe(node)}"
                        result.breaksInsideWord() -> "Mot coupé en fin de ligne : ${describe(node)}"
                        result.didOverflowHeight && (lastLine < 0 || !result.isLineEllipsized(lastLine)) ->
                            "Texte coupé en hauteur sans points de suspension : ${describe(node)}"
                        else -> null
                    }
                }
            }

    /** La V1 n'a aucun défilement horizontal : une plage horizontale non nulle est une violation. */
    fun horizontalScroll(rule: ComposeContentTestRule): List<String> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0f }
            .map { "Défilement horizontal : ${describe(it)}" }

    /**
     * Une ligne plus large que le texte affiché. `didOverflowWidth` ne convient pas ici : le résultat que rend
     * l'action sémantique a un MultiParagraph à la largeur maximale des contraintes (1170 px) alors que sa
     * taille est la largeur du texte, si bien que tout texte plus court que l'écran passerait pour coupé.
     * Tolérance [LINE_ROUNDING_PX] : une ligne centrée a ses bords arrondis au pixel entier (1 px de plus que la taille).
     */
    private fun TextLayoutResult.linesOverflowWidth(): Boolean =
        (0 until lineCount).any { line -> getLineRight(line) - getLineLeft(line) > size.width + LINE_ROUNDING_PX }

    /**
     * Un retour à la ligne tombé entre deux lettres ou chiffres (« Récen / ts ») : Android découpe ainsi un mot
     * plus large que la place disponible, chaque morceau tenant dans sa ligne, si bien que le contrôle de largeur
     * ne voit rien. Une coupure après une espace ou un trait d'union est normale.
     */
    private fun TextLayoutResult.breaksInsideWord(): Boolean {
        val text = layoutInput.text.text
        return (0 until lineCount - 1).any { line ->
            val end = getLineEnd(line)
            end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
        }
    }

    private fun SemanticsNode.hasLabel(): Boolean {
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("")
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        return (text + description + editable).isNotBlank()
    }

    private fun describe(node: SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        val label = (text.ifBlank { description }).ifBlank { "sans texte" }
        return "« ${label.take(60)} » (nœud ${node.id})"
    }
}
