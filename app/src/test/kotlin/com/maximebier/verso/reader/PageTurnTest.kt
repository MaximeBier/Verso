package com.maximebier.verso.reader

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PageTurnTest {
    private val width = 1000f
    private val touch = Offset(930f, 600f)

    private fun anim(forward: Boolean) =
        PageTurnAnim(forward, curled = null, under = null, grab = Offset(width, touch.y), touch = touch, turnedEarly = false)

    @Test
    fun aFlingInTheTurnDirectionCommitsEvenWithLittleTravel() {
        assertThat(PageTurnDriver.shouldCommit(forward = true, travelled = 0.02f, velocityX = -2000f)).isTrue()
        assertThat(PageTurnDriver.shouldCommit(forward = false, travelled = 0.02f, velocityX = 2000f)).isTrue()
    }

    @Test
    fun aFlingAgainstTheTurnDirectionCancelsEvenAfterLongTravel() {
        assertThat(PageTurnDriver.shouldCommit(forward = true, travelled = 0.8f, velocityX = 2000f)).isFalse()
        assertThat(PageTurnDriver.shouldCommit(forward = false, travelled = 0.8f, velocityX = -2000f)).isFalse()
    }

    @Test
    fun withoutAFlingTheTravelledShareDecides() {
        assertThat(PageTurnDriver.shouldCommit(forward = true, travelled = PageTurnDriver.COMMIT_FRACTION + 0.01f, velocityX = 0f)).isTrue()
        assertThat(PageTurnDriver.shouldCommit(forward = true, travelled = PageTurnDriver.COMMIT_FRACTION - 0.01f, velocityX = 0f)).isFalse()
    }

    @Test
    fun forwardEdgeFollowsTheFingerButNeverPassesItsFlatPlace() {
        val a = anim(forward = true)
        assertThat(a.edgeXFor(touch + Offset(-300f, 0f))).isEqualTo(width - 300f)
        // Doigt revenu à droite de l’appui : la page reste à plat.
        assertThat(a.edgeXFor(touch + Offset(200f, 0f))).isEqualTo(width)
    }

    @Test
    fun backwardEdgeStartsTurnedAndComesBackTwiceAsFastAsTheFinger() {
        val a = anim(forward = false)
        assertThat(a.edgeXFor(touch)).isEqualTo(-width)
        assertThat(a.edgeXFor(touch + Offset(250f, 0f))).isEqualTo(-width + 500f)
        // Jamais au-delà de la page à plat, jamais en deçà de la page tournée.
        assertThat(a.edgeXFor(touch + Offset(5000f, 0f))).isEqualTo(width)
        assertThat(a.edgeXFor(touch + Offset(-300f, 0f))).isEqualTo(-width)
    }

    @Test
    fun theTiltFadesOutAsThePageComesBackFlat() = runTest {
        val a = anim(forward = true)
        a.tilt = 300f
        a.edgeX.snapTo(width)
        assertThat(a.edge).isEqualTo(Offset(width, touch.y))
        a.edgeX.snapTo(width - 0.5f * width)
        assertThat(a.edge.y).isEqualTo(touch.y + 300f)
    }

    @Test
    fun turnedFractionGoesFromFlatToTurned() = runTest {
        val a = anim(forward = true)
        assertThat(a.turnedFraction()).isEqualTo(0f)
        a.edgeX.snapTo(-width)
        assertThat(a.turnedFraction()).isEqualTo(1f)
    }
}
