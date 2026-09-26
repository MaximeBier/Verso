package com.maximebier.verso.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.maximebier.verso.VersoApplication
import com.maximebier.verso.core.text.TocNode
import com.maximebier.verso.core.text.chapterPathAt
import com.maximebier.verso.core.text.remainingMinutes
import com.maximebier.verso.core.text.shortLocation
import com.maximebier.verso.data.BookRepository
import com.maximebier.verso.reader.ReaderController
import com.maximebier.verso.readium.Locators
import java.io.File
import kotlin.math.floor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

data class ReaderUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val publication: Publication? = null,
    /** Locator de création de la surface : position sauvegardée, puis dernière position affichée. */
    val initialLocator: Locator? = null,
    val bookTitle: String = "",
    val totalWords: Long = 0,
    val toc: List<TocNode> = emptyList(),
    val readingOrderHrefs: List<String> = emptyList(),
    /** Fichier (sans fragment) de la position de lecture. */
    val currentHref: String? = null,
    val chapterPath: List<String> = emptyList(),
    val shortLocation: String? = null,
    /** Progression totale de la position de LECTURE (jamais de la position affichée). */
    val readingProgression: Double = 0.0,
    val readingPercent: Int = 0,
    val remainingMinutes: Int = 0,
    val barsVisible: Boolean = false,
    val tocVisible: Boolean = false,
    val journalVisible: Boolean = false,
)

class ReaderViewModel(
    private val bookId: Long,
    private val books: BookRepository,
    private val openPublication: suspend (File) -> Result<Publication>,
    private val clock: () -> Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val positionSaver = PositionSaver(
        bookId = bookId,
        save = books::saveReadingPosition,
        scope = viewModelScope,
    )
    private var controller: ReaderController? = null
    private var controllerJob: Job? = null

    /** Saut demandé sans surface prête (activité recréée) : appliqué au prochain [onReaderReady]. */
    private var pendingJump: PendingJump? = null
    private var tocLinks: List<Link> = emptyList()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = books.book(bookId)
        if (book == null) {
            _uiState.update { it.copy(loading = false, failed = true) }
            return
        }
        val publication = openPublication(File(book.filePath)).getOrElse {
            _uiState.update { it.copy(loading = false, failed = true) }
            return
        }
        books.markOpened(bookId, clock())
        tocLinks = publication.tableOfContents.ifEmpty { publication.readingOrder }
        val saved = book.readingLocatorJson?.let(Locators::fromJson)
        _uiState.update {
            it.copy(
                publication = publication,
                initialLocator = saved,
                bookTitle = book.title,
                totalWords = book.totalWords,
                toc = tocLinks.toTocNodes(),
                readingOrderHrefs = publication.readingOrder.map { link -> link.url().removeFragment().toString() },
            )
        }
        onReadingChanged(saved, book.progression)
        // `loading = false` en dernier : l’état publié est alors complet (progression comprise).
        _uiState.update { it.copy(loading = false) }
    }

    fun onReaderReady(readerController: ReaderController) {
        if (controller === readerController) return
        controller = readerController
        positionSaver.attach(readerController)
        controllerJob?.cancel()
        controllerJob = viewModelScope.launch {
            readerController.displayed.filterNotNull().collect { locator ->
                // Reprise au même endroit si la surface est recréée (rotation, thème).
                _uiState.update { it.copy(initialLocator = locator) }
                // Étape 4 : la position de lecture suit la position affichée (remplacé par 5.3).
                val position = Locators.toPosition(locator)
                onReadingChanged(locator, position.totalProgression)
                positionSaver.requestSave(position)
            }
        }
        pendingJump?.let { jump ->
            pendingJump = null
            if (jump.closesToc) closeTocAndBars()
            viewModelScope.launch { readerController.go(jump.target) }
        }
    }

    /**
     * Surface retirée de la composition (rotation, thème : l’activité est recréée). Jusqu’au prochain
     * [onReaderReady], les sauts sont mémorisés au lieu de partir vers l’ancienne rendition.
     */
    fun onReaderGone() {
        controllerJob?.cancel()
        controllerJob = null
        controller = null
    }

    private fun onReadingChanged(reading: Locator?, progression: Double) {
        val href = reading?.let(Locators::hrefKey)
        _uiState.update { state ->
            val path = href?.let { chapterPathAt(state.toc, it) } ?: emptyList()
            state.copy(
                currentHref = href,
                chapterPath = path,
                shortLocation = shortLocation(path),
                readingProgression = progression,
                readingPercent = readingPercent(progression),
                remainingMinutes = remainingMinutes(state.totalWords, progression),
            )
        }
    }

    fun toggleBars() = _uiState.update { it.copy(barsVisible = !it.barsVisible) }

    fun showToc() = _uiState.update { it.copy(tocVisible = true) }

    fun hideToc() = _uiState.update { it.copy(tocVisible = false) }

    fun showJournal() = _uiState.update { it.copy(journalVisible = true) }

    fun hideJournal() = _uiState.update { it.copy(journalVisible = false) }

    /** Saut par le sommaire ; `index` = rang dans le préordre du sommaire (`preorder`, même parcours que `buildTocRows`). */
    fun jumpToTocEntry(index: Int) {
        val publication = _uiState.value.publication ?: return
        val link = preorder(tocLinks) { it.children }.getOrNull(index) ?: return
        val target = publication.locatorFromLink(link) ?: return
        if (controller == null) {
            // Sommaire laissé ouvert : il se ferme quand le saut part vraiment.
            pendingJump = PendingJump(target, closesToc = true)
            return
        }
        closeTocAndBars()
        jumpTo(target)
    }

    private fun closeTocAndBars() = _uiState.update { it.copy(tocVisible = false, barsVisible = false) }

    /**
     * Saut explicite (sommaire ; « Reprendre ici » du journal en 6.4) : ne touche pas la position de lecture.
     * Étape 4 : déplacement seul ; 5.3 le fait précéder de `ReaderEvent.Jumped` pour la machine à états.
     */
    fun jumpTo(locator: Locator) {
        val current = controller
        if (current == null) {
            pendingJump = PendingJump(locator, closesToc = false)
            return
        }
        viewModelScope.launch { current.go(locator) }
    }

    private data class PendingJump(val target: Locator, val closesToc: Boolean)

    /** Mise en arrière-plan de l’écran (`ON_STOP`). */
    fun onStop() = positionSaver.flush()

    override fun onCleared() {
        _uiState.value.publication?.close()
    }

    companion object {
        fun factory(bookId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VersoApplication).container
                ReaderViewModel(
                    bookId = bookId,
                    books = container.books,
                    openPublication = container.readiumOpener::open,
                    clock = container.clock,
                )
            }
        }
    }
}

/** Pourcentage entier arrondi vers le bas (« 31 % lu »). */
fun readingPercent(progression: Double): Int = floor(progression.coerceIn(0.0, 1.0) * 100).toInt()

internal fun List<Link>.toTocNodes(): List<TocNode> = mapIndexed { index, link ->
    TocNode(
        title = link.displayTitle(index),
        href = link.url().removeFragment().toString(),
        children = link.children.toTocNodes(),
    )
}

/** Titre du sommaire, ou nom du fichier pour un EPUB sans titres (sommaire absent : ordre de lecture). */
private fun Link.displayTitle(index: Int): String =
    title?.takeIf { it.isNotBlank() }
        ?: url().removeFragment().toString().substringAfterLast('/').ifBlank { (index + 1).toString() }
