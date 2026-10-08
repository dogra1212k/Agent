#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-checks
adb install -r dist/Agent-Voice-1.0.0.apk
adb install -r dist/app-debug-androidTest.apk
adb shell pm grant com.dogra.agent android.permission.RECORD_AUDIO
adb shell pm grant com.dogra.agent android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am instrument -w com.dogra.agent.test/android.test.InstrumentationTestRunner | tee device-checks/instrumentation.txt
grep -Eq 'OK \([0-9]+ tests?\)' device-checks/instrumentation.txt
adb shell am start -W -n com.dogra.agent/.MainActivity
adb shell uiautomator dump /sdcard/agent-window.xml
adb pull /sdcard/agent-window.xml device-checks/window.xml
adb exec-out screencap -p > device-checks/home.png
adb logcat -d > device-checks/logcat.txt
if grep -q 'FATAL EXCEPTION' device-checks/logcat.txt; then
  echo 'A fatal exception occurred during smoke checks.'
  exit 1
fi
