# Settings, second half: the position arrows in both modes, List offsets, clock and line. Starts from its own seeds (the first half is 06-settings-a).
settings_home() { # open Settings → Customize from the home screen ($1: y of an empty spot)
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  A shell input swipe 540 "$1" 540 "$1" 1500 # not pg_settings: Settings comes back on the page it was left on
  tap "Impostazioni Ganimede"; tap "Personalizza"
}

for mode in classic list; do
  step "Settings arrows: $mode mode"
  seed $mode
  settings_home 1500   # an empty spot in both modes
  if [ $mode = classic ]; then parts=("Dock:classic_dock_y:su" "Griglia delle app:classic_grid_y:giù")
  else parts=("Orologio:list_clock_y:su" "Data e meteo:list_line_y:giù" "App:list_apps_y:giù" "Alfabeto:alpha_y:giù"); fi

  for part in "${parts[@]}"; do
    name="${part%%:*}"; rest="${part#*:}"; key="${rest%%:*}"; dir="${rest##*:}"; sg=1; [ "$dir" = su ] && sg=-1
    tap "$name"                                                   # its name under the preview picks the brick
    xy=$(find_desc "$name: Sposta $dir") || { ko "$name: no arrows"; continue; }
    early() { [ $((10#$(A shell date +%S | tr -d '\r'))) -lt 45 ]; } # the two pictures are ~6 s apart
    wait_until 20 early # the preview draws the time: a minute turning between the two pictures would change it too
    m0=$(A shell date +%H%M); A exec-out screencap -p > "$OUT/set-$key-before.png"
    read -r px1 py1 px2 py2 <<<"$(bounds "desc:Anteprima della tua schermata Home")"
    pcrop="$((px2 - px1))x$((py2 - py1))+$px1+$py1" # only the preview: the status-bar clock changes too
    set -- $xy; A shell input tap "$1" "$2"
    set1() { [ -n "$(pref "$key")" ]; }; wait_until 5 set1; v1=$(pref "$key")
    A shell input tap "$1" "$2"
    moreb() { [ "$(pref "$key")" != "$v1" ]; }; wait_until 5 moreb
    v=$(pref "$key"); [ $((${v:-0} * sg)) -gt 0 ] && ok "$name $dir twice: $key = $v dp" || ko "$name $dir: $key = ${v:-unset}"
    A shell input swipe "$1" "$2" "$1" "$2" 2000                  # held down two seconds: keeps going
    held() { local x; x=$(pref "$key"); [ $((${x:-0} * sg)) -gt $((${v:-0} * sg + 8)) ]; }; wait_until 3 held
    v2=$(pref "$key"); [ $((${v2:-0} * sg)) -gt $((${v:-0} * sg + 8)) ] && ok "$name held: $key $v → $v2 dp" || ko "$name hold did nothing ($v → $v2)"
    A exec-out screencap -p > "$OUT/set-$key-after.png"; m1=$(A shell date +%H%M)
    d=$(fdiff "$OUT/set-$key-before.png" "$OUT/set-$key-after.png" "$pcrop")
    if [ "$m0" != "$m1" ]; then ko "$name: the minute turned between the two pictures (proves nothing): $m0 → $m1"
    else [ "${d:-0}" -gt 0 ] && ok "$name: preview changed ($d px)" || ko "$name: preview unchanged (${d:-no} px differ in $pcrop)"; fi
    xy=$(find_desc "$name: Ripristina posizione"); set -- $xy; A shell input tap "$1" "$2"
    restored() { [ "$(pref "$key")" != "$v2" ]; }; wait_until 5 restored
  done

  if [ $mode = list ]; then # drag the clock brick up in the preview: it lands on the 4 dp grid, and Undo puts it back
    pill "Tutto lo schermo"
    tap "Tutto lo schermo"; wait_until 3 settled
    # The preview clock sits just above its date/weather row (text: date, then weather): grab it there.
    read -r lx0 ly0 _ _ <<<"$(bounds '[^"]*☁ imposta meteo')"
    cy=$((ly0 - $(dp 30))); A shell input draganddrop $((lx0 + $(dp 20))) "$cy" $((lx0 + $(dp 20))) $((cy - 60)) 1200 # 60 px up: inside the top limit
    # The stored value is the one "Posizione" shows (x … · y … dp), above the top, on the 4 dp grid.
    clocked() {
      v=$(pref list_clock_y); sy=$(screen | grep -o 'text="x [^"]* y [-+]*[0-9]* dp"' | head -1 | sed 's/.* y \([-+]*[0-9]*\) dp"$/\1/'); sy=${sy#+}
      [ -n "$v" ] && [ "$v" = "$sy" ] && [ "$v" -lt 0 ] && [ $((v % 4)) -eq 0 ]
    }
    # On a FAIL: the files now (a .bak = a write still in flight), then whether the value reaches the file late (slow disk) or never (not saved).
    wait_until 5 clocked && ok "clock dragged in the preview: list_clock_y = $v dp, as “Posizione” shows" \
      || { files=$(A exec-out run-as $PK ls shared_prefs | tr '\n' ' '); t=$SECONDS; late() { lv=$(pref list_clock_y); [ -n "$lv" ]; }
        wait_until 20 late && lt="the file got $lv $((SECONDS - t)) s later" || lt="still not in the file 20 s later"
        ko "drag in the preview: list_clock_y = ${v:-unset}, “Posizione” shows y ${sy:-nothing}; files: $files; $lt"; }
    dragged=${v:-0}
    wait_until 5 hasd "Orologio: Sposta su" && ok "the dragged brick's controls show" || ko "dragging did not pick the brick"
    tap "Annulla"
    zero() { local x; x=$(pref list_clock_y); [ "${x:-0}" -eq 0 ]; }; wait_until 5 zero
    v=$(pref list_clock_y); [ "$dragged" -lt 0 ] && [ "${v:-0}" -eq 0 ] && ok "Undo takes the drag back ($dragged → ${v:-unset})" || ko "undo: list_clock_y $dragged → ${v:-unset} (needs a stored drag < 0 first)"

    # The line has no size knob: hit its weather text, drag it, and prove only its key changed (it may cling to the clock's edge).
    line0=$(pref list_line_y); line0=${line0:-0}
    clock0=$(pref list_clock_y); clock0=${clock0:-0}
    if read -r line_x line_y <<<"$(cx '[^"]*☁ imposta meteo')" && [ -n "$line_x" ]; then
      A shell input draganddrop "$line_x" "$line_y" "$line_x" "$((line_y - $(dp 20)))" 1200
      lined() { local l c; l=$(pref list_line_y); c=$(pref list_clock_y); [ "${l:-0}" -ne "$line0" ] && [ "${c:-0}" -eq "$clock0" ]; }
      wait_until 5 lined && ok "line dragged without moving the clock" || ko "line drag: list_line_y=$(pref list_line_y), list_clock_y=$(pref list_clock_y)"
      wait_until 5 hasd "Data e meteo: Sposta su" && ok "dragging picked the no-knob line brick" || ko "line drag did not pick its brick"
      tap "Annulla"
      line_undone() { local l c; l=$(pref list_line_y); c=$(pref list_clock_y); [ "${l:-0}" -eq "$line0" ] && [ "${c:-0}" -eq "$clock0" ]; }
      wait_until 5 line_undone && ok "Undo restores the line drag and leaves the clock still" || ko "line Undo: list_line_y=$(pref list_line_y), list_clock_y=$(pref list_clock_y)"
    else ko "date/weather row not found in the preview"; fi

    # Drag the apps brick past the preview's start edge (the drag picks it): its fixed gesture strip is the hard limit.
    if read -r app_x app_y <<<"$(cx "Calendar")" && [ -n "$app_x" ]; then
      A shell input draganddrop "$app_x" "$app_y" "$(dp 1)" "$app_y" 1200
      apps_moved() { local x; x=$(pref apps_offset); [ -n "$x" ] && [ "$x" -ne 40 ]; }
      wait_until 5 apps_moved
      [ "$(pref apps_offset)" = 32 ] && ok "apps brick stops at the 32 dp gesture strip" \
        || ko "apps brick stopped at apps_offset=$(pref apps_offset), expected 32"
    else ko "Calendar row not found in the apps preview"; fi
  fi
done

step "List mode: old app offsets stay outside the gesture strip"
seed list int apps_offset 4
if read -r fav_x _ _ _ <<<"$(bounds "desc:Calendar")" && [ -n "$fav_x" ]; then
  [ "$fav_x" -ge "$(dp 32)" ] && ok "old apps_offset 4: favourites start outside the 32 dp strip" \
    || ko "old apps_offset 4: favourites start at $fav_x px, before $(dp 32) px"
else ko "Calendar favourite missing with old apps_offset 4"; fi

step "Settings: App arrows start from the effective offset"
settings_home 1500
# A tap on an app row in the preview picks the App brick (its pill may sit off screen in the strip).
read -r app_x app_y <<<"$(cx "Calendar")" && [ -n "$app_x" ] && A shell input tap "$app_x" "$app_y"
if xy=$(find_desc "App: Sposta a destra") && [ -n "$xy" ]; then
  set -- $xy; A shell input tap "$1" "$2"
  apps_arrow_moved() { [ "$(pref apps_offset)" != 4 ]; }
  wait_until 5 apps_arrow_moved
  [ "$(pref apps_offset)" = 36 ] && ok "old apps_offset 4: one outward arrow press starts at 32 and saves 36" \
    || ko "old apps_offset 4: one outward arrow press saved apps_offset=$(pref apps_offset), expected 36"
else ko "App: no right arrow"; fi

step "Settings: List clock and line move independently on home"
seed list
line_bounds() { bounds "☁ imposta meteo"; }
read -r _ clock_y0 _ _ <<<"$(clock_bounds)"
read -r _ line_y0 _ _ <<<"$(line_bounds)"
settings_home 1500
tap "Data e meteo"
if xy=$(find_desc "Data e meteo: Sposta giù") && [ -n "$xy" ]; then
  set -- $xy; A shell input tap "$1" "$2"; A shell input tap "$1" "$2"
else ko "Date and weather: no down arrow"; fi
line8() { [ "$(pref list_line_y)" = 8 ]; }
wait_until 5 line8 && ok "Date and weather saved +8 dp" || ko "list_line_y = $(pref list_line_y), expected 8"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
read -r _ clock_y1 _ _ <<<"$(clock_bounds)"
read -r _ line_y1 _ _ <<<"$(line_bounds)"
near "$clock_y1" "$clock_y0" "$(dp 1)" && ok "moving Date and weather leaves the clock still" \
  || ko "line move changed clock y $clock_y0 → $clock_y1"
moved "Date and weather" y "$line_y0" "$line_y1" "$(dp 8)"
settings_home 1500
tap "Orologio"
if xy=$(find_desc "Orologio: Sposta su") && [ -n "$xy" ]; then
  set -- $xy; A shell input tap "$1" "$2"; A shell input tap "$1" "$2"
else ko "Clock: no up arrow"; fi
clock8() { [ "$(pref list_clock_y)" = -8 ]; }
wait_until 5 clock8 && ok "Clock saved -8 dp" || ko "list_clock_y = $(pref list_clock_y), expected -8"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
read -r _ clock_y2 _ _ <<<"$(clock_bounds)"
read -r _ line_y2 _ _ <<<"$(line_bounds)"
near "$line_y2" "$line_y1" "$(dp 1)" && ok "moving Clock leaves Date and weather still" \
  || ko "clock move changed line y $line_y1 → $line_y2"
moved "Clock" y "$clock_y1" "$clock_y2" "$(dp -8)"
