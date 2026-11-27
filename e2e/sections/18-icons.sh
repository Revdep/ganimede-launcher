capture() { # capture content-desc out.png: a crop of only that node (an app cell or row, a folder tile), as drawn
  local out="$2" b
  b=$(bounds "desc:$1")
  [ -n "$b" ] || return 1
  set -- $b
  A exec-out screencap -p > "$OUT/icons-screen.png"
  magick "$OUT/icons-screen.png" -crop "$(( $3 - $1 ))x$(( $4 - $2 ))+$1+$2" +repage "$out"
}
capture_calendar() { capture Calendar "$1"; }

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

  d=$(fdiff "$OUT/icons-$mode-original.png" "$OUT/icons-$mode-themed.png")
  [ "${d:-0}" -gt 0 ] && ok "$mode: Tinted changes the icon ($d px)" || ko "$mode: Tinted leaves the icon unchanged"
  pixels=$(colour_pixels "$OUT/icons-$mode-themed.png" "#E01010")
  [ "${pixels:-0}" -gt 20 ] && ok "$mode: the icon uses the chosen red accent ($pixels pixels)" \
    || ko "$mode: no chosen accent in the icon (${pixels:-0} pixels)"
done

step "Tinted icon plate: the selected shape is used"
setp string themed_shape none
capture_calendar "$OUT/icons-classic-no-plate.png"
d=$(fdiff "$OUT/icons-classic-themed.png" "$OUT/icons-classic-no-plate.png")
[ "${d:-0}" -gt 0 ] && ok "the chosen plate shape changes the icon ($d px)" || ko "removing the tinted plate changes nothing"

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
step "Built-in Arcticons pack: offered in the Icon pack card"
seed classic
A shell pm list packages | grep -qiE "icon|arcticons" && echo "  (note: an installed package looks like an icon pack)"
pg_settings; stap "Personalizza"
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
  d=$(fdiff "$OUT/icons-$mode-own.png" "$OUT/icons-$mode-builtin.png")
  [ "${d:-0}" -gt 0 ] && ok "$mode: the built-in pack changes Calendar's icon ($d px)" || ko "$mode: the built-in pack leaves Calendar's icon unchanged"
  pixels=$(colour_pixels "$OUT/icons-$mode-builtin.png" "#E01010")
  [ "${pixels:-0}" -gt 20 ] && ok "$mode: the built-in icon is drawn in the red accent ($pixels pixels)" \
    || ko "$mode: no red accent in the built-in icon (${pixels:-0} pixels)"
done
shot 77-builtin-icons

step "Built-in Arcticons pack: an accent under 3:1 on the dark wallpaper draws the icon in the text colour"
seed classic
setp string icon_pack "$BUILTIN"; setp int grid_icon 80; setp boolean hide_labels true # only the icon in the crop
setp int accent -12636266 # #3F2B96 indigo: about 1.7:1 on a dark backdrop
capture_calendar "$OUT/icons-low-contrast.png" || ko "low contrast: Calendar icon not found"
white=$(colour_pixels "$OUT/icons-low-contrast.png" "#FFFFFF"); indigo=$(colour_pixels "$OUT/icons-low-contrast.png" "#3F2B96")
[ "${white:-0}" -gt 20 ] && [ "${indigo:-0}" -le 20 ] && ok "low contrast: drawn in the white text colour ($white px), not indigo ($indigo px)" \
  || ko "low contrast: white ${white:-0} px, indigo ${indigo:-0} px (expected white, no indigo)"

step "Built-in Arcticons pack: folder thumbnails follow the home's 3:1 rule (dark text, lavender accent)"
seed classic
APPS='"com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"'
setp string folder:mini "{\"name\":\"Mini\",\"where\":\"fav\",\"apps\":[$APPS]}"
setp string fav 'f:mini'
setp string icon_pack "$BUILTIN"; setp int grid_icon 80; setp boolean hide_labels true
setp string text_color dark; setp int accent -4609803 # lavender #B9A8F5: under 3:1 on the light backdrop dark text is for
capture Mini "$OUT/icons-folder-dark-text.png" || ko "folder thumbnails: folder Mini not found"
# the tile's inside only: the dark wallpaper around it is close to ink
magick "$OUT/icons-folder-dark-text.png" -gravity center -crop 70%x70%+0+0 +repage "$OUT/icons-folder-inside.png"
ink=$(colour_pixels "$OUT/icons-folder-inside.png" "#1B1820"); lav=$(colour_pixels "$OUT/icons-folder-inside.png" "#B9A8F5")
[ "${ink:-0}" -gt 20 ] && [ "${lav:-0}" -le 20 ] && ok "folder thumbnails: drawn in the ink text colour ($ink px), not lavender ($lav px)" \
  || ko "folder thumbnails: ink ${ink:-0} px, lavender ${lav:-0} px (expected ink, no lavender)"

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
  if [ $lang = en ]; then pg_settings "Ganimede settings" "Touch a part of the preview to adjust it"; tapn "About" 15; done_by="converted and algorithmically restyled"
  else pg_settings; tapn "Informazioni" 15; done_by="convertite e ridisegnate in modo algoritmico"; fi # About is at the bottom of a long page
  credited() { shows "Arcticons" "CC BY-SA 4.0" "https://creativecommons.org/licenses/by-sa/4.0/" "$done_by"; }
  wait_until 5 credited && ok "$lang: About names Arcticons, CC BY-SA 4.0, its licence URI and “$done_by”" \
    || ko "$lang: About lacks the Arcticons credit (Arcticons, CC BY-SA 4.0, licence URI, “$done_by”)"
done
