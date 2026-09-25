package com.maximebier.verso.ui.settings

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.components.DetailTopBar
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoTheme
import com.maximebier.verso.ui.theme.VersoTypography

/** Un composant embarqué, sa licence et le texte complet de celle-ci (res/raw). */
data class LicenseEntry(
    @StringRes val component: Int,
    @StringRes val licenseName: Int,
    @RawRes val text: Int,
)

/** Liste statique, écrite à la main (aucun plugin qui télécharge des métadonnées). */
object OpenSourceLicenses {
    val entries: List<LicenseEntry> = listOf(
        LicenseEntry(R.string.license_component_verso, R.string.license_name_apache_2, R.raw.license_apache_2_0),
        LicenseEntry(R.string.license_component_readium, R.string.license_name_bsd_3, R.raw.license_bsd_3_readium),
        LicenseEntry(R.string.license_component_atkinson, R.string.license_name_ofl, R.raw.license_ofl_atkinson),
        LicenseEntry(R.string.license_component_lucide, R.string.license_name_isc, R.raw.license_isc_lucide),
        LicenseEntry(R.string.license_component_androidx, R.string.license_name_apache_2, R.raw.license_apache_2_0),
        LicenseEntry(R.string.license_component_kotlin, R.string.license_name_apache_2, R.raw.license_apache_2_0),
        LicenseEntry(R.string.license_component_coil, R.string.license_name_apache_2, R.raw.license_apache_2_0),
        LicenseEntry(R.string.license_component_jsoup, R.string.license_name_mit, R.raw.license_mit_jsoup),
        LicenseEntry(R.string.license_component_desugar, R.string.license_name_gpl2_classpath, R.raw.license_gpl2_classpath_desugar),
        LicenseEntry(R.string.license_component_okio, R.string.license_name_apache_2, R.raw.license_apache_2_0),
        LicenseEntry(R.string.license_component_jspecify, R.string.license_name_apache_2, R.raw.license_apache_2_0),
    )
}

/** Point d'entrée de LicensesRoute (signature figée par 2.2, appelé par VersoNavHost). */
@Composable
fun LicensesDestination(onBack: () -> Unit) {
    LicensesScreen(onBack = onBack)
}

/** Écran « Licences open source » (non dessiné) : DetailTopBar + lignes dépliables. */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxSize().background(colors.background)) {
        DetailTopBar(title = stringResource(R.string.settings_licenses), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        ) {
            item {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
                    style = VersoTypography.body,
                    color = colors.textSecondary,
                )
            }
            itemsIndexed(OpenSourceLicenses.entries) { index, entry ->
                LicenseRow(
                    entry = entry,
                    expanded = expanded == index,
                    onToggle = { expanded = if (expanded == index) null else index },
                )
            }
        }
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry, expanded: Boolean, onToggle: () -> Unit) {
    val colors = VersoTheme.colors
    val context = LocalContext.current
    val toggleLabel = stringResource(if (expanded) R.string.licenses_hide_text else R.string.licenses_show_text)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggle)
                .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(entry.component), style = VersoTypography.rowTitle, color = colors.text)
                Text(stringResource(entry.licenseName), style = VersoTypography.caption, color = colors.textSecondary)
            }
            Icon(
                imageVector = VersoIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.padding(end = 12.dp).size(20.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            val text = remember(entry.text) {
                context.resources.openRawResource(entry.text).bufferedReader().use { it.readText() }
            }
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                style = VersoTypography.caption,
                color = colors.text,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
    }
}
