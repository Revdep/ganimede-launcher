#!/usr/bin/env bash
# Shared helpers of the e2e scripts. Source it after setting SERIAL and HERE:  . "$HERE/lib.sh"
# Emulators, or a physical test phone the owner listed in E2E_DEVICES; one e2e at a time per device (flock);
# animations off (ANIM=1 to keep them). Anything that destroys data or changes a device setting runs on emulators only, except
# making Ganimede the Home app on a listed test phone
# (emu_only, setting); every setting changed is put back on exit, also on Ctrl-C or a failure (keep, defer).
case "$SERIAL" in emulator-*) ;; *) case " ${E2E_DEVICES:-} " in *" $SERIAL "*) ;; *) echo "emulators only: $SERIAL (or list it in E2E_DEVICES)" >&2; exit 2;; esac;; esac
exec 9>"/tmp/ganimede-e2e-$SERIAL.lock"; flock 9
OUT="$HERE/out"; mkdir -p "$OUT"
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
ko() { echo "  FAIL: $*"; FAIL=$((FAIL + 1)); A exec-out screencap -p > "$OUT/fail-$FAIL.png"; } # the screen at the failure
# One adb call straight to stdout (~2 s: uiautomator starts a VM and waits for 1 s of idle): a failed dump is no XML,
# never the previous screen's file. A whole dump is kept in $SCR.
dump() { rm -f "$SCR"; A exec-out uiautomator dump /dev/tty 2>/dev/null | tee "$SCR"; grep -q '</hierarchy>' "$SCR" || rm -f "$SCR"; }
screen() { [ -s "$SCR" ] && cat "$SCR" || dump; } # the cached read while nothing has acted on the screen since
has() { screen | grep -q "text=\"$1"; }
expect() { wait_until 10 has "$1" && { ok "sees “$1”"; return 0; }; ko "doesn't see “$1”"; return 1; }
tap() { tapn "$1" 0; }
stap() { tapn "$1" 3; } # in a scrolling page: scrolls down up to three times to find it
_tb() { TB=$(screen | grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]'); [ -n "$TB" ]; }
_scroll() {
  local from=2120 to=1920 ime top
  ime=$(A shell settings get secure default_input_method | tr -d '\r' | cut -d/ -f1)
  top=$(screen | grep -o "package=\"$ime\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" | head -1 \
    | sed -n 's/.*bounds="\[[0-9]*,\([0-9]*\)\].*/\1/p')
  if [ -n "$top" ] || A shell dumpsys input_method | grep -q 'mInputShown=true'; then
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
    prev=; A shell input swipe 150 2250 150 2050 900; n=$((n + 1)) # slow: no fling past the row
  done
  return 1
}
# pill NAME: scroll Personalizza's pill strip until the pill is on screen (it grows with every pill added); then tap it as usual
pill() {
  local d n y from to
  for d in left right; do
    for n in 1 2 3 4 5 6; do
      has "$1" && return 0
      y=$(screen | grep -oE "text=\"(Tutto lo schermo|Dock|Griglia delle app|Orologio|Temi|Cartelle|Data e meteo|Alfabeto|Drawer)\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]" | head -1 | sed -E 's/.*\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]/\1 \2/' | awk '{print int(($1+$2)/2)}')
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
# Change one setting the way the app stores it, then restart the home screen.
setp() { # setp int|boolean|string|del key [value]
  local f="$OUT/prefs.xml"
  A shell am force-stop $PK
  A exec-out run-as $PK cat shared_prefs/ganimede.xml > "$f"
  sed -i "/ name=\"$2\"/d" "$f"
  case $1 in
    int|boolean) sed -i "s@</map>@    <$1 name=\"$2\" value=\"$3\" />\n</map>@" "$f" ;;
    string) sed -i "s@</map>@    <string name=\"$2\">$3</string>\n</map>@" "$f" ;;
  esac
  A exec-in "run-as $PK sh -c 'cat > shared_prefs/ganimede.xml'" < "$f"
  start_home
}
# the home screen has the focus and no window transition is playing (the focus moves when one starts: touches sent before it ends are lost)
focus() { local w; w=$(A shell dumpsys window | grep -E "mCurrentFocus|reason=Transition"); [[ $w == *mCurrentFocus*MainActivity* && $w != *reason=Transition* ]]; }
start_home() {
  A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK" \
    || A shell cmd role add-role-holder android.app.role.HOME $PK >/dev/null 2>&1 # another launcher holding HOME would start a second task
  A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK" || { ko "Ganimede doesn't hold the HOME role"; return 1; }
  A shell am force-stop $PK; A shell input keyevent KEYCODE_HOME; wait_until 10 focus; }
hasd() { screen | grep -q "content-desc=\"$1\""; }  # a node with this content-desc
valid() { screen >/dev/null; [ -s "$SCR" ]; } # a screen read that worked: an absent node means nothing without one
hasnt() { valid && ! has "$1"; }
nod() { valid && ! hasd "$1"; }
settled() { local a; a=$(dump); [ -n "$a" ] && [ "$a" = "$(dump)" ]; } # two screen dumps in a row alike: nothing is moving
# Stops the Clock app and deletes its timers. A force-stop is not enough: the next timer intent starts the app again and
# it re-arms the timers it saved, so a 15-minute timer of an earlier run rings mid-run and its heads-up over the screen
# top takes the taps meant for the home.
noclock() { is_emulator || return 0; A shell pm clear com.google.android.deskclock >/dev/null 2>&1; }
# pg_settings [menu row] [hint]: the classic home's long-press menu (1900 is an empty spot) → Ganimede settings, which
# shows its hint (Italian by default). For a fresh start (after seed or setp): a running Settings comes back on its last page.
pg_settings() {
  local hint="${2:-}"
  [ -n "$hint" ] || hint="Tocca una parte dell'anteprima per regolarla"
  A shell input swipe 540 1900 540 1900 1500; tap "${1:-Impostazioni Ganimede}"
  expect "$hint"
}
# one node (the dialog message is one) whose text holds all the fragments in order (the dump wraps a text holding " in single quotes)
shows() { local f re='text=["'"'"'][^>]*'; for f in "$@"; do re="$re$f[^>]*"; done; screen | grep -q "$re"; }
noshows() { ! shows "$@"; }
# fdiff a.png b.png [WxH+X+Y]: how many pixels differ (inside the crop, if given). Pixels, never file bytes: a PNG
# written by magick carries its dates, so cmp calls two identical pictures different (issue 086).
hold() { A shell input draganddrop "$1" "$2" "$1" "$2" 1500; }  # long-press in place and release: opens the item's menu
clear_text() { A shell input keyevent KEYCODE_MOVE_END; A shell input keyevent $(printf 'KEYCODE_DEL %.0s' $(seq 24)); }
fdiff() { compare -metric AE "$1${3:+[$3]}" "$2${3:+[$3]}" null: 2>&1 | grep -oE '^[0-9]+' | head -1; }
# seed classic|list: a configured app without the wizard: onboarded, mode, favourites, Italian, the home role.
# (the APK must be installed). Leaves the home screen in front.
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
    <string name="icon_shape">squircle</string>
</map>
XML
  A exec-in "run-as $PK sh -c 'mkdir -p shared_prefs; cat > shared_prefs/ganimede.xml'" < "$f"
  A shell cmd role add-role-holder android.app.role.HOME $PK >/dev/null 2>&1
  start_home
  drawn() { dump | grep -q "package=\"$PK\""; }; wait_until 10 drawn # touches sent before the first frame are lost
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
  wait_until 8 hasd "Show roots"
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
