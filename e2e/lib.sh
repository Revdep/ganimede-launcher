#!/usr/bin/env bash
# Shared helpers of the e2e scripts. Source it after setting SERIAL and HERE:  . "$HERE/lib.sh"
# Emulators, or a physical test phone the owner listed in E2E_DEVICES; one e2e at a time per device (flock);
# animations off (ANIM=1 to keep them). Anything that destroys data or changes a device setting runs on emulators only, except
# making Ganimede the Home app on a listed test phone
# (emu_only, setting); every setting changed is put back on exit, also on Ctrl-C or a failure (keep, defer).
case "$SERIAL" in emulator-*) ;; *) case " ${E2E_DEVICES:-} " in *" $SERIAL "*) ;; *) echo "emulators only: $SERIAL (or list it in E2E_DEVICES)" >&2; exit 2;; esac;; esac
exec 9>"/tmp/ganimede-e2e-$SERIAL.lock"; flock 9
OUT="$HERE/out/$SERIAL"; mkdir -p "$OUT"; rm -f "$OUT"/fail-* # per device: two runs from one checkout keep apart; fail-N is this run's
SCR="$OUT/screen.xml" # the last screen read, a file so that subshells share it
PK=org.ganimede.launcher
# Every adb call goes through A. One that can change the screen drops the cached screen read (see `screen`).
A() {
  case "$*" in "exec-out run-as $PK cat "*|"exec-out screencap "*|"exec-out uiautomator "*|"shell dumpsys "*|"shell settings get "*) ;; *) rm -f "$SCR";; esac
  "$ADB" -s "$SERIAL" "$@"
}
ADB=$(command -v adb || echo "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb")
command -v magick >/dev/null || { echo "ImageMagick (magick) is missing: the wallpaper checks need it." >&2; exit 2; }
# The guard of every destructive step. E2E_PHYSICAL=1 plays a physical phone (to test the guard itself).
is_emulator() { [ -z "${E2E_PHYSICAL:-}" ] && [[ $SERIAL == emulator-* ]]; }
emu_only() { is_emulator && return 0; SKIPS=$((SKIPS + 1)); echo "  SKIP: $* (destroys data or changes settings: emulators only)"; return 1; }
# One trap for the whole run: the deferred commands run last-in first-out; a section that stops the run early is a failure.
CLEANUP=(); CUR=
defer() { CLEANUP+=("$1"); }
cleanup() {
  local rc=$? i; trap - EXIT
  for ((i = ${#CLEANUP[@]} - 1; i >= 0; i--)); do eval "${CLEANUP[i]}" >/dev/null 2>&1; done
  [ -z "$CUR" ] || { echo "ABORTED: section $CUR stopped the run" >&2; rc=1; }
  exit $rc
}
trap cleanup EXIT; trap 'exit 130' INT; trap 'exit 143' TERM
keep() { # keep namespace key: the setting gets its present value back on exit
  local old; old=$(A shell settings get "$1" "$2" | tr -d '\r')
  if [ "$old" = null ]; then defer "A shell settings delete $1 $2"; else defer "A shell settings put $1 $2 '$old'"; fi
}
setting() { emu_only "settings put $1 $2" && keep "$1" "$2" && A shell settings put "$1" "$2" "$3"; } # setting namespace key value
# A run without a booted, awake device only produces blank screenshots: refuse it.
[ "$(A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] || { echo "No booted device $SERIAL: start the emulator first." >&2; exit 2; }
A shell input keyevent KEYCODE_WAKEUP; A shell wm dismiss-keyguard
if is_emulator; then
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do setting global $s "${ANIM:-0}"; done
  for s in "secure enabled_notification_listeners" "system accelerometer_rotation" "system user_rotation"; do keep $s; done
  setting system font_scale 1.0 # a run killed mid font test leaves 1.15: List mode's clock then has no room to move up
else echo "SKIP: $SERIAL is not an emulator: destructive sections and every settings change are left out."; fi
A logcat -b crash -c
PASS=0; FAIL=0; SKIPS=0
DENS=$(A shell wm density | grep -o '[0-9]*' | tail -1)
read -r W H <<<"$(A shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr x ' ')" # real screen size in px

# wait_until <timeout_s> <command…>: run the command every 0.2 s until it succeeds; fails when the time is up.
wait_until() {
  local end=$((SECONDS + $1)); shift
  while :; do "$@" && return 0; [ $SECONDS -ge $end ] && return 1; sleep 0.2; rm -f "$SCR"; done # every retry reads the screen again
}
step() { echo; echo "== $*"; }
ok() { echo "  PASS: $*"; PASS=$((PASS + 1)); }
ko() { # the screen at the failure, and who is in front and which activity or process went (a stray Camera in front of Settings, run imp/5)
  echo "  FAIL: $*"; FAIL=$((FAIL + 1)); A exec-out screencap -p > "$OUT/fail-$FAIL.png"
  { A shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity|\* Task"
    A logcat -d -b events | grep -E "am_finish_activity|am_proc_died|am_kill|am_anr|wm_task_moved|am_proc_start|am_create_activity" | tail -80; } > "$OUT/fail-$FAIL-activities.txt" 2>/dev/null
}
# One adb call straight to stdout (~2 s: uiautomator starts a VM and waits for 1 s of idle): a failed dump is no XML,
# never the previous screen's file. A whole dump is kept in $SCR.
dump() { rm -f "$SCR"; A exec-out uiautomator dump /dev/tty 2>/dev/null | tee "$SCR"; grep -q '</hierarchy>' "$SCR" || rm -f "$SCR"; }
screen() { [ -s "$SCR" ] && cat "$SCR" || dump; } # the cached read while nothing has acted on the screen since
has() { screen | grep -q "text=\"$1"; }
expect() { wait_until 10 has "$1" && { ok "sees “$1”"; return 0; }; ko "doesn't see “$1”"; return 1; }
tap() { tapn "$1" 0; }
stap() { tapn "$1" 3; } # in a scrolling page: scrolls down up to three times to find it
kbd() { A shell dumpsys input_method | grep -q 'mInputShown=true'; } # the keyboard is up
# typ text: types once the keyboard is up. Keys sent while it is still coming up arrive out of order ("cal" typed as
# "cla", run 46083578007, drawer).
typ() { wait_until 5 kbd; A shell input text "$1"; }
_tb() { TB=$(screen | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]'); [ -n "$TB" ]; }
_scroll() {
  local from=2120 to=1520 ime top # 600 px a step: a third of the screen, so nothing is skipped
  ime=$(A shell settings get secure default_input_method | tr -d '\r' | cut -d/ -f1)
  top=$(screen | grep -o "package=\"$ime\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" | head -1 \
    | sed -n 's/.*bounds="\[[0-9]*,\([0-9]*\)\].*/\1/p')
  if [ -n "$top" ] || kbd; then
    [ -n "$top" ] || top=$((H / 2))
    from=$((top - 20)); to=$((from - 200)) # keep the gesture above the IME without changing its state
  fi
  A shell input swipe 150 "$from" 150 "$to" 700
}
tapn() { # tapn text max-scrolls: waits for the text (or scrolls to it), then taps its middle
  local TB prev= n=0
  if [ "$2" -eq 0 ]; then wait_until 5 _tb "$1"
  else # after a scroll the position must be the same in two dumps in a row (no fling left)
    while :; do
      _tb "$1" && [ "$TB" = "$prev" ] && break
      prev=$TB
      [ -n "$TB" ] && { rm -f "$SCR"; continue; } # the second read must be a new one
      [ $n -ge "$2" ] && break
      _scroll; n=$((n + 1))
    done
  fi
  [ -z "$TB" ] && { ko "no “$1” to tap"; return 1; }
  local x1 y1 x2 y2; read -r x1 y1 x2 y2 <<<"$(echo "$TB" | tr -d '[' | tr ']' ' ' | tr ',' ' ')"
  A shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}
shot() { A exec-out screencap -p > "$OUT/$1.png"; }
dp() { echo $(($1 * DENS / 160)); }
pref() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -o "name=\"$1\" value=\"-*[0-9]*" | grep -o '\-*[0-9]*$'; }
# bounds of the first node whose text (or content-desc with "desc:") is exactly $1: "x1 y1 x2 y2", empty if none
bounds() {
  local attr=text v="$1" r; case "$v" in desc:*) attr=content-desc; v="${v#desc:}";; esac
  # a screen dump now and then comes back empty: try again
  _bounds_try() { r=$(screen | grep -o "$attr=\"$v\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' '); [ -n "$r" ]; }
  wait_until 3 _bounds_try && echo "$r"
  return 0
}
# Center of a node with this content-desc, scrolling a settings page until it is stable and visible.
find_desc() {
  local n=0 b prev= want="$1"
  while [ $n -lt 40 ]; do
    b=$(dump | grep -o "content-desc=\"$want\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' ')
    if [ -n "$b" ]; then set -- $b; [ "$4" -lt 2300 ] && { [ "$b" = "$prev" ] && { echo $((($1 + $3) / 2)) $((($2 + $4) / 2)); return 0; }; prev=$b; n=$((n + 1)); continue; }; fi
    prev=; A shell input swipe 150 2250 150 1750 900; n=$((n + 1)) # 500 px, slow: no fling past the row
  done
  return 1
}
# pill NAME: scroll Personalizza's pill strip until the pill is on screen (it grows with every pill added); then tap it as usual
pill() {
  local d n y from to row prev
  for d in left right; do
    prev=
    for n in 1 2 3 4 5 6; do
      has "$1" && return 0
      row=$(screen | grep -oE "text=\"(Tutto lo schermo|Dock|Griglia delle app|Orologio|Temi|Cartelle|Data e meteo|Alfabeto|Drawer)\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]")
      [ "$row" = "$prev" ] && break # the strip did not move: that end is reached, go the other way
      prev=$row
      y=$(echo "$row" | head -1 | sed -E 's/.*\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]/\1 \2/' | awk '{print int(($1+$2)/2)}')
      [ -n "$y" ] || return 1
      if [ $d = left ]; then from=900 to=150; else from=150 to=900; fi
      A shell input swipe $from "$y" $to "$y" 500
    done
  done
  has "$1"
}
cx() { local b; b=$(bounds "$1"); [ -z "$b" ] && return 1; set -- $b; echo $((($1 + $3) / 2)) $((($2 + $4) / 2)); }
lx() { local b; b=$(bounds "$1"); [ -z "$b" ] && return 1; set -- $b; echo "$1" $((($2 + $4) / 2)); } # left edge, middle height
see() { local n=0; while [ $n -lt 30 ]; do has "$1" && { ok "sees “$1”"; return 0; }; _scroll; n=$((n + 1)); done; ko "doesn't see “$1”"; return 1; }
near() { local d=$(($1 - $2)); [ ${d#-} -le "$3" ]; } # near a b tolerance
# put_prefs file: the app's prefs become this file. Through `adb shell`, which waits for the device command and returns
# its status: `adb exec-in` returns once stdin is sent, neither waiting for the device command nor reporting it, so the
# rename after the copy ran late or never (run 46088594668: the first seed left no file and the wizard opened; later
# writes left the old prefs).
# Written beside, then renamed: a home started mid-write never reads a half file. A force-stop during the app's own save
# leaves ganimede.xml.bak, which Android restores over any new file (run 46081658363, menu): it goes.
put_prefs() {
  A shell "run-as $PK sh -c 'mkdir -p shared_prefs && cat > shared_prefs/e2e.tmp && rm -f shared_prefs/ganimede.xml.bak && mv shared_prefs/e2e.tmp shared_prefs/ganimede.xml'" < "$1" \
    || { ko "put_prefs: the prefs were not written"; return 1; }
}
# edit_prefs file int|boolean|string|set|del key [value] ...: the settings written into the file the way the app stores
# them (del takes no value; a set holds the one item value).
edit_prefs() {
  local f="$1"; shift
  while [ $# -ge 2 ]; do
    sed -i "/ name=\"$2\"/d" "$f"
    case $1 in
      int|boolean) sed -i "s@</map>@    <$1 name=\"$2\" value=\"$3\" />\n</map>@" "$f" ;;
      string) sed -i "s@</map>@    <string name=\"$2\">$3</string>\n</map>@" "$f" ;;
      set) sed -i "s@</map>@    <set name=\"$2\"><string>$3</string></set>\n</map>@" "$f" ;;
    esac
    if [ "$1" = del ]; then shift 2; elif [ $# -ge 3 ]; then shift 3; else ko "edit_prefs: no value for $2"; return 1; fi
  done
}
# Change settings, then restart the home screen once. A restart takes ~9 s (force-stop, focus, two alike dumps): a step
# gives all its settings to one call (18-icons took 1311 s with ~107 restarts, 288 s before issue 078).
setp() { # setp int|boolean|string|set|del key [value] [int|boolean|string|set|del key [value] ...]
  local f="$OUT/prefs.xml"
  A shell am force-stop $PK
  A exec-out run-as $PK sh -c 'cat shared_prefs/ganimede.xml.bak 2>/dev/null || cat shared_prefs/ganimede.xml' > "$f" # the file Android would load
  edit_prefs "$f" "$@"
  put_prefs "$f"
  start_home
}
# the home screen has the focus and no window transition is playing (the focus moves when one starts: touches sent before it ends are lost)
focus() { local w; w=$(A shell dumpsys window | grep -E "mCurrentFocus|reason=Transition"); [[ $w == *mCurrentFocus*MainActivity* && $w != *reason=Transition* ]]; }
drawn() { dump | grep -q "package=\"$PK\""; } # touches sent before the first frame are lost
# start_home: the home restarted and ready for touches. Its first frame has no apps yet (onResume builds the screen before
# the apps load); once they load, every view is built again and a touch held on the old one is lost (run 46081674535,
# widgets: the long-press on the clock opened no menu). So it also waits for the screen to stop changing.
start_home() {
  A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK" \
    || A shell cmd role add-role-holder android.app.role.HOME $PK >/dev/null 2>&1 # another launcher holding HOME would start a second task
  A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK" || { ko "Ganimede doesn't hold the HOME role"; return 1; }
  A shell am force-stop $PK; A shell input keyevent KEYCODE_HOME; wait_until 10 focus && wait_until 10 drawn && wait_until 10 settled; }
target48() { local b; b=$(bounds "$1"); [ -n "$b" ] || return 1; set -- $b; [ $(($3 - $1)) -ge $(($(dp 48) - 1)) ] && [ $(($4 - $2)) -ge $(($(dp 48) - 1)) ]; } # target48 text|desc:…: the node is at least 48 x 48 dp
hasd() { screen | grep -q "content-desc=\"$1\""; }  # a node with this content-desc
valid() { screen >/dev/null; [ -s "$SCR" ]; } # a screen read that worked: an absent node means nothing without one
hasnt() { valid && ! has "$1"; }
nod() { valid && ! hasd "$1"; }
settled() { local a; a=$(dump); [ -n "$a" ] && [ "$a" = "$(dump)" ]; } # two screen dumps in a row alike: nothing is moving
# Stops the Clock app and deletes its timers. A force-stop is not enough: the next timer intent starts the app again and
# it re-arms the timers it saved, so a 15-minute timer of an earlier run rings mid-run and its heads-up over the screen
# top takes the taps meant for the home.
noclock() { is_emulator || return 0; A shell pm clear com.google.android.deskclock >/dev/null 2>&1; }
# home_menu [row]: long-presses an empty spot (1900) until the home menu shows that row (default Impostazioni Ganimede).
# Issue 067: in the appicon run of 27 Nov, both emulators loaded by parallel runs, `setp` → HOME → `focus` → long-press
# lost the press 3 of 3 times, in a classic seed; the same sequence later passed 3/3 in list and classic, and the whole
# section passed in both modes, so the cause is the load-dependent timing after a restart, not one home mode.
home_menu() { # one place for the retry (pg_settings uses it too); the WARN lets lost presses be counted across runs
  local n; for n in 1 2 3; do
    A shell input swipe 540 1900 540 1900 1500; rm -f "$SCR"; wait_until 3 has "${1:-Impostazioni Ganimede}" && return 0
    echo "  WARN: long press $n lost (section ${CUR:-?}): the home menu did not open"
  done; return 1; }
# open_drawer: swipes up from an empty spot until the search shows, for steps that need the drawer open; a step that
# tests the swipe itself (02-drawer's first ones, 17-anim) keeps its single swipe. Same lost-input cause as home_menu.
open_drawer() { local _; for _ in 1 2; do A shell input swipe 540 1900 540 600 150; wait_until 3 has "Cerca app" && return 0; done; A shell input swipe 540 1900 540 600 150; expect "Cerca app"; }
# pg_settings [menu row] [hint]: the classic home's long-press menu (1900 is an empty spot) → Ganimede settings, which
# shows its hint (Italian by default). For a fresh start (after seed or setp): a running Settings comes back on its last page.
pg_settings() {
  local hint="${2:-}"
  [ -n "$hint" ] || hint="Tocca una parte dell'anteprima per regolarla"
  home_menu "${1:-}"; tap "${1:-Impostazioni Ganimede}"
  expect "$hint"
}
# one node (the dialog message is one) whose text holds all the fragments in order (the dump wraps a text holding " in single quotes)
shows() { local f re='text=["'"'"'][^>]*'; for f in "$@"; do re="$re$f[^>]*"; done; screen | grep -q "$re"; }
noshows() { ! shows "$@"; }
# fdiff a.png b.png [WxH+X+Y]: how many pixels differ (inside the crop, if given). Pixels, never file bytes: a PNG
# written by magick carries its dates, so cmp calls two identical pictures different (issue 086).
fdiff() { compare -metric AE "$1${3:+[$3]}" "$2${3:+[$3]}" null: 2>&1 | grep -oE '^[0-9]+' | head -1; }
# seed classic|list [setp's settings ...]: a configured app without the wizard: onboarded, mode, favourites, Italian, the
# home role, and the settings given, all in one start (the APK must be installed). Leaves the home screen in front.
seed() {
  local f="$OUT/seed.xml" fav
  fav="com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;com.google.android.deskclock/com.android.deskclock.DeskClock&#10;com.google.android.contacts/com.android.contacts.activities.PeopleActivity"
  A shell am force-stop $PK; noclock
  cat > "$f" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarded" value="true" />
    <boolean name="fab" value="true" />
    <string name="mode">$1</string>
    <string name="fav">$fav</string>
    <string name="lang">it</string>
    <string name="icon_container">squircle</string>
</map>
XML
  shift; edit_prefs "$f" "$@"
  put_prefs "$f"
  A shell cmd role add-role-holder android.app.role.HOME $PK >/dev/null 2>&1
  start_home
}
# Crop of the wallpaper (an empty spot of the classic home) as a hash: anything drawn over the home changes it.
wall() { A exec-out screencap -p | magick png:- -crop "40x40+$((W - 120))+1000" +repage -depth 8 rgb:- 2>/dev/null | md5sum | cut -d' ' -f1; }
moved() { # moved "what" axis(x|y) before after expected-px
  local d=$(($4 - $3))
  near "$d" "$5" "$(dp 3)" && ok "$1 moved $d px on $2 (expected $5)" || ko "$1 moved $d px on $2, expected $5"
}
# In an Open document picker, show $1 (Recent may list only indexed files) and tap it.
_file_shown() { has "$1" || screen | grep -q "content-desc=\"$1, "; } # a list row, or a grid cell (named only in its desc)
pick_file() {
  if ! wait_until 8 hasd "Show roots"; then # who is in front, and which activity or process went (run 46093576561: the home, no picker; issue 089)
    ko "the file picker did not open"
    A shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity|\* Task" > "$OUT/fail-$FAIL-activities.txt"
    A logcat -d -b events | grep -E "am_finish_activity|am_proc_died|am_kill|am_anr|wm_task_moved|am_proc_start" | tail -200 > "$OUT/fail-$FAIL-events.txt"
    return 1
  fi
  if ! wait_until 4 _file_shown "$1"; then
    read -r rx ry <<<"$(cx "desc:Show roots")"; [ -n "$rx" ] && A shell input tap "$rx" "$ry"
    tap "Downloads"; wait_until 8 _file_shown "$1"
  fi
  # Images open in a grid that names files only in content-desc: the list view shows the name as text.
  has "$1" || { read -r rx ry <<<"$(cx "desc:List view")" && [ -n "$rx" ] && A shell input tap "$rx" "$ry" && wait_until 8 has "$1"; }
  tap "$1"
}

fresh_wizard() { # uninstall, install, open the first wizard step ("Welcome to Ganimede")
  emu_only "uninstalling the launcher" || return 1
  A uninstall org.ganimede.launcher >/dev/null 2>&1
  noclock
  A install "$APK" >/dev/null && ok "installed" || { ko "install failed"; exit 1; }
  A shell cmd role add-role-holder android.app.role.HOME com.google.android.apps.nexuslauncher >/dev/null 2>&1
  installed() { A shell cmd package resolve-activity --brief org.ganimede.launcher 2>/dev/null | grep -q MainActivity; } # right after installing, a start can be dropped
  wait_until 10 installed
  A shell am start -W -n org.ganimede.launcher/.MainActivity >/dev/null 2>&1
  wait_until 10 has "Welcome to Ganimede"
}
