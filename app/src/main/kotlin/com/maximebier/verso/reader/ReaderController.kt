package com.maximebier.verso.reader

import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.publication.Locator

/**
 * Ce que le reste de l’app voit du moteur de lecture. L’implémentation (navigateur classique de Readium,
 * `EpubNavigatorFragment`) est `FragmentReaderController`.
 */
interface ReaderController {
    /** Position affichée ; suit chaque scroll. `null` avant le premier affichage. */
    val displayed: StateFlow<Locator?>

    /** Une émission par geste de défilement, une fois le défilement stabilisé. */
    val gestures: Flow<GestureSignal>

    /** Hauteur visible du texte, en pixels ; 0 tant que la vue n’est pas mesurée. */
    val viewportHeightPx: Int

    /** Mode pages : page affichée dans le chapitre et nombre de pages ; null en continu ou avant le premier compte. */
    val pageInfo: StateFlow<PageInfo?>

    /** Saut sans animation (sommaire, journal, « Revenir »). */
    suspend fun go(locator: Locator)

    /** Locator affiché enrichi du texte visible (extrait de 2 lignes de la carte Reprendre). */
    suspend fun excerptLocator(): Locator?

    /** Nouveaux réglages de lecture, appliqués sans recréer le lecteur ; le texte reste au même locator. */
    fun submit(settings: ReadingSettings, theme: AppTheme, scrollMode: ScrollMode)

    /** Mode pages : page suivante ou précédente (action TalkBack du pied de page), comme un tap sur un côté. Sans effet en continu. */
    fun turn(forward: Boolean)
}

/** `chapterTurn` : le glissé, commencé au bord, a ouvert le chapitre voisin (lecture, pas navigation). */
data class GestureSignal(val timeMs: Long, val isFling: Boolean, val chapterTurn: Boolean = false)

/** Réglages envoyés au moteur de lecture. */
data class ReaderStyle(val settings: ReadingSettings, val theme: AppTheme, val scrollMode: ScrollMode)

/**
 * « Page 2 sur 9 » : page du chapitre affiché (1..pageCount). [chapterAnchor] : ancre du sommaire (fragment) où commence
 * ce chapitre dans le fichier, mesurée dans la page ; null sans chapitre ancré (le fichier entier, ou le texte avant la
 * première ancre).
 */
data class PageInfo(val page: Int, val pageCount: Int, val chapterAnchor: String? = null)

/**
 * Mode pages, lu dans la WebView : nombre de pages du fichier affiché et, pour chacun de ses chapitres ancrés (ordre du
 * sommaire), la page (1-based) où se trouve son ancre ; vide si le fichier est un seul chapitre. [currentPage] : page
 * affichée (1-based) lue au même moment ; null si illisible (la progression du locator sert alors).
 */
data class PageLayout(val pageCount: Int, val anchors: List<AnchorPage> = emptyList(), val currentPage: Int? = null)

/** Ancre du sommaire (fragment) et page du fichier où elle se trouve. */
data class AnchorPage(val id: String, val page: Int)
