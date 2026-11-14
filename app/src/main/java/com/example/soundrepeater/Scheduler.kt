package com.example.soundrepeater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun minutes(c: Context) = prefs(c).getInt("minutes", 5)

    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, ChimeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun start(c: Context, minutes: Int) {
        prefs(c).edit().putInt("minutes", minutes).putBoolean("enabled", true).apply()
        scheduleNext(c)
    }

    fun stop(c: Context) {
        prefs(c).edit().putBoolean("enabled", false).apply()
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(c))
    }

    fun scheduleNext(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + minutes(c) * 60_000L
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c))
        }
    }
}
