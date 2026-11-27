#!/usr/bin/env bash
# Side-by-side check of 5broz, Stremio and Nuvio on the same emulator, same steps, same measurements.
#   MODE=phone|tv   APKS="name=path ..."   OUT=dir
# For each app: cold start (am start -W), first-run screens, home, scrolling smoothness
# (dumpsys gfxinfo), opening a title, memory, then a second start. Screenshots at every step.
MODE=${MODE:-phone}
OUT=${OUT:-rivals-$MODE}
mkdir -p $OUT
exec > >(tee $OUT/run.txt) 2>&1
set -x
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2)
CAT=android.intent.category.LAUNCHER
[ "$MODE" = tv ] && CAT=android.intent.category.LEANBACK_LAUNCHER
R=$OUT/results.txt
: > $R
cat > /tmp/5broz.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="gate" value="true" />
    <string name="pid">guest</string>
    <string name="pname">اختبار</string>
</map>
XML
adb push /tmp/5broz.xml /data/local/tmp/5broz.xml

shot() { adb exec-out screencap -p > $OUT/$1.png; }
dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1; }
tap_any() {   # tap the first node whose text/desc contains any of the words
  dump
  local xy; xy=$(python3 ci/find_any.py /tmp/ui.xml "$@")
  echo "tap_any $* -> $xy"
  [ -n "$xy" ] && adb shell input tap $xy && return 0
  return 1
}
key() { adb shell input keyevent $1; sleep ${2:-1}; }

for spec in $APKS; do
  NAME=${spec%%=*}; APK=${spec#*=}
  P=$(aapt2 dump badging "$APK" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
  [ -z "$P" ] && P=$(python3 ci/apk_pkg.py "$APK")
  echo "=== $NAME ($P) $(du -h "$APK" | cut -f1)" | tee -a $R
  adb install -r -g "$APK" || { echo "$NAME install failed" >> $R; continue; }
  if [ "$NAME" = 5broz ]; then
    adb shell "run-as $P mkdir -p shared_prefs"
    adb shell "cat /data/local/tmp/5broz.xml | run-as $P sh -c 'cat > shared_prefs/5broz.xml'"
  fi
  ACT=$(adb shell cmd package resolve-activity --brief -c $CAT $P | tail -1 | tr -d '\r')
  [ -z "$ACT" ] || [[ "$ACT" != */* ]] && ACT=$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $P | tail -1 | tr -d '\r')
  echo "activity $ACT"

  # 1) cold start
  T1=$(adb shell am start -W -n "$ACT" | grep -E "TotalTime" | grep -o '[0-9]*')
  echo "$NAME cold start (first frame): ${T1} ms" | tee -a $R
  sleep 1; shot $NAME-01-1s
  sleep 2; shot $NAME-02-3s
  sleep 4; shot $NAME-03-7s

  # 2) first-run screens: how many taps before the catalogue shows
  steps=0
  for i in 1 2 3 4 5 6; do
    if tap_any "Skip" "SKIP" "Guest" "guest" "Continue without" "Get started" "Get Started" "Let's go" "Next" "NEXT" "Later" "Not now" "Allow" "ALLOW" "Accept" "I agree" "Continue" "Done" "Start" "Browse" "Maybe later" "No thanks" "Close" "Dismiss" "OK"; then
      steps=$((steps+1)); sleep 3; shot $NAME-04-onboard-$i
    else
      break
    fi
  done
  echo "$NAME first-run screens tapped through: $steps" | tee -a $R
  sleep 8; shot $NAME-05-home
  dump; cp /tmp/ui.xml $OUT/$NAME-home.xml

  # 3) scrolling / moving through the catalogue: frame times
  adb shell dumpsys gfxinfo $P reset > /dev/null
  if [ "$MODE" = tv ]; then
    for i in 1 2 3 4 5 6; do key DPAD_RIGHT 0.6; done
    for i in 1 2 3 4; do key DPAD_DOWN 0.8; done
  else
    for i in 1 2 3 4 5 6; do adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) 250; sleep 0.8; done
    for i in 1 2 3; do adb shell input swipe $((W*4/5)) $((H/2)) $((W/5)) $((H/2)) 250; sleep 0.8; done
  fi
  adb shell dumpsys gfxinfo $P > $OUT/$NAME-gfx.txt
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" $OUT/$NAME-gfx.txt | head -6 | sed "s/^/$NAME scroll: /" | tee -a $R
  shot $NAME-06-scrolled

  # 4) open a title
  if [ "$MODE" = tv ]; then
    key DPAD_CENTER 1; shot $NAME-07-title-1s; sleep 3; shot $NAME-08-title-4s
  else
    dump
    XY=$(python3 ci/find_any.py /tmp/ui.xml "@poster")
    echo "poster $XY"
    if [ -n "$XY" ]; then
      adb shell input tap $XY; sleep 1; shot $NAME-07-title-1s; sleep 3; shot $NAME-08-title-4s
    fi
  fi
  dump; cp /tmp/ui.xml $OUT/$NAME-title.xml
  key BACK 2; shot $NAME-09-back

  # 5) memory, then a second start
  adb shell dumpsys meminfo $P | grep -E "TOTAL PSS|TOTAL:" | head -1 | sed "s/^/$NAME memory: /" | tee -a $R
  adb shell am force-stop $P; sleep 2
  T2=$(adb shell am start -W -n "$ACT" | grep -E "TotalTime" | grep -o '[0-9]*')
  echo "$NAME second start: ${T2} ms" | tee -a $R
  sleep 4; shot $NAME-10-second-start
  adb shell am force-stop $P
  adb shell pm uninstall $P > /dev/null
  echo >> $R
done
cat $R
