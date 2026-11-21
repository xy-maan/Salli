package com.example.soundrepeater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val t0 = SystemClock.elapsedRealtime()
        val app = context.applicationContext

        if (!Scheduler.isEnabled(app)) {
            SalliLog.log(app, "ALARM", "fired but reminder is OFF, ignoring")
            return
        }

        val planned = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)
        val minutes = intent.getIntExtra(Scheduler.EXTRA_MINUTES, Scheduler.minutes(app))
        val late = if (planned > 0) System.currentTimeMillis() - planned else -1L
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val playInSilent = Scheduler.playInSilent(app)
        SalliLog.log(
            app, "ALARM",
            "FIRED planned=${SalliLog.time(planned)} late=${late}ms interval=${minutes}min " +
                "playInSilent=$playInSilent ${SalliLog.state(app)}"
        )

        // Schedule the next one first, using the interval stored in this very alarm.
        Scheduler.scheduleAfter(app, minutes, planned)

        // Silent/vibrate and the option is off: stay on schedule, just don't make a sound.
        val ringerNormal = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
        if (!ScheduleMath.shouldPlay(playInSilent, ringerNormal)) {
            SalliLog.log(app, "PLAY", "SKIPPED phone is silent/vibrate (ringer=${audio.ringerMode}) and play-in-silent is OFF")
            return
        }

        val resId = app.resources.getIdentifier("chime", "raw", app.packageName)
        if (resId == 0) {
            SalliLog.log(app, "PLAY", "chime resource missing (res/raw/chime.*)")
            return
        }

        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).build()

        var player: MediaPlayer? = null
        var done = false
        fun finish(reason: String) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            try { player?.release() } catch (_: Exception) {}
            try { audio.abandonAudioFocusRequest(focus) } catch (_: Exception) {}
            val awake = SystemClock.elapsedRealtime() - t0
            SalliLog.log(app, "PLAY", "done reason=$reason awake=${awake}ms ${Scheduler.recordAwake(app, awake)}")
            try { pending.finish() } catch (_: Exception) {} // releases the CPU wake-lock
        }

        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attrs)
            app.resources.openRawResourceFd(resId).use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnCompletionListener { finish("completed") }
            mp.setOnErrorListener { _, what, extra ->
                SalliLog.log(app, "PLAY", "ERROR what=$what extra=$extra")
                finish("error")
                true
            }
            mp.prepare()
            val focusResult = audio.requestAudioFocus(focus)
            SalliLog.log(app, "PLAY", "starting focus=${if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "granted" else "denied($focusResult)"}")
            mp.start()
            handler.postDelayed({ finish("timeout8s") }, 8000) // safety cutoff (foreground broadcasts must end within 10 s)
        } catch (e: Exception) {
            SalliLog.log(app, "PLAY", "EXCEPTION ${e.javaClass.simpleName}: ${e.message}")
            finish("exception")
        }
    }
}
