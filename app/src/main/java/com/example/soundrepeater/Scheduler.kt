package com.example.soundrepeater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_AT = "at"
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 1440

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun minutes(c: Context) = prefs(c).getInt("minutes", 5)

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
        val m = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        prefs(c).edit().putInt("minutes", m).putBoolean("enabled", true).commit()
        schedule(c, m, System.currentTimeMillis() + m * 60_000L)
    }

    fun stop(c: Context) {
        prefs(c).edit().putBoolean("enabled", false).commit()
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(c, 0, 0))
    }

    /** After a reboot or update: start counting again with the saved interval. */
    fun resume(c: Context) {
        val m = minutes(c).coerceIn(MIN_MINUTES, MAX_MINUTES)
        schedule(c, m, System.currentTimeMillis() + m * 60_000L)
    }

    /** After a chime: next time = previous planned time + interval (no drift). */
    fun scheduleAfter(c: Context, minutes: Int, previousAt: Long) {
        val m = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val step = m * 60_000L
        val now = System.currentTimeMillis()
        var next = if (previousAt > 0) previousAt + step else now + step
        if (next <= now) next += ((now - next) / step + 1) * step
        schedule(c, m, next)
    }

    // "Alarm clock" alarm: not delayed by Doze or battery optimizers, no extra battery use.
    private fun schedule(c: Context, minutes: Int, at: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(c, minutes, at))
    }
}
