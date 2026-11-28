# The icon model (issue 078), second half: built-in glyph treatments, the fixture apps, work profile, app pages, the picker.
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
  # icon_category false: the fixture declares a category (issue 087), these steps are about its silhouette; 64 dp: at 80 the cell cuts the plate's sides
  sz=icon_size; [ "$mode" = classic ] && sz=grid_icon
  seed "$mode" string fav "com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;$FIXTURE/$FIXTURE.Open" int accent -2093040 \
    string icon_pack "$BUILTIN" boolean hide_labels true boolean icon_category false int $sz 64 $(model line circle solid accent)
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
# icon_category false: the fixture declares a category (issue 087), this step is about its silhouette
seed classic string folder:fb "{\"name\":\"Fb\",\"where\":\"fav\",\"apps\":[\"$FIXTURE/$FIXTURE.Open\",\"com.google.android.calendar/com.android.calendar.AllInOneActivity\"]}" string fav 'f:fb' \
  int accent -2093040 string icon_pack "$BUILTIN" int grid_icon 80 boolean hide_labels true boolean icon_category false $(model line circle solid accent)
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
      seed classic int accent -2093040 string icon_pack "$pack" int grid_icon 64 boolean hide_labels true $(model "$@") string fav "$comp"
      capture "$label" "$OUT/icons-badge-own.png" || { ko "badge: no own $label"; return; }
      # del grid: Classic keeps the grid seeded from the first fav, the own app would stay in the first cell beside the work copy
      # and capture takes the first cell of that name (run 46109393420: own and badged work copy side by side, the own one compared)
      setp string fav "$comp#$serial" del grid; capture "$label" "$OUT/icons-badge-work.png" || { ko "badge: no work $label (serial '$serial')"; return; }
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
    # original without a container: the badge is drawn once into the cache, not on every bind (Fable c2-icons P2-1)
    badge_of Calendar "com.google.android.calendar/com.android.calendar.AllInOneActivity" "" original none solid accent
    # a folder thumbnail goes through the same render: the folder with the work copy differs from the one with the own app
    seed classic int accent -2093040 string icon_pack "$BUILTIN" int grid_icon 80 boolean hide_labels true $(model line circle solid accent)
    for who in own work; do
      k="$FIXTURE/$FIXTURE.Open"; [ $who = work ] && k="$k#$serial"
      setp string folder:bw "{\"name\":\"Bw\",\"where\":\"fav\",\"apps\":[\"$k\"]}" string fav 'f:bw'
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
