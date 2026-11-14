package com.example.soundrepeater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Scheduler.isEnabled(context)) Scheduler.scheduleNext(context)
    }
}
