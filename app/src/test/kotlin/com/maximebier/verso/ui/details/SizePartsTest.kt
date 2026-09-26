package com.maximebier.verso.ui.details

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SizePartsTest {

    @Test
    fun kilobytesBelowOneMegabyte() {
        assertThat(sizeParts(850_000)).isEqualTo(SizeParts(megabytes = false, value = "850"))
        assertThat(sizeParts(200)).isEqualTo(SizeParts(megabytes = false, value = "1"))
        assertThat(sizeParts(999_999)).isEqualTo(SizeParts(megabytes = false, value = "999"))
    }

    @Test
    fun megabytesWithOneFrenchDecimal() {
        assertThat(sizeParts(1_234_567)).isEqualTo(SizeParts(megabytes = true, value = "1,2"))
        assertThat(sizeParts(12_345_678)).isEqualTo(SizeParts(megabytes = true, value = "12,3"))
        assertThat(sizeParts(1_000_000)).isEqualTo(SizeParts(megabytes = true, value = "1,0"))
    }
}
