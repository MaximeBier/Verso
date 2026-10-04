package com.maximebier.verso.ui.reader

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import com.maximebier.verso.R
import com.maximebier.verso.core.translation.Translation
import com.maximebier.verso.core.translation.TranslationResult
import com.maximebier.verso.ui.a11y.rememberReducedMotion
import com.maximebier.verso.ui.components.OutlinedPillButton
import com.maximebier.verso.ui.components.VersoIconButton
import com.maximebier.verso.ui.components.VersoIcons
import com.maximebier.verso.ui.theme.VersoShapes
import com.maximebier.verso.ui.theme.VersoTheme
import kotlin.math.max
import kotlin.math.roundToInt

/** Glissé vers le bas au-delà duquel la feuille se ferme. */
private val DismissDragDistance = 56.dp

/** Lancer vers le bas (par seconde) au-delà duquel la feuille se ferme, même sur une courte distance. */
private val DismissVelocity = 1_000.dp

/** Part de la fenêtre au-delà de laquelle le contenu défile (passage long, texte à 200 %, paysage) ; poignée et en-tête en plus. */
private const val MAX_HEIGHT_FRACTION = 0.45f

/**
 * Feuille de traduction (3.08 à 3.10) : petite, sans voile, posée en bas pour laisser voir le passage sélectionné.
 * Mot ou expression courte en tête (en anglais pour TalkBack), sinon « Traduction » ; puis la traduction, un état
 * d’attente ou d’échec, et « Anglais → Français ». La croix, un glissé vers le bas ou un tap sur le texte la ferment.
 */
@Composable
fun TranslationSheet(
    state: TranslationSheetState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    val density = LocalDensity.current
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val dismiss by rememberUpdatedState(onDismiss)
    val drag = remember { mutableFloatStateOf(0f) }
    val dismissPx = with(density) { DismissDragDistance.toPx() }
    val flingPx = with(density) { DismissVelocity.toPx() }
    val sheetLabel = stringResource(R.string.translation_sheet)
    val maxHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } * MAX_HEIGHT_FRACTION
    // Fin du glissé : fermée au-delà de la distance ou du lancer, sinon remise en place.
    val settle: suspend (Float) -> Unit = { velocity ->
        when {
            drag.floatValue > dismissPx || velocity > flingPx -> dismiss()
            reducedMotion -> drag.floatValue = 0f
            else -> animate(drag.floatValue, 0f) { value, _ -> drag.floatValue = value }
        }
    }
    // Le contenu défile (passage long, texte à 200 %) et capte les glissés verticaux : ce qu’il ne consomme pas vers
    // le bas descend la feuille, et un glissé vers le haut la remonte avant de faire défiler.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || drag.floatValue <= 0f) return Offset.Zero
                val used = max(available.y, -drag.floatValue)
                drag.floatValue += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                drag.floatValue += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.floatValue <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    Column(
        modifier = modifier
            // Glissé lu avant le décalage : la zone de glissé ne suit pas le doigt, les écarts restent entiers.
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> drag.floatValue = (drag.floatValue + delta).coerceAtLeast(0f) },
                onDragStopped = { velocity -> settle(velocity) },
            )
            .nestedScroll(nested)
            .offset { IntOffset(0, drag.floatValue.roundToInt()) }
            .fillMaxWidth()
            .shadow(20.dp, VersoShapes.sheetTop, clip = false)
            .clip(VersoShapes.sheetTop)
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .semantics { paneTitle = sheetLabel }
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.outline),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.short) english(Translation.lookupForm(state.source)) else AnnotatedString(sheetLabel),
                // 16 sp, interligne 1,4 (3.08).
                style = typography.body.copy(lineHeight = 22.4.sp),
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            VersoIconButton(
                icon = VersoIcons.Close,
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismiss,
                // La croix déborde de 12 dp dans la marge, comme dans la maquette.
                modifier = Modifier.offset(x = 12.dp),
            )
        }
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TranslationBody(state.result, onRetry)
            // Dans la zone qui défile : en paysage à 200 %, la légende n’est jamais écrasée.
            if (state.result == null || state.result is TranslationResult.Word || state.result is TranslationResult.Passage) {
                Text(
                    text = stringResource(R.string.translation_languages),
                    style = typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun TranslationBody(result: TranslationResult?, onRetry: () -> Unit) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    when (result) {
        null -> {
            val loading = stringResource(R.string.translation_loading)
            CircularProgressIndicator(
                color = colors.accent,
                trackColor = colors.progressTrack,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(24.dp)
                    .semantics { contentDescription = loading },
            )
        }
        is TranslationResult.Word -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 26 sp semi-gras, interligne 1,25 ; autres sens 18 sp, interligne 1,45 (3.08).
            Text(
                text = result.primary,
                style = typography.body.copy(fontSize = 26.sp, lineHeight = 32.5.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            if (result.alternatives.isNotEmpty()) {
                Text(
                    text = result.alternatives.joinToString(stringResource(R.string.translation_separator)),
                    style = typography.body.copy(fontSize = 18.sp, lineHeight = 26.1.sp),
                    color = colors.text,
                )
            }
        }
        // 20 sp, interligne 1,55 (3.09).
        is TranslationResult.Passage -> Text(
            text = result.text,
            style = typography.body.copy(fontSize = 20.sp, lineHeight = 31.sp),
            color = colors.text,
        )
        TranslationResult.Offline -> Failure(
            title = stringResource(R.string.translation_offline_title),
            message = stringResource(R.string.translation_offline_body),
            onRetry = onRetry,
        )
        TranslationResult.Unavailable -> Failure(title = null, message = stringResource(R.string.translation_unavailable), onRetry = onRetry)
        TranslationResult.TooLong -> Failure(title = null, message = stringResource(R.string.translation_too_long), onRetry = null)
    }
}

/** « Pas de connexion » (3.10) et les autres échecs : titre éventuel, message, « Réessayer ». */
@Composable
private fun Failure(title: String?, message: String, onRetry: (() -> Unit)?) {
    val colors = VersoTheme.colors
    val typography = VersoTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) {
            // 18 sp gras, interligne 1,4.
            Text(text = title, style = typography.bodyStrong.copy(fontSize = 18.sp, lineHeight = 25.2.sp), color = colors.text)
        }
        // 16 sp, interligne 1,5.
        Text(text = message, style = typography.body, color = colors.textSecondary)
    }
    if (onRetry != null) {
        OutlinedPillButton(text = stringResource(R.string.translation_retry), onClick = onRetry, icon = VersoIcons.Retry)
    }
}

/** Texte anglais : TalkBack le lit avec la voix anglaise. */
private fun english(text: String): AnnotatedString =
    AnnotatedString(text, spanStyle = SpanStyle(localeList = LocaleList("en")))
