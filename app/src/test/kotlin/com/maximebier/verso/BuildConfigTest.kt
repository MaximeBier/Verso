package com.maximebier.verso

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildConfigTest {

    @Test
    fun applicationIdIsTheFinalOne() {
        assertThat(BuildConfig.APPLICATION_ID).isEqualTo("com.maximebier.verso")
    }

    @Test
    fun versionNameIsV1() {
        assertThat(BuildConfig.VERSION_NAME).isEqualTo("1.0.0")
    }
}
