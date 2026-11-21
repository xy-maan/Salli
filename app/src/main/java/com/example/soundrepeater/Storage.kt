package com.example.soundrepeater

import android.content.Context

/**
 * Settings and the log live in "device-protected" storage, which Android lets apps read
 * right after a restart, BEFORE the user types the PIN/password. That is what allows the
 * reminder to resume as soon as the phone has booted.
 */
object Storage {
    @Volatile private var migrated = false

    fun ctx(c: Context): Context {
        val app = c.applicationContext
        val dp = app.createDeviceProtectedStorageContext()
        if (!migrated) {
            synchronized(this) {
                if (!migrated) {
                    try {
                        // One-time move of the settings saved by older versions.
                        dp.moveSharedPreferencesFrom(app, "settings")
                        migrated = true
                    } catch (_: Exception) {
                        // Old storage is still locked (before first unlock): try again later.
                    }
                }
            }
        }
        return dp
    }
}
