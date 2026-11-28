# The icon model (issue 078): glyph treatment x plate, with fill and colour; the old shapes migrate unchanged.
# Without the built-in pack Calendar's glyph is its monochrome layer; with it (issue 077) the Arcticons geometry. The Icons
# card's Line/Bold/Sharp tiles always draw a built-in glyph.
legacy() { setp del icon_glyph del icon_container del icon_fill del icon_color string icon_shape "$1" string themed_shape "$2"; }
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
# each row by its own tile, looked for on its own: a heading sits above its tiles and the next row may be below the screen,
# so whether two of them share the screen depends on where the scroll stops (the Category row of 087 moved it: run 46109393420)
reach "Riempimento: Pieno" >/dev/null && reach "${C}Colore del testo" >/dev/null \
  && ok "Original over the built-in set: the Fill and Colour rows show (its glyphs use them)" || ko "Original over the built-in set: no Fill or Colour row"
scroll_find exact "Icone per categoria" && ok "the built-in set: the Icons card offers Category icons" || ko "the built-in set: no Category icons row"
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
capture_calendar "$OUT/icons-pick-before.png" || ko "picker: Calendar icon not found"
read -r px py <<<"$(cx "desc:Calendar")"
hold "$px" "$py"
tap "Icona dell'app…"; tap "Arcticons (integrato)"
wait_until 5 has "calendar" && tap "calendar" # the field starts with the app's name
clear_text; typ okay
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
