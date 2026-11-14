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

class ChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Scheduler.isEnabled(context)) return

        // Schedule the next one first, so timing never drifts.
        Scheduler.scheduleNext(context)

        val app = context.applicationContext
        val resId = app.resources.getIdentifier("chime", "raw", app.packageName)
        if (resId == 0) return // sound file missing

        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).build()

        var player: MediaPlayer? = null
        var done = false
        fun finish() {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            try { player?.release() } catch (_: Exception) {}
            audio.abandonAudioFocusRequest(focus)
            pending.finish() // releases the CPU wake-lock immediately
        }

        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attrs)
            app.resources.openRawResourceFd(resId).use {
                mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepare()
            audio.requestAudioFocus(focus)
            mp.start()
            handler.postDelayed({ finish() }, 9000) // safety cutoff
        } catch (e: Exception) {
            finish()
        }
    }
}
