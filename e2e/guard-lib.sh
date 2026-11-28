#!/usr/bin/env bash
# guard.sh runs this before any device is touched: lib.sh's pure helpers (edit_prefs, typ, differ) run under `set -u`, as run.sh
# runs them, against made-up data and a fake adb. A `$3: unbound variable` (edit_prefs del, run 46108965810) is caught here,
# not by a shard that stopped mid-suite. Needs ImageMagick like lib.sh does; without it there is nothing to run, and it says so.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
command -v magick >/dev/null && command -v compare >/dev/null || { echo "guard-lib: no ImageMagick, helpers not exercised" >&2; exit 0; }
T=$(mktemp -d); trap 'rm -rf "$T" "/tmp/ganimede-e2e-emulator-guard-$$.lock"' EXIT
mkdir "$T/bin"
cat > "$T/bin/adb" <<'FAKE' # only what lib.sh's start and the helpers below ask of a device
#!/usr/bin/env bash
[ "$1" = -s ] && shift 2
case "$*" in
  "shell getprop sys.boot_completed") echo 1 ;;
  "shell wm density") echo "Physical density: 420" ;;
  "shell wm size") echo "Physical size: 1080x2424" ;;
  "shell settings get "*) echo null ;;
  "shell dumpsys input_method") echo "mInputShown=true" ;;
  "shell input text "*) printf %s "${4//%s/ }" >> "$FAKE_FIELD" ;;
  "exec-out uiautomator dump /dev/tty") printf '<hierarchy><node class="android.widget.EditText" text="%s" focused="true" bounds="[0,0][1,1]" /></hierarchy>\n' "$(cat "$FAKE_FIELD")" ;;
esac
FAKE
chmod +x "$T/bin/adb"
export PATH="$T/bin:$PATH" FAKE_FIELD="$T/field"; : > "$FAKE_FIELD"
fails=0
n() { ! "$@"; } # n command…: it must fail (t's command is a function of lib.sh, which a bash -c would not see)
t() { # t description command…: the command must succeed
  local d=$1; shift; "$@" || { echo "guard-lib: $d" >&2; fails=$((fails + 1)); }
}
SERIAL="emulator-guard-$$"; LIB="$HERE/lib.sh"; HERE="$T"; . "$LIB" # a made-up emulator and a scratch HERE: nothing of the checkout's e2e/out is touched
exec 9>&-; trap - EXIT; trap 'rm -rf "$T" "/tmp/ganimede-e2e-emulator-guard-$$.lock"' EXIT # lib.sh's own trap and lock are not needed from here
CLEANUP=(); FAIL=0

# edit_prefs: every kind, a value with &, and del as the last word with no value (the case that stopped run 46108965810)
P="$T/prefs.xml"
printf "<map>\n    <int name=\"a\" value=\"1\" />\n    <string name=\"gone\">x</string>\n    <set name=\"s\">\n        <string>old</string>\n    </set>\n</map>\n" > "$P"
edit_prefs "$P" int a 2 boolean b true string c 'x&y' set s item del gone
t "edit_prefs: int replaced"     grep -q '<int name="a" value="2" />' "$P"
t "edit_prefs: boolean written"  grep -q '<boolean name="b" value="true" />' "$P"
t "edit_prefs: a & stays a &"    grep -q '<string name="c">x&y</string>' "$P"
t "edit_prefs: set replaced whole" grep -q '<set name="s"><string>item</string></set>' "$P"
t "edit_prefs: old set item gone" n grep -q "<string>old</string>" "$P"
t "edit_prefs: del removed the key" n grep -q gone "$P"
t "edit_prefs: del alone, no value" edit_prefs "$P" del a
t "edit_prefs: del alone removed the key" n grep -q 'name="a"' "$P"

# typ: the text arrives once, spaces included, into an empty field and into one that holds something
typ "cal bar"
t "typ: empty field holds the text" test "$(field)" = "cal bar"
typ "zz"
t "typ: a field with text takes more" test "$(field)" = "cal barzz"

# differ: two pictures that differ, two that don't, a missing file ("no", not "differ")
magick -size 4x4 xc:red "$T/r.png"; magick -size 4x4 xc:blue "$T/b.png"; cp "$T/r.png" "$T/r2.png"
t "differ: red and blue differ" differ "$T/r.png" "$T/b.png"
t "differ: red and red don't" n differ "$T/r.png" "$T/r2.png"
t "differ: a missing file is no difference" n differ "$T/r.png" "$T/none.png"

[ $fails = 0 ] || { echo "guard-lib: $fails helper check(s) failed under set -u (lib.sh)" >&2; exit 1; }
