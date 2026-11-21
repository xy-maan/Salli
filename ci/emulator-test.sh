#!/usr/bin/env bash
# Runs inside the Android emulator job. Uses REAL alarms (1-minute interval) and checks
# the app's own log (files/salli.log) plus Android's system dumps.
set -u
PKG=com.example.soundrepeater
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=ci-output
mkdir -p "$OUT"; : > "$OUT/summary.txt"
FAIL=0
say() { echo "$*" | tee -a "$OUT/summary.txt"; }
ok()  { say "PASS  $*"; }
bad() { say "FAIL  $*"; FAIL=1; }
getlog() { adb exec-out run-as "$PKG" cat /data/user_de/0/$PKG/files/salli.log 2>/dev/null | tr -d '\r'; }
fired()  { getlog | grep -c "\[ALARM\] FIRED"; }

say "== Salli emulator test $(date -u +%FT%TZ) =="
adb install -r "$APK" >/dev/null || { bad "APK install failed"; exit 1; }

# ---- Phase 1: reminder keeps going after the app is closed --------------------------
adb shell am start -n "$PKG/.MainActivity" --ei debug_start_minutes 1 >/dev/null
sleep 8
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am kill "$PKG"   # like swiping the app away from recents
say "phase 1: 1-minute reminder started, app closed and its process killed"
sleep 185
n1=$(fired); n1=${n1:-0}
if [ "$n1" -ge 2 ]; then ok "phase 1: $n1 chimes while the app was closed (need >= 2)"; else bad "phase 1: only $n1 chimes (need >= 2)"; fi

# ---- Phase 2: same thing in deep Doze (the situation that delayed chimes before) ----
adb shell dumpsys battery unplug >/dev/null
adb shell dumpsys deviceidle enable >/dev/null 2>&1
adb shell dumpsys deviceidle force-idle > "$OUT/doze.txt" 2>&1
say "phase 2: forced deep Doze -> $(tr '\n' ' ' < "$OUT/doze.txt" | head -c 100)"
sleep 185
n2=$(fired); n2=${n2:-0}
d=$((n2 - n1))
if [ "$d" -ge 2 ]; then ok "phase 2: $d chimes during Doze (need >= 2)"; else bad "phase 2: only $d chimes during Doze (need >= 2)"; fi
adb shell dumpsys deviceidle unforce >/dev/null 2>&1
adb shell dumpsys battery reset >/dev/null 2>&1

# ---- Timing: every chime must be on time -------------------------------------------
getlog > "$OUT/salli.log"
maxlate=$(grep "\[ALARM\] FIRED" "$OUT/salli.log" | sed -n 's/.*late=\(-\{0,1\}[0-9]*\)ms.*/\1/p' | sort -n | tail -1)
maxlate=${maxlate:-999999}
if [ "$maxlate" -lt 20000 ]; then ok "timing: worst lateness ${maxlate} ms (limit 20000)"; else bad "timing: worst lateness ${maxlate} ms (limit 20000)"; fi

# ---- Interval must never change on its own -----------------------------------------
bad_iv=$(grep "\[ALARM\] FIRED" "$OUT/salli.log" | grep -vc "interval=1min")
if [ "$bad_iv" -eq 0 ]; then ok "interval: every chime used exactly 1 min"; else bad "interval: $bad_iv chimes used another interval"; fi

# ---- Battery checks ----------------------------------------------------------------
maxawake=$(grep "\[PLAY\] done" "$OUT/salli.log" | sed -n 's/.*awake=\([0-9]*\)ms.*/\1/p' | sort -n | tail -1)
maxawake=${maxawake:-0}
if [ "$maxawake" -lt 9500 ]; then ok "battery: awake at most ${maxawake} ms per chime (bounded by the 9 s cutoff)"; else bad "battery: awake ${maxawake} ms for one chime"; fi

adb shell dumpsys activity services "$PKG" > "$OUT/services.txt" 2>&1
if grep -q "ServiceRecord" "$OUT/services.txt"; then bad "battery: app has a running service"; else ok "battery: no service running"; fi

UID_APP=$(adb shell dumpsys package "$PKG" | grep -m1 -o 'userId=[0-9]*' | cut -d= -f2)
held=1
for i in 1 2 3; do
  adb shell dumpsys power > "$OUT/power.txt" 2>&1
  if ! grep "WAKE_LOCK" "$OUT/power.txt" | grep -q "uid=$UID_APP"; then held=0; break; fi
  sleep 5
done
if [ "$held" -eq 0 ]; then ok "battery: no wake lock held between chimes"; else bad "battery: wake lock still held between chimes"; fi

adb shell dumpsys alarm > "$OUT/alarm.txt" 2>&1

# ---- Phase 3: Stop really stops ----------------------------------------------------
adb shell am start -n "$PKG/.MainActivity" --ez debug_stop true >/dev/null
sleep 2
n3=$(fired); n3=${n3:-0}
sleep 130
n4=$(fired); n4=${n4:-0}
if [ "$n4" -eq "$n3" ]; then ok "stop: no chimes after pressing Stop"; else bad "stop: $((n4 - n3)) chimes after Stop"; fi

# ---- Phase 4: a restart restores the reminder without opening the app --------------
adb shell am start -n "$PKG/.MainActivity" --ei debug_start_minutes 1 >/dev/null
sleep 5
b0=$(getlog | grep -c "\[BOOT\]"); n5=$(fired)
adb reboot
adb wait-for-device
for i in $(seq 1 60); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done
sleep 100
b1=$(getlog | grep -c "\[BOOT\]"); n6=$(fired)
if [ "$b1" -gt "$b0" ]; then ok "phase 4: boot broadcast received after restart"; else bad "phase 4: no boot broadcast after restart"; fi
if [ "$n6" -gt "$n5" ]; then ok "phase 4: chime played after restart without opening the app"; else bad "phase 4: no chime after restart"; fi
adb shell am start -n "$PKG/.MainActivity" --ez debug_stop true >/dev/null

getlog > "$OUT/salli.log"
say "== done: $([ $FAIL -eq 0 ] && echo ALL PASSED || echo SOME FAILED) =="
exit $FAIL
