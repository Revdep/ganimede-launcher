# The icon model (issue 078): glyph treatment x plate, with fill and colour; the old shapes migrate unchanged.
# Without the built-in pack Calendar's glyph is its monochrome layer; with it (issue 077) the Arcticons geometry. The Icons
# card's Line/Bold/Sharp tiles always draw a built-in glyph.
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
legacy() { setp del icon_glyph del icon_container del icon_fill del icon_color string icon_shape "$1" string themed_shape "$2"; }
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
migrated() { # migrated what glyph container fill colour: the four new settings saved, the old ones gone
  stored icon_glyph "$2" && stored icon_container "$3" && stored icon_fill "$4" && stored icon_color "$5" \
    && ! A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q 'name="icon_shape"\|name="themed_shape"' \
    && ok "$1: saved as $2/$3/$4/$5, old keys removed" \
    || ko "$1: after the update: $(A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -o 'name="[a-z_]*shape"\|name="icon_[a-z]*">[a-z]*' | tr '\n' ' ')"
}

for mode in list classic; do
  step "Legacy Tinted and Monochrome migrate unchanged: $mode mode"
  size=icon_size; [ "$mode" = classic ] && size=grid_icon
  seed "$mode" int accent -2093040 boolean hide_labels true int $size 80 $(model original none solid accent) # hide_labels: only the icon in the crop, no white label
  capture_calendar "$OUT/icons-$mode-original.png" || { ko "$mode: Calendar icon not found"; continue; }
  legacy theme circle
  capture_calendar "$OUT/icons-$mode-tinted.png" || { ko "$mode: Calendar icon not found after the update"; continue; }
  migrated "$mode Tinted" line circle solid accent
  look line circle solid accent
  capture_calendar "$OUT/icons-$mode-line-circle.png"
  same "$OUT/icons-$mode-tinted.png" "$OUT/icons-$mode-line-circle.png" \
    && ok "$mode: old Tinted looks as Line + Circle + Solid" || ko "$mode: migrated Tinted differs from Line + Circle + Solid"
  differ "$OUT/icons-$mode-original.png" "$OUT/icons-$mode-line-circle.png" \
    && ok "$mode: Line changes the icon" || ko "$mode: Line leaves the icon unchanged"
  pixels=$(colour_pixels "$OUT/icons-$mode-line-circle.png" "$RED")
  [ "${pixels:-0}" -gt 20 ] && ok "$mode: the plate uses the chosen red accent ($pixels pixels)" \
    || ko "$mode: no chosen accent in the icon (${pixels:-0} pixels)"

  legacy mono circle
  capture_calendar "$OUT/icons-$mode-mono.png"
  migrated "$mode Monochrome" line none solid text
  # Not only new against new: the old Monochrome itself is a white glyph at alpha D0 on the wallpaper, with no plate.
  white=$(colour_pixels "$OUT/icons-$mode-mono.png" "#FFFFFF")
  faded=$(magick "$OUT/icons-$mode-mono.png" -alpha off -colorspace gray -fx "u > 0.72 && u < 0.88" -format "%[fx:round(mean*w*h)]" info:) # D0 = 0.82 of white over a dark wallpaper
  red=$(colour_pixels "$OUT/icons-$mode-mono.png" "$RED")
  [ "${white:-0}" -eq 0 ] && [ "${faded:-0}" -gt 20 ] && [ "${red:-0}" -eq 0 ] \
    && ok "$mode: Monochrome glyph at alpha D0 ($faded px), no plate" || ko "$mode: Monochrome white $white, D0-white $faded, red $red px"
  step "Text colour, no plate: $mode mode"
  look line none solid text
  capture_calendar "$OUT/icons-$mode-text.png"
  same "$OUT/icons-$mode-mono.png" "$OUT/icons-$mode-text.png" \
    && ok "$mode: old Monochrome looks as Line + No plate + Text colour" || ko "$mode: migrated Monochrome differs"
  pixels=$(colour_pixels "$OUT/icons-$mode-text.png" "$RED")
  [ "${pixels:-0}" -eq 0 ] && ok "$mode: text colour, no red" || ko "$mode: text colour still has $pixels red pixels"
  look line none solid accent
  capture_calendar "$OUT/icons-$mode-accent.png"
  differ "$OUT/icons-$mode-text.png" "$OUT/icons-$mode-accent.png" \
    && ok "$mode: theme colour differs from text colour" || ko "$mode: theme and text colour look the same"
done

step "No plate colours: the glyph in the accent, nothing behind it, the fill means nothing"
o="$OUT/icons-classic-original.png"; a="$OUT/icons-classic-accent.png"
pixels=$(colour_pixels "$a" "$RED")
[ "${pixels:-0}" -gt 20 ] && ok "accent glyph without a plate ($pixels red pixels)" || ko "no red glyph without a plate (${pixels:-0})"
read -r w h <<<"$(magick "$a" -format "%w %h" info:)"
corners=0
for xy in "1 1" "$((w - 2)) 1" "1 $((h - 2))" "$((w - 2)) $((h - 2))"; do
  set -- $xy; alike "$(pixel "$a" "$1" "$2")" "$(pixel "$o" "$1" "$2")" && corners=$((corners + 1))
done
[ $corners -eq 4 ] && ok "no plate: the crop's corners are the wallpaper" || ko "no plate: $corners of 4 corners are the wallpaper"
look line none soft accent
capture_calendar "$OUT/icons-classic-accent-soft.png"
same "$a" "$OUT/icons-classic-accent-soft.png" && ok "no plate: Soft looks as Solid" || ko "no plate: the fill changed the icon"

step "Solid and soft plate"
look line none solid accent int grid_icon 64 # at 80 dp the icon is wider than its cell, which cuts the plate's sides
capture_calendar "$OUT/icons-nop.png"
look line squircle solid accent
capture_calendar "$OUT/icons-solid.png"
look line squircle soft accent
capture_calendar "$OUT/icons-soft.png"
if read -r px py pw ph <<<"$(box "$OUT/icons-solid.png" "$RED")" && [ -n "$px" ]; then
  for fill in solid soft; do # only the plate's square: the cell's label is white too
    magick "$OUT/icons-$fill.png" -crop "${pw}x${ph}+$px+$py" +repage "$OUT/icons-$fill-plate.png"
  done
  white=$(colour_pixels "$OUT/icons-solid-plate.png" "#FFFFFF")
  [ "${white:-0}" -gt 20 ] && ok "solid: white glyph on the red plate ($white px)" || ko "solid: glyph white pixels ${white:-0}"
  edge=$(pixel "$OUT/icons-soft.png" $((px + 1)) $((py + ph / 2)))
  alike "$edge" E01010 && ok "soft: a red border at the plate's left edge" || ko "soft: left edge is #$edge"
  ix=$((px + pw / 6)); iy=$((py + ph / 2)) # inside the plate, left of the glyph
  inner=$(pixel "$OUT/icons-soft.png" $ix $iy)
  ! alike "$inner" E01010 && ! alike "$inner" "$(pixel "$OUT/icons-nop.png" $ix $iy)" \
    && ok "soft: a faint plate (#$inner)" || ko "soft: inside the plate #$inner"
  white=$(colour_pixels "$OUT/icons-soft-plate.png" "#FFFFFF")
  [ "${white:-0}" -eq 0 ] && ok "soft: no white glyph" || ko "soft: $white white pixels on the plate"
else ko "solid: no red plate found"; fi

step "Text colour on a solid plate"
look line circle solid text
capture_calendar "$OUT/icons-text-plate.png"
plate=$(colour_pixels "$OUT/icons-text-plate.png" "#FFFFFF"); glyph=$(colour_pixels "$OUT/icons-text-plate.png" "#1B1820")
red=$(colour_pixels "$OUT/icons-text-plate.png" "$RED")
[ "${plate:-0}" -gt 20 ] && [ "${glyph:-0}" -gt 20 ] && [ "${red:-0}" -eq 0 ] \
  && ok "white plate ($plate) with a dark glyph ($glyph), no red" || ko "text plate: white $plate, dark $glyph, red $red"

step "Default accent: the glyph stays readable on the plate"
look line circle solid accent int accent -4609803
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
pv_now() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "name=\"icon_pack\">$BUILTIN<"; }
tapn "Arcticons (integrato)" 60 && wait_until 5 pv_now && ok "choosing it stores icon_pack=$BUILTIN" || ko "choosing it did not store icon_pack=$BUILTIN"
original_kept() { ! A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q 'name="icon_glyph">[a-z]' || stored icon_glyph original; }
original_kept && ok "choosing the built-in set keeps the icon style Original" || ko "choosing the built-in set changed icon_glyph"
reach "${S}Originale" >/dev/null && ok "with the built-in set the Icons card still offers Original" || ko "no Original tile with the built-in set"
reach "${C}Colore del testo" >/dev/null && exact "Colore" && exact "Riempimento" \
  && ok "Original over the built-in set: the Fill and Colour rows show (its glyphs use them)" || ko "Original over the built-in set: no Fill or Colour row"
exact "Icone per categoria" && ok "the built-in set: the Icons card offers Category icons" || ko "the built-in set: no Category icons row"
tapn "Icone per categoria" 60 && wait_until 5 bpref icon_category false && ok "a tap on Category icons saves icon_category = false" || ko "Category icons saved nothing"
tapn "Nessuno (icone delle app)" 60 && wait_until 5 stored icon_pack "" && original_kept && ok "back to no pack: the icon style is still Original" \
  || ko "back to no pack: the pack did not change, or icon_glyph did"
valid && ! exact "Icone per categoria" && ok "no pack: no Category icons row" || ko "no pack: the Category icons row is still there"

for mode in list classic; do
  step "Built-in Arcticons pack draws Calendar in the accent: $mode mode"
  size=icon_size; [ "$mode" = classic ] && size=grid_icon
  seed "$mode" int accent -2093040 int $size 80
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
# hide_labels: only the icon in the crop; no plate: on the wallpaper (the seed's squircle would put the icon on an indigo plate,
# issue 078); #3F2B96 indigo: about 1.7:1 on a dark backdrop
seed classic string icon_pack "$BUILTIN" int grid_icon 80 boolean hide_labels true $(model line none solid accent) int accent -12636266
capture_calendar "$OUT/icons-low-contrast.png" || ko "low contrast: Calendar icon not found"
white=$(colour_pixels "$OUT/icons-low-contrast.png" "#FFFFFF"); indigo=$(colour_pixels "$OUT/icons-low-contrast.png" "#3F2B96")
[ "${white:-0}" -gt 20 ] && [ "${indigo:-0}" -le 20 ] && ok "low contrast: drawn in the white text colour ($white px), not indigo ($indigo px)" \
  || ko "low contrast: white ${white:-0} px, indigo ${indigo:-0} px (expected white, no indigo)"

step "Built-in Arcticons pack: folder thumbnails follow the home's 3:1 rule (dark text, lavender accent)"
APPS='"com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"'
# lavender #B9A8F5: under 3:1 on the light backdrop dark text is for
seed classic string folder:mini "{\"name\":\"Mini\",\"where\":\"fav\",\"apps\":[$APPS]}" string fav 'f:mini' \
  string icon_pack "$BUILTIN" int grid_icon 80 boolean hide_labels true string text_color dark int accent -4609803
capture Mini "$OUT/icons-folder-dark-text.png" || ko "folder thumbnails: folder Mini not found"
# the tile's inside only: the dark wallpaper around it is close to ink
magick "$OUT/icons-folder-dark-text.png" -gravity center -crop 70%x70%+0+0 +repage "$OUT/icons-folder-inside.png"
ink=$(colour_pixels "$OUT/icons-folder-inside.png" "#1B1820"); lav=$(colour_pixels "$OUT/icons-folder-inside.png" "#B9A8F5")
[ "${ink:-0}" -gt 20 ] && [ "${lav:-0}" -le 20 ] && ok "folder thumbnails: drawn in the ink text colour ($ink px), not lavender ($lav px)" \
  || ko "folder thumbnails: ink ${ink:-0} px, lavender ${lav:-0} px (expected ink, no lavender)"

# Issue 081: the picker offers every icon of the built-in set (no 4000 cap), found by name, saved by name.
step "Picker reaches icons past 4000"
seed classic
setp string icon_pack "$BUILTIN"; setp int grid_icon 80; setp boolean hide_labels true
CAL=com.google.android.calendar/com.android.calendar.AllInOneActivity
capture_calendar "$OUT/icons-pick-before.png" || ko "picker: Calendar icon not found"
read -r px py <<<"$(cx "desc:Calendar")"
hold "$px" "$py"
tap "Icona dell'app…"; tap "Arcticons (integrato)"
wait_until 5 has "calendar" && tap "calendar" # the field starts with the app's name
clear_text; A shell input text okay
wait_until 10 hasd okay && ok "searching okay shows the okay icon, far past the 4000th name" || ko "searching okay finds no okay icon"
read -r ox oy <<<"$(cx "desc:okay")"
[ -n "$oy" ] && A shell input tap "$ox" "$oy"
saved() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -qF "name=\"icon:$CAL\">$BUILTIN|okay<"; }
wait_until 5 saved && ok "the pick is saved by name ($BUILTIN|okay)" || ko "the pick is not saved as $BUILTIN|okay"
start_home
capture_calendar "$OUT/icons-pick-after.png" || ko "picker: Calendar icon not found after the pick"
d=$(fdiff "$OUT/icons-pick-before.png" "$OUT/icons-pick-after.png")
[ "${d:-0}" -gt 0 ] && ok "the picked icon replaces Calendar's on the home ($d px)" || ko "the pick leaves Calendar's icon unchanged"
shot 81-picked-icon

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
  seed classic string lang $lang
  if [ $lang = en ]; then pg_settings "Ganimede settings" "Touch a part of the preview to adjust it"; tapn "About" 15; done_by="converted and algorithmically restyled"
  else pg_settings; tapn "Informazioni" 15; done_by="convertite e ridisegnate in modo algoritmico"; fi # About is at the bottom of a long page
  credited() { shows "Arcticons" "CC BY-SA 4.0" "https://creativecommons.org/licenses/by-sa/4.0/" "$done_by"; }
  wait_until 5 credited && ok "$lang: About names Arcticons, CC BY-SA 4.0, its licence URI and “$done_by”" \
    || ko "$lang: About lacks the Arcticons credit (Arcticons, CC BY-SA 4.0, licence URI, “$done_by”)"
done

for mode in list classic; do
  step "Built-in treatments differ, the glyph centred on its plate: $mode mode"
  size=icon_size; [ "$mode" = classic ] && size=grid_icon
  seed "$mode" int accent -2093040 string icon_pack "$BUILTIN" boolean hide_labels true int $size 64 # hide_labels: only the icon in the crop
  sums=""
  for g in original line bold sharp duotone hollow; do
    look $g circle solid accent
    capture_calendar "$OUT/icons-$mode-builtin-$g.png" || ko "$mode: no Calendar with $g"
    sums="$sums$(magick "$OUT/icons-$mode-builtin-$g.png" -format %# info:)"$'\n'
  done
  n=$(printf %s "$sums" | sort -u | grep -c .)
  [ "$n" = 5 ] && ok "$mode: Line, Bold, Sharp, Duotone and Hollow differ" || ko "$mode: $n different of Line, Bold, Sharp, Duotone, Hollow (expected 5)"
  same "$OUT/icons-$mode-builtin-original.png" "$OUT/icons-$mode-builtin-line.png" \
    && ok "$mode: Original draws a built-in icon as Line (it has no colours of its own)" || ko "$mode: built-in Original differs from Line"
  img="$OUT/icons-$mode-builtin-line.png"
  if read -r gx gy gw gh <<<"$(box "$img" "#FFFFFF")" && [ -n "$gx" ] && read -r qx qy qw qh <<<"$(box "$img" "$RED")" && [ -n "$qx" ]; then
    dx=$(( (2 * gx + gw) - (2 * qx + qw) )); dy=$(( (2 * gy + gh) - (2 * qy + qh) )) # twice the offset of the centres
    [ ${dx#-} -le 4 ] && [ ${dy#-} -le 4 ] && ok "$mode: built-in glyph centred on its plate (offset $dx/2, $dy/2 px)" \
      || ko "$mode: built-in glyph off centre by $dx/2, $dy/2 px (glyph $gx $gy $gw $gh, plate $qx $qy $qw $qh)"
  else ko "$mode: no white glyph or red plate in the built-in Line icon"; fi
done

step "Duotone fills the closed shapes at 32%; Hollow is centred on its plate"
seed classic
setp int accent -2093040; setp string icon_pack "$BUILTIN"; setp boolean hide_labels true; setp int grid_icon 64
look line none solid accent
capture_calendar "$OUT/icons-d-line.png"
look duotone none solid accent
capture_calendar "$OUT/icons-d-duo.png"
stroke_duo=$(colour_pixels "$OUT/icons-d-duo.png" "$RED")
# the fill: pixels that are neither the accent nor what Line shows at the same spot (the wallpaper beside the stroke)
magick "$OUT/icons-d-duo.png" "$OUT/icons-d-line.png" -compose difference -composite -alpha off -colorspace gray -threshold 3% "$OUT/icons-d-differs.png"
magick "$OUT/icons-d-duo.png" -alpha off -fuzz 4% -fill white +opaque "$RED" -fill black -opaque "$RED" -colorspace gray "$OUT/icons-d-notred.png"
fillpx=$(magick "$OUT/icons-d-differs.png" "$OUT/icons-d-notred.png" -compose multiply -composite -format "%[fx:round(mean*w*h)]" info:)
linepx=$(colour_pixels "$OUT/icons-d-line.png" "$RED")
[ "$stroke_duo" -gt 20 ] && [ "$linepx" -gt 20 ] && ok "Duotone: $stroke_duo stroke pixels in the accent" || ko "Duotone: $stroke_duo accent pixels (Line $linepx), expected more than 20"
[ "$fillpx" -gt 20 ] && ok "Duotone: $fillpx translucent fill pixels Line does not have" || ko "Duotone: $fillpx fill pixels, expected more than 20"
look duotone squircle soft accent
capture_calendar "$OUT/icons-d-soft.png"
look line squircle solid accent
capture_calendar "$OUT/icons-d-solid.png"
if read -r px py pw ph <<<"$(box "$OUT/icons-d-solid.png" "$RED")" && [ -n "$px" ]; then
  edge=$(pixel "$OUT/icons-d-soft.png" $((px + 1)) $((py + ph / 2)))
  alike "$edge" E01010 && ok "Duotone + squircle + soft: a red border at the plate's left edge" || ko "Duotone soft: left edge is #$edge"
  magick "$OUT/icons-d-soft.png" -crop "${pw}x${ph}+$px+$py" +repage "$OUT/icons-d-soft-plate.png"
  white=$(colour_pixels "$OUT/icons-d-soft-plate.png" "#FFFFFF")
  [ "${white:-0}" -eq 0 ] && ok "Duotone + squircle + soft: no white pixel" || ko "Duotone soft: $white white pixels on the plate"
else ko "Duotone soft: no red plate found"; fi
look hollow circle solid accent
capture_calendar "$OUT/icons-d-hollow.png"
if read -r gx gy gw gh <<<"$(box "$OUT/icons-d-hollow.png" "#FFFFFF")" && [ -n "$gx" ] && read -r qx qy qw qh <<<"$(box "$OUT/icons-d-hollow.png" "$RED")" && [ -n "$qx" ]; then
  dx=$(( (2 * gx + gw) - (2 * qx + qw) )); dy=$(( (2 * gy + gh) - (2 * qy + qh) ))
  [ ${dx#-} -le 4 ] && [ ${dy#-} -le 4 ] && ok "Hollow glyph centred on its plate (offset $dx/2, $dy/2 px)" \
    || ko "Hollow glyph off centre by $dx/2, $dy/2 px"
else ko "Hollow: no white glyph or red plate"; fi

step "Icons card: Line, Bold, Sharp on a built-in glyph; rows shown only where they mean something"
seed classic int accent -2093040 $(model original circle solid accent)
pg_settings; stap "Personalizza"
pill "Temi"; tap "Temi"
find_desc "${S}Spigoloso" >/dev/null || ko "no Sharp tile"
for tile in Linea Marcato Spigoloso Bicolore Contorno; do hasd "$S$tile" && ok "sees the $tile tile" || ko "no $tile tile"; done
! screen | grep -qiE 'text="[^"]*(Cutout|Ritaglio)' && ok "Icon style: no Cutout tile" || ko "a Cutout tile is still offered"
A exec-out screencap -p > "$OUT/icons-screen.png"
sums=""
for tile in Originale Linea Marcato Spigoloso Bicolore Contorno; do # the icon part of each tile: 10 dp padding, then the 44 dp icon
  if read -r x1 y1 x2 y2 <<<"$(bounds "desc:$S$tile")" && [ -n "$x1" ]; then
    s=$(dp 44)
    magick "$OUT/icons-screen.png" -crop "${s}x${s}+$(( (x1 + x2 - s) / 2 ))+$((y1 + $(dp 10)))" +repage "$OUT/icons-tile-$tile.png"
    sums="$sums$(magick "$OUT/icons-tile-$tile.png" -format %# info:)"$'\n'
  fi
done
n=$(printf %s "$sums" | sort -u | grep -c .)
[ "$n" = 6 ] && ok "Original, Line, Bold, Sharp, Duotone, Hollow tiles all differ" || ko "$n different tiles of 6"
tile="$OUT/icons-tile-Linea.png"
if read -r gx gy gw gh <<<"$(box "$tile" "#FFFFFF")" && [ -n "$gx" ] && read -r qx qy qw qh <<<"$(box "$tile" "$RED")" && [ -n "$qx" ]; then
  dx=$(( (2 * gx + gw) - (2 * qx + qw) )); dy=$(( (2 * gy + gh) - (2 * qy + qh) )) # twice the offset of the centres
  [ ${dx#-} -le 4 ] && [ ${dy#-} -le 4 ] && ok "built-in glyph centred on its plate (offset $dx/2, $dy/2 px)" \
    || ko "built-in glyph off centre by $dx/2, $dy/2 px (glyph $gx $gy $gw $gh, plate $qx $qy $qw $qh)"
  long=$gw; [ "$gh" -gt "$long" ] && long=$gh
  near "$long" "$((qw * 26 / 48))" 3 && ok "glyph's longer side 26/48 of the plate ($gw x $gh on $qw)" \
    || ko "glyph $gw x $gh on a $qw plate, expected a longer side of $((qw * 26 / 48))"
else ko "Line tile: no glyph or plate found"; fi
nofill && ! exact "Colore" && ok "Original: no Fill, no Colour" || ko "Original shows Fill or Colour"
tapd "${S}Marcato"
wait_until 5 stored icon_glyph bold && ok "the Bold tile saves icon_glyph = bold" || ko "Bold saved nothing"
tapd "${P}Nessuna piastra" "${P}Cerchio"
wait_until 5 stored icon_container none || ko "No plate saved nothing"
reach "${C}Colore del testo" >/dev/null
nofill && exact "Colore" && ok "No plate: no Fill row, a Colour row" || ko "No plate: Fill or Colour wrong"
tapd "${C}Colore del testo"
wait_until 5 stored icon_color text && ok "the Text colour tile saves icon_color = text" || ko "Text colour saved nothing"
tapd "${S}Puntini (minimal)"
wait_until 5 stored icon_glyph dot || ko "Dots saved nothing"
reach "${S}Puntini (minimal)" >/dev/null
valid && ! exact "Piastra" && ! exact "Riempimento" && ok "Dots: no Plate, no Fill" || ko "Dots shows Plate or Fill"
! screen | grep -qiE 'text="[^"]*(Cutout|Ritaglio|Colorata|Monocromatica|Tinted|Monochrome)' \
  && ok "no Cutout, Tinted or Monochrome anywhere" || ko "an old icon style is still offered"

step "An old-style (not adaptive) icon stays as it is with Original and a shape"
FIXTURE=org.ganimede.fixture
fixture_install && ok "fixture app built and installed" \
  || ko "fixture app not built or installed (./gradlew -Pe2e :fixture:assembleDebug)"
icons_fixture_cleanup() { A uninstall "$FIXTURE" >/dev/null 2>&1; }
defer icons_fixture_cleanup
seed classic string fav "$FIXTURE/$FIXTURE.Open" boolean hide_labels true $(model original none solid accent)
capture Fixture "$OUT/icons-fixture-none.png" || ko "the fixture's icon is not on the home"
look original circle solid accent
capture Fixture "$OUT/icons-fixture-circle.png" || ko "the fixture's icon is gone with a circle (the app may have crashed)"
same "$OUT/icons-fixture-none.png" "$OUT/icons-fixture-circle.png" \
  && ok "a square icon is not cut to the circle" || ko "a square icon changed with the circle shape"

step "Built-in set with Original: covered apps get the accent glyph, the others keep their own colours"
look original none solid accent string fav "com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;$FIXTURE/$FIXTURE.Open" int accent -2093040 string icon_pack "$BUILTIN"
capture Calendar "$OUT/icons-original-builtin-calendar.png" || ko "no Calendar with the built-in set"
capture Fixture "$OUT/icons-original-builtin-fixture.png" || ko "no fixture app with the built-in set"
red=$(colour_pixels "$OUT/icons-original-builtin-calendar.png" "$RED")
[ "${red:-0}" -gt 20 ] && ok "Original + built-in set: Calendar is the red accent glyph ($red px)" || ko "Original + built-in set: Calendar has $red red px"
green=$(colour_pixels "$OUT/icons-original-builtin-fixture.png" "#2E7D32"); red=$(colour_pixels "$OUT/icons-original-builtin-fixture.png" "$RED")
[ "${green:-0}" -gt 20 ] && [ "${red:-0}" -eq 0 ] && ok "Original + built-in set: the uncovered fixture keeps its own green ($green px)" \
  || ko "Original + built-in set: fixture green $green, red $red px"
look original none solid text
capture Calendar "$OUT/icons-original-builtin-text.png" || ko "no Calendar with the built-in set in the text colour"
white=$(colour_pixels "$OUT/icons-original-builtin-text.png" "#FFFFFF"); red=$(colour_pixels "$OUT/icons-original-builtin-text.png" "$RED")
[ "${white:-0}" -gt 20 ] && [ "${red:-0}" -eq 0 ] && ok "Original + built-in set + Text colour: Calendar's glyph is white ($white px), no red" \
  || ko "Original + built-in set + Text colour: white $white, red $red px"

# Issue 079: an app with no Arcticons entry takes its glyph from its own icon (here the fixture's alpha silhouette): same
# colours as the Arcticons glyph, fitted to 26/48 of the plate and centred on its own pixels.
centre_box() { # png: "x y w h" of the white glyph, centred on the red plate within 2 px, or nothing
  local gx gy gw gh qx qy qw qh dx dy
  read -r gx gy gw gh <<<"$(box "$1" "#FFFFFF")" && [ -n "$gx" ] && read -r qx qy qw qh <<<"$(box "$1" "$RED")" && [ -n "$qx" ] || return 1
  dx=$(( (2 * gx + gw) - (2 * qx + qw) )); dy=$(( (2 * gy + gh) - (2 * qy + qh) )) # twice the offset of the centres
  echo "$gx $gy $gw $gh $dx $dy $qx $qy $qw $qh"
}
for mode in list classic; do
  step "Fallback glyph matches Arcticons on the plate: $mode mode"
  seed "$mode"
  setp string fav "com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;$FIXTURE/$FIXTURE.Open"
  setp int accent -2093040; setp string icon_pack "$BUILTIN"; setp boolean hide_labels true
  setp boolean icon_category false # the fixture declares a category (issue 087): these steps are about its silhouette
  if [ "$mode" = classic ]; then setp int grid_icon 64; else setp int icon_size 64; fi # at 80 dp the cell cuts the plate's sides
  look line circle solid accent
  cal="$OUT/icons-$mode-fallback-calendar.png" fix="$OUT/icons-$mode-fallback-fixture.png"
  capture Calendar "$cal" || ko "$mode: no Calendar"
  capture Fixture "$fix" || ko "$mode: no fixture app"
  if read -r gx gy gw gh dx dy qx qy qw qh <<<"$(centre_box "$fix")" && [ -n "$gx" ] && read -r cx_ cy_ cw ch _ <<<"$(centre_box "$cal")" && [ -n "$cx_" ]; then
    ok "$mode: the fixture has a red plate and a white glyph, as Calendar"
    [ ${dx#-} -le 4 ] && [ ${dy#-} -le 4 ] && ok "$mode: fallback glyph centred on its plate (offset $dx/2, $dy/2 px)" \
      || ko "$mode: fallback glyph off centre by $dx/2, $dy/2 px (glyph $gx $gy $gw $gh, plate $qx $qy $qw $qh)"
    long=$gw; [ "$gh" -gt "$long" ] && long=$gh; clong=$cw; [ "$ch" -gt "$clong" ] && clong=$ch
    near "$long" "$clong" 3 && ok "$mode: fallback glyph's longer side $long px, Calendar's $clong px" \
      || ko "$mode: fallback glyph's longer side $long px, Calendar's $clong px (more than 3 apart)"
  else ko "$mode: no white glyph on a red plate (fixture: '$(centre_box "$fix")', Calendar: '$(centre_box "$cal")')"; fi

  step "Fallback glyph on a soft plate: $mode mode"
  look line circle soft accent
  capture Calendar "$OUT/icons-$mode-fallback-calendar-soft.png" || ko "$mode: no Calendar (soft)"
  capture Fixture "$OUT/icons-$mode-fallback-fixture-soft.png" || ko "$mode: no fixture app (soft)"
  edges=""
  for a in calendar fixture; do
    img="$OUT/icons-$mode-fallback-$a-soft.png"
    read -r rx ry rw rh <<<"$(box "$img" "$RED")"
    [ -n "$rx" ] && alike "$(pixel "$img" $((rx + 1)) $((ry + rh / 2)))" E01010 && edges="$edges $a"
  done
  [ "$edges" = " calendar fixture" ] && ok "$mode: soft: a red border on both plates" || ko "$mode: soft: red border only on:$edges"
  white=$(colour_pixels "$OUT/icons-$mode-fallback-fixture-soft.png" "#FFFFFF"); red=$(colour_pixels "$OUT/icons-$mode-fallback-fixture-soft.png" "$RED")
  [ "${white:-0}" -eq 0 ] && [ "${red:-0}" -gt 20 ] && ok "$mode: soft: the fallback glyph is red ($red px), no white" \
    || ko "$mode: soft: fixture white $white, red $red px"

  step "Fallback glyph with no plate: $mode mode"
  look original none solid accent
  capture Fixture "$OUT/icons-$mode-fallback-fixture-own.png" || ko "$mode: no fixture app (Original)"
  look line none solid accent
  capture Fixture "$OUT/icons-$mode-fallback-fixture-nop.png" || ko "$mode: no fixture app (no plate)"
  f="$OUT/icons-$mode-fallback-fixture-nop.png" o="$OUT/icons-$mode-fallback-fixture-own.png"
  red=$(colour_pixels "$f" "$RED")
  read -r w h <<<"$(magick "$f" -format "%w %h" info:)"
  # the frame reaches the crop's corners, so the wallpaper shows only through its hole: the same in its own icon
  [ "${red:-0}" -gt 20 ] && alike "$(pixel "$f" $((w / 2)) $((h / 2)))" "$(pixel "$o" $((w / 2)) $((h / 2)))" \
    && ok "$mode: no plate: a red fallback glyph ($red px), the wallpaper through its middle" \
    || ko "$mode: no plate: fixture red $red px, middle #$(pixel "$f" $((w / 2)) $((h / 2))) against its own icon's #$(pixel "$o" $((w / 2)) $((h / 2)))"

  step "Monochrome-layer fallback matches Arcticons on the plate: $mode mode"
  setp string fav "com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;$FIXTURE/$FIXTURE.Mono"
  look line circle solid accent
  mono="$OUT/icons-$mode-fallback-mono.png"
  capture "Fixture mono" "$mono" || ko "$mode: no Fixture mono"
  if read -r gx gy gw gh dx dy qx qy qw qh <<<"$(centre_box "$mono")" && [ -n "$gx" ] && read -r cx_ cy_ cw ch _ <<<"$(centre_box "$cal")" && [ -n "$cx_" ]; then
    [ ${dx#-} -le 4 ] && [ ${dy#-} -le 4 ] && ok "$mode: monochrome glyph centred on its plate (offset $dx/2, $dy/2 px)" \
      || ko "$mode: monochrome glyph off centre by $dx/2, $dy/2 px (glyph $gx $gy $gw $gh, plate $qx $qy $qw $qh)"
    long=$gw; [ "$gh" -gt "$long" ] && long=$gh; clong=$cw; [ "$ch" -gt "$clong" ] && clong=$ch
    near "$long" "$clong" 3 && ok "$mode: monochrome glyph's longer side $long px, Calendar's $clong px" \
      || ko "$mode: monochrome glyph's longer side $long px, Calendar's $clong px (more than 3 apart)"
  else ko "$mode: no white monochrome glyph on a red plate ('$(centre_box "$mono")')"; fi
done

step "Folder thumbnails draw a fallback app's glyph, not its own colours (Classic)"
seed classic
setp string folder:fb "{\"name\":\"Fb\",\"where\":\"fav\",\"apps\":[\"$FIXTURE/$FIXTURE.Open\",\"com.google.android.calendar/com.android.calendar.AllInOneActivity\"]}"
setp string fav 'f:fb'
setp int accent -2093040; setp string icon_pack "$BUILTIN"; setp int grid_icon 80; setp boolean hide_labels true
setp boolean icon_category false # the fixture declares a category (issue 087): this step is about its silhouette
look line circle solid accent
if capture Fb "$OUT/icons-folder-fallback.png"; then
  green=$(colour_pixels "$OUT/icons-folder-fallback.png" "#2E7D32"); red=$(colour_pixels "$OUT/icons-folder-fallback.png" "$RED")
  [ "${green:-0}" -eq 0 ] && [ "${red:-0}" -gt 20 ] && ok "folder: the fixture is a red glyph as Calendar ($red px), not its green" \
    || ko "folder: fixture green $green, red $red px (expected no green, red glyphs)"
else ko "folder Fb not found"; fi

# Issue 079: another profile's app keeps its badge (the briefcase) with the original icon, a glyph and in a folder thumbnail.
# A managed profile from the shell needs the emulator and a build that lets `pm create-user --managed` run without a DPC:
# when the system refuses, the step says so and is skipped (the badge then stays checked by hand).
step "Work-profile app keeps its badge: original, fallback glyph, Arcticons, folder thumbnail (Classic)"
if emu_only "icons: a work profile is created on the emulator"; then
  wuid=$(A shell pm create-user --profileOf 0 --managed ganimede-work 2>&1 | grep -oE 'id [0-9]+' | grep -oE '[0-9]+')
  if [ -z "$wuid" ]; then
    SKIPS=$((SKIPS + 1)); echo "  SKIP: this system refuses 'pm create-user --profileOf 0 --managed' (no work profile to badge)"
  else
    defer "A shell pm remove-user $wuid"
    A shell am start-user -w "$wuid" >/dev/null 2>&1
    for pk in $FIXTURE com.google.android.calendar; do A shell pm install-existing --user "$wuid" "$pk" >/dev/null 2>&1; done
    serial=$(A shell dumpsys user | grep -E "UserInfo\{$wuid:" | grep -oE 'serialNo=[0-9]+' | head -1 | cut -d= -f2)
    corner() { magick "$1" -gravity SouthEast -crop "40%x40%+0+0" +repage "$2"; } # where the badge sits
    rest() { magick "$1" -gravity NorthWest -crop "50%x50%+0+0" +repage "$2"; } # where a badge at the icon's scale never reaches
    badge_of() { # badge_of label component pack look...: the cell as the user's own app and as the work copy; only the work copy's corner differs
      local label="$1" comp="$2" pack="$3"; shift 3
      seed classic; setp int accent -2093040; setp string icon_pack "$pack"; setp int grid_icon 64; setp boolean hide_labels true
      look "$@"
      setp string fav "$comp"; capture "$label" "$OUT/icons-badge-own.png" || { ko "badge: no own $label"; return; }
      setp string fav "$comp#$serial"; capture "$label" "$OUT/icons-badge-work.png" || { ko "badge: no work $label (serial '$serial')"; return; }
      corner "$OUT/icons-badge-own.png" "$OUT/icons-badge-own-c.png"; corner "$OUT/icons-badge-work.png" "$OUT/icons-badge-work-c.png"
      differ "$OUT/icons-badge-own-c.png" "$OUT/icons-badge-work-c.png" \
        && ok "badge: the work $label has a badge in its corner ($*)" || ko "badge: the work $label has no badge ($*)"
      # a badge drawn smaller than the icon (Fable c1-icons 1) lands in its top-left quarter: that quarter stays as the own app's
      rest "$OUT/icons-badge-own.png" "$OUT/icons-badge-own-r.png"; rest "$OUT/icons-badge-work.png" "$OUT/icons-badge-work-r.png"
      same "$OUT/icons-badge-own-r.png" "$OUT/icons-badge-work-r.png" \
        && ok "badge: the work $label is the own one's outside the corner ($*)" || ko "badge: the work $label differs outside the badge corner ($*)"
    }
    badge_of Fixture "$FIXTURE/$FIXTURE.Open" "$BUILTIN" original circle solid accent
    badge_of Fixture "$FIXTURE/$FIXTURE.Open" "$BUILTIN" line circle solid accent
    badge_of Calendar "com.google.android.calendar/com.android.calendar.AllInOneActivity" "$BUILTIN" line circle solid accent
    badge_of Fixture "$FIXTURE/$FIXTURE.Open" "$BUILTIN" dot none solid accent # Fable c1-icons 13: a dot had no badge
    # no pack: Calendar's own adaptive icon cut to the circle (Fable c1-icons 1: the badge went into the cached cut, at 160 dpi)
    badge_of Calendar "com.google.android.calendar/com.android.calendar.AllInOneActivity" "" original circle solid accent
    # a folder thumbnail goes through the same render: the folder with the work copy differs from the one with the own app
    seed classic; setp int accent -2093040; setp string icon_pack "$BUILTIN"; setp int grid_icon 80; setp boolean hide_labels true
    look line circle solid accent
    for who in own work; do
      k="$FIXTURE/$FIXTURE.Open"; [ $who = work ] && k="$k#$serial"
      setp string folder:bw "{\"name\":\"Bw\",\"where\":\"fav\",\"apps\":[\"$k\"]}"; setp string fav 'f:bw'
      capture Bw "$OUT/icons-badge-folder-$who.png" || ko "badge: no folder Bw ($who)"
    done
    [ -f "$OUT/icons-badge-folder-own.png" ] && [ -f "$OUT/icons-badge-folder-work.png" ] && differ "$OUT/icons-badge-folder-own.png" "$OUT/icons-badge-folder-work.png" \
      && ok "badge: the folder thumbnail of the work copy differs from the own app's (its badge)" || ko "badge: folder thumbnails of the own and the work app look alike"
  fi
fi

step "App pages with their own icons (Classic, drawer off)"
seed classic boolean drawer false $(model original circle solid accent)
chrome_cell() { # chrome_cell out.png: the Chrome cell on the app pages
  local n=0
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  while [ $n -lt 4 ]; do capture Chrome "$1" && return 0; A shell input swipe 900 1000 150 1000 300; rm -f "$SCR"; n=$((n + 1)); done
  return 1
}
chrome_cell "$OUT/icons-chrome-home-look.png" || ko "no Chrome on the app pages"
setp string drawer_icon_glyph original string drawer_icon_container square boolean drawer_follow false
chrome_cell "$OUT/icons-chrome-own-look.png" || ko "no Chrome on the app pages (own look)"
differ "$OUT/icons-chrome-home-look.png" "$OUT/icons-chrome-own-look.png" \
  && ok "the app pages draw their own plate (square, home circle)" || ko "the app pages' own plate changes nothing"

step "A folder on an app page shows its apps in the app pages' own look (Classic, drawer off)"
# cell 20: page two's first; drawer_labels: no white name under the tile; home: white glyphs, app pages: red
seed classic boolean drawer false int grid_cols 4 int grid_rows 5 int grid_pages 2 \
  string folder:x "{&quot;name&quot;:&quot;Cartella X&quot;,&quot;where&quot;:&quot;grid&quot;,&quot;apps&quot;:[&quot;com.google.android.calendar/com.android.calendar.AllInOneActivity&quot;]}" \
  string grid "{&quot;20&quot;:&quot;f:x&quot;}" int accent -2093040 string icon_pack "builtin:arcticons" \
  boolean drawer_follow false boolean drawer_labels false $(model original none solid text) string drawer_icon_color accent
folder_cell() { # folder_cell out.png: the folder on page two
  local n=0
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  while [ $n -lt 4 ]; do capture "Cartella X" "$1" && return 0; A shell input swipe 900 1000 150 1000 300; rm -f "$SCR"; n=$((n + 1)); done
  return 1
}
if folder_cell "$OUT/icons-folder-app-page.png"; then
  red=$(colour_pixels "$OUT/icons-folder-app-page.png" "$RED"); white=$(colour_pixels "$OUT/icons-folder-app-page.png" "#FFFFFF")
  [ "${red:-0}" -gt 20 ] && [ "${white:-0}" -eq 0 ] && ok "the folder's Calendar is red as the app page's icons ($red px), not the home's white" \
    || ko "folder on an app page: red $red, white $white px (expected red, no white)"
else ko "no Cartella X on the app pages"; fi

step "Icon picker (the fixture is an icon pack): in landscape the search and Cancel stay on the screen (issue 064)"
seed classic string fav "$FIXTURE/$FIXTURE.Open" boolean hide_labels false
read -r fx fy <<<"$(cx Fixture)" || ko "no Fixture on the home"
A shell input draganddrop "$fx" "$fy" "$fx" "$fy" 1500 # long-press: the app's menu
expect "Icona…" && tap "Icona…"; expect "Tutti i pacchetti di icone" && tap "Tutti i pacchetti di icone"
expect "Annulla"
A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 1
defer 'A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1'
wait_until 8 turned || ko "the screen did not turn"
find_box() { screen | grep -o 'class="android.widget.EditText"[^>]*' | grep -q 'bounds='; }
wait_until 5 find_box && wait_until 5 has "Annulla" && ok "landscape: the search field and Annulla are both on the screen" \
  || ko "landscape: the picker's search or Annulla is off the screen"
A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1
tap "Annulla"

# Issue 087: an app with no Arcticons entry and no monochrome layer shows the glyph of its category (the fixture declares
# android:appCategory="news"): the same as Calendar picked to show the newspaper icon by hand; off, the silhouette.
for mode in list classic; do
  step "Category glyph for an app without an Arcticons icon: $mode mode"
  size=icon_size; [ "$mode" = classic ] && size=grid_icon
  seed "$mode" string fav "com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;$FIXTURE/$FIXTURE.Open" \
    int accent -2093040 string icon_pack "$BUILTIN" boolean hide_labels true int $size 64 \
    string "icon:$CAL" "$BUILTIN|emoji_newspaper" $(model line circle solid accent)
  cal="$OUT/icons-$mode-category-calendar.png" fix="$OUT/icons-$mode-category-fixture.png"
  capture Calendar "$cal" || ko "$mode: no Calendar"
  capture Fixture "$fix" || ko "$mode: no fixture app"
  wc=$(colour_pixels "$cal" "#FFFFFF"); wf=$(colour_pixels "$fix" "#FFFFFF"); red=$(colour_pixels "$fix" "$RED")
  [ "${wc:-0}" -gt 20 ] && [ "${red:-0}" -gt 20 ] && near "${wf:-0}" "${wc:-0}" $((wc / 20 + 3)) \
    && ok "$mode: the fixture shows the news glyph like Calendar's picked newspaper (white $wf and $wc px, red plate)" \
    || ko "$mode: fixture white $wf px, Calendar white $wc px, fixture red $red px (expected the same glyph on a red plate)"
  setp boolean icon_category false
  capture Fixture "$OUT/icons-$mode-category-off.png" || ko "$mode: no fixture app (category icons off)"
  wo=$(colour_pixels "$OUT/icons-$mode-category-off.png" "#FFFFFF"); red=$(colour_pixels "$OUT/icons-$mode-category-off.png" "$RED")
  [ "${red:-0}" -gt 20 ] && ! near "${wo:-0}" "${wc:-0}" $((wc / 20 + 3)) \
    && ok "$mode: Category icons off: the silhouette, not the newspaper (white $wo px), the plate still red" \
    || ko "$mode: Category icons off: white $wo px against the newspaper's $wc, red $red px"
done
