package com.maximebier.verso.ui.a11y

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.maximebier.verso.ui.theme.VersoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Le réglage n'est écrit que dans le bac à sable Robolectric, jamais sur un appareil. */
@RunWith(AndroidJUnit4::class)
class ReducedMotionTest {

    @get:Rule val composeRule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun animationsAreOnByDefault() {
        assertThat(isReducedMotionEnabled(ctx)).isFalse()
    }

    @Test
    fun zeroAnimatorDurationScaleMeansReducedMotion() {
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertThat(isReducedMotionEnabled(ctx)).isTrue()
    }

    @Test
    fun composablesReadTheSetting() {
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var read: Boolean? = null
        composeRule.setContent { VersoTheme { read = rememberReducedMotion() } }
        composeRule.waitForIdle()
        assertThat(read).isTrue()
    }
}
