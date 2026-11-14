# Salli | صلِّ

<p align="center">
  <img src="icon-preview.png" alt="Salli icon" width="120">
</p>

**Salli** is a tiny, battery-friendly Android app that plays a short, fixed sound at a regular interval you choose, as a gentle reminder to send blessings upon the Prophet ﷺ (*صلِّ على محمد*).

Set the interval once and forget about it. The reminder keeps running in the background, survives reboots, and uses almost no battery.

---

## Features

- **One simple job:** play a sound every X minutes (5, 10, 15, 30, 60, or any custom value).
- **Fixed built-in sound:** no sound picker and no dependence on the phone's ringtone.
- **Survives restarts:** after a reboot or app update, the reminder resumes automatically if it was enabled.
- **Start / Stop:** one tap to enable or disable.
- **Live updates:** changing the interval while running applies immediately.
- **Minimal UI:** dark blue and gold design, a single screen, no ads, no accounts, no internet.

## Battery-first design

Battery usage was the main design goal, so Salli deliberately avoids what makes most reminder apps drain power:

| Common approach | Salli |
| --- | --- |
| Foreground service running all day | **No service at all** |
| Persistent notification | **None** |
| Timer or loop keeping the CPU awake | **System alarm only**, the app is not running between chimes |
| Player kept in memory | Player created and released on every play |
| Wake-lock held for long periods | Held **only while the clip plays** (a few seconds) |

Between chimes the app uses effectively no CPU, memory, or battery. It wakes up, plays the clip, schedules the next alarm, and goes back to sleep.

## How it works

1. **`Scheduler`** stores the interval and on/off state in `SharedPreferences` and sets the next alarm with `AlarmManager.setExactAndAllowWhileIdle`, which fires even in Doze mode.
2. **`ChimeReceiver`** is triggered by the alarm. It schedules the next alarm first, so timing never drifts. It then plays `res/raw/chime` through `MediaPlayer`, briefly ducking other audio, and releases everything when the clip ends.
3. **`BootReceiver`** listens for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` and re-arms the alarm if the reminder was enabled.
4. **`MainActivity`** is the single screen, built in code with no XML layouts and no third-party libraries.

## Permissions

| Permission | Why |
| --- | --- |
| `RECEIVE_BOOT_COMPLETED` | Resume the reminder after a restart |
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Fire on time, even in Doze |
| `WAKE_LOCK` | Keep the CPU awake only while the clip plays |

No internet, storage, notification, or location permissions are used.

## Tech

- Kotlin, plain Android SDK (no AndroidX, no dependencies)
- Min SDK 26 (Android 8.0), target SDK 34
- Adaptive vector icon, with no bitmap assets

## Project structure

```
app/src/main/
├── java/com/example/soundrepeater/
│   ├── MainActivity.kt     # UI
│   ├── Scheduler.kt        # alarm + saved settings
│   ├── ChimeReceiver.kt    # plays the sound, chains the next alarm
│   └── BootReceiver.kt     # restores the alarm after reboot
├── res/
│   ├── raw/chime.*         # the fixed sound
│   ├── drawable/           # icon foreground
│   ├── mipmap-anydpi-v26/  # adaptive launcher icon
│   └── values/             # colors, theme, strings
└── AndroidManifest.xml
```

## Known limitations

- If the app is **force-stopped** from system settings, Android cancels its alarms until the app is opened again. A normal swipe-away from recents is fine.
- Some manufacturers (Xiaomi, Huawei, Oppo, Samsung) restrict background apps aggressively. If chimes stop, set the app's battery mode to **Unrestricted**.
- The sound plays on the **media volume** and follows silent mode.
- The clip should be shorter than about 9 seconds.

## License

Personal project. Use and modify freely.
