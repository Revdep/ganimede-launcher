# The icon model (issue 078), second part: built-in glyph treatments, the Icons card, an old-style icon, app pages.
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
# Seven tiles (080 added Duotone and Hollow) make a row wider than the screen: each is brought whole on the screen along
# its row before it is seen and cropped, one screenshot each (run 46109393420: Duotone and Hollow were off to the right).
xy=$(find_desc "${S}Originale") || ko "no Original tile"
row=${xy#* }
! screen | grep -qiE 'text="[^"]*(Cutout|Ritaglio)' && ok "Icon style: no Cutout tile" || ko "a Cutout tile is still offered"
sums=""
for tile in Originale Linea Marcato Spigoloso Bicolore Contorno; do # the icon part of each tile: 10 dp padding, then the 44 dp icon
  if [ -n "$row" ] && htile "$S$tile" "$row" && read -r x1 y1 x2 y2 <<<"$(bounds "desc:$S$tile")" && [ -n "$x1" ]; then
    ok "sees the $tile tile"
    A exec-out screencap -p > "$OUT/icons-screen.png"
    s=$(dp 44)
    magick "$OUT/icons-screen.png" -crop "${s}x${s}+$(( (x1 + x2 - s) / 2 ))+$((y1 + $(dp 10)))" +repage "$OUT/icons-tile-$tile.png"
    sums="$sums$(magick "$OUT/icons-tile-$tile.png" -format %# info:)"$'\n'
  else ko "no $tile tile"; fi
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
xy=$(reach "${S}Marcato") && htile "${S}Puntini (minimal)" "${xy#* }" && read -r px py <<<"$(cx "desc:${S}Puntini (minimal)")" \
  && A shell input tap "$px" "$py"
wait_until 5 stored icon_glyph dot || ko "Dots saved nothing"
reach "${S}Puntini (minimal)" >/dev/null
valid && ! exact "Piastra" && ! exact "Riempimento" && ok "Dots: no Plate, no Fill" || ko "Dots shows Plate or Fill"
! screen | grep -qiE 'text="[^"]*(Cutout|Ritaglio|Colorata|Monocromatica|Tinted|Monochrome)' \
  && ok "no Cutout, Tinted or Monochrome anywhere" || ko "an old icon style is still offered"

step "An old-style (not adaptive) icon stays as it is with Original and a shape"
icons_fixture
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

# Fable c3 P2-1: a pinned shortcut taken out of the favourites takes its picked icon with it, or the 4 MB set stays needed
# (and the key stays in the backup).
step "Remove from favourites also drops the shortcut's picked icon"
SC="s:org.ganimede.e2e|zz1|Zz Posta"
seed list string fav "$SC" string "icon:$SC" "$BUILTIN|okay"
pick_stored() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -qF "name=\"icon:$SC\">$BUILTIN|okay<"; }
pick_stored && ok "the picked icon is stored for the shortcut" || ko "the seeded pick is not stored"
wait_until 8 has "Zz Posta" || ko "the shortcut is not in the list"
read -r ax ay <<<"$(cx "Zz Posta")"; hold "$ax" "$ay"
expect "Rimuovi dai preferiti" && tap "Rimuovi dai preferiti"
wait_until 5 hasnt "Zz Posta"
prefs_read && ! grep -qF "name=\"icon:$SC\"" <<<"$PREFS_XML" && ok "the picked icon went with the shortcut" || ko "the picked icon is still stored after Rimuovi dai preferiti, or prefs unreadable"
