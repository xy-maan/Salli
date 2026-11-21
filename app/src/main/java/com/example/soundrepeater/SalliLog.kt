package com.example.soundrepeater

import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny file logger for debugging. One line per event in files/salli.log.
 * It only writes when something happens (a few lines per chime), so it costs
 * essentially nothing. The file is capped at ~200 KB (oldest half is dropped).
 */
object SalliLog {
    private const val FILE = "salli.log"
    private const val MAX_BYTES = 200_000L
    private val lock = Any()

    fun file(c: Context) = File(c.applicationContext.filesDir, FILE)

    fun log(c: Context, tag: String, msg: String) {
        try {
            synchronized(lock) {
                val f = file(c)
                if (f.length() > MAX_BYTES) trim(f)
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                f.appendText("$ts [$tag] $msg\n")
            }
        } catch (_: Exception) {
            // logging must never crash the app
        }
    }

    private fun trim(f: File) {
        val text = f.readText()
        val cut = text.indexOf('\n', text.length / 2)
        f.writeText(if (cut >= 0) text.substring(cut + 1) else "")
    }

    /** Last [maxChars] characters of the log. */
    fun read(c: Context, maxChars: Int = 80_000): String = try {
        synchronized(lock) {
            val f = file(c)
            if (!f.exists()) "" else f.readText().let { if (it.length > maxChars) it.takeLast(maxChars) else it }
        }
    } catch (_: Exception) { "" }

    fun clear(c: Context) {
        try { synchronized(lock) { file(c).delete() } } catch (_: Exception) {}
    }

    fun time(ms: Long): String =
        if (ms <= 0) "n/a" else SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ms))

    fun clock(ms: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    /**
     * Snapshot of the things that decide whether/how a chime is heard or delayed.
     * ringer: 0 silent, 1 vibrate, 2 normal.  bucket: 10 active, 20 working set,
     * 30 frequent, 40 rare, 45 restricted.
     */
    fun state(c: Context): String {
        val app = c.applicationContext
        val p = mutableListOf<String>()
        try {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            p += "doze=${pm.isDeviceIdleMode}"
            p += "powerSave=${pm.isPowerSaveMode}"
            p += "batteryOptIgnored=${pm.isIgnoringBatteryOptimizations(app.packageName)}"
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            p += "alarmVol=${am.getStreamVolume(AudioManager.STREAM_ALARM)}/${am.getStreamMaxVolume(AudioManager.STREAM_ALARM)}"
            p += "ringer=${am.ringerMode}"
            val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            p += "bat=${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            p += "charging=${bm.isCharging}"
            if (Build.VERSION.SDK_INT >= 28) {
                val us = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                p += "bucket=${us.appStandbyBucket}"
            }
        } catch (e: Exception) {
            p += "stateError=${e.javaClass.simpleName}"
        }
        return p.joinToString(" ")
    }
}
