#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-output
adb install -r dist/GymGlow_v1.5.apk
adb logcat -c
adb shell am start -W -n com.aess.gymflow.test/com.aess.gymflow.MainActivity | tee smoke-output/launch.txt
sleep 5
adb shell pidof com.aess.gymflow.test > smoke-output/pid.txt
adb shell uiautomator dump /sdcard/gymflow-window.xml
adb pull /sdcard/gymflow-window.xml smoke-output/window.xml
adb exec-out screencap -p > smoke-output/onboarding.png
# Force-stop/relaunch exercises persistent state loading and activity startup again.
adb shell am force-stop com.aess.gymflow.test
adb shell am start -W -n com.aess.gymflow.test/com.aess.gymflow.MainActivity > smoke-output/relaunch.txt
sleep 3
adb shell pidof com.aess.gymflow.test >> smoke-output/pid.txt
adb logcat -b crash -d > smoke-output/crash.txt
if grep -q 'com.aess.gymflow.test' smoke-output/crash.txt; then
  cat smoke-output/crash.txt
  exit 1
fi
grep -q 'com.aess.gymflow.test' smoke-output/window.xml
printf '%s\n' ANDROID_STARTUP_SMOKE_OK
