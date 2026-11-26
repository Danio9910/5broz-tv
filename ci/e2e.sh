#!/usr/bin/env bash
# Walks through the app like a person: home -> title -> play (torrent), with screenshots.
PKG=com.fivebroz.android
OUT=e2e
mkdir -p $OUT
exec > >(tee $OUT/run.txt) 2>&1
set -x
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Skip the access-code screen (code is a secret) by pre-seeding the app's settings.
cat > /tmp/5broz.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="gate" value="true" />
    <string name="pid">guest</string>
    <string name="pname">اختبار</string>
</map>
XML
adb push /tmp/5broz.xml /data/local/tmp/5broz.xml
adb shell "run-as $PKG mkdir -p shared_prefs"
adb shell "cat /data/local/tmp/5broz.xml | run-as $PKG sh -c 'cat > shared_prefs/5broz.xml'"

shot() { adb exec-out screencap -p > $OUT/$1.png; }
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null
  local xy; xy=$(python3 ci/find.py /tmp/ui.xml "$1")
  echo "tap '$1' -> $xy"
  [ -n "$xy" ] && adb shell input tap $xy
}

adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 20; shot 1-home
# Find a well-known, released film through search.
tap_text "بحث"
sleep 3
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml $OUT/ui-search.xml
tap_text "@edit"
sleep 2; shot 2a-after-tap
adb shell input text "Shawshank"
sleep 8; shot 2-search
adb shell input keyevent 111   # hide keyboard
sleep 1
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml $OUT/ui-results.xml
tap_text "@poster"
sleep 15; shot 2b-detail
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml $OUT/ui-detail.xml
tap_text "تشغيل"
sleep 30; shot 3-player-30s
sleep 45; shot 4-player-75s
sleep 45; shot 5-player-120s
echo "--- app log ---" > $OUT/logcat.txt
adb logcat -d -s 5broz:V ExoPlayerImpl:V AndroidRuntime:E >> $OUT/logcat.txt
grep -q "PLAYER READY" $OUT/logcat.txt && echo "E2E: PLAYER READY" || echo "E2E: player never became ready"
exit 0
