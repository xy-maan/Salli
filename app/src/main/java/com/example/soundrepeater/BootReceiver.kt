package com.example.soundrepeater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val enabled = Scheduler.isEnabled(app)
        SalliLog.log(app, "BOOT", "received ${intent.action} enabled=$enabled")
        if (enabled) Scheduler.resume(app)
    }
}
