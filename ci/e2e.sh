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
    <string name="force_season">Ramadan</string>
    <boolean name="force_night" value="true" />
    <boolean name="force_recap" value="true" />
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

tap_avatar() {
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
}

SIZE=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); W=${SIZE%x*}; H=${SIZE#*x}
echo "screen ${W}x${H}"
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 0.5; shot 0a-intro
sleep 0.4; shot 0b-intro
sleep 19; shot 1-home
adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 400
sleep 2; shot 1b-home-scrolled
# Long-press a poster: the quick-actions menu.
adb shell uiautomator dump /sdcard/ui.xml > /dev/null; adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null
XY=$(python3 ci/find.py /tmp/ui.xml "@poster"); echo "long-press $XY"
[ -n "$XY" ] && adb shell input swipe $XY $XY 1000
sleep 3; shot 1c-item-menu
adb shell input keyevent 4; sleep 2
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
# Cast row and an actor's page.
adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 500; sleep 2
shot 2c-detail-cast
adb shell uiautomator dump /sdcard/ui.xml > /dev/null; adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null
XY=$(python3 - <<PY
import re,xml.etree.ElementTree as ET
r=ET.parse("/tmp/ui.xml").getroot()
for n in r.iter("node"):
    if "طاقم التمثيل" in (n.get("text") or ""):
        b=list(map(int,re.findall(r"\d+",n.get("bounds"))))
        print(b[2]-60, b[3]+110); break
PY
)
echo "cast $XY"
if [ -n "$XY" ]; then adb shell input tap $XY; sleep 8; shot 2d-person; adb shell input keyevent 4; sleep 3; fi
adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*9/10)) 300; sleep 1
adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*9/10)) 300; sleep 2
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
sleep 14; shot 6a2-pause-screen
adb shell input tap $CX $CY; sleep 1.5       # bring the controls back
tap_text "السرعة"
sleep 2; shot 6d-speed
tap_text "1.5×"; sleep 1
sleep 1; shot 6e-speed-set
tap_text "المزيد"; sleep 2; shot 6b0-more
tap_text "غرفة مشاهدة"
sleep 8; shot 6b-party
tap_text "🔥"; tap_text "❤️"; sleep 0.8; shot 6b1-reactions
adb shell input keyevent 4; sleep 2
tap_text "الترجمة"
sleep 2; shot 6c-subtitles
adb shell input swipe $((H*9/10)) $((W*3/4)) $((H*9/10)) $((W/4)) 400; sleep 1; shot 6c2-subtitles-more
adb shell input keyevent 4; sleep 1
# Picture-in-picture: resume, then go home — the film should shrink into a floating window.
adb shell input keyevent 126; sleep 3
adb shell input keyevent 3; sleep 5
shot 6f-pip
adb shell dumpsys activity activities | grep -iE "pinned|mode=pinned|windowingMode=2" | head -5 > $OUT/pip.txt; echo end >> $OUT/pip.txt
# Profiles: open the picker, manage mode and the new-profile editor (nothing is saved).
adb shell am force-stop $PKG; sleep 2
adb shell am start -n $PKG/.MainActivity; sleep 12
tap_avatar
sleep 3; shot 6g-profile-menu
tap_text "الإعدادات"; sleep 3; shot 6h-settings
adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/5)) 500; sleep 1; shot 6i-settings-2
adb shell input keyevent 4; sleep 2
tap_avatar; sleep 3
tap_text "تبديل الملف" || true
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
tap_avatar; sleep 2
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
    <string name="test_update_url">https://httpbin.org/drip?duration=150&amp;numbytes=3000000&amp;code=200</string>
</map>
XML
adb push /tmp/5broz-upd.xml /data/local/tmp/5broz-upd.xml
adb shell "cat /data/local/tmp/5broz-upd.xml | run-as $PKG sh -c 'cat > shared_prefs/5broz.xml'"
# The test build downloads a 3 MB file that the server drips out over 150 s,
# so most of it can only arrive while the screen is off.
# Keep the playback log (with TIMING lines) before the update test clears it.
adb logcat -d -s 5broz:V System.out:I AndroidRuntime:E > $OUT/play-log.txt
grep -E "TIMING|PLAYER READY|race|player error|autosync|introdb" $OUT/play-log.txt > $OUT/timing.txt; echo end >> $OUT/timing.txt
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
sleep 75
C=$(size); echo "still off, 165s in: ${C} KB" | tee -a $OUT/update.txt
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
