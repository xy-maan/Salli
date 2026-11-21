package com.example.soundrepeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ScheduleMathTest {
    private val min = 60_000L

    @Test
    fun clampKeepsValuesInRange() {
        assertEquals(1, ScheduleMath.clamp(0))
        assertEquals(1, ScheduleMath.clamp(-5))
        assertEquals(5, ScheduleMath.clamp(5))
        assertEquals(1440, ScheduleMath.clamp(99_999))
    }

    @Test
    fun nextChimeIsExactlyOneIntervalAfterThePlan() {
        val planned = 1_000_000L
        // The alarm fired 700 ms late; the next one must still be planned + interval (no drift).
        assertEquals(planned + 5 * min, ScheduleMath.nextAfter(planned, planned + 700, 5))
    }

    @Test
    fun missedChimesAreSkippedAndStayOnTheGrid() {
        val planned = 1_000_000L
        val now = planned + 23 * min + 10_000 // phone was off for ~23 minutes, interval 5
        val next = ScheduleMath.nextAfter(planned, now, 5)
        assertTrue(next - now >= 5 * min / 2)
        assertTrue(next - now < 5 * min * 3 / 2)
        assertEquals(0L, (next - planned) % (5 * min))
    }

    @Test
    fun withoutAPlanItCountsFromNow() {
        val now = 5_000_000L
        assertEquals(now + 10 * min, ScheduleMath.nextAfter(0L, now, 10))
    }

    @Test
    fun outOfRangeIntervalsAreClamped() {
        val planned = 1_000_000L
        assertEquals(planned + 1 * min, ScheduleMath.nextAfter(planned, planned + 100, 0))
        assertEquals(planned + 1440 * min, ScheduleMath.nextAfter(planned, planned + 100, 5000))
    }

    @Test
    fun neverSchedulesInThePast() {
        val rnd = Random(7)
        repeat(5000) {
            val planned = 1_000_000L + rnd.nextInt(1_000_000)
            val now = planned + rnd.nextInt(10_000_000)
            val minutes = 1 + rnd.nextInt(60)
            assertTrue(ScheduleMath.nextAfter(planned, now, minutes) > now)
        }
    }

    @Test
    fun thousandChimesWithJitterKeepExactSpacing() {
        val rnd = Random(42)
        val start = 1_000_000L
        var planned = start
        repeat(1000) {
            val now = planned + rnd.nextInt(8_000) // each chime fires 0-8 s late
            planned = ScheduleMath.nextAfter(planned, now, 7)
        }
        // After 1000 chimes the schedule must be exactly 1000 intervals later: zero drift.
        assertEquals(start + 1000 * 7 * min, planned)
    }

    @Test
    fun veryLateAlarmDoesNotCauseAQuickDoubleChime() {
        // Real case from the phone log: 1-minute interval, alarm fired 51.5 s late.
        val planned = 1_000_000L
        val now = planned + 51_494
        val next = ScheduleMath.nextAfter(planned, now, 1)
        assertTrue(next - now >= 30_000)       // not 8 seconds later
        assertEquals(0L, (next - planned) % min) // still on the original grid
    }

    @Test
    fun nextChimeIsNeverCloserThanHalfAnInterval() {
        val rnd = Random(99)
        repeat(5000) {
            val minutes = 1 + rnd.nextInt(30)
            val planned = 1_000_000L + rnd.nextInt(1_000_000)
            val now = planned + rnd.nextInt(3 * minutes * 60_000)
            assertTrue(ScheduleMath.nextAfter(planned, now, minutes) - now >= minutes * 60_000L / 2)
        }
    }

    @Test
    fun silentRuleOnlyBlocksWhenTheOptionIsOff() {
        assertTrue(ScheduleMath.shouldPlay(playInSilent = true, ringerIsNormal = true))
        assertTrue(ScheduleMath.shouldPlay(playInSilent = true, ringerIsNormal = false))
        assertTrue(ScheduleMath.shouldPlay(playInSilent = false, ringerIsNormal = true))
        assertFalse(ScheduleMath.shouldPlay(playInSilent = false, ringerIsNormal = false))
    }

    @Test
    fun silentPeriodKeepsTheOriginalSchedule() {
        // Started at S with a 1-minute interval. The chain keeps ticking (silently) for
        // 25 minutes; when sound is back at S + 25 min 24 s the next chime is at S + 26 min.
        val start = 36_610_000L // e.g. 10:10:10
        var planned = start + min
        repeat(25) {
            planned = ScheduleMath.nextAfter(planned, planned + 300, 1)
        }
        assertEquals(start + 26 * min, planned)
        assertTrue(planned > start + 25 * min + 24_000)
    }
}
