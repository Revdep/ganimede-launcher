# Issues 043/050: the Ganimede button image is chosen from Settings → Pulsante Ganimede; there is no app icon choice.
# The only launcher entry is .IconDefault (an upgrade from main's build must keep it so); MainActivity is HOME only.

button_page() {
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus || return 1
  home_menu || return 1; tap "Impostazioni Ganimede" || return 1 # not pg_settings: Settings may come back on the button page
  stap "Pulsante Ganimede" || return 1
  wait_until 5 has "Mostra il pulsante Ganimede"
}

choice_after() { # tap the choice with this content-description below its chooser label (inline tiles/radios)
  local lb label_bottom nodes b x1 y1 x2 y2 best= best_y=$H
  lb=$(bounds "$1"); [ -n "$lb" ] || return 1
  read -r _ _ _ label_bottom <<<"$lb"
  nodes=$(screen | grep -oE "content-desc=\"($2|$1: $2)\"[^>]*bounds=\"[^\"]*\"" \
    | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' ')
  while read -r b; do
    [ -n "$b" ] || continue
    read -r x1 y1 x2 y2 <<<"$b"
    if [ "$y1" -ge "$label_bottom" ] && [ "$y1" -lt "$best_y" ]; then best="$b"; best_y=$y1; fi
  done <<<"$nodes"
  [ -n "$best" ] || return 1
  read -r x1 y1 x2 y2 <<<"$best"
  A shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

choose_icon() { # chooser label, choice: tap the tile by its group-qualified name, scrolling it into view if needed
  button_page || return 1
  local i b x1 y1 x2 y2
  for i in 0 1 2 3; do
    b=$(bounds "desc:$1: $2")
    if [ -n "$b" ]; then read -r x1 y1 x2 y2 <<<"$b"; A shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2)); return 0; fi
    _scroll
  done
  ko "“$1” does not offer “$2”"; return 1
}

launcher_component() {
  A shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER "$PK" 2>/dev/null | tr -d '\r' | tail -n 1
}
launcher_is() {
  local c; c=$(launcher_component)
  [ "$c" = "$PK/.$1" ] || [ "$c" = "$PK/$PK.$1" ]
}
launcher_entries() {
  A shell cmd package query-activities --brief -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER "$PK" 2>/dev/null | tr -d '\r' | sed 's/^ *//' \
    | grep -Ec "^$PK/(\.|$PK\.)Icon(Default|Gray|Colour)$"
}
one_launcher_entry() { [ "$(launcher_entries)" -eq 1 ]; }
home_component() {
  A shell cmd package resolve-activity --brief -a android.intent.action.MAIN \
    -c android.intent.category.HOME 2>/dev/null | tr -d '\r' | tail -n 1
}
home_is_ganimede() {
  local c; c=$(home_component)
  [ "$c" = "$PK/.MainActivity" ] || [ "$c" = "$PK/$PK.MainActivity" ]
}

fab_icon() {
  A exec-out run-as $PK cat shared_prefs/ganimede.xml | tr -d '\r' | grep ' name="fab_icon"' \
    | sed -E 's/.*value="([^"]*)".*/\1/; s/.*">([^<]*)<.*/\1/'
}

centre_pixel() { # screenshot path [content-desc]: print the Ganimede button's centre pixel as RRGGBB
  local b x1 y1 x2 y2 x y
  b=$(bounds "desc:${2:-Pulsante Ganimede}"); [ -n "$b" ] || return 1
  read -r x1 y1 x2 y2 <<<"$b"; x=$(((x1 + x2) / 2)); y=$(((y1 + y2) / 2))
  A exec-out screencap -p > "$1"
  magick "$1" -depth 8 -format "%[hex:p{$x,$y}]" info:- | cut -c1-6
}

vertical_pixels() { # screenshot path: print pixels one quarter from the button's top and bottom
  local b x1 y1 x2 y2 x q top bottom
  b=$(bounds "desc:Pulsante Ganimede"); [ -n "$b" ] || return 1
  read -r x1 y1 x2 y2 <<<"$b"; x=$(((x1 + x2) / 2)); q=$(((y2 - y1) / 4))
  top=$((y1 + q)); bottom=$((y2 - q))
  A exec-out screencap -p > "$1"
  magick "$1" -depth 8 -format "%[hex:p{$x,$top}] %[hex:p{$x,$bottom}]" info:-
}

grey_pixel() {
  local p="$1" r g b max min avg
  [ ${#p} -eq 6 ] || return 1
  r=$((16#${p:0:2})); g=$((16#${p:2:2})); b=$((16#${p:4:2}))
  max=$r; [ $g -gt $max ] && max=$g; [ $b -gt $max ] && max=$b
  min=$r; [ $g -lt $min ] && min=$g; [ $b -lt $min ] && min=$b
  avg=$(((r + g + b) / 3))
  [ $((max - min)) -le 16 ] && [ $avg -ge 30 ] && [ $avg -le 230 ] # the gray logo's core is a dark gray (#383838)
}

custom_pixel() {
  local p="$1" r g b
  [ ${#p} -eq 6 ] || return 1
  r=$((16#${p:0:2})); g=$((16#${p:2:2})); b=$((16#${p:4:2}))
  near "$r" 24 8 && near "$g" 200 8 && near "$b" 154 8 # fixture colour: #18C89A
}

red_pixel() {
  local p=${1:0:6} r g b
  [ ${#p} -eq 6 ] || return 1
  r=$((16#${p:0:2})); g=$((16#${p:2:2})); b=$((16#${p:4:2}))
  [ "$r" -ge 160 ] && [ "$g" -le 100 ] && [ "$b" -le 100 ]
}

blue_pixel() {
  local p=${1:0:6} r g b
  [ ${#p} -eq 6 ] || return 1
  r=$((16#${p:0:2})); g=$((16#${p:2:2})); b=$((16#${p:4:2}))
  [ "$r" -le 120 ] && [ "$g" -le 120 ] && [ "$b" -ge 140 ]
}

component_state() { # exact PackageManager override: default, enabled or disabled
  local alias=$1
  A shell dumpsys package "$PK" | tr -d '\r' | awk -v full="$PK.$alias" -v short=".$alias" -v bare="$alias" '
    $1 == "User" && $2 == "0:" { user = 1; group = ""; next }
    user && $1 == "User" && $2 != "0:" { user = 0 }
    !user { next }
    $1 == "enabledComponents:" { group = "enabled"; next }
    $1 == "disabledComponents:" { group = "disabled"; next }
    group != "" && ($1 == full || $1 == short || $1 == bare) { print group; found = 1; exit }
    group != "" && /:$/ { group = "" }
    END { if (!found) print "default" }
  '
}

effective_state() { # alias, saved state: "default" means the manifest's own value (shell may not reset it to default)
  case "$2" in default) [ "$1" = IconDefault ] && echo enabled || echo disabled ;; *) echo "$2" ;; esac
}
restore_component() {
  local command
  case "$(effective_state "$1" "$2")" in
    enabled) command=enable ;;
    disabled) command=disable ;;
    *) ko "unknown saved state '$2' for $1"; return 1 ;;
  esac
  A shell pm "$command" --user 0 "$PK/.$1" >/dev/null
}

checked_choice_after() { # chooser label, description suffix
  local lb label_bottom nodes b x1 y1 x2 y2
  lb=$(bounds "$1"); [ -n "$lb" ] || return 1
  read -r _ _ _ label_bottom <<<"$lb"
  nodes=$(screen | grep -o "content-desc=\"[^\"]*$2\"[^>]*checked=\"true\"[^>]*bounds=\"[^\"]*\"" \
    | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' ')
  while read -r b; do
    [ -n "$b" ] || continue
    read -r x1 y1 x2 y2 <<<"$b"
    [ "$y1" -ge "$label_bottom" ] && return 0
  done <<<"$nodes"
  return 1
}

# This section deliberately mutates aliases and prefs, so preserve their exact incoming states before seed resets them.
PREFS_BEFORE="$OUT/appicon-prefs-before.xml"
PREFS_AFTER="$OUT/appicon-prefs-after.xml"
if A exec-out run-as "$PK" cat shared_prefs/ganimede.xml > "$PREFS_BEFORE" 2>/dev/null; then
  PREFS_EXISTED=1
else
  PREFS_EXISTED=0; rm -f "$PREFS_BEFORE"
fi
ICON_DEFAULT_BEFORE=$(component_state IconDefault)

seed classic
A shell run-as "$PK" rm -f files/fab-icon.png

step "Icon choosers: only the button icon is offered, every tile has its group-qualified accessible name"
button_page
for description in \
  "Icona del pulsante: Predefinita" "Icona del pulsante: Grigia" "Icona del pulsante: Colori" \
  "Icona del pulsante: Immagine personale…"; do
  shown_desc() { hasd "$1" || { _scroll; hasd "$1"; }; } # the second button row sits below the fold once tiles carry thumbnails
  shown_desc "$description" \
    && ok "the tile is named '$description'" \
    || ko "no visible tile named '$description'"
done
has "Icona del pulsante" && ok "the page offers Icona del pulsante" || ko "the page lost Icona del pulsante"
app_chooser_gone() { ! has "Icona dell'app" && ! hasd "Icona dell'app: Predefinita" && ! hasd "Icona dell'app: Grigia" && ! hasd "Icona dell'app: Colori"; }
button_page
app_chooser_gone \
  && ok "the page has no Icona dell'app chooser" \
  || ko "the Pulsante Ganimede page still offers Icona dell'app"

# No public build ever offered the app icon chooser (the public history starts after issue 050 removed it), so the
# upgrade users can make is from main's build: e2e/build-legacy.sh makes it (the CI build job does), missing is a fail.
# What an update must keep: the prefs the user chose in main's build, the files in its private dir, the HOME role, the one
# launcher entry. Each is set in main's build, then read after the install, before anything of this build starts.
step "Upgrade from main's build: chosen prefs, private files, HOME role and the launcher entry survive the install"
LEGACY_APK="$HERE/legacy/ganimede-main.apk"
# the APK is main as of the merge-base: an older one tests an update from a main that no longer exists (the shards have no history: no check there)
legacy_stale() {
  local want; want=$(git -C "$HERE/.." merge-base origin/main HEAD 2>/dev/null) && want=$(git -C "$HERE/.." rev-parse "$want") || return 1
  [ "$(cat "$HERE/legacy/ganimede-main.commit" 2>/dev/null)" != "$want" ]
}
main_gray() { [ "$(fab_icon)" = gray ]; }
kept_prefs() { [ "$(fab_icon)" = gray ] && [ "$(pref list_apps_y)" = -8 ]; }
if [ ! -f "$LEGACY_APK" ]; then
  ko "no $LEGACY_APK: run e2e/build-legacy.sh"
elif legacy_stale; then
  ko "$LEGACY_APK is not main at the merge-base with origin/main: run e2e/build-legacy.sh"
elif ! A install -r -d "$LEGACY_APK" >/dev/null; then
  ko "setup: main's build did not install"
else
  setp int list_apps_y -8 # in main's build: it restarts the home, which takes the HOME role
  A shell "run-as $PK sh -c 'echo kept > files/upgrade-marker'"
  if choose_icon "Icona del pulsante" "Grigia" && wait_until 5 main_gray \
     && [ "$(pref list_apps_y)" = -8 ] && [ "$(A shell run-as "$PK" cat files/upgrade-marker | tr -d '\r')" = kept ] \
     && A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK"; then
    # The user is not on Ganimede's home during an upgrade: no HOME press, no start. The package itself must put the entry right.
    A install -r -d "$APK" >/dev/null || ko "this build did not install over main's"
    kept_prefs \
      && ok "the prefs chosen in main's build are intact after the upgrade (fab_icon gray, list_apps_y -8)" \
      || ko "after the upgrade fab_icon is '$(fab_icon)' and list_apps_y '$(pref list_apps_y)', expected gray and -8"
    [ "$(A shell run-as "$PK" cat files/upgrade-marker | tr -d '\r')" = kept ] \
      && ok "a file in the app's private dir survives the upgrade" \
      || ko "files/upgrade-marker did not survive the upgrade"
    A shell cmd role get-role-holders android.app.role.HOME | grep -q "$PK" \
      && ok "Ganimede still holds the HOME role right after the upgrade" \
      || ko "the HOME role holder is '$(A shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')' after the upgrade"
    wait_until 10 launcher_is IconDefault \
      && ok "LAUNCHER resolves to .IconDefault right after the upgrade" \
      || ko "LAUNCHER resolves to '$(launcher_component)' right after the upgrade, expected .IconDefault"
    one_launcher_entry \
      && ok "exactly one Ganimede LAUNCHER entry exists right after the upgrade" \
      || ko "$(launcher_entries) Ganimede LAUNCHER entries exist right after the upgrade, expected one"
    home_is_ganimede \
      && ok "HOME still resolves to Ganimede MainActivity" \
      || ko "HOME resolves to '$(home_component)', expected Ganimede MainActivity"
    start_home && kept_prefs \
      && ok "this build starts on main's prefs and keeps them" \
      || ko "after the first start of this build fab_icon is '$(fab_icon)' and list_apps_y '$(pref list_apps_y)', expected gray and -8"
  else
    ko "setup: main's build did not keep what the test chose in it (gray button, list_apps_y -8, a private file, the HOME role)"
  fi
  A shell run-as "$PK" rm -f files/upgrade-marker
fi

step "Ganimede button icon: gray image replaces the star; default restores it"
setp boolean fab true
setp string fab_icon default
gray_text=0; gray_colour=0
wait_until 5 has "✳" && ok "the default button starts with ✳" || ko "the default button has no ✳"
choose_icon "Icona del pulsante" "Grigia" && ok "the button icon chooser offers gray"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
wait_until 5 hasnt "✳" \
  && { ok "gray button icon no longer shows the ✳ text"; gray_text=1; } \
  || ko "gray button icon still shows the ✳ text"
pixel=$(centre_pixel "$OUT/appicon-button-gray.png")
grey_pixel "$pixel" \
  && { ok "gray button icon has a gray centre pixel (#$pixel)"; gray_colour=1; } \
  || ko "gray button icon centre is #${pixel:-missing}, expected gray rather than the accent/star"

choose_icon "Icona del pulsante" "Predefinita" && ok "the button icon chooser offers default"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
if [ $gray_text -eq 1 ] && [ $gray_colour -eq 1 ] && wait_until 5 has "✳"; then
  ok "after a visible gray icon, default brings ✳ back"
else
  ko "default did not bring ✳ back after a visible gray icon"
fi

step "Ganimede button icon: a picked image is saved and displayed"
CUSTOM_NAME=ganimede-button-custom.png
CUSTOM_LOCAL="$OUT/$CUSTOM_NAME"
CUSTOM_DEVICE="/sdcard/Download/$CUSTOM_NAME"
magick -size 64x64 "xc:#18C89A" "$CUSTOM_LOCAL"
A push "$CUSTOM_LOCAL" "$CUSTOM_DEVICE" >/dev/null
if choose_icon "Icona del pulsante" "Immagine personale…"; then
  pick_file "$CUSTOM_NAME" || ko "the document picker did not offer $CUSTOM_NAME"
fi
custom_saved() { [ "$(fab_icon)" = custom ]; }
wait_until 10 custom_saved \
  && ok "the picked image saves fab_icon = custom" \
  || ko "fab_icon = '$(fab_icon)', expected custom after picking an image"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
pixel=$(centre_pixel "$OUT/appicon-button-custom.png")
custom_pixel "$pixel" \
  && ok "custom button centre matches the picked image colour (#$pixel)" \
  || ko "custom button centre is #${pixel:-missing}, expected #18C89A"

step "Ganimede button icon: finishing a slow import updates Home without another action"
LARGE_NAME=ganimede-button-large.png
LARGE_LOCAL="$OUT/$LARGE_NAME"
LARGE_DEVICE="/sdcard/Download/$LARGE_NAME"
magick -size 6000x4000 "xc:#18C89A" -attenuate 1 +noise Random -colorspace gray -depth 8 \
  -define png:color-type=0 "$LARGE_LOCAL"
A push "$LARGE_LOCAL" "$LARGE_DEVICE" >/dev/null
setp string fab_icon default
if choose_icon "Icona del pulsante" "Immagine personale…"; then
  pick_file "$LARGE_NAME" || ko "the document picker did not offer $LARGE_NAME"
fi
# Android hands the picked file to Settings only when Settings is back in front; a Home pressed while the picker is still
# closing leaves the result pending and no import starts (run 46128604173: HOME 4 s after the picker opened, fab_icon stayed
# default for 35 s). So wait for Settings, then press Home without waiting for the worker: Home must notice the late import.
settings_front() { A shell dumpsys window | grep -q "mCurrentFocus.*SettingsActivity"; }
wait_until 10 settings_front || ko "the picker did not return to Settings with $LARGE_NAME"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
import_t=$SECONDS
wait_until 30 custom_saved \
  && ok "the slow import finishes $((SECONDS - import_t)) s after returning Home" \
  || ko "the slow import did not save fab_icon = custom within $((SECONDS - import_t)) s of returning Home"
wait_until 5 hasnt "✳" \
  && ok "the live Home button shows the imported image without another action" \
  || ko "the live Home button still shows ✳ after the import finished"

step "Ganimede button icon: EXIF RightTop is displayed upright"
EXIF_NAME=ganimede-button-right-top.jpg
EXIF_LOCAL="$OUT/$EXIF_NAME"
EXIF_DEVICE="/sdcard/Download/$EXIF_NAME"
magick -size 120x80 "xc:#E02020" -fill "#2040E0" -draw "rectangle 72,0 119,79" \
  -orient RightTop "$EXIF_LOCAL"
# A new JPEG has no EXIF profile for -orient to update, so add the minimal Orientation=6 APP1 record.
python3 -I - "$EXIF_LOCAL" <<'PY'
import struct, sys

path = sys.argv[1]
jpeg = open(path, "rb").read()
tiff = b"II" + struct.pack("<H", 42) + struct.pack("<I", 8)
tiff += struct.pack("<H", 1) + struct.pack("<HHI", 0x0112, 3, 1) + struct.pack("<H", 6) + b"\0\0" + b"\0\0\0\0"
app1 = b"Exif\0\0" + tiff
open(path, "wb").write(jpeg[:2] + b"\xff\xe1" + struct.pack(">H", len(app1) + 2) + app1 + jpeg[2:])
PY
A push "$EXIF_LOCAL" "$EXIF_DEVICE" >/dev/null
setp string fab_icon default
if choose_icon "Icona del pulsante" "Immagine personale…"; then
  pick_file "$EXIF_NAME" || ko "the document picker did not offer $EXIF_NAME"
fi
wait_until 10 custom_saved \
  && ok "the EXIF-oriented JPEG is imported" \
  || ko "the EXIF-oriented JPEG did not save fab_icon = custom"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
read -r top_pixel bottom_pixel <<<"$(vertical_pixels "$OUT/appicon-button-exif.png")"
red_pixel "$top_pixel" \
  && ok "the upright image is red near the top (#${top_pixel:0:6})" \
  || ko "the upright image top is #${top_pixel:0:6}, expected red"
blue_pixel "$bottom_pixel" \
  && ok "the upright image is blue near the bottom (#${bottom_pixel:0:6})" \
  || ko "the upright image bottom is #${bottom_pixel:0:6}, expected blue"

step "Ganimede button icon: unreasonable declared dimensions are refused without losing the previous icon"
HUGE_NAME=ganimede-button-30000.png
HUGE_LOCAL="$OUT/$HUGE_NAME"
HUGE_DEVICE="/sdcard/Download/$HUGE_NAME"
# ImageMagick supplies a real PNG signature; the replacement chunks encode a valid, tiny-on-disk 30,000² 1-bit image.
magick -size 1x1 -depth 1 xc:black "$HUGE_LOCAL"
python3 -I - "$HUGE_LOCAL" <<'PY'
import struct, sys, zlib

path = sys.argv[1]
signature = open(path, "rb").read(8)
width = height = 30_000
row = b"\0" + bytes((width + 7) // 8)
encoder = zlib.compressobj(9)
data = bytearray()
for _ in range(height):
    data += encoder.compress(row)
data += encoder.flush()

def chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))

ihdr = struct.pack(">IIBBBBB", width, height, 1, 0, 0, 0, 0)
open(path, "wb").write(signature + chunk(b"IHDR", ihdr) + chunk(b"IDAT", data) + chunk(b"IEND", b""))
PY
A push "$HUGE_LOCAL" "$HUGE_DEVICE" >/dev/null
setp string fab_icon gray
if choose_icon "Icona del pulsante" "Immagine personale…"; then
  pick_file "$HUGE_NAME" || ko "the document picker did not offer $HUGE_NAME"
fi
# The refusal is a Toast, which uiautomator does not dump (as in 16-backup.sh): the proof is the unchanged choice below.
[ "$(fab_icon)" = gray ] \
  && ok "a refused image keeps the previous fab_icon" \
  || ko "a refused image changed fab_icon to '$(fab_icon)', expected gray"
A shell pidof "$PK" >/dev/null && has "Icona del pulsante" \
  && ok "Ganimede stays alive on the icon chooser after the refusal" \
  || ko "Ganimede crashed or left the icon chooser while refusing the image"

step "Ganimede button icon: restored custom without its private file normalises to default"
A shell run-as "$PK" rm -f files/fab-icon.png
setp string fab_icon custom
normalised_icon() { [ "$(fab_icon)" = default ]; }
wait_until 10 normalised_icon \
  && ok "missing custom image normalises fab_icon to default" \
  || ko "missing custom image left fab_icon = '$(fab_icon)', expected default"
wait_until 5 has "✳" \
  && ok "missing custom image displays the default star" \
  || ko "missing custom image did not display the default star"
button_page
checked_choice_after "Icona del pulsante" "Predefinita" \
  && ok "the button chooser selects Predefinita after normalisation" \
  || ko "the button chooser does not select Predefinita after the custom file is missing"

step "Ganimede button icon: the Settings preview draws the same icon as the home"
seed classic
setp string fab_icon gray
pg_settings
preview_button() { [ -n "$(bounds "desc:Pulsante Ganimede")" ]; }
wait_until 5 preview_button \
  && ok "the preview's button is named Pulsante Ganimede" \
  || ko "the Settings preview has no button named Pulsante Ganimede"
pixel=$(centre_pixel "$OUT/appicon-preview-gray.png")
grey_pixel "$pixel" \
  && ok "the preview's gray button has a gray centre pixel (#$pixel)" \
  || ko "the preview's button centre is #${pixel:-missing}, expected the gray image rather than the accent circle"
hasnt "✳" \
  && ok "the preview's gray button no longer shows the ✳ text" \
  || ko "the preview's gray button still shows the ✳ text"

# Restore exact incoming prefs and component overrides, and remove every imported private image.
A shell am force-stop "$PK"
if [ "$PREFS_EXISTED" -eq 1 ]; then
  put_prefs "$PREFS_BEFORE"
else
  A shell run-as "$PK" rm -f shared_prefs/ganimede.xml
fi
restore_component IconDefault "$ICON_DEFAULT_BEFORE"
A shell run-as "$PK" rm -f files/fab-icon.png

same_alias() { [ "$(effective_state "$1" "$(component_state "$1")")" = "$(effective_state "$1" "$2")" ]; }
same_alias IconDefault "$ICON_DEFAULT_BEFORE" \
  && ok "cleanup restores the default launcher alias state" \
  || ko "cleanup did not restore the default launcher alias to $ICON_DEFAULT_BEFORE"
if [ "$PREFS_EXISTED" -eq 1 ]; then
  A exec-out run-as "$PK" cat shared_prefs/ganimede.xml > "$PREFS_AFTER"
  cmp -s "$PREFS_BEFORE" "$PREFS_AFTER" \
    && ok "cleanup restores the exact incoming preferences" \
    || ko "cleanup changed the incoming preferences"
else
  A shell run-as "$PK" test ! -e shared_prefs/ganimede.xml \
    && ok "cleanup restores the initially absent preferences file" \
    || ko "cleanup left a preferences file that did not initially exist"
fi
A shell run-as "$PK" test ! -e files/fab-icon.png \
  && ok "cleanup deletes the private imported image" \
  || ko "cleanup left files/fab-icon.png behind"

A shell rm -f "$CUSTOM_DEVICE" "$LARGE_DEVICE" "$EXIF_DEVICE" "$HUGE_DEVICE"
rm -f "$CUSTOM_LOCAL" "$LARGE_LOCAL" "$EXIF_LOCAL" "$HUGE_LOCAL" "$PREFS_BEFORE" "$PREFS_AFTER"
