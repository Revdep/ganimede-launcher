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
    cp "$2" "$OUT/icons-prev.png"; rm -f "$SCR"; n=$((n + 1))
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
strings() { # strings key value ...: several text settings in one write (value "-" removes it); the app restarts once
  local f="$OUT/prefs.xml"
  A shell am force-stop $PK
  A exec-out run-as $PK cat shared_prefs/ganimede.xml > "$f"
  while [ $# -gt 1 ]; do
    sed -i "/ name=\"$1\"/d" "$f"
    [ "$2" = - ] || sed -i "s@</map>@    <string name=\"$1\">$2</string>\n</map>@" "$f"
    shift 2
  done
  put_prefs "$f"
  start_home
}
look() { strings icon_glyph "$1" icon_container "$2" icon_fill "$3" icon_color "$4"; } # look glyph container fill colour
legacy() { strings icon_glyph - icon_container - icon_fill - icon_color - icon_shape "$1" themed_shape "$2"; }
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
  if [ -n "${2:-}" ] && ! hasd "$1" && xy=$(cx "desc:$2"); then set -- "$1" $xy; A shell input swipe 150 "$3" 900 "$3" 1500; wait_until 5 settled; fi # slow: no fling to stop with the tap
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
  seed "$mode"
  setp int accent -2093040; setp boolean hide_labels true # only the icon in the crop: no white label
  if [ "$mode" = classic ]; then setp int grid_icon 80; else setp int icon_size 80; fi
  look original none solid accent
  capture_calendar "$OUT/icons-$mode-original.png" || { ko "$mode: Calendar icon not found"; continue; }
  legacy theme circle
  capture_calendar "$OUT/icons-$mode-tinted.png" || { ko "$mode: Calendar icon not found after the update"; continue; }
  migrated "$mode Tinted" line circle solid accent
  look line circle solid accent
  capture_calendar "$OUT/icons-$mode-line-circle.png"
  same "$OUT/icons-$mode-tinted.png" "$OUT/icons-$mode-line-circle.png" \
    && ok "$mode: old Tinted looks as Line + Circle + Solid" || ko "$mode: migrated Tinted differs from Line + Circle + Solid"
  same "$OUT/icons-$mode-original.png" "$OUT/icons-$mode-line-circle.png" \
    && ko "$mode: Line leaves the icon unchanged" || ok "$mode: Line changes the icon"
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
  same "$OUT/icons-$mode-text.png" "$OUT/icons-$mode-accent.png" \
    && ko "$mode: theme and text colour look the same" || ok "$mode: theme colour differs from text colour"
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
setp int grid_icon 64 # at 80 dp the icon is wider than its cell, which cuts the plate's sides
look line none solid accent
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
look line circle solid accent
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
pv_now() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "name=\"icon_pack\">$BUILTIN<"; }
tapn "Arcticons (integrato)" 60 && wait_until 5 pv_now && ok "choosing it stores icon_pack=$BUILTIN" || ko "choosing it did not store icon_pack=$BUILTIN"
original_kept() { ! A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q 'name="icon_glyph">[a-z]' || stored icon_glyph original; }
original_kept && ok "choosing the built-in set keeps the icon style Original" || ko "choosing the built-in set changed icon_glyph"
reach "${S}Originale" >/dev/null && ok "with the built-in set the Icons card still offers Original" || ko "no Original tile with the built-in set"
reach "${C}Colore del testo" >/dev/null && exact "Colore" && exact "Riempimento" \
  && ok "Original over the built-in set: the Fill and Colour rows show (its glyphs use them)" || ko "Original over the built-in set: no Fill or Colour row"
tapn "Nessuno (icone delle app)" 60 && wait_until 5 stored icon_pack "" && original_kept && ok "back to no pack: the icon style is still Original" \
  || ko "back to no pack: the pack did not change, or icon_glyph did"

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
look line none solid accent # on the wallpaper: the seed's squircle would put the icon on an indigo plate (issue 078)
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
  seed classic
  setp string lang $lang
  if [ $lang = en ]; then pg_settings "Ganimede settings" "Touch a part of the preview to adjust it"; tapn "About" 15; done_by="converted and algorithmically restyled"
  else pg_settings; tapn "Informazioni" 15; done_by="convertite e ridisegnate in modo algoritmico"; fi # About is at the bottom of a long page
  credited() { shows "Arcticons" "CC BY-SA 4.0" "https://creativecommons.org/licenses/by-sa/4.0/" "$done_by"; }
  wait_until 5 credited && ok "$lang: About names Arcticons, CC BY-SA 4.0, its licence URI and “$done_by”" \
    || ko "$lang: About lacks the Arcticons credit (Arcticons, CC BY-SA 4.0, licence URI, “$done_by”)"
done

for mode in list classic; do
  step "Built-in treatments differ, the glyph centred on its plate: $mode mode"
  seed "$mode"
  setp int accent -2093040; setp string icon_pack "$BUILTIN"; setp boolean hide_labels true # only the icon in the crop
  if [ "$mode" = classic ]; then setp int grid_icon 64; else setp int icon_size 64; fi
  sums=""
  for g in original line bold sharp; do
    look $g circle solid accent
    capture_calendar "$OUT/icons-$mode-builtin-$g.png" || ko "$mode: no Calendar with $g"
    sums="$sums$(magick "$OUT/icons-$mode-builtin-$g.png" -format %# info:)"$'\n'
  done
  n=$(printf %s "$sums" | sort -u | grep -c .)
  [ "$n" = 3 ] && ok "$mode: Line, Bold and Sharp differ" || ko "$mode: $n different of Line, Bold, Sharp"
  same "$OUT/icons-$mode-builtin-original.png" "$OUT/icons-$mode-builtin-line.png" \
    && ok "$mode: Original draws a built-in icon as Line (it has no colours of its own)" || ko "$mode: built-in Original differs from Line"
  img="$OUT/icons-$mode-builtin-line.png"
  if read -r gx gy gw gh <<<"$(box "$img" "#FFFFFF")" && [ -n "$gx" ] && read -r qx qy qw qh <<<"$(box "$img" "$RED")" && [ -n "$qx" ]; then
    dx=$(( (2 * gx + gw) - (2 * qx + qw) )); dy=$(( (2 * gy + gh) - (2 * qy + qh) )) # twice the offset of the centres
    [ ${dx#-} -le 4 ] && [ ${dy#-} -le 4 ] && ok "$mode: built-in glyph centred on its plate (offset $dx/2, $dy/2 px)" \
      || ko "$mode: built-in glyph off centre by $dx/2, $dy/2 px (glyph $gx $gy $gw $gh, plate $qx $qy $qw $qh)"
  else ko "$mode: no white glyph or red plate in the built-in Line icon"; fi
done

step "Icons card: Line, Bold, Sharp on a built-in glyph; rows shown only where they mean something"
seed classic
setp int accent -2093040
look original circle solid accent
pg_settings; stap "Personalizza"
pill "Temi"; tap "Temi"
find_desc "${S}Spigoloso" >/dev/null || ko "no Sharp tile"
for tile in Linea Marcato Spigoloso; do hasd "$S$tile" && ok "sees the $tile tile" || ko "no $tile tile"; done
A exec-out screencap -p > "$OUT/icons-screen.png"
sums=""
for tile in Originale Linea Marcato Spigoloso; do # the icon part of each tile: 10 dp padding, then the 44 dp icon
  if read -r x1 y1 x2 y2 <<<"$(bounds "desc:$S$tile")" && [ -n "$x1" ]; then
    s=$(dp 44)
    magick "$OUT/icons-screen.png" -crop "${s}x${s}+$(( (x1 + x2 - s) / 2 ))+$((y1 + $(dp 10)))" +repage "$OUT/icons-tile-$tile.png"
    sums="$sums$(magick "$OUT/icons-tile-$tile.png" -format %# info:)"$'\n'
  fi
done
n=$(printf %s "$sums" | sort -u | grep -c .)
[ "$n" = 4 ] && ok "Original, Line, Bold, Sharp tiles all differ" || ko "$n different tiles of 4"
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
"$HERE/../gradlew" -p "$HERE/.." -q -Pe2e :fixture:assembleDebug >/dev/null 2>&1 \
  && A install -r "$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" >/dev/null && ok "fixture app built and installed" \
  || ko "fixture app not built or installed (./gradlew -Pe2e :fixture:assembleDebug)"
icons_fixture_cleanup() { A uninstall "$FIXTURE" >/dev/null 2>&1; }
defer icons_fixture_cleanup
seed classic
setp string fav "$FIXTURE/$FIXTURE.Open"; setp boolean hide_labels true
look original none solid accent
capture Fixture "$OUT/icons-fixture-none.png" || ko "the fixture's icon is not on the home"
look original circle solid accent
capture Fixture "$OUT/icons-fixture-circle.png" || ko "the fixture's icon is gone with a circle (the app may have crashed)"
same "$OUT/icons-fixture-none.png" "$OUT/icons-fixture-circle.png" \
  && ok "a square icon is not cut to the circle" || ko "a square icon changed with the circle shape"

step "Built-in set with Original: covered apps get the accent glyph, the others keep their own colours"
setp string fav "com.google.android.calendar/com.android.calendar.AllInOneActivity\&#10;$FIXTURE/$FIXTURE.Open"
setp int accent -2093040; setp string icon_pack "$BUILTIN"
look original none solid accent
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

step "App pages with their own icons (Classic, drawer off)"
seed classic
setp boolean drawer false
look original circle solid accent
chrome_cell() { # chrome_cell out.png: the Chrome cell on the app pages
  local n=0
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  while [ $n -lt 4 ]; do capture Chrome "$1" && return 0; A shell input swipe 900 1000 150 1000 300; rm -f "$SCR"; n=$((n + 1)); done
  return 1
}
chrome_cell "$OUT/icons-chrome-home-look.png" || ko "no Chrome on the app pages"
strings drawer_icon_glyph original drawer_icon_container square
setp boolean drawer_follow false
chrome_cell "$OUT/icons-chrome-own-look.png" || ko "no Chrome on the app pages (own look)"
same "$OUT/icons-chrome-home-look.png" "$OUT/icons-chrome-own-look.png" \
  && ko "the app pages' own plate changes nothing" || ok "the app pages draw their own plate (square, home circle)"

step "A folder on an app page shows its apps in the app pages' own look (Classic, drawer off)"
seed classic
setp boolean drawer false; setp int grid_cols 4; setp int grid_rows 5; setp int grid_pages 2 # cell 20: page two's first
setp string folder:x "{\&quot;name\&quot;:\&quot;Cartella X\&quot;,\&quot;where\&quot;:\&quot;grid\&quot;,\&quot;apps\&quot;:[\&quot;com.google.android.calendar/com.android.calendar.AllInOneActivity\&quot;]}"
setp string grid "{\&quot;20\&quot;:\&quot;f:x\&quot;}"
setp int accent -2093040; setp string icon_pack "builtin:arcticons"
setp boolean drawer_follow false; setp boolean drawer_labels false # no white name under the tile
look original none solid text; strings drawer_icon_color accent # home: white glyphs; app pages: red
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
