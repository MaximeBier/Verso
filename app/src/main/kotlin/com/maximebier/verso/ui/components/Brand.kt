package com.maximebier.verso.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.RenderVectorGroup
import androidx.compose.ui.graphics.vector.VectorConfig
import androidx.compose.ui.graphics.vector.VectorProperty
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.maximebier.verso.R
import com.maximebier.verso.ui.theme.VersoTheme

/**
 * Logotype (en-tête de la bibliothèque) : `ic_logotype`, le « v » à l’accent et « erso » à la couleur du texte du
 * thème affiché. Taille fixe en dp : il ne suit ni la police de l’app ni la taille de texte d’Android.
 */
@Composable
fun VersoLogotype(modifier: Modifier = Modifier) {
    val colors = VersoTheme.colors
    val image = ImageVector.vectorResource(R.drawable.ic_logotype)
    val fills = remember(colors.accent, colors.text) {
        mapOf(LOGOTYPE_V to FillConfig(SolidColor(colors.accent)), LOGOTYPE_ERSO to FillConfig(SolidColor(colors.text)))
    }
    val painter = rememberVectorPainter(
        defaultWidth = image.defaultWidth,
        defaultHeight = image.defaultHeight,
        viewportWidth = image.viewportWidth,
        viewportHeight = image.viewportHeight,
        name = image.name,
    ) { _, _ -> RenderVectorGroup(group = image.root, configs = fills) }
    Image(
        painter = painter,
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.semantics { heading() },
    )
}

/** Couleur de remplissage imposée à un tracé nommé du vecteur. */
private class FillConfig(private val fill: Brush) : VectorConfig {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T =
        if (property is VectorProperty.Fill) fill as T else defaultValue
}

/** Noms des deux tracés de `ic_logotype.xml`. */
private const val LOGOTYPE_V = "v"
private const val LOGOTYPE_ERSO = "erso"

/**
 * Icône de l’app (1.01, 1.09), décorative : les calques de l’icône de lancement (fond `ic_launcher_background`,
 * premier plan `ic_launcher_foreground`), dont on montre les 72 dp centraux sur 108, aux coins arrondis.
 */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * ICON_CORNER_FRACTION))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * ADAPTIVE_ICON_FULL / ADAPTIVE_ICON_VISIBLE),
        )
    }
}

/** Icône adaptative : calque de 108 dp, dont 72 dp visibles. */
private const val ADAPTIVE_ICON_FULL = 108f
private const val ADAPTIVE_ICON_VISIBLE = 72f

/** Arrondi des maquettes : 22 sur 72. */
private const val ICON_CORNER_FRACTION = 22f / 72f
