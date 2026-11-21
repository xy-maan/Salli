package com.example.soundrepeater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val enabled = Scheduler.isEnabled(app)
        SalliLog.log(app, "BOOT", "received ${intent.action} enabled=$enabled")
        if (!enabled) return
        // BOOT_COMPLETED comes after the PIN and may follow LOCKED_BOOT_COMPLETED: don't restart
        // the timer twice. After an app update the old alarm is gone, so always re-arm then.
        val second = intent.action == Intent.ACTION_BOOT_COMPLETED
        Scheduler.resume(app, skipIfArmed = second)
    }
}
