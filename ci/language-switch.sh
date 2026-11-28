#!/usr/bin/env bash
# Switches the language on a real app process, the way a person does, and checks it sticks.
# Instrumented tests can't do this: the switch restarts the process, which ends the test run.
# Taps each language on the welcome screen, waits for the restart, checks the screen is in that
# language, then force-stops and relaunches to check the choice was saved.
set -u
PKG=com.owl.tracker
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p logs
[ -f "$APK" ] || ./gradlew -q :app:assembleDebug
adb install -r -g "$APK" >/dev/null || { echo "install failed"; exit 1; }
adb shell pm clear $PKG </dev/null >/dev/null

# every adb call reads from /dev/null, or it eats the language list the loop below reads
launch() { adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 </dev/null >/dev/null 2>&1; sleep 6; }
dump() { adb shell uiautomator dump /sdcard/ui.xml </dev/null >/dev/null 2>&1; adb shell cat /sdcard/ui.xml </dev/null; }
# waits up to 30 s for text on screen
shows() { for _ in 1 2 3 4 5 6 7 8 9 10; do dump | grep -q "$1" && return 0; sleep 3; done; return 1; }
# what is on screen, for the log when a check fails
texts() { dump | grep -o 'text="[^"]\+"' | head -25 | tr '\n' ' '; echo; }

# taps the node whose text or content-desc starts with $1
tap() {
  local xml b
  shows "$1" || true
  xml=$(dump)
  b=$(echo "$xml" | python3 -c '
import sys,re
x=sys.stdin.read(); want=sys.argv[1]
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    t=re.search(r" text=\"([^\"]*)\"", n); d=re.search(r" content-desc=\"([^\"]*)\"", n)
    if (t and t.group(1).startswith(want)) or (d and d.group(1).startswith(want)):
        b=re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
        if b:
            x1,y1,x2,y2=map(int,b.groups()); print((x1+x2)//2,(y1+y2)//2); break
' "$1")
  [ -n "$b" ] || { echo "no node starting with '$1'"; return 1; }
  adb shell input tap $b </dev/null
}

# language code, its own name (the button), and its "Get started" (proof the screen switched)
check() {
  local code=$1 name=$2 start=$3 pid1 pid2
  pid1=$(adb shell pidof $PKG </dev/null | tr -d '\r')
  tap "$name" || { texts; return 1; }
  sleep 4
  if ! shows "$start"; then
    pid2=$(adb shell pidof $PKG </dev/null | tr -d '\r')
    echo "FAIL $code: screen not in $name after the switch (pid $pid1 → $pid2). On screen: $(texts)"; return 1
  fi
  pid2=$(adb shell pidof $PKG </dev/null | tr -d '\r')
  adb shell am force-stop $PKG </dev/null; launch
  if ! shows "$start"; then echo "FAIL $code: back to another language after relaunch. On screen: $(texts)"; return 1; fi
  echo "ok $code: switched (pid $pid1 → $pid2) and kept after relaunch"
}

launch
fail=0
while IFS='|' read -r code name start; do
  [ -n "$code" ] || continue
  check "$code" "$name" "$start" || fail=1
done < <(python3 - <<'EOF'
import json,re
src=open('app/src/main/kotlin/com/owl/tracker/i18n/I18n.kt',encoding='utf-8').read()
langs=re.findall(r'Language\("(\w+)", "([^"]+)", "[^"]+"\)',src)
order=[l for l in langs if l[0]!='en']+[l for l in langs if l[0]=='en']
for code,name in order:
    start='Get started' if code=='en' else json.load(open(f'app/src/main/assets/i18n/{code}.json',encoding='utf-8'))['Get started']
    print(f'{code}|{name}|{start}')
EOF
)
adb exec-out screencap -p </dev/null > logs/language-switch.png 2>/dev/null || true
exit $fail
