package com.example.soundrepeater

/** Pure time arithmetic (no Android classes) so it can be unit-tested anywhere. */
object ScheduleMath {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 1440

    fun clamp(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /**
     * Next chime time after a chime that was planned for [previousAt].
     * Anchored to the plan (no drift); if the device was asleep/off for a while,
     * missed chimes are skipped and the next one lands on the original grid
     * (and never sooner than half an interval from now).
     */
    fun nextAfter(previousAt: Long, nowMs: Long, minutes: Int): Long {
        val step = clamp(minutes) * 60_000L
        var next = if (previousAt > 0) previousAt + step else nowMs + step
        if (next <= nowMs) next += ((nowMs - next) / step + 1) * step
        // A very late alarm must not cause a quick second chime a few seconds later:
        // always keep at least half an interval before the next one.
        if (next - nowMs < step / 2) next += step
        return next
    }
}
