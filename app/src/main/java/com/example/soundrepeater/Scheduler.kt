package com.example.soundrepeater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_AT = "at"
    const val MIN_MINUTES = ScheduleMath.MIN_MINUTES
    const val MAX_MINUTES = ScheduleMath.MAX_MINUTES

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun minutes(c: Context) = prefs(c).getInt("minutes", 5)
    fun nextAt(c: Context) = prefs(c).getLong("next_at", 0L)

    // The interval and the planned time are carried inside the alarm itself,
    // so every chime re-arms the next one with exactly the same interval.
    private fun pending(c: Context, minutes: Int, at: Long): PendingIntent {
        val i = Intent(c, ChimeReceiver::class.java)
            .putExtra(EXTRA_MINUTES, minutes)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun start(c: Context, minutes: Int) {
        val m = ScheduleMath.clamp(minutes)
        SalliLog.log(c, "SCHED", "START requested=${minutes}min using=${m}min")
        prefs(c).edit().putInt("minutes", m).putBoolean("enabled", true).commit()
        schedule(c, m, System.currentTimeMillis() + m * 60_000L)
    }

    fun stop(c: Context) {
        SalliLog.log(c, "SCHED", "STOP")
        prefs(c).edit().putBoolean("enabled", false).putLong("next_at", 0L).commit()
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(c, 0, 0))
    }

    /** After a reboot or update: start counting again with the saved interval. */
    fun resume(c: Context) {
        val m = ScheduleMath.clamp(minutes(c))
        SalliLog.log(c, "SCHED", "RESUME interval=${m}min")
        schedule(c, m, System.currentTimeMillis() + m * 60_000L)
    }

    /** After a chime: next time = previous planned time + interval (no drift). */
    fun scheduleAfter(c: Context, minutes: Int, previousAt: Long) {
        val m = ScheduleMath.clamp(minutes)
        schedule(c, m, ScheduleMath.nextAfter(previousAt, System.currentTimeMillis(), m))
    }

    /** Totals used to check battery behaviour: how long we were awake per chime. */
    fun recordAwake(c: Context, awakeMs: Long): String {
        val p = prefs(c)
        val n = p.getInt("chimes", 0) + 1
        val total = p.getLong("awake_ms", 0L) + awakeMs
        p.edit().putInt("chimes", n).putLong("awake_ms", total).apply()
        return "chimes=$n totalAwake=${total}ms avgAwake=${total / n}ms"
    }

    fun chimeCount(c: Context) = prefs(c).getInt("chimes", 0)
    fun avgAwakeMs(c: Context): Long {
        val n = chimeCount(c)
        return if (n == 0) 0L else prefs(c).getLong("awake_ms", 0L) / n
    }

    // "Alarm clock" alarm: not delayed by Doze or battery optimizers, no extra battery use.
    private fun schedule(c: Context, minutes: Int, at: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(c, minutes, at))
        prefs(c).edit().putLong("next_at", at).apply()
        SalliLog.log(
            c, "SCHED",
            "SCHEDULED next=${SalliLog.time(at)} in=${(at - System.currentTimeMillis()) / 1000}s interval=${minutes}min"
        )
    }
}
