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
- **Survives restarts:** after a reboot or app update, the reminder resumes automatically if it was enabled, even before you type the PIN (Direct Boot). The first chime comes one full interval after the phone finishes booting.
- **Start / Stop:** one tap to enable or disable.
- **Silent-mode switch:** choose whether chimes play when the phone is on silent or vibrate. When off, the schedule keeps running, so the next audible chime lands on the original timing (started at 10:10:10 every minute, back on sound at 10:35:34, next chime at 10:36:10).
- **Live updates:** changing the interval while running applies immediately.
- **Minimal UI:** dark blue and gold design, a single screen, no ads, no accounts, no internet.
- **Built-in debug log:** records every alarm, chime and error (see *Debugging and testing*).

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
.github/workflows/    # build + unit tests, emulator test
ci/emulator-test.sh     # script run by the emulator test
app/src/test/           # unit tests
app/src/main/
├── java/com/example/soundrepeater/
│   ├── MainActivity.kt     # UI + log tools
│   ├── Scheduler.kt        # alarm + saved settings
│   ├── ScheduleMath.kt     # pure timing math (unit-tested)
│   ├── SalliLog.kt         # debug log file
│   ├── Storage.kt          # settings/log storage readable before the first unlock
│   ├── ChimeReceiver.kt    # plays the sound, chains the next alarm
│   └── BootReceiver.kt     # restores the alarm after reboot
├── res/
│   ├── raw/chime.*         # the fixed sound
│   ├── drawable/           # icon foreground
│   ├── mipmap-anydpi-v26/  # adaptive launcher icon
│   └── values/             # colors, theme, strings
└── AndroidManifest.xml
```

## Debugging and testing

**1. The log file (on the phone).** The app writes every event to `salli.log` (private app storage, capped at about 200 KB). In the app use **Copy log** (puts it on the clipboard, ready to paste into a chat), **Share log**, or **Clear log**. The screen also shows the next chime time and the average time the phone stayed awake per chime.

Example lines and what they mean:

```
[SCHED] START requested=5min using=5min
[SCHED] SCHEDULED next=14:05:00.000 in=300s interval=5min
[ALARM] FIRED planned=14:05:00.000 late=212ms interval=5min doze=false powerSave=false batteryOptIgnored=true alarmVol=5/7 ringer=1 bat=83% charging=false bucket=10
[PLAY] starting focus=granted
[PLAY] done reason=completed awake=4870ms chimes=1 totalAwake=4870ms avgAwake=4870ms
```

| Field | Meaning |
| --- | --- |
| `late` | How many ms after the planned time the alarm fired. Healthy: under a few seconds. |
| `interval` | The interval carried by the alarm itself. It must never change by itself. |
| `doze` / `powerSave` | Whether Android was in deep sleep or battery saver when the alarm fired. |
| `batteryOptIgnored` | `true` means the app is set to "Don't optimize". |
| `alarmVol` / `ringer` | Alarm volume (must be above 0) and ringer mode (0 silent, 1 vibrate, 2 normal). |
| `bucket` | Android standby bucket: 10 active, 20 working set, 30 frequent, 40 rare. |
| `awake` | Milliseconds the app kept the phone awake for this chime (should be about the clip length). |
| `[PLAY] ERROR / EXCEPTION` | The sound failed to play, with the reason. |
| `[PLAY] SKIPPED` | The phone was silent/vibrate and the silent-mode switch was off. |
| `playInSilent` | State of the silent-mode switch when the alarm fired. |
| `[BOOT]` | The reminder was restored after a restart (`LOCKED_BOOT_COMPLETED` = before the PIN, `BOOT_COMPLETED` = after it) or an app update. |

**2. Unit tests (automatic, every push).** `ScheduleMathTest` checks the timing arithmetic: no drift over 1000 chimes, missed chimes after a power-off, clamping of silly values, never scheduling in the past. They run in the `unit-tests` job of the *Build APK* workflow, a separate job, so a failing test never blocks the APK. The report is attached to the run as `unit-test-report`.

**3. Emulator test (real alarms, manual).** Actions tab, then *Emulator test*, then *Run workflow*. It installs the debug APK on an Android emulator and runs about 10 minutes of real alarms at a 1-minute interval:

| Phase | Checks |
| --- | --- |
| 1 | Chimes keep coming after the app is closed and its process killed |
| 2 | Chimes keep coming in forced deep Doze, with timing within 20 s |
| every chime | The interval is exactly the chosen one |
| battery | Awake time per chime is bounded, no service is running, no wake lock is held between chimes |
| 3 | Pressing Stop really stops the chimes |

The result appears on the run page and the full log is attached as `emulator-test-output`. The emulator test is still a simulation: it cannot reproduce Samsung's own battery manager, so keep using the in-app log on the real phone.

The activity also accepts two test hooks in debug builds only: `--ei debug_start_minutes N` and `--ez debug_stop true`.

## Battery audit

Checked in the code and the manifest:

| Check | Result |
| --- | --- |
| Services declared | **None** |
| Foreground service, notification, JobScheduler, WorkManager, timers, threads, polling loops | **None** |
| Code that runs between chimes | **None**; the only trigger is one system alarm at a time |
| Wake lock | Only during playback, with a hard 8-second cutoff; released on completion, error or exception |
| Audio player | Created per chime, released right after |
| Boot receiver | Runs once per restart or app update |
| Logging | A few small appends per chime; no background work |
| Skipped chimes (silent mode, switch off) | The alarm still fires, but nothing plays and no wake lock is taken, so it costs a few milliseconds |
| Permissions | Boot, exact alarm, wake lock only; no internet, location or storage |

Rough cost: the phone is awake for about as long as the clip plays, plus a fraction of a second. With a roughly 5-second chime that is about 24 minutes of CPU wake time per day at 5-minute intervals, 12 minutes at 10, and 8 minutes at 15. A 1-minute interval is for testing only. The real value is measured by the app itself: see *avg awake per chime* on the main screen and `awake=` in the log. After a day of use, check Settings, then Battery, to see Salli's share.

## Known limitations

- If the app is **force-stopped** from system settings, Android cancels its alarms until the app is opened again. A normal swipe-away from recents is fine.
- Some manufacturers (Xiaomi, Huawei, Oppo, Samsung) restrict background apps aggressively. If chimes stop, set the app's battery mode to **Unrestricted**.
- The sound plays on the **media volume** and follows silent mode.
- The clip should be shorter than about 8 seconds.

## License

Personal project. Use and modify freely.
