@file:OptIn(ExperimentalMaterial3Api::class)

package com.maximebier.verso.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.LineSpacing
import com.maximebier.verso.core.settings.Margins
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoSegmentedButton
import com.maximebier.verso.ui.theme.TintDialogNavigationBar
import com.maximebier.verso.ui.theme.VersoColors
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoPalette
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.fontFamilyFor

data class ReadingSettingsSheetState(
    val settings: ReadingSettings,
    val themeMode: ThemeMode,
    val scrollMode: ScrollMode,
)

/**
 * Feuille « Réglages de lecture » (2.02) : ouverte à mi-hauteur **sans voile**, le texte reste visible au-dessus et
 * change en direct. En haut Police, Taille, Thème ; en faisant glisser, Interligne, Marges et Défilement.
 *
 * La feuille vit dans sa propre fenêtre (Dialog) : aucun toucher, sur elle ou au-dessus, n’atteint la surface de
 * lecture (qui annulerait la remise en page en cours). Un toucher au-dessus de la feuille la referme.
 */
@Composable
fun ReadingSettingsSheet(
    state: ReadingSettingsSheetState,
    onFontSelected: (ReadingFont) -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onLineSpacingSelected: (LineSpacing) -> Unit,
    onMarginsSelected: (Margins) -> Unit,
    onDismiss: () -> Unit,
    scrollModeRow: (@Composable () -> Unit)? = null,
) {
    val colors = VersoTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Mi-hauteur d’abord (skipPartiallyExpanded = false), dépliable en glissant vers le haut.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        sheetMaxWidth = Dp.Unspecified,
        shape = VersoShapes.sheetTop,
        containerColor = colors.surface,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        // Sans voile (spec) : l’effet se voit sur le texte.
        scrimColor = Color.Transparent,
        // Poignée dessinée dans le contenu, sans sémantique (voir VersoBottomSheet).
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        TintDialogNavigationBar(color = colors.surface, dark = VersoTheme.isDark)
        ReadingSettingsContent(
            state = state,
            onFontSelected = onFontSelected,
            onSmaller = onSmaller,
            onLarger = onLarger,
            onThemeSelected = onThemeSelected,
            onLineSpacingSelected = onLineSpacingSelected,
            onMarginsSelected = onMarginsSelected,
            onClose = onDismiss,
            scrollModeRow = scrollModeRow,
        )
    }
}

@Composable
internal fun ReadingSettingsContent(
    state: ReadingSettingsSheetState,
    onFontSelected: (ReadingFont) -> Unit,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onLineSpacingSelected: (LineSpacing) -> Unit,
    onMarginsSelected: (Margins) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrollModeRow: (@Composable () -> Unit)? = null,
) {
    val colors = VersoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        // Poignée décorative (comme VersoBottomSheet) : la feuille se glisse, « Fermer » la referme.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 4.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.outline),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = stringResource(R.string.reader_settings_title),
                style = VersoTheme.typography.sheetTitle,
                color = colors.text,
                modifier = Modifier.weight(1f).padding(top = 10.dp).semantics { heading() },
            )
            VersoIconButton(icon = VersoIcons.Close, contentDescription = stringResource(R.string.common_close), onClick = onClose)
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.reader_settings_font))
            FontCards(selected = state.settings.font, onSelect = onFontSelected)
            TextSizeRow(state.settings, onSmaller, onLarger)
            SectionLabel(stringResource(R.string.reader_settings_theme))
            ThemeSwatches(selected = state.themeMode, onSelect = onThemeSelected)
            // Maquette : filet 12 dp sous les pastilles, 16 dp au-dessus d’Interligne (12 + 4).
            HorizontalDivider(thickness = 1.dp, color = colors.divider)
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledSegments(
                    label = stringResource(R.string.reader_settings_line_spacing),
                    options = listOf(
                        stringResource(R.string.reader_settings_line_spacing_tight),
                        stringResource(R.string.reader_settings_line_spacing_normal),
                        stringResource(R.string.reader_settings_line_spacing_airy),
                    ),
                    selectedIndex = LineSpacing.entries.indexOf(state.settings.lineSpacing),
                    onSelect = { onLineSpacingSelected(LineSpacing.entries[it]) },
                )
                LabeledSegments(
                    label = stringResource(R.string.reader_settings_margins),
                    options = listOf(
                        stringResource(R.string.reader_settings_margins_narrow),
                        stringResource(R.string.reader_settings_margins_normal),
                        stringResource(R.string.reader_settings_margins_wide),
                    ),
                    selectedIndex = Margins.entries.indexOf(state.settings.margins),
                    onSelect = { onMarginsSelected(Margins.entries[it]) },
                )
                scrollModeRow?.invoke()
            }
        }
    }
}

/** Intitulé de section (2.02) : 14 sp 700, couleur secondaire. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = VersoTheme.typography.captionBold,
        color = VersoTheme.colors.textSecondary,
        modifier = modifier.semantics { heading() },
    )
}

/** Segments avec intitulé au-dessus, et un complément à droite (« Pour ce livre », tâche 12.4). */
@Composable
internal fun LabeledSegments(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    trailing: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(label, Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = VersoTheme.typography.caption, color = VersoTheme.colors.textSecondary)
            }
        }
        VersoSegmentedButton(options = options, selectedIndex = selectedIndex, onSelect = onSelect, groupLabel = label)
    }
}

/** Rangée Défilement (2.02) : Continu / Pages, « Pour ce livre » à droite ; réglage propre au livre ouvert. */
@Composable
internal fun ScrollModeRow(selected: ScrollMode, onSelect: (ScrollMode) -> Unit) {
    LabeledSegments(
        label = stringResource(R.string.reader_settings_scroll),
        trailing = stringResource(R.string.reader_settings_scroll_for_this_book),
        options = listOf(
            stringResource(R.string.reader_settings_scroll_continuous),
            stringResource(R.string.reader_settings_scroll_pages),
        ),
        selectedIndex = ScrollMode.entries.indexOf(selected),
        onSelect = { onSelect(ScrollMode.entries[it]) },
    )
}

private val CardGap = 8.dp
private val CardPadding = 8.dp

/** Trois cartes « Aa » dans leur police (2.02) ; sélection = bordure accent 2 dp, gras, coche. */
@Composable
private fun FontCards(selected: ReadingFont, onSelect: (ReadingFont) -> Unit) {
    val labels = ReadingFont.entries.map { fontLabel(it) }
    val group = stringResource(R.string.reader_settings_font)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = fittingColumns(labels, VersoTheme.typography.captionBold, maxWidth, CardGap, CardPadding, listOf(3, 1))
        AdaptiveGrid(
            items = ReadingFont.entries,
            columns = columns,
            gap = CardGap,
            modifier = Modifier.semantics { contentDescription = group }.selectableGroup(),
        ) { font, cellModifier ->
            FontCard(font, fontLabel(font), font == selected, { onSelect(font) }, cellModifier)
        }
    }
}

@Composable
private fun fontLabel(font: ReadingFont): String = stringResource(
    when (font) {
        ReadingFont.LITERATA -> R.string.reader_settings_font_literata
        ReadingFont.ATKINSON -> R.string.reader_settings_font_atkinson
        ReadingFont.SYSTEM -> R.string.reader_settings_font_system
    },
)

@Composable
private fun FontCard(font: ReadingFont, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    Box(
        modifier
            .heightIn(min = 80.dp)
            .clip(VersoShapes.small)
            .background(if (selected) colors.background else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.outline, VersoShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(CardPadding),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_glyph),
                fontFamily = fontFamilyFor(font),
                fontSize = 28.sp,
                lineHeight = 28.sp,
                color = colors.text,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = label,
                style = if (selected) VersoTheme.typography.captionBold else VersoTheme.typography.caption.copy(fontWeight = FontWeight(500)),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Icon(
                VersoIcons.CheckSwitch,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
            )
        }
    }
}

/** « Taille du texte » : − valeur + ; boutons de 48 dp, grisés aux bornes (14 et 32). */
@Composable
private fun TextSizeRow(settings: ReadingSettings, onSmaller: () -> Unit, onLarger: () -> Unit) {
    val colors = VersoTheme.colors
    val label = stringResource(R.string.reader_settings_text_size)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = VersoTheme.typography.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
        Row(
            Modifier.semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RoundOutlinedIconButton(VersoIcons.Minus, stringResource(R.string.reader_settings_text_smaller), settings.canShrink, onSmaller)
            Text(
                text = stringResource(R.string.reader_settings_text_size_value, settings.fontSizeSp),
                style = VersoTheme.typography.bookTitleStrong,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            RoundOutlinedIconButton(VersoIcons.Plus, stringResource(R.string.reader_settings_text_larger), settings.canGrow, onLarger)
        }
    }
}

@Composable
private fun RoundOutlinedIconButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = VersoTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(VersoDimens.controlMin).border(1.dp, colors.outline, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = colors.text, disabledContentColor = colors.textSecondary),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(VersoDimens.iconSmall))
    }
}

private val SwatchGap = 4.dp

/** Cinq pastilles (2.02) dans l’ordre de ThemeMode : Auto, Clair, Sépia, Sombre, Nuit. */
@Composable
private fun ThemeSwatches(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val labels = ThemeMode.entries.map { themeLabel(it) }
    val group = stringResource(R.string.reader_settings_theme)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = fittingColumns(labels, VersoTheme.typography.captionBold, maxWidth, SwatchGap, 2.dp, listOf(5, 3, 2))
        AdaptiveGrid(
            items = ThemeMode.entries,
            columns = columns,
            gap = SwatchGap,
            modifier = Modifier.semantics { contentDescription = group }.selectableGroup(),
        ) { mode, cellModifier ->
            ThemeSwatch(mode, themeLabel(mode), mode == selected, { onSelect(mode) }, cellModifier)
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.AUTO -> R.string.reader_settings_theme_auto
        ThemeMode.LIGHT -> R.string.reader_settings_theme_light
        ThemeMode.SEPIA -> R.string.reader_settings_theme_sepia
        ThemeMode.DARK -> R.string.reader_settings_theme_dark
        ThemeMode.NIGHT -> R.string.reader_settings_theme_night
    },
)

/** Palette montrée par la pastille ; Auto = diagonale clair / sombre. */
private fun swatchPalette(mode: ThemeMode): VersoColors = when (mode) {
    ThemeMode.AUTO, ThemeMode.LIGHT -> VersoPalette.Light
    ThemeMode.SEPIA -> VersoPalette.Sepia
    ThemeMode.DARK -> VersoPalette.Dark
    ThemeMode.NIGHT -> VersoPalette.Night
}

@Composable
private fun ThemeSwatch(mode: ThemeMode, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = VersoTheme.colors
    val palette = swatchPalette(mode)
    Column(
        modifier
            .heightIn(min = 72.dp)
            .clip(VersoShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 52.dp, height = 40.dp)) {
            val shape = VersoShapes.small
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(palette.background)
                    // border dessine après le contenu : le filet reste visible par-dessus la diagonale d’Auto.
                    .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.outline, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (mode == ThemeMode.AUTO) {
                    val dark = VersoPalette.Dark.background
                    Canvas(Modifier.fillMaxSize()) {
                        val triangle = Path().apply {
                            moveTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(triangle, dark)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.reader_settings_glyph),
                        style = VersoTheme.typography.bodyStrong,
                        color = palette.text,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VersoIcons.CheckSwitch, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            text = label,
            style = if (selected) VersoTheme.typography.captionBold else VersoTheme.typography.caption.copy(fontWeight = FontWeight(500)),
            color = colors.text,
            textAlign = TextAlign.Center,
        )
    }
}
