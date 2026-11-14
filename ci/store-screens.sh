#!/usr/bin/env bash
# Runs inside the emulator step of .github/workflows/store-assets.yml. Sets the emulator up like a
# tidy phone (Indian time, a date late in the month, gesture navigation), then runs
# StoreScreenshots#capture (Play screenshots) and StoreScreenshots#tour while recording the screen.
# Everything lands in store-screens/.
#   $1: emulator date as MMDDhhmmYYYY (empty keeps the real date)
#   $2: "true" for the dark theme
#   $4: font scale (1.0 normal); $5: screen density for the display size (empty keeps the profile's).
#       Any other text or display size skips the tour.
#   $3: "sms-demo" for the SMS permission walkthrough (StoreScreenshots#smsDemo) instead, saved in
#       store-screens/ too, for tools/make_sms_demo_video.py
set -uo pipefail
DATE="${1:-}"
NIGHT="${2:-false}"
WHAT="${3:-screenshots}"
FONT_SCALE="${4:-1.0}"
DENSITY="${5:-}"
OUT=store-screens
mkdir -p "$OUT" logs
PKG=com.owl.tracker
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
CLASS=$PKG.store.StoreScreenshots
status=0

# Same readiness check as ci/device-tests.sh: three good checks in a row.
wait_ready() {
  adb wait-for-device
  ok=0
  for i in $(seq 1 120); do
    boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$boot" = "1" ] \
      && adb shell dumpsys user 2>/dev/null | grep -q "RUNNING_UNLOCKED" \
      && adb shell pm path android >/dev/null 2>&1 \
      && adb shell content query --uri content://settings/system --projection name 2>/dev/null | grep -q "Row:"; then
      ok=$((ok + 1))
      if [ "$ok" -ge 3 ]; then
        echo "Device ready after about $((i * 10)) s"
        adb shell input keyevent 82 >/dev/null 2>&1 || true
        return 0
      fi
    else
      ok=0
    fi
    sleep 10
  done
  echo "Device still not stable after 20 minutes; continuing anyway"
}

ran_ok() { # instrumentation output says the test passed and was not skipped
  grep -q "^OK (1 test)" "$1" && ! grep -q "INSTRUMENTATION_STATUS_CODE: -4" "$1"
}

wait_ready 2>&1 | tee logs/ready.log

echo "== Clock, time zone and navigation"
adb root >/dev/null 2>&1 || true
sleep 3
wait_ready >/dev/null 2>&1
adb shell settings put global auto_time 0 || true
adb shell settings put global auto_time_zone 0 || true
adb shell setprop persist.sys.timezone Asia/Kolkata || true
[ -n "$DATE" ] && { adb shell "date $DATE.00" || echo "could not set the date; keeping the real one"; }
adb shell date
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
if [ "$NIGHT" = "true" ]; then adb shell cmd uimode night yes || true; else adb shell cmd uimode night no || true; fi
# Text and display size, as a person with big text set on their phone would have them
adb shell settings put system font_scale "$FONT_SCALE" || true
[ -n "$DENSITY" ] && { adb shell wm density "$DENSITY" || true; }
echo "font_scale=$(adb shell settings get system font_scale | tr -d '\r') density=$(adb shell wm density | tr -d '\r' | tr '\n' ' ')"
# A slow emulator can show "<app> isn't responding" over every screenshot: hide such dialogs, and close any already up
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
sleep 15
wait_ready >/dev/null 2>&1

echo "== Install"
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

if [ "$WHAT" = "sms-demo" ]; then
  echo "== SMS permission walkthrough"
  adb logcat -c || true
  adb shell am instrument -w -r -e storeShots 1 -e class "$CLASS#seedInbox" "$RUNNER" 2>&1 | tee logs/seed.log
  ran_ok logs/seed.log || { echo "::error::StoreScreenshots#seedInbox did not pass"; status=1; }
  # Installed with -g, so take READ_SMS away again: the walkthrough must show Android's own prompt
  adb shell pm revoke $PKG android.permission.READ_SMS || true
  adb shell pm clear-permission-flags $PKG android.permission.READ_SMS user-set user-fixed >/dev/null 2>&1 || true
  sleep 3
  adb shell am instrument -w -r -e storeShots 1 -e class "$CLASS#smsDemo" "$RUNNER" 2>&1 | tee logs/sms-demo.log
  ran_ok logs/sms-demo.log || { echo "::error::StoreScreenshots#smsDemo did not pass"; status=1; }
  adb logcat -d -s OwlStoreShots:* TestRunner:* AndroidRuntime:E > logs/sms-demo-logcat.txt 2>&1 || true
  adb pull /sdcard/Android/data/$PKG/files/sms-demo/. "$OUT/" || echo "::warning::Could not pull the walkthrough"
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  ls -la "$OUT"
  exit $status
fi

echo "== Screenshots"
adb logcat -c || true
# Time limits: once, adb stayed stuck after the test had finished, until the job's own hour ran out
timeout 1800 adb shell am instrument -w -r -e storeShots 1 -e class "$CLASS#capture" "$RUNNER" 2>&1 | tee logs/capture.log
ran_ok logs/capture.log || { echo "::error::StoreScreenshots#capture did not pass"; status=1; }
timeout 120 adb logcat -d -s OwlStoreShots:* TestRunner:* AndroidRuntime:E OwlScene:* OwlHistory:* chromium:W > logs/capture-logcat.txt 2>&1 || true
# Steps that were skipped, here in the log rather than only in the artifact
grep -E " [WE] (OwlStoreShots|OwlHistory)" logs/capture-logcat.txt | head -60 || true
timeout 300 adb pull /sdcard/Android/data/$PKG/files/store-screens/. "$OUT/" || echo "::warning::Could not pull screenshots"
ls -la "$OUT"

if [ "$FONT_SCALE" != "1.0" ] || [ -n "$DENSITY" ]; then
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  exit $status
fi

echo "== Tour, recorded"
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell screenrecord --size 720x1600 --bit-rate 8000000 --time-limit 170 /sdcard/tour.mp4 &
REC=$!
sleep 2
timeout 900 adb shell am instrument -w -r -e storeShots 1 -e class "$CLASS#tour" "$RUNNER" 2>&1 | tee logs/tour.log
ran_ok logs/tour.log || echo "::warning::StoreScreenshots#tour did not pass"
sleep 1
adb shell pkill -2 screenrecord || kill -INT "$REC" || true
wait "$REC" 2>/dev/null || true
sleep 3
timeout 300 adb pull /sdcard/tour.mp4 "$OUT/tour.mp4" || echo "::warning::No screen recording"

adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
ls -la "$OUT"
exit $status
