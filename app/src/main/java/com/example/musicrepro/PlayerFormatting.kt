package com.example.musicrepro

import java.util.Locale

internal fun formatTime(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
