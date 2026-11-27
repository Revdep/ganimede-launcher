capture_calendar() { # output png: only Calendar's accessible app cell/row
  local out="$1" b
  b=$(bounds "desc:Calendar")
  [ -n "$b" ] || return 1
  set -- $b
  A exec-out screencap -p > "$OUT/icons-screen.png"
  magick "$OUT/icons-screen.png" -crop "$(( $3 - $1 ))x$(( $4 - $2 ))+$1+$2" +repage "$out"
}

colour_pixels() { # png #RRGGBB: how many pixels have that colour
  magick "$1" -fuzz 4% -fill "$2" -opaque "$2" -depth 8 -format %c histogram:info:- \
    | awk -v c="$2" '$0 ~ c {gsub(":", "", $1); print $1; found=1} END{if (!found) print 0}'
}

for mode in list classic; do
  step "Generated tinted icons: $mode mode"
  seed "$mode"
  setp int accent -2093040
  if [ "$mode" = classic ]; then setp int grid_icon 80; else setp int icon_size 80; fi
  setp string icon_shape original
  capture_calendar "$OUT/icons-$mode-original.png" || { ko "$mode: Calendar icon not found"; continue; }
  setp string themed_shape circle
  setp string icon_shape theme
  capture_calendar "$OUT/icons-$mode-themed.png" || { ko "$mode: themed Calendar icon not found"; continue; }

  cmp -s "$OUT/icons-$mode-original.png" "$OUT/icons-$mode-themed.png" \
    && ko "$mode: Tinted leaves the icon unchanged" || ok "$mode: Tinted changes the icon"
  pixels=$(colour_pixels "$OUT/icons-$mode-themed.png" "#E01010")
  [ "${pixels:-0}" -gt 20 ] && ok "$mode: the icon uses the chosen red accent ($pixels pixels)" \
    || ko "$mode: no chosen accent in the icon (${pixels:-0} pixels)"
done

step "Tinted icon plate: the selected shape is used"
setp string themed_shape none
capture_calendar "$OUT/icons-classic-no-plate.png"
cmp -s "$OUT/icons-classic-themed.png" "$OUT/icons-classic-no-plate.png" \
  && ko "removing the tinted plate changes nothing" || ok "the chosen plate shape changes the icon"

step "Tinted icon with the default accent: the glyph stays readable on the plate"
setp string themed_shape circle
setp int accent -4609803
capture_calendar "$OUT/icons-default-accent.png" || ko "default accent: Calendar icon not found"
plate=$(colour_pixels "$OUT/icons-default-accent.png" "#B9A8F5")
glyph=$(colour_pixels "$OUT/icons-default-accent.png" "#1B1820")
[ "${plate:-0}" -gt 20 ] && [ "${glyph:-0}" -gt 20 ] && ok "default accent: lavender plate ($plate) with dark glyph ($glyph)" \
  || ko "default accent: plate $plate, glyph $glyph pixels"
shot 34-themed-icons-default-accent

# Issue 077: the built-in Arcticons set, always offered, drawn in the accent, credited on the About page.
BUILTIN=builtin:arcticons
open_settings() { # open_settings "Ganimede settings" row: long-press the classic home (1900 is an empty spot), open a settings page
  A shell input swipe 540 1900 540 1900 1500; tap "$1" && tapn "$2" 15 # About is at the bottom of a long page
}
shows() { screen | grep -q "text=\"[^\"]*$1"; } # a text node containing $1
same_pixels() { [ "$(magick compare -metric AE "$1" "$2" null: 2>&1 | cut -d' ' -f1)" = 0 ]; } # cmp also sees PNG metadata
step "Built-in Arcticons pack: offered in the Icon pack card"
seed classic
A shell pm list packages | grep -qiE "icon|arcticons" && echo "  (note: an installed package looks like an icon pack)"
open_settings "Impostazioni Ganimede" "Personalizza"
pill "Temi"; tap "Temi"
see "Arcticons (integrato)"
pv_now() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "name=\"icon_pack\">$BUILTIN<"; }
tap "Arcticons (integrato)" && wait_until 5 pv_now && ok "choosing it stores icon_pack=$BUILTIN" || ko "choosing it did not store icon_pack=$BUILTIN"

for mode in list classic; do
  step "Built-in Arcticons pack draws Calendar in the accent: $mode mode"
  seed "$mode"
  setp int accent -2093040
  if [ "$mode" = classic ]; then setp int grid_icon 80; else setp int icon_size 80; fi
  capture_calendar "$OUT/icons-$mode-own.png" || { ko "$mode: Calendar icon not found"; continue; }
  setp string icon_pack "$BUILTIN"
  capture_calendar "$OUT/icons-$mode-builtin.png" || { ko "$mode: built-in Calendar icon not found"; continue; }
  same_pixels "$OUT/icons-$mode-own.png" "$OUT/icons-$mode-builtin.png" \
    && ko "$mode: the built-in pack leaves Calendar's icon unchanged" || ok "$mode: the built-in pack changes Calendar's icon"
  pixels=$(colour_pixels "$OUT/icons-$mode-builtin.png" "#E01010")
  [ "${pixels:-0}" -gt 20 ] && ok "$mode: the built-in icon is drawn in the red accent ($pixels pixels)" \
    || ko "$mode: no red accent in the built-in icon (${pixels:-0} pixels)"
done
shot 77-builtin-icons

step "APK size: the built-in set stays small"
bin=$(unzip -lv "$APK" | awk '$NF == "assets/icons.bin" {print $3}') # its compressed size: what it adds to the APK
[ -n "$bin" ] && [ "$bin" -lt 3000000 ] && ok "assets/icons.bin takes $bin bytes in the APK (< 3 MB)" || ko "assets/icons.bin takes '${bin}' bytes in the APK, expected < 3 MB"
REL="$HERE/../Ganimede-Launcher-release.apk"
if [ -f "$REL" ] && [ -z "$(find "$HERE/../src/main" "$HERE/../build.gradle" -newer "$REL" -type f -print -quit)" ]; then
  rel=$(stat -c %s "$REL")
  [ "$rel" -lt 4000000 ] && ok "release APK is $rel bytes (< 4 MB)" || ko "release APK is $rel bytes, expected < 4 MB"
else SKIPS=$((SKIPS + 1)); echo "  SKIP: no release APK newer than the sources (./gradlew assembleRelease)"; fi

step "About credits Arcticons (CC BY-SA 4.0), in English and Italian"
for lang in en it; do
  seed classic
  setp string lang $lang
  if [ $lang = en ]; then open_settings "Ganimede settings" "About"; done_by="converted and algorithmically restyled"
  else open_settings "Impostazioni Ganimede" "Informazioni"; done_by="convertite e ridisegnate in modo algoritmico"; fi
  credited() { shows "Arcticons" && shows "CC BY-SA 4.0" && shows "$done_by"; }
  wait_until 5 credited && ok "$lang: About names Arcticons, CC BY-SA 4.0 and “$done_by”" \
    || ko "$lang: About lacks the Arcticons credit (Arcticons, CC BY-SA 4.0, “$done_by”)"
done
