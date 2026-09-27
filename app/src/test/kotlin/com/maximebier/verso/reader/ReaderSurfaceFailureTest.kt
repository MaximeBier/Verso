package com.maximebier.verso.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.core.settings.ReadingSettings
import com.maximebier.verso.core.settings.ScrollMode
import com.maximebier.verso.data.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Revue finale I3 : la surface qui ne peut pas afficher un livre ne laisse pas un écran vide sans issue. Hébergée ici
 * dans une `ComponentActivity` (pas de gestionnaire de fragments) : c’est ce cas qui est vérifié. Le refus d’un
 * livre à mise en page fixe se fait avant, au chargement
 * (`ReaderViewModelTest.fixedLayoutBookFailsWithoutBecomingTheLastOpenedBook`).
 */
@RunWith(AndroidJUnit4::class)
class ReaderSurfaceFailureTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun surfaceWithoutAFragmentHostIsReportedAsFailed() {
        val page = Link(href = Url("page-1.xhtml")!!, mediaType = MediaType.XHTML)
        val fixedLayout = Publication(
            manifest = Manifest(
                metadata = Metadata(
                    localizedTitle = LocalizedString("Album illustré"),
                    conformsTo = setOf(Publication.Profile.EPUB),
                    layout = Layout.FIXED,
                ),
                readingOrder = listOf(page),
            ),
        )
        var failures = 0
        var ready = 0

        compose.setContent {
            ReaderSurface(
                publication = fixedLayout,
                initialLocator = null,
                initialStyle = ReaderStyle(ReadingSettings(), AppTheme.LIGHT, ScrollMode.CONTINUOUS),
                onReady = { ready++ },
                onCenterTap = {},
                onFailed = { failures++ },
            )
        }
        compose.waitForIdle()

        assertThat(failures).isEqualTo(1)
        assertThat(ready).isEqualTo(0)
    }
}
