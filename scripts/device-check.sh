#!/usr/bin/env bash
# Install the debug build on a connected device, reset the test vault, and save
# screenshots of the app, the widget, a task toggle, the popup and the settings.
#
#   scripts/device-check.sh [--no-build]
#
# Needs one device (or ANDROID_SERIAL) with USB debugging on, and a widget on
# its current home page showing TestVault/Today.md. `install -r` keeps widgets,
# so placing one is a one-time step. Screenshots go to build/device-check/<time>/.
# Exit status: 0 all steps passed, 1 setup failed, 2 some steps failed.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ROOT"

PKG=com.kleanthi.obsidianwidget
VAULT=/sdcard/Documents/TestVault
TOGGLE_TASK="Tap my checkbox to test the toggle"
POPUP_LINE="Tap this line to open the popup."

build=1
case "${1:-}" in
  "") ;;
  --no-build) build=0 ;;
  -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
  *) die "unknown argument: $1 (see --help)" ;;
esac
[ -x "$ADB" ] || die "adb not found at $ADB"

# --- device ------------------------------------------------------------------
# "serial state" lines from `adb devices`, limited to ANDROID_SERIAL if set.
device_list() {
  "$ADB" devices | sed '1d' | awk -v want="${ANDROID_SERIAL:-}" \
    'NF >= 2 && (want == "" || $1 == want) { print $1, $2 }'
}
list="$(device_list)"
[ -n "$list" ] || die "no device connected: plug it in and turn on USB debugging"
[ "$(wc -l <<<"$list" | tr -d ' ')" -eq 1 ] ||
  die "more than one device: pick one with ANDROID_SERIAL="$'\n'"$list"
serial="${list%% *}"
status="${list##* }"

# After the user accepts the USB debugging prompt, adb can keep reporting
# "unauthorized" until its server restarts (seen on the Tab S6 Lite).
if [ "$status" = unauthorized ]; then
  note "device $serial is unauthorized: restarting the adb server once"
  "$ADB" kill-server >/dev/null 2>&1 || true
  "$ADB" start-server >/dev/null 2>&1
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    list="$(device_list)"; status="${list##* }"
    [ "$status" = device ] && break
    sleep 2
  done
fi
[ "$status" = device ] ||
  die "device $serial is '$status': accept the USB debugging prompt on it, then run again"
export ANDROID_SERIAL="$serial"
note "device $serial ($("$ADB" shell getprop ro.product.model | tr -d '\r'))"

# --- install -----------------------------------------------------------------
apk=app/build/outputs/apk/debug/app-debug.apk
if [ $build -eq 1 ]; then
  note "build: assembleDebug"
  ./gradlew ${GRADLE_FLAGS:-} -q assembleDebug || die "assembleDebug failed"
fi
[ -f "$apk" ] || die "no debug APK at $apk (run without --no-build)"
note "install -r (keeps the widgets)"
result="$("$ADB" install -r "$apk" 2>&1 || true)"
case "$result" in
  *Success*) ;;
  *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
    die "the device has $PKG signed with another key (the release build?)."$'\n'\
"Uninstalling removes its widgets, so this script won't. If that is OK:"$'\n'\
"  $ADB -s $serial uninstall $PKG" ;;
  *) die "install failed: $result" ;;
esac

# --- test vault --------------------------------------------------------------
note "push tools/test-vault to $VAULT (resets Today.md)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cp -R tools/test-vault/. "$tmp/"
today="$(date +%F)"
mkdir -p "$tmp/Daily"
printf '# %s\n\n- [ ] Daily-note task for %s\n- [x] Already done today\n' \
  "$today" "$today" > "$tmp/Daily/$today.md"
"$ADB" shell mkdir -p "$VAULT"
"$ADB" push "$tmp/." "$VAULT/" >/dev/null

# --- screens -----------------------------------------------------------------
out="build/device-check/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
failed=0
fail() { echo "FAIL: $*" >&2; failed=$((failed + 1)); }
shot() { "$ADB" exec-out screencap -p > "$out/$1.png"; echo "  saved $out/$1.png"; }

# Bounds "x1 y1 x2 y2" of the first on-screen node whose text or
# content-desc is exactly $1; empty output if there is none.
bounds() {
  "$ADB" shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1 || return 0
  "$ADB" shell cat /sdcard/window_dump.xml | python3 -c '
import html, re, sys
want = sys.argv[1]
for node in re.findall(r"<node [^>]*>", sys.stdin.read()):
    attrs = dict(re.findall(r" ([a-z-]+)=\"([^\"]*)\"", node))
    if want in (html.unescape(attrs.get("text", "")), html.unescape(attrs.get("content-desc", ""))):
        print(*re.findall(r"\d+", attrs["bounds"]))
        break
' "$1"
}
tap_center() {
  local b; b="$(bounds "$1")"
  [ -n "$b" ] || return 1
  set -- $b
  "$ADB" shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

"$ADB" shell input keyevent KEYCODE_WAKEUP
"$ADB" shell wm dismiss-keyguard >/dev/null 2>&1 || true

note "app screen (opening it refreshes every widget)"
"$ADB" shell am start -W -n "$PKG/.setup.SetupActivity" >/dev/null
sleep 2
shot 01-app

note "home screen"
"$ADB" shell input keyevent KEYCODE_HOME
sleep 3
shot 02-home
if [ -z "$(bounds "$POPUP_LINE")" ]; then
  fail "no widget showing TestVault/Today.md on the current home page (see 02-home.png)"
  echo "Place one first: long-press home > Widgets > Obsidian Widget > Today." >&2
  exit 2
fi

note "task toggle"
b="$(bounds "$TOGGLE_TASK")"
if [ -z "$b" ]; then
  fail "task row '$TOGGLE_TASK' not found"
else
  # The checkbox glyph sits left of the task text: tap 16dp left of the text.
  density="$("$ADB" shell wm density | tr -d '\r' | sed -n 's/.*: //p' | tail -1)"
  set -- $b
  "$ADB" shell input tap $(( $1 - 16 * density / 160 )) $(( ($2 + $4) / 2 ))
  sleep 2
  shot 03-toggled
  note_now="$("$ADB" shell cat "$VAULT/Today.md" | tr -d '\r')"
  if grep -q "^- \[x\] $TOGGLE_TASK" <<<"$note_now"; then
    echo "  Today.md now has '- [x] $TOGGLE_TASK'"
  else
    fail "the tap did not write '- [x] $TOGGLE_TASK' to Today.md"
  fi
fi

note "popup viewer"
if tap_center "$POPUP_LINE"; then
  sleep 2; shot 04-popup
  "$ADB" shell input keyevent KEYCODE_BACK; sleep 1
else
  fail "popup line not found"
fi

note "widget settings"
if tap_center "Widget settings"; then
  sleep 2; shot 05-settings
  "$ADB" shell input keyevent KEYCODE_HOME; sleep 1
else
  fail "settings button (content description 'Widget settings') not found"
fi

echo
if [ $failed -eq 0 ]; then
  note "all steps passed; review the screenshots in $out"
else
  note "$failed step(s) failed; screenshots in $out"
  exit 2
fi
