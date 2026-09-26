package com.maximebier.verso.ui.common

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class BookTextsTest {

    @Test
    fun percentIsFlooredAndBounded() {
        assertThat(percentOf(0.0)).isEqualTo(0)
        assertThat(percentOf(0.319)).isEqualTo(31)
        assertThat(percentOf(0.999)).isEqualTo(99)
        assertThat(percentOf(1.0)).isEqualTo(100)
        assertThat(percentOf(-0.2)).isEqualTo(0)
        assertThat(percentOf(1.7)).isEqualTo(100)
    }

    @Test
    fun dateIsFormattedInFrench() {
        val zone = ZoneId.of("Europe/Paris")
        val millis = LocalDateTime.of(2026, 9, 12, 12, 0).atZone(zone).toInstant().toEpochMilli()
        assertThat(formatDate(millis, "d MMMM yyyy", zone)).isEqualTo("12 septembre 2026")
    }
}
