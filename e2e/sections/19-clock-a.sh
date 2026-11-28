emu_only "clock: disables apps and sets accessibility" || return 0
# Clock, first half: every clock style fits on screen in List and Classic (the second half is 19-clock-b: alarms, date, screen reader).
# Every clock style has an on-screen view and exposes its complete value in List and a user-resized Classic block.
# ClockFitTest checks the draw bounds themselves; screenshots cannot separate translucent ink from a moving wallpaper.
clock_complete() {
  screen | grep -qE 'text="[0-9]{2}:[0-9]{2} · [^"]+, [^"]+ [0-9]+"'
}

resize_classic_clock() {
  local b x1 y1 x2 y2 hb hx hy
  b=$(clock_bounds) || return 1
  read -r x1 y1 x2 y2 <<<"$b"
  A shell input swipe $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) 1300
  tap "Ridimensiona" || return 1
  wait_until 5 hasd "Ridimensiona bordo inferiore" || return 1
  hb=$(bounds "desc:Ridimensiona bordo inferiore"); [ -n "$hb" ] || return 1
  read -r hx hy <<<"$(cx "desc:Ridimensiona bordo inferiore")"
  A shell input swipe "$hx" "$hy" "$hx" $((hy - $(dp 120))) 800
  wait_until 5 nod "Ridimensiona bordo inferiore"
}

styles=(overlap basic dayofweek vertical horizontal words flip analog)
for mode in list classic; do
  step "Clock fits every style: $mode mode"
  # one restart per setting change: every setting a check needs goes in the same seed or setp
  if [ "$mode" = list ]; then
    # the furthest user-selectable position leaves the least width
    seed list string text_color dark boolean clock24 true int list_clock_size 160 int clock_offset 240
  else
    seed classic string text_color dark boolean clock24 true
    resize_classic_clock && ok "classic clock uses the smallest user-resized block" || ko "classic clock could not be resized"
  fi

  for hand in ltr rtl; do
    [ "$hand" = rtl ] && lh=true || lh=false
    if [ "$mode" = classic ] && [ "$hand" = ltr ]; then
      setp int list_clock_size 40 string clock_style basic boolean left_handed $lh
      read -r small_x1 small_y1 small_x2 small_y2 <<<"$(clock_bounds)"
      setp int list_clock_size 160
      read -r large_x1 large_y1 large_x2 large_y2 <<<"$(clock_bounds)"
      if [ -n "$small_x1" ] && [ -n "$large_x1" ] \
        && [ "$((small_x2 - small_x1))" -eq "$((large_x2 - large_x1))" ] \
        && [ "$((small_y2 - small_y1))" -eq "$((large_y2 - large_y1))" ]; then
        ok "classic clock ignores the List-only size setting"
      else
        ko "classic clock inherited the List-only size setting"
      fi
    fi
    for style in "${styles[@]}"; do
      setp string clock_style "$style" boolean left_handed $lh
      wait_until 5 clock_bounds >/dev/null \
        && ok "$mode $style $hand: clock container stays on-screen" \
        || ko "$mode $style $hand: clock container is outside the screen"
      wait_until 5 clock_complete \
        && ok "$mode $style $hand: complete time and date are exposed" \
        || ko "$mode $style $hand: incomplete clock information"
    done
  done
  shot "35-clock-$mode-max-rtl"
done
