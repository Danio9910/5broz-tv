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
    <string name="test_addon">https://www.5broz.com/addon-test</string>
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
key MENU 3; shot tv3b-item-menu
key BACK 2
key DPAD_CENTER 10; shot tv4-detail
key DPAD_DOWN; key DPAD_DOWN; key DPAD_DOWN 2; shot tv4b-detail-lower
key BACK 3
key DPAD_RIGHT; key DPAD_RIGHT; key DPAD_RIGHT 2; shot tv5-rail
# Play a film for a minute, leave, then the TV home screen's "Continue watching" should list it.
adb shell am start -a android.intent.action.VIEW -d "fivebroz://title/movie/tt0111161" $PKG
sleep 12; shot tv6-deeplink
key DPAD_CENTER 70; shot tv7-player
key BACK 1; key BACK 3
adb shell content query --uri content://android.media.tv/watch_next_program --projection title:last_playback_position_millis:intent_uri > $OUT/watchnext.txt 2>&1
echo "--- end ---" >> $OUT/watchnext.txt
adb logcat -d -s 5broz:V | grep -E "TIMING|PLAYER READY|race|player error" | sed "s|^|movie/tt0111161 |" > $OUT/timing.txt
# Time-to-picture on more titles: a classic, a recent film and a series.
for T in movie/tt15239678 series/tt0903747 movie/tt1375666; do
  adb shell am force-stop $PKG; sleep 2
  adb logcat -c
  adb shell am start -a android.intent.action.VIEW -d "fivebroz://title/$T" $PKG
  sleep 10; key DPAD_CENTER 1
  for i in $(seq 1 45); do adb logcat -d -s 5broz:V | grep -q "PLAYER READY" && break; sleep 2; done
  N=$(echo $T | tr / -); shot tv8-$N
  adb logcat -d -s 5broz:V | grep -E "TIMING|PLAYER READY|race|player error" | sed "s|^|$T |" >> $OUT/timing.txt
done
echo end >> $OUT/timing.txt
adb logcat -d -s 5broz:V AndroidRuntime:E > $OUT/logcat.txt; echo "--- end ---" >> $OUT/logcat.txt
exit 0
