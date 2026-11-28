#!/usr/bin/env bash
# Shared helpers of the e2e scripts. Source it after setting SERIAL and HERE:  . "$HERE/lib.sh"
# Emulators, or a physical test phone the owner listed in E2E_DEVICES; one e2e at a time per device (flock);
# animations off (ANIM=1 to keep them). Anything that destroys data or changes a device setting runs on emulators only, except
# making Ganimede the Home app on a listed test phone
# (emu_only, setting); every setting changed is put back on exit, also on Ctrl-C or a failure (keep, defer).
case "$SERIAL" in emulator-*) ;; *) case " ${E2E_DEVICES:-} " in *" $SERIAL "*) ;; *) echo "emulators only: $SERIAL (or list it in E2E_DEVICES)" >&2; exit 2;; esac;; esac
ADB=$(command -v adb || echo "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb")
"$ADB" start-server >/dev/null 2>&1 # before fd 9 exists: a server started by our first adb call would inherit the lock and keep it after the run
exec 9>"/tmp/ganimede-e2e-$SERIAL.lock"; flock 9
OUT="$HERE/out/$SERIAL"; mkdir -p "$OUT"; rm -f "$OUT"/fail-* # per device: two runs from one checkout keep apart; fail-N is this run's
SCR="$OUT/screen.xml" # the last screen read, a file so that subshells share it
PK=org.ganimede.launcher
# Every adb call goes through A. One that can change the screen drops the cached screen read (see `screen`).
A() {
  # reads that leave the screen as it was keep the cached dump (a logcat -d, a service check, a role or package query, a property)
  case "$*" in "exec-out run-as $PK cat "*|"exec-out screencap "*|"exec-out uiautomator "*|"shell dumpsys "*|"shell settings get "*\
    |"logcat -d "*|"shell service check "*|"shell cmd role get-role-holders "*|"shell cmd package resolve-activity "*|"shell pm path "*|"shell getprop "*\
    |"shell wm size"|"shell wm density") ;; *) rm -f "$SCR";; esac
  "$ADB" -s "$SERIAL" "$@"
}
command -v magick >/dev/null && command -v compare >/dev/null || { echo "ImageMagick (magick, compare) is missing: the wallpaper and pixel checks need it." >&2; exit 2; }
# The fixture app (e2e/fixture), built only when its APK is missing or older than its sources (CI builds it once, in the build job), then installed.
fixture_install() {
  local apk="$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" out
  if [ ! -f "$apk" ] || [ -n "$(find "$HERE/fixture/src" "$HERE/fixture/build.gradle" -newer "$apk" -type f -print -quit)" ]; then
    out=$("$HERE/../gradlew" -p "$HERE/.." -q -Pe2e :fixture:assembleDebug 2>&1) || { echo "$out" | tail -30 >&2; return 1; } # gradle's own words
  fi
  A install -r "$apk" >/dev/null
}
# The fixture's ScreenReader service stands in for TalkBack: it lists and performs a node's actions, and logs what is announced.
FIX=org.ganimede.fixture
A11Y_TAG=FixtureA11y
a11y_on() { # enables it; a11y_off (also run on exit) puts the device's own accessibility settings back (a test phone may run TalkBack)
  A11Y_SERVICES_BEFORE=$(A shell settings get secure enabled_accessibility_services | tr -d '\r'); A11Y_ENABLED_BEFORE=$(A shell settings get secure accessibility_enabled | tr -d '\r')
  defer a11y_off
  # Returns once the system has bound it (its "connected" line), never on the setting alone: in run 46109393420 (folders-b,
  # 0.1 s after a fresh install, 3 min after boot) it was never bound and every a11y call went unanswered. A fresh install is
  # first seen by the package manager; the setting is deleted before each write so that the write is a change the system
  # acts on (also when it already held the service), and written a second time if no bind follows: the precondition, not
  # the gesture under test.
  wait_until 10 a11y_installed || ko "the package manager lists no ScreenReader service of the fixture"
  local _
  for _ in 1 2; do
    A logcat -b main -c
    A shell settings delete secure enabled_accessibility_services >/dev/null
    A shell settings put secure enabled_accessibility_services $FIX/.ScreenReader >/dev/null
    A shell settings put secure accessibility_enabled 1 >/dev/null
    wait_until 20 a11y_connected && return 0
  done
  ko "the fixture's ScreenReader was not bound after two writes of the setting; $(a11y_state)"
}
a11y_installed() { A shell cmd package query-services --brief -a android.accessibilityservice.AccessibilityService | grep -qE "$FIX/($FIX)?\.ScreenReader"; } # short (pkg/.Cls) or full form, any indent
a11y_connected() { A logcat -d -s $A11Y_TAG:I | grep -q 'connected'; }
a11y_state() { # the system's view of the accessibility services, for a ko
  echo "the system's accessibility state: $(A shell dumpsys accessibility \
    | grep -E 'installedServiceCount|Bound services|Enabled services|Binding services|Crashed services|UiAutomation' | cut -c1-200 | tr -s ' \r\n' ' ')"
}
a11y_off() {
  if [ "$A11Y_SERVICES_BEFORE" = null ] || [ -z "$A11Y_SERVICES_BEFORE" ]; then A shell settings delete secure enabled_accessibility_services >/dev/null
  else A shell settings put secure enabled_accessibility_services "$A11Y_SERVICES_BEFORE" >/dev/null; fi
  A shell settings put secure accessibility_enabled "${A11Y_ENABLED_BEFORE/null/0}" >/dev/null
}
a11y_done() { A logcat -d -s $A11Y_TAG:I | grep -q 'done performed='; }
a11y() { # a11y list|click|label [regex] [desc]: asks the service to act on the node whose description starts with desc (default the clock) and waits for its answer
  local _ # the service binds some seconds after the setting (or after the install): no answer means not listening yet, ask again
  for _ in 1 2 3 4; do
    A logcat -b main -c
    A shell am broadcast -a org.ganimede.fixture.A11Y -p $FIX --es desc "'${3:-Orologio}'" --es mode "$1" --es label "'${2:-.}'" >/dev/null # desc and label quoted again for the device shell: a space splits, a | is a pipe
    wait_until 5 a11y_done && return 0
  done
  # never answered: run 46109393420 (folders-b) showed only that no fixture process ever started; this line says why
  ko "the fixture's ScreenReader never answered ($1 ${3:-Orologio}); $(a11y_state)"
  return 1
}
a11y_performed() { A logcat -d -s $A11Y_TAG:I | grep -q 'done performed=true'; }
a11y_said() { A logcat -d -s $A11Y_TAG:I | grep -q "announce .*$1"; } # what the app announced since the last a11y call
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
# Every step's time, printed when the next one starts (run.sh closes a section's last one): the slow steps show in the log.
STEP_T=
step_time() { [ -z "$STEP_T" ] || echo "  (step: $((SECONDS - STEP_T)) s)"; STEP_T=; }
step() { step_time; echo; echo "== $*"; STEP_T=$SECONDS; }
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
# typ text: types once the keyboard is up, then checks the focused field holds what it held plus the text. Keys sent while the
# keyboard is still coming up arrive out of order ("cal" typed as "cla", run 46083578007, drawer), and Gboard reorders keys
# with the keyboard up too ("pro pro" came out "por pro", run 46107202244, shard 9). A wrong field is emptied (what it held
# before is lost then) and typed again, 3 tries, then a ko with the text found. The text is the precondition of the check
# that follows, not a gesture to retry.
field() { screen | grep -o '<node [^>]*class="android.widget.EditText"[^>]*focused="true"[^>]*>' | head -1 | sed -n 's/.* text="\([^"]*\)".*/\1/p'; }
typ() { # %s is the space of "input text": a bare space cuts the text there
  local want="$1" before got n=0
  wait_until 5 kbd; before=$(field)
  while :; do
    A shell input text "${want// /%s}"
    got=$(field)
    [ "$got" = "$before$want" ] || [ "$got" = "$want" ] && return 0
    n=$((n + 1)); [ $n -lt 3 ] || { ko "typ: the field holds “$got”, not “$want” after 3 tries"; return 1; }
    A shell input keyevent KEYCODE_MOVE_END $(printf 'KEYCODE_DEL %.0s' $(seq $((${#got} + 1)))); before=
  done
}
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
launcher_comps() { # installed launcher activities as "package/class", sorted (query-activities --brief indents lines and may print ".Class" short names)
  A shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    | tr -d '\r' | sed 's/^ *//' | grep '^[A-Za-z0-9_.]*/' | awk -F/ '{c=$2; if (c ~ /^\./) c=$1 c; print $1"/"c}' | sort -u
}
# the stored value of a pref, int or string (empty when unset)
_val() { sed -n "s|.*name=\"$1\" value=\"\([^\"]*\)\".*|\1|p; s|.*name=\"$1\">\([^<]*\)<.*|\1|p"; } # "|" not "/": keys such as use:pkg/Activity hold slashes
pref() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | _val "$1"; }
# The negative checks on the prefs: an empty or unreadable file holds nothing, so "does not hold X" would pass on it. These are false
# unless the file was read whole (it ends with </map>). pref_lacks key regex: the value of the key doesn't match; set_lacks key regex: no item of the set does.
prefs_read() { P=$(A exec-out run-as $PK cat shared_prefs/ganimede.xml); grep -q '</map>' <<<"$P"; }
pref_lacks() { prefs_read && ! grep -q "$2" <<<"$(_val "$1" <<<"$P")"; }
set_lacks() { prefs_read && ! sed -n "\\@name=\"$1\"@,\\@</set>@p" <<<"$P" | grep -q "$2"; }
# bounds of the first node whose text (or content-desc with "desc:") is exactly $1: "x1 y1 x2 y2", empty if none
bounds() {
  local attr=text v="$1" r; case "$v" in desc:*) attr=content-desc; v="${v#desc:}";; esac
  # a screen dump now and then comes back empty: try again
  _bounds_try() { r=$(screen | grep -o "$attr=\"$v\"[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' '); [ -n "$r" ]; }
  wait_until 3 _bounds_try && echo "$r"
  return 0
}
# hsee text row-text: swipes the sideways row holding row-text until the node "text" is whole inside the card (at most 6 times)
hsee() {
  local n=0 y b x1 y1 x2 y2; b=$(bounds "$2"); [ -n "$b" ] || return 1; read -r x1 y1 x2 y2 <<<"$b"; y=$(((y1 + y2) / 2))
  while [ $n -lt 6 ]; do
    b=$(bounds "$1"); [ -n "$b" ] && { read -r x1 y1 x2 y2 <<<"$b"; [ "$x2" -le $((W - $(dp 24))) ] && return 0; }
    A shell input swipe $((W - 100)) $y 200 $y 300; rm -f "$SCR"; n=$((n + 1))
  done
  ko "no “$1” in the row of “$2”"; return 1
}
# label_whole text min-width% [min-height%]: the node's label is drawn whole (issue 088): its crop, trimmed, covers that much of the node
label_whole() {
  local b x1 y1 x2 y2 w h tw th; b=$(bounds "$1"); [ -n "$b" ] || return 1; read -r x1 y1 x2 y2 <<<"$b"
  w=$((x2 - x1)); h=$((y2 - y1))
  read -r tw th <<<"$(A exec-out screencap -p | magick png:- -crop "${w}x${h}+${x1}+${y1}" +repage -fuzz 12% -trim -format '%w %h' info: 2>/dev/null)"
  [ -n "$th" ] && [ $((tw * 100)) -ge $(($2 * w)) ] && [ $((th * 100)) -ge $((${3:-0} * h)) ]
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
# scroll_find cmd…: runs the command on each screen read, scrolling a page between; 0 found, 1 the page's end reached without it
# (two scrolls in a row that change nothing: one can be swallowed; a text absent from a long page cost 30 scrolls, ~80 s), 2 gave up after 30 scrolls
scroll_find() {
  local n=0 prev= cur same=0
  while [ $n -lt 30 ]; do
    "$@" && return 0
    cur=$(screen); if [ -n "$cur" ] && [ "$cur" = "$prev" ]; then same=$((same + 1)); [ $same -ge 2 ] && return 1; else same=0; fi; prev=$cur
    _scroll; n=$((n + 1))
  done; return 2
}
see() { scroll_find has "$1" && { ok "sees “$1”"; return 0; }; ko "doesn't see “$1”"; return 1; }
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
  local f="$1" v; shift
  while [ $# -ge 2 ]; do
    [ "$1" = del ] || [ $# -ge 3 ] || { ko "edit_prefs: no value for $2"; return 1; } # not a crash on $3 under set -u
    v=${3-}; v=${v//&/\\&} # a & in the value is the whole match to sed: the callers write it plain
    # @: keys hold "/" (use:package/class). A set is deleted whole: its items sit on lines of their own up to </set>
    sed -i -e "\\@^ *<set name=\"$2\">\$@,\\@</set>@d" -e "\\@ name=\"$2\"@d" "$f"
    case $1 in
      int|boolean) sed -i "s@</map>@    <$1 name=\"$2\" value=\"$3\" />\n</map>@" "$f" ;;
      string) sed -i "s@</map>@    <string name=\"$2\">$v</string>\n</map>@" "$f" ;;
      set) sed -i "s@</map>@    <set name=\"$2\"><string>$v</string></set>\n</map>@" "$f" ;;
    esac
    if [ "$1" = del ]; then shift 2; else shift 3; fi
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
# A framework restart (run 46105280536, shard 2, folders: system_server pid 562 -> 11563, a black screen, "cmd: Can't find
# service: activity/role") is no role problem: it says so, keeps what killed it, and waits for Android to come back.
system_up() { A shell service check activity | grep -q ': found' && A shell service check role | grep -q ': found'; }
start_home() {
  system_up || { { A logcat -d -b crash; A logcat -d -b system | grep -iE "watchdog|fatal|system_server" | tail -40; } > "$OUT/system-restart.txt" 2>&1
    ko "Android's system server is down (restarting): not a role problem, what killed it is in system-restart.txt"
    wait_until 180 system_up || return 1; }
  A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK" \
    || A shell cmd role add-role-holder android.app.role.HOME $PK >/dev/null 2>&1 # another launcher holding HOME would start a second task
  A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK" || { ko "Ganimede doesn't hold the HOME role"; return 1; }
  A shell am force-stop $PK; A shell input keyevent KEYCODE_HOME; wait_until 10 focus && wait_until 10 drawn && wait_until 10 settled; }
target48() { local b; b=$(bounds "$1"); [ -n "$b" ] || return 1; set -- $b; [ $(($3 - $1)) -ge $(($(dp 48) - 1)) ] && [ $(($4 - $2)) -ge $(($(dp 48) - 1)) ]; } # target48 text|desc:…: the node is at least 48 x 48 dp
hasd() { screen | grep -q "content-desc=\"$1\""; }  # a node with this content-desc
valid() { screen >/dev/null; [ -s "$SCR" ]; } # a screen read that worked: an absent node means nothing without one
hasnt() { valid && ! has "$1"; }
nod() { valid && ! hasd "$1"; }
# two screen reads in a row alike: nothing is moving. The first is the cached one when nothing has acted on the screen since
# (start_home's drawn just read it): one uiautomator dump (~2 s) less per home restart.
settled() { local a; a=$(screen); [ -n "$a" ] && [ "$a" = "$(dump)" ]; }
# Stops the Clock app and deletes its timers. A force-stop is not enough: the next timer intent starts the app again and
# it re-arms the timers it saved, so a 15-minute timer of an earlier run rings mid-run and its heads-up over the screen
# top takes the taps meant for the home.
noclock() { is_emulator || return 0; A shell pm clear com.google.android.deskclock >/dev/null 2>&1; }
# timer: a 15-minute Clock timer, waited until Android lists its notification (timer_up); noclock takes it away
timer() { A shell am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH 900 \
  --ez android.intent.extra.alarm.SKIP_UI true >/dev/null 2>&1; wait_until 10 timer_up; }
timer_up() { A shell dumpsys notification | grep -q "pkg=com.google.android.deskclock.*Timers"; }
listening() { A shell settings get secure enabled_notification_listeners | grep -q NotifService; } # Ganimede is a notification listener
bpref() { [ "$(pref "$1")" = "${2:-true}" ]; } # bpref key [true|false]: the stored boolean is that
turned() { screen | grep -q '<hierarchy[^>]*rotation="[13]"'; } # the dump's root says the display is turned
docked() { read -r _ y <<<"$(cx "desc:Calendar")"; [ -n "$y" ] && [ "$y" -gt $((H - $(dp 200))) ]; } # Calendar sits in the dock
# clock_bounds: the clock's bounds, empty (and false) unless they are a real box inside the screen
clock_bounds() {
  local b
  b=$(bounds "desc:Orologio, tocca per le sveglie")
  [ -n "$b" ] || return 1
  set -- $b
  [ "$1" -ge 0 ] && [ "$2" -ge 0 ] && [ "$3" -le "$W" ] && [ "$4" -le "$H" ] \
    && [ "$3" -gt "$1" ] && [ "$4" -gt "$2" ] || return 1
  echo "$b"
}
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
# fdiff a.png b.png [WxH+X+Y]: how many pixels differ (inside the crop, if given; FUZZ=10% fdiff ... forgives small colour differences). Pixels, never file bytes: a PNG
# written by magick carries its dates, so cmp calls two identical pictures different (issue 086).
# compare prints a big count as 1.04858e+06: awk turns it into an integer
fdiff() { compare -metric AE ${FUZZ:+-fuzz "$FUZZ"} "$1${3:+[$3]}" "$2${3:+[$3]}" null: 2>&1 | awk 'match($0, /^[0-9.]+(e[+-]?[0-9]+)?/) { printf "%d\n", substr($0, RSTART, RLENGTH); exit }'; }
# differ a.png b.png [crop]: they differ by at least one pixel. An fdiff that fails (a missing file, no number) is "no", never "differ"
# and never "same": `! same` passed on it, `same && ko || ok` too. Use differ wherever a check wants a difference.
differ() { [ "$(fdiff "$@")" -gt 0 ] 2>/dev/null; }
hold() { A shell input draganddrop "$1" "$2" "$1" "$2" 1500; }  # long-press in place and release: opens the item's menu (home or pop-up)
clear_text() { A shell input keyevent KEYCODE_MOVE_END; A shell input keyevent $(printf 'KEYCODE_DEL %.0s' $(seq 24)); }
# seed classic|list [setp's settings ...]: a configured app without the wizard: onboarded, mode, favourites, Italian, the
# home role, and the settings given, all in one start (the APK must be installed). Leaves the home screen in front.
SEED_FAV="com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;com.google.android.deskclock/com.android.deskclock.DeskClock&#10;com.google.android.contacts/com.android.contacts.activities.PeopleActivity" # seed's favourites
seed() {
  local f="$OUT/seed.xml"
  A shell am force-stop $PK; noclock
  cat > "$f" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarded" value="true" />
    <boolean name="fab" value="true" />
    <string name="mode">$1</string>
    <string name="fav">$SEED_FAV</string>
    <string name="lang">it</string>
    <string name="icon_container">squircle</string>
</map>
XML
  shift; edit_prefs "$f" "$@"
  put_prefs "$f"
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

# Helpers of the icon sections (18-icons-a, 18-icons-b), each of which runs alone.
# The icon model (issue 078) and the built-in Arcticons pack (077): shared names.
RED="#E01010"
# the Icons card tiles as TalkBack names them: "group: choice" (the rows share some names)
S="Stile delle icone: " P="Piastra: " C="Colore: "

capture() { # capture desc out.png: only that accessible cell/row/tile, twice alike (an icon may still be drawing)
  local b n=0
  rm -f "$OUT/icons-prev.png"
  while [ $n -lt 5 ]; do
    b=$(bounds "desc:$1"); [ -n "$b" ] || return 1
    set -- "$1" "$2" $b
    A exec-out screencap -p > "$OUT/icons-screen.png"
    magick "$OUT/icons-screen.png" -crop "$(( $5 - $3 ))x$(( $6 - $4 ))+$3+$4" +repage "$2"
    [ -f "$OUT/icons-prev.png" ] && same "$OUT/icons-prev.png" "$2" && return 0
    # a new screen read (~2 s) only once two shots differ, something moved: two alike at one place need none
    [ -f "$OUT/icons-prev.png" ] && rm -f "$SCR"
    cp "$2" "$OUT/icons-prev.png"; n=$((n + 1))
  done
  return 1 # never two shots alike: the picture may still be moving, no caller may compare it
}
capture_calendar() { capture Calendar "$1"; }
same() { [ "$(fdiff "$1" "$2")" = 0 ]; } # same pixels (lib.sh fdiff: never cmp on PNGs)

colour_pixels() { # png #RRGGBB: how many pixels have that colour
  magick "$1" -fuzz 4% -fill "$2" -opaque "$2" -depth 8 -format %c histogram:info:- \
    | awk -v c="$2" '$0 ~ c {gsub(":", "", $1); print $1; found=1} END{if (!found) print 0}'
}
box() { # png #RRGGBB: "x y w h" around the pixels of that colour (nothing when none)
  magick "$1" -alpha off -fuzz 4% -fill black +opaque "$2" -fill white -opaque "$2" -format "%@" info: 2>/dev/null \
    | sed -E 's/^([0-9]+)x([0-9]+)\+([0-9]+)\+([0-9]+)$/\3 \4 \1 \2/' | grep -E '^[0-9]+ [0-9]+ [0-9]+ [0-9]+$'
}
pixel() { magick "$1" -format "%[hex:u.p{$2,$3}]" info: | cut -c1-6; } # png x y: RRGGBB
alike() { # hex hex: every channel within 10
  local a=$((16#$1)) b=$((16#$2)) s d
  for s in 16 8 0; do d=$(( ((a >> s) & 255) - ((b >> s) & 255) )); [ ${d#-} -le 10 ] || return 1; done
}
# Every restart costs ~9 s (lib.sh setp): a step gives all its settings to one seed or setp.
model() { echo string icon_glyph "$1" string icon_container "$2" string icon_fill "$3" string icon_color "$4"; } # as setp's words
look() { setp $(model "$1" "$2" "$3" "$4") "${@:5}"; } # look glyph container fill colour [setp's settings ...]
exact() { screen | grep -q "text=\"$1\""; }
nofill() { valid && ! exact "Riempimento"; }
reach() { # reach desc [neighbour]: the tile on screen, looked for below then above (a picked tile scrolls its row: swipe it right)
  local i xy dir
  for dir in "2100 1600" "1600 2100"; do # slow swipes below the preview and the pills: no fling past the row
    for i in $(seq 8); do
      hasd "$1" && break 2; [ -n "${2:-}" ] && hasd "$2" && break 2
      A shell input swipe 150 ${dir% *} 150 ${dir#* } 1200; rm -f "$SCR"
    done
  done
  # The neighbour as find_desc gives it, above y 2300: a row half under the navigation bar is in the dump (Settings draws
  # edge to edge), and a swipe to the right along the bar switches to the previous app (run imp/5: Camera, from 11-gestures).
  if [ -n "${2:-}" ] && ! hasd "$1" && xy=$(find_desc "$2"); then set -- "$1" $xy; A shell input swipe 150 "$3" 900 "$3" 1500; wait_until 5 settled; fi # slow: no fling to stop with the tap
  find_desc "$1"
}
tapd() { local xy; xy=$(reach "$@") && [ -n "$xy" ] && { set -- $xy; A shell input tap "$1" "$2"; }; }
stored() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "<string name=\"$1\">$2</string>"; }
BUILTIN=builtin:arcticons
CAL=com.google.android.calendar/com.android.calendar.AllInOneActivity

# The folder pop-up's card (12-folders-a and -b)
card() { # the pop-up card: the last ScrollView of the dump (the pop-up window comes after the home), "x1 y1 x2 y2"
  screen | grep -o 'class="android.widget.ScrollView"[^>]*bounds="[^"]*"' | tail -1 \
    | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' '
}
