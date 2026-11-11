package com.example.musicrepro

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFormattingTest {
    @Test
    fun formatTimeRendersMinutesAndSeconds() {
        assertEquals("3:07", formatTime(187_000L))
    }

    @Test
    fun formatTimeClampsNegativeDurationsToZero() {
        assertEquals("0:00", formatTime(-1L))
    }
}
