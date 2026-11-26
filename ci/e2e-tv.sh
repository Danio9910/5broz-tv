#!/usr/bin/env bash
# Android TV emulator: drive the app with the remote's arrow keys, with screenshots.
PKG=com.fivebroz.android
OUT=e2e-tv
mkdir -p $OUT
exec > >(tee $OUT/run.txt) 2>&1
set -x
adb install -r app/build/outputs/apk/debug/app-debug.apk
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
key() { adb shell input keyevent $1; sleep ${2:-1}; }

adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 25; shot tv1-home
key DPAD_LEFT; key DPAD_LEFT 2; shot tv2-moved-in-row
key DPAD_DOWN 2; shot tv3-next-row
key DPAD_CENTER 10; shot tv4-detail
key BACK 3
key DPAD_RIGHT; key DPAD_RIGHT; key DPAD_RIGHT 2; shot tv5-rail
adb logcat -d -s 5broz:V AndroidRuntime:E > $OUT/logcat.txt; echo "--- end ---" >> $OUT/logcat.txt
exit 0
