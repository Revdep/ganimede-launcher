# The icon model (issue 078), third part: the fixture apps' fallback glyphs, the work profile badge, the picker, category glyphs.
step "The fixture app (old-style icon, monochrome layer, category, icon pack) is installed"
icons_fixture
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

step "Icon picker (the fixture is an icon pack): in landscape the search and Cancel stay on the screen (issue 064)"
# rotate: the picker is a dialog of the home, which stays upright unless its rotation is allowed (HomeLook); an app's menu
# says "Icona dell'app…" ("Icona…" is a folder's or a shortcut's): run 46109393420 found neither the item nor a turned screen
seed classic string fav "$FIXTURE/$FIXTURE.Open" boolean hide_labels false boolean rotate true
read -r fx fy <<<"$(cx Fixture)" || ko "no Fixture on the home"
A shell input draganddrop "$fx" "$fy" "$fx" "$fy" 1500 # long-press: the app's menu
expect "Icona dell'app…" && tap "Icona dell'app…"; expect "Tutti i pacchetti di icone" && tap "Tutti i pacchetti di icone"
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
