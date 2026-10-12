#!/usr/bin/env bash
# Small adb helpers for driving the app on a connected phone during manual testing.
#   tools/ui.sh tap "Bắt đầu"     tap the first element whose text/description contains the string
#   tools/ui.sh shot name         save a screenshot to $TEMP/calo/name.png
#   tools/ui.sh texts             list visible texts
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
DEV="${DEV:-$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')}"
a() { MSYS_NO_PATHCONV=1 "$ADB" -s "$DEV" "$@"; }
dump() { a shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; a exec-out cat /sdcard/ui.xml; }
case "$1" in
  tap)
    xy=$(dump | python -c "
import re,sys
x=sys.stdin.buffer.read().decode('utf-8','ignore'); t=sys.argv[1]
for m in re.finditer(r'<node[^>]*?(?:text|content-desc)=\"([^\"]*)\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x):
    full=m.group(0)
    if t in full:
        b=list(map(int,m.groups()[1:])); print((b[0]+b[2])//2,(b[1]+b[3])//2); break
" "$2")
    [ -z "$xy" ] && { echo "not found: $2"; exit 1; }
    a shell input tap $xy; echo "tapped $2 at $xy" ;;
  shot) mkdir -p "$TEMP/calo"; a exec-out screencap -p > "$TEMP/calo/$2.png"; cygpath -w "$TEMP/calo/$2.png" ;;
  texts) dump | python -c "
import re,sys
x=sys.stdin.buffer.read().decode('utf-8','ignore')
print(' | '.join(t for t in re.findall(r'text=\"([^\"]+)\"',x)))" ;;
  *) a "$@" ;;
esac
