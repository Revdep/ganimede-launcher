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
