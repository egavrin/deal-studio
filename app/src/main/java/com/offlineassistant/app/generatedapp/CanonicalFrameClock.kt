package com.offlineassistant.app.generatedapp

/** Accumulates foreground frame time without replaying a background pause. */
internal class CanonicalFrameClock(intervalMillis: Long) {
    private val intervalNanos = intervalMillis.coerceIn(16L, 60_000L) * 1_000_000L
    private var previous: Long? = null
    private var accumulated = 0L

    fun frame(nowNanos: Long): Long? {
        val last = previous
        previous = nowNanos
        if (last == null) return null
        accumulated += (nowNanos - last).coerceIn(0L, 64_000_000L)
        if (accumulated < intervalNanos) return null
        val elapsed = accumulated / 1_000_000L
        accumulated %= 1_000_000L
        return elapsed
    }
}
