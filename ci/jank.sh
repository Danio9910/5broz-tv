#!/usr/bin/env bash
# Smoothness, screen by screen, on the optimised (bench) build: frame times for each gesture
# (dumpsys gfxinfo) plus a Perfetto trace of scrolling the home screen, analysed into the
# slowest parts of the UI. MODE=phone|tablet|tv  APK=path  OUT=dir
MODE=${MODE:-phone}; OUT=${OUT:-jank-$MODE}; P=com.fivebroz.android
mkdir -p $OUT
exec > >(tee $OUT/run.txt) 2>&1
set -x
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f1)
H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | cut -dx -f2)
R=$OUT/frames.txt; : > $R
shot() { adb exec-out screencap -p > $OUT/$1.png; }
key() { adb shell input keyevent $1; sleep ${2:-0.5}; }
dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1; }
tap_any() { dump; local xy; xy=$(python3 ci/find_any.py /tmp/ui.xml "$@"); [ -n "$xy" ] && adb shell input tap $xy; }
swipe_v() { adb shell input swipe $((W/2)) $((H*3/4)) $((W/2)) $((H/4)) ${1:-300}; sleep 0.7; }
swipe_v_back() { adb shell input swipe $((W/2)) $((H/4)) $((W/2)) $((H*3/4)) ${1:-300}; sleep 0.7; }
swipe_h() { adb shell input swipe $((W*4/5)) $1 $((W/5)) $1 300; sleep 0.7; adb shell input swipe $((W/5)) $1 $((W*4/5)) $1 300; sleep 0.7; }
begin() { adb shell dumpsys gfxinfo $P reset > /dev/null; }
end() {
  adb shell dumpsys gfxinfo $P > $OUT/gfx-$1.txt
  local n j p50 p90 p99
  n=$(grep -m1 "Total frames rendered" $OUT/gfx-$1.txt | grep -o '[0-9]*' | head -1)
  j=$(grep -m1 "Janky frames:" $OUT/gfx-$1.txt | sed 's/.*Janky frames: //')
  p50=$(grep -m1 "50th percentile" $OUT/gfx-$1.txt | grep -o '[0-9]*ms')
  p90=$(grep -m1 "90th percentile" $OUT/gfx-$1.txt | grep -o '[0-9]*ms')
  p99=$(grep -m1 "99th percentile" $OUT/gfx-$1.txt | grep -o '[0-9]*ms')
  echo "$1: frames=$n janky=$j p50=$p50 p90=$p90 p99=$p99" | tee -a $R
}

adb install -r -g "$APK"
ACT=$P/.MainActivity
adb shell am start -W -n $ACT --ez bench true
sleep 14; shot 01-home

# Perfetto: record the home screen while it is scrolled (or moved through with the remote).
adb shell rm -f /data/misc/perfetto-traces/home.pftrace
adb shell perfetto --background -o /data/misc/perfetto-traces/home.pftrace -t 16s -a $P -b 64mb sched freq gfx view input am wm dalvik
sleep 1
begin
if [ "$MODE" = tv ]; then
  for i in 1 2 3 4 5 6 7 8; do key DPAD_LEFT 0.45; done
  for i in 1 2 3 4; do key DPAD_RIGHT 0.45; done
  for i in 1 2 3 4 5; do key DPAD_DOWN 0.7; done
  for i in 1 2 3; do key DPAD_UP 0.7; done
else
  for i in 1 2 3 4; do swipe_v; done
  for i in 1 2 3 4; do swipe_v_back; done
fi
end home-move
sleep 6
adb pull /data/misc/perfetto-traces/home.pftrace $OUT/home.pftrace || true
shot 02-home-moved

if [ "$MODE" != tv ]; then
  # a row, sideways
  Y=$(python3 - <<PY
import re,xml.etree.ElementTree as ET,subprocess
subprocess.run("adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1",shell=True)
try:
    nodes=list(ET.parse('/tmp/ui.xml').getroot().iter('node'))
    for n in nodes:
        b=list(map(int,re.findall(r'\d+',n.get('bounds') or '')))
        if len(b)==4 and n.get('scrollable')=='true' and (b[2]-b[0])>(b[3]-b[1]) and b[1]>200:
            print((b[1]+b[3])//2); break
except Exception: pass
PY
)
  [ -z "$Y" ] && Y=$((H*55/100))
  begin; for i in 1 2 3; do swipe_h $Y; done; end row-sideways
fi

# open a title and move through its page
if [ "$MODE" = tv ]; then
  key DPAD_CENTER 4; shot 03-title
  begin; for i in 1 2 3 4 5; do key DPAD_DOWN 0.7; done; for i in 1 2 3; do key DPAD_UP 0.7; done; end title-move
  key BACK 2
else
  dump; XY=$(python3 ci/find_any.py /tmp/ui.xml "@poster"); [ -n "$XY" ] && adb shell input tap $XY
  sleep 4; shot 03-title
  begin; for i in 1 2 3; do swipe_v; done; for i in 1 2; do swipe_v_back; done; end title-scroll
  key BACK 2
fi

# opening a title (the transition itself)
begin
if [ "$MODE" = tv ]; then key DPAD_CENTER 3; key BACK 2; else dump; XY=$(python3 ci/find_any.py /tmp/ui.xml "@poster"); [ -n "$XY" ] && adb shell input tap $XY; sleep 3; key BACK 2; fi
end open-and-back

# search: typing and results
if [ "$MODE" = tv ]; then
  key DPAD_RIGHT 0.6; key DPAD_DOWN 0.6; key DPAD_CENTER 2
else
  tap_any "بحث"; sleep 2
fi
begin
adb shell input text "star"; sleep 4
if [ "$MODE" = tv ]; then for i in 1 2 3; do key DPAD_DOWN 0.6; done; else swipe_v; swipe_v_back; fi
end search
shot 04-search

cat $R
