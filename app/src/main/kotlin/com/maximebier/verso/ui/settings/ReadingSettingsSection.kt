package com.maximebier.verso.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.core.settings.ReadingFont
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.DarkThemeVariant
import com.maximebier.verso.data.ThemeMode
import com.maximebier.verso.ui.components.ChoiceRow
import com.maximebier.verso.ui.components.RadioRow
import com.maximebier.verso.ui.components.SegmentedRow
import com.maximebier.verso.ui.components.SettingsParagraph
import com.maximebier.verso.ui.components.SettingsSection
import com.maximebier.verso.ui.components.VersoDialog
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.components.VersoTextButton
import com.maximebier.verso.ui.theme.VersoDimens
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.fontFamilyFor

/** Dialogue ouvert depuis une ligne de choix de la section Lecture. */
private enum class ReadingDialog { THEME, TEXT_SIZE, SCROLL }

@Composable
fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.AUTO -> R.string.settings_theme_auto
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.SEPIA -> R.string.settings_theme_sepia
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.NIGHT -> R.string.settings_theme_night
    },
)

@Composable
private fun scrollModeLabel(mode: ScrollMode): String =
    stringResource(if (mode == ScrollMode.CONTINUOUS) R.string.settings_scroll_continuous else R.string.settings_scroll_pages)

/**
 * Section « Lecture » des Paramètres (2.09) : phrase d’aide, polices, thème, taille et défilement par défaut.
 * « Thème sombre » (1.09, maquettes du 2026-09-29) suit la ligne Thème : 2.09 date d’avant ce réglage.
 */
@Composable
fun ReadingSettingsSection(
    reading: ReadingSettings,
    themeMode: ThemeMode,
    darkThemeVariant: DarkThemeVariant,
    onFontChange: (ReadingFont) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDarkThemeVariantChange: (DarkThemeVariant) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onDefaultScrollModeChange: (ScrollMode) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<ReadingDialog?>(null) }
    SettingsSection(title = stringResource(R.string.settings_section_reading)) {
        SettingsParagraph(text = stringResource(R.string.settings_reading_help))
        FontChoices(selected = reading.font, onSelect = onFontChange)
        ChoiceRow(
            title = stringResource(R.string.settings_theme),
            value = themeModeLabel(themeMode),
            onClick = { dialog = ReadingDialog.THEME },
        )
        DarkThemeVariantSelector(selected = darkThemeVariant, onSelect = onDarkThemeVariantChange)
        ChoiceRow(
            title = stringResource(R.string.settings_text_size),
            value = reading.fontSizeSp.toString(),
            onClick = { dialog = ReadingDialog.TEXT_SIZE },
        )
        ChoiceRow(
            title = stringResource(R.string.settings_scroll),
            value = scrollModeLabel(reading.defaultScrollMode),
            onClick = { dialog = ReadingDialog.SCROLL },
        )
    }
    val close = { dialog = null }
    when (dialog) {
        ReadingDialog.THEME -> RadioDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { themeModeLabel(it) },
            selectedIndex = ThemeMode.entries.indexOf(themeMode),
            onSelect = { onThemeModeChange(ThemeMode.entries[it]) },
            onDismiss = close,
        )
        ReadingDialog.SCROLL -> RadioDialog(
            title = stringResource(R.string.settings_scroll),
            options = ScrollMode.entries.map { scrollModeLabel(it) },
            selectedIndex = ScrollMode.entries.indexOf(reading.defaultScrollMode),
            onSelect = { onDefaultScrollModeChange(ScrollMode.entries[it]) },
            onDismiss = close,
        )
        ReadingDialog.TEXT_SIZE -> TextSizeDialog(reading = reading, onFontSizeChange = onFontSizeChange, onDismiss = close)
        null -> Unit
    }
}

/** Trois polices en boutons radio, chacune montrée sur une phrase du livre (18 sp, interligne 1,45). */
@Composable
private fun FontChoices(selected: ReadingFont, onSelect: (ReadingFont) -> Unit) {
    val colors = VersoTheme.colors
    val legend = stringResource(R.string.settings_font)
    Text(
        text = legend,
        style = VersoTheme.typography.bodyStrong,
        color = colors.text,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp).semantics { heading() },
    )
    val fonts = listOf(
        Triple(ReadingFont.LITERATA, R.string.settings_font_literata, R.string.settings_font_literata_summary),
        Triple(ReadingFont.ATKINSON, R.string.settings_font_atkinson, R.string.settings_font_atkinson_summary),
        Triple(ReadingFont.SYSTEM, R.string.settings_font_system, R.string.settings_font_system_summary),
    )
    Column(Modifier.selectableGroup()) {
        fonts.forEach { (font, name, summary) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = VersoDimens.fontChoiceRow)
                    .selectable(selected = font == selected, role = Role.RadioButton, onClick = { onSelect(font) })
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(
                    selected = font == selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.outline),
                    modifier = Modifier.size(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(name), style = VersoTheme.typography.rowTitle, color = colors.text)
                    Text(stringResource(summary), style = VersoTheme.typography.caption, color = colors.textSecondary)
                    Text(
                        text = stringResource(R.string.settings_font_sample),
                        style = TextStyle(fontFamily = fontFamilyFor(font), fontSize = 18.sp, lineHeight = 26.1.sp),
                        color = colors.text,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Choix unique appliqué aussitôt ; « Fermer » ferme le dialogue. */
@Composable
private fun RadioDialog(title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    VersoDialog(
        title = title,
        onDismissRequest = onDismiss,
        buttons = { VersoTextButton(text = stringResource(R.string.common_close), onClick = onDismiss) },
    ) {
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, label ->
                // Ligne de la tâche 10.1 (bouton radio, libellé en gras quand choisi, 48 dp), sans sa marge : le dialogue a la sienne.
                RadioRow(title = label, selected = index == selectedIndex, onClick = { onSelect(index) }, horizontalPadding = 0.dp)
            }
        }
    }
}

/** Taille du texte : − et + (48 dp, intitulés TalkBack), désactivés aux bornes, valeur au milieu. */
@Composable
private fun TextSizeDialog(reading: ReadingSettings, onFontSizeChange: (Int) -> Unit, onDismiss: () -> Unit) {
    VersoDialog(
        title = stringResource(R.string.settings_text_size),
        onDismissRequest = onDismiss,
        buttons = { VersoTextButton(text = stringResource(R.string.common_close), onClick = onDismiss) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersoIconButton(
                icon = VersoIcons.Minus,
                contentDescription = stringResource(R.string.settings_text_size_decrease),
                onClick = { onFontSizeChange(reading.smaller().fontSizeSp) },
                enabled = reading.canShrink,
            )
            Text(reading.fontSizeSp.toString(), style = VersoTheme.typography.screenTitle, color = VersoTheme.colors.text)
            VersoIconButton(
                icon = VersoIcons.Plus,
                contentDescription = stringResource(R.string.settings_text_size_increase),
                onClick = { onFontSizeChange(reading.larger().fontSizeSp) },
                enabled = reading.canGrow,
            )
        }
    }
}

/** « Thème sombre : Sombre / Nuit » (1.09) : le thème foncé de l'automatique quand le téléphone est en sombre. */
@Composable
private fun DarkThemeVariantSelector(selected: DarkThemeVariant, onSelect: (DarkThemeVariant) -> Unit) {
    val variants = DarkThemeVariant.entries
    SegmentedRow(
        title = stringResource(R.string.settings_dark_theme),
        options = variants.map { stringResource(it.labelRes()) },
        selectedIndex = variants.indexOf(selected),
        onSelect = { onSelect(variants[it]) },
    )
}

@androidx.annotation.StringRes
private fun DarkThemeVariant.labelRes(): Int = when (this) {
    DarkThemeVariant.DARK -> R.string.settings_dark_theme_dark
    DarkThemeVariant.NIGHT -> R.string.settings_dark_theme_night
}
