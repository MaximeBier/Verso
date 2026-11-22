package com.maximebier.verso

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildConfigTest {

    @Test
    fun applicationIdIsTheFinalOne() {
        assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.maximebier.verso")
    }

    @Test
    fun versionNameIsSemver() {
        assertThat(BuildConfig.VERSION_NAME).matches("""\d+\.\d+\.\d+""")
    }
}
