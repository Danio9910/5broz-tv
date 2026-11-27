#!/usr/bin/env bash
# Android TV emulator: drive the app with the remote's arrow keys, with screenshots.
PKG=com.fivebroz.android
# Household token for the database (from the access code kept as a GitHub secret); empty if not set.
HOUSE=""
if [ -n "$ACCESS_CODE" ]; then
  HOUSE=$(curl -s -X POST https://www.5broz.com/api/gate -H 'content-type: application/json' --data-binary "{\"code\":\"$ACCESS_CODE\"}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('house') or '')" 2>/dev/null)
fi
OUT=e2e-tv
mkdir -p $OUT
exec > >(tee $OUT/run.txt) 2>&1
set -x
adb install -r app/build/outputs/apk/debug/app-debug.apk
cat > /tmp/5broz.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="gate" value="true" />
    <string name="house">$HOUSE</string>
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
# Phone as remote: a phone (this script, through the same household tables) types a search on
# the TV, presses keys, then opens a title — the TV should follow within a second or two.
SUPA=https://tnaswphvdieoifaiudhz.supabase.co/rest/v1
ANON=$(grep -o "eyJ[A-Za-z0-9._-]*" app/src/main/java/com/fivebroz/android/data/Store.kt | head -1)
DEV=$(adb shell "run-as $PKG cat shared_prefs/5broz.xml" | grep -o 'name="device_id">[^<]*' | cut -d'>' -f2 | tr -d '\r')
echo "tv device id: $DEV" > $OUT/remote.txt
# (tracing off inside, so the household token never reaches the published log)
sig() { { set +x; } 2>/dev/null; curl -s -X POST "$SUPA/household_signals" -H "apikey: $ANON" -H "Authorization: Bearer $ANON" -H "x-house: $HOUSE" -H "Content-Type: application/json" -d "[{\"to_device\":\"$DEV\",\"kind\":\"$1\",\"payload\":$2}]" >> $OUT/remote.txt; echo " sent $1 $2" >> $OUT/remote.txt; set -x; }
if [ -n "$DEV" ]; then
  sig text '{"text":"Inception"}'; sleep 6; shot tv5b-remote-search
  sig key '{"key":"down"}'; sleep 2; sig key '{"key":"down"}'; sleep 3; shot tv5c-remote-keys
  sig open '{"type":"movie","id":"tt1375666"}'; sleep 8; shot tv5d-remote-open
  key BACK 2; key BACK 2
fi
# Play a film for a minute, leave, then the TV home screen's "Continue watching" should list it.
adb shell am start -a android.intent.action.VIEW -d "fivebroz://title/movie/tt0111161" $PKG
sleep 12; shot tv6-deeplink
key DPAD_CENTER 70; shot tv7-player
key BACK 1; key BACK 3
adb shell content query --uri content://android.media.tv/watch_next_program --projection title:last_playback_position_millis:intent_uri > $OUT/watchnext.txt 2>&1
echo "--- end ---" >> $OUT/watchnext.txt
adb logcat -d -s 5broz:V System.out:I | grep -E "TIMING|PLAYER READY|race|player error|autosync|introdb|frame rate|5broz-engine|STALL" | sed "s|^|movie/tt0111161 |" > $OUT/timing.txt
# Time-to-picture on more titles: a classic, a recent film and a series.
for T in movie/tt15239678 series/tt0903747 movie/tt1375666; do
  adb shell am force-stop $PKG; sleep 2
  adb logcat -c
  adb shell am start -a android.intent.action.VIEW -d "fivebroz://title/$T" $PKG
  sleep 10; key DPAD_CENTER 1
  # Only this run's process counts (logcat can still hold the previous title's lines).
  P=$(adb shell pidof $PKG | tr -d '\r')
  for i in $(seq 1 45); do adb logcat -d -s 5broz:V | grep " $P " | grep -q "PLAYER READY" && break; sleep 2; done
  N=$(echo $T | tr / -); shot tv8-$N
  adb logcat -d -s 5broz:V System.out:I | grep " $P " | grep -E "TIMING|PLAYER READY|race|player error|autosync|introdb|frame rate|5broz-engine|STALL" | sed "s|^|$T |" >> $OUT/timing.txt
done
echo end >> $OUT/timing.txt
adb logcat -d -s 5broz:V AndroidRuntime:E > $OUT/logcat.txt; echo "--- end ---" >> $OUT/logcat.txt
exit 0
