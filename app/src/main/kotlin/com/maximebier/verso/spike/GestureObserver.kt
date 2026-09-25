package com.maximebier.verso.spike

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity

/**
 * Observe chaque geste sans le consommer (passe Initial, avant la WebView) et rapporte,
 * au relâchement, la vitesse verticale du doigt en px/s.
 */
fun Modifier.observeGestures(onRelease: (timeMs: Long, velocityYPxPerSecond: Float) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val tracker = VelocityTracker()
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            tracker.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    onRelease(System.currentTimeMillis(), tracker.calculateVelocity().y)
                    break
                }
            }
        }
    }

/**
 * Connexion de défilement imbriqué posée AU-DESSUS de ReflowableWebRendition (variante A) :
 * reçoit les deltas de scroll (drag = UserInput, inertie = SideEffect) et la vitesse de chaque relâchement,
 * sans rien consommer.
 */
class ScrollObserver(
    private val onScroll: (deltaYPx: Float, isInertia: Boolean) -> Unit,
    private val onFling: (velocityYPxPerSecond: Float) -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        android.util.Log.d("VersoSpikeScroll", "pre y=${available.y} x=${available.x} source=$source")
        onScroll(available.y, source == NestedScrollSource.SideEffect)
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        onFling(available.y)
        return Velocity.Zero
    }
}
