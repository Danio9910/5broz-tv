#!/usr/bin/env bash
# Walks through the app like a person: home -> title -> play (torrent), with screenshots.
PKG=com.fivebroz.android
OUT=${OUT:-e2e}
mkdir -p $OUT
exec > >(tee $OUT/run.txt) 2>&1
set -x
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Skip Android's one-time "Viewing full screen" notice (a real user sees it once).
adb shell settings put secure immersive_mode_confirmations confirmed

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

SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
echo "screen ${W}x${H}"
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 20; shot 1-home
adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 400
sleep 2; shot 1b-home-scrolled
adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*9/10)) 300
sleep 1
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
PS=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)
adb shell input tap $(( ${PS#*x} / 2 )) $(( ${PS%x*} / 2 ))   # landscape: reveal the controls
sleep 1; shot 6-controls
# Pause first (still screen = the UI tree can be read; controls stay up while paused).
CX=$(( ${PS#*x} / 2 )); CY=$(( ${PS%x*} / 2 ))
sleep 6
adb shell input tap $CX $CY; sleep 0.7      # show controls
adb shell input tap $CX $CY; sleep 2        # big centre button = pause
shot 6a-paused
tap_text "غرفة مشاهدة"
sleep 8; shot 6b-party
adb shell input keyevent 4; sleep 2
tap_text "الترجمة"
sleep 2; shot 6c-subtitles
adb shell input keyevent 4; sleep 1
# Profiles: open the picker, manage mode and the new-profile editor (nothing is saved).
adb shell am force-stop $PKG; sleep 2
adb shell am start -n $PKG/.MainActivity; sleep 12
tap_text "تبديل" || true
adb shell uiautomator dump /sdcard/ui.xml > /dev/null; adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null
python3 - <<PY
import re,subprocess,xml.etree.ElementTree as ET
r=ET.parse("/tmp/ui.xml").getroot()
# the profile avatar sits at the top corner of the header
best=None
for n in r.iter("node"):
    b=list(map(int,re.findall(r"\d+",n.get("bounds"))))
    if b[1] < 300 and b[0] < 200 and (b[2]-b[0]) < 200 and n.get("clickable")=="true":
        best=b; break
if best: subprocess.run(["adb","shell","input","tap",str((best[0]+best[2])//2),str((best[1]+best[3])//2)])
PY
sleep 8; shot 7-profiles
tap_text "إدارة الملفات"
sleep 2; shot 8-manage
tap_text "+"
sleep 3; shot 9-new-profile
adb shell input keyevent 4; sleep 2
# Tonight tab (re-seed the test profile first — switching profiles cleared it)
adb shell am force-stop $PKG; sleep 1
adb shell "cat /data/local/tmp/5broz.xml | run-as $PKG sh -c 'cat > shared_prefs/5broz.xml'"
adb shell am start -n $PKG/.MainActivity; sleep 12
tap_text "الليلة"
sleep 6; shot 10-tonight
tap_text "الرئيسية"; sleep 3
tap_text "انضمام لغرفة مشاهدة"; sleep 2; shot 11-join
adb shell input text QATEST; sleep 1
adb shell input keyevent 66
sleep 90; shot 12-guest
adb logcat -d -s 5broz:V | grep -E "party|READY" > $OUT/party.txt; echo end >> $OUT/party.txt
# Update: the download must keep going with the screen OFF.
adb shell am force-stop $PKG; sleep 1
cat > /tmp/5broz-upd.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="gate" value="true" />
    <string name="pid">guest</string>
    <string name="pname">اختبار</string>
    <boolean name="test_update" value="true" />
</map>
XML
adb push /tmp/5broz-upd.xml /data/local/tmp/5broz-upd.xml
adb shell "cat /data/local/tmp/5broz-upd.xml | run-as $PKG sh -c 'cat > shared_prefs/5broz.xml'"
adb emu network speed 1500        # ~190 KB/s: the download takes minutes, so it must run while the screen is off
adb logcat -c
adb shell am start -n $PKG/.MainActivity; sleep 14; shot 13-update-offer
tap_text "تحديث الآن"
sleep 6; shot 13b-downloading
DL=/sdcard/Android/data/$PKG/files/Download
size() { adb shell "du -k $DL 2>/dev/null | tail -1" | awk '{print $1}'; }
adb shell input keyevent 26       # screen OFF
sleep 3
A=$(size); echo "screen off, downloaded so far: ${A} KB" | tee $OUT/update.txt
sleep 90
B=$(size); echo "still off 90s later: ${B} KB" | tee -a $OUT/update.txt
adb shell dumpsys power | grep -E "mWakefulness=|Display Power" | tee -a $OUT/update.txt
adb emu network speed full
sleep 40
C=$(size); echo "after network restored (screen still off): ${C} KB" | tee -a $OUT/update.txt
adb shell input keyevent 224      # wake
sleep 2; adb shell input keyevent 82; sleep 4
shot 14-after-screen-off
{
  echo "--- files ---"
  adb shell ls -l $DL/
  echo "--- log ---"
  adb logcat -d -s 5broz:V | grep -i update
} >> $OUT/update.txt 2>&1
echo "--- app log ---" > $OUT/logcat.txt
adb logcat -d -s 5broz:V ExoPlayerImpl:V AndroidRuntime:E >> $OUT/logcat.txt
grep -q "PLAYER READY" $OUT/logcat.txt && echo "E2E: PLAYER READY" || echo "E2E: player never became ready"
exit 0
