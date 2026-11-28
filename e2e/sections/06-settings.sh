# Settings on a configured app: only the chosen mode's controls; each brick of the home screen is picked by its name
# under the preview, its position arrows change the preview and the setting, and holding an arrow repeats; in List
# mode a brick is also dragged in the preview, and Undo takes that back.
step "Settings: only the chosen mode's controls"
seed classic
pg_settings
stap "Personalizza"
wait_until 5 has "Griglia delle app" && has "Dock" && ok "classic bricks offered by name" || ko "classic bricks missing"
has "Alfabeto" && ko "list bricks shown in classic" || ok "no list-only bricks in classic"
expect "Sistema icone, widget e orologio"                  # classic bricks are arranged on the home itself
pill "Temi"
expect "Temi"; pill "Orologio"; expect "Orologio"; hasnt "Cassetto" && ok "drawer on: no Drawer pill" || ko "Drawer pill shown with the drawer on"
pill "Temi"; tap "Temi"; see "CARATTERE"                                  # card titles are upper case
pill "Tutto lo schermo"
tap "Tutto lo schermo"
tap "Griglia delle app"; see "Bordo sinistro"
see "5 × 6"; xy=$(cx "desc:5 × 6"); set -- $xy; A shell input tap "$1" "$2"
homegrid() { [ "$(pref grid_cols)" = 5 ] && [ "$(pref grid_rows)" = 6 ]; }; wait_until 5 homegrid
homegrid && ok "home grid picker saves 5 × 6" || ko "home grid = $(pref grid_cols) × $(pref grid_rows)"
shot 26-settings-classic
tap "Dock"
if read -r ux uy <<<"$(cx "desc:Dock: Sposta su")" && [ -n "$ux" ]; then
  A shell input tap "$ux" "$uy"
  moved4() { [ "$(pref classic_dock_y)" = -4 ]; }; wait_until 5 moved4
  [ "$(pref classic_dock_y)" = "-4" ] && ok "the dock's own arrow moves it 4 dp" || ko "dock arrow: classic_dock_y = $(pref classic_dock_y)"
  tap "Annulla"
  undone() { [ -z "$(pref classic_dock_y)" ]; }
  wait_until 5 undone
  [ -z "$(pref classic_dock_y)" ] && ok "Undo takes the move back" || ko "undo left classic_dock_y = $(pref classic_dock_y)"
else ko "dock arrows missing"; fi
fs=$(A shell settings get system font_scale | tr -d '\r'); [ "$fs" = null ] && fs=1.0
for s in 1.15 "$fs"; do # Android builds the settings again: the picked brick stays picked
  setting system font_scale "$s" || continue; sleep 2; wait_until 5 settled
  dockarrows() { [ -n "$(cx "desc:Dock: Sposta su")" ]; }; wait_until 5 dockarrows && ok "font scale $s: the Dock stays picked" || ko "font scale $s: the picked Dock was lost"
done
# The Drawer pill is the app pages: the drawer off, every page after the first holds apps. Switch the drawer off here.
pill "Tutto lo schermo"; tap "Tutto lo schermo"; see "Attiva cassetto app" && tap "Attiva cassetto app"
bpref() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "name=\"$1\" value=\"$2\""; }
wait_until 5 bpref drawer false && ok "the drawer switch is saved off" || ko "drawer not saved off"
pill "Orologio"
tap "Orologio"; wait_until 5 has "✓ Sovrapposto" && ok "Classic clock pill: the clock styles" || ko "clock pill shows no clock styles"
pill "Cassetto"; tap "Cassetto"; expect "Segui home"
wait_until 5 hasd "Anteprima delle pagine delle app" && ok "Drawer pill: the preview shows the app pages" || ko "preview did not switch to the app pages"
pill "Tutto lo schermo"
tap "Tutto lo schermo"
wait_until 5 hasd "Anteprima della tua schermata Home" && ok "another pill: the preview is the home again" || ko "preview stayed on the app pages"
# the real home: Calendar sits on page one, Chrome on an app page (drawer off)
cell_h() { local x1 y1 x2 y2; read -r x1 y1 x2 y2 <<<"$(bounds "desc:$1")"; echo $((${y2:-0} - ${y1:-0})); }
chrome_h() { local n=0 h; while [ $n -lt 4 ]; do h=$(cell_h Chrome); [ "$h" -gt 0 ] && break; A shell input swipe 900 1000 150 1000 300; n=$((n + 1)); done; echo "$h"; }
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
if read -r x1 y1 x2 y2 <<<"$(bounds "desc:Calendar")" && [ -n "$x1" ]; then
  cell_w=$((x2 - x1)); near "$cell_w" "$((W / 5))" "$(dp 12)" && ok "the real home has five columns" \
    || ko "five-column home cell is $cell_w px, expected about $((W / 5))"
  cal0=$((y2 - y1))
else ko "Calendar cell missing from the five-column home"; cal0=0; fi
chrome0=$(chrome_h); [ "$chrome0" -gt 0 ] && ok "an app page shows Chrome" || ko "no Chrome on the app pages"
# back in the settings, the drawer stops following the home and gets bigger icons
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
home_menu; tap "Impostazioni Ganimede"; tap "Personalizza" # not pg_settings: Settings comes back on the page it was left on
pill "Cassetto"; tap "Cassetto"
tap "Segui home"
wait_until 5 bpref drawer_follow false && ok "the drawer stops following home" || ko "drawer_follow was not saved false"
home_icon=$(pref grid_icon)
see "Dimensione icone"
if xy=$(find_desc "Dimensione icone: Aumenta") && [ -n "$xy" ]; then set -- $xy; A shell input tap "$1" "$2"; else ko "no icon size slider in the Drawer pill"; fi
drawericon() { [ -n "$(pref drawer_icon)" ]; }; wait_until 5 drawericon
[ -n "$(pref drawer_icon)" ] && [ "$(pref grid_icon)" = "$home_icon" ] && ok "drawer icon size is its own" \
  || ko "drawer_icon=$(pref drawer_icon), grid_icon=$(pref grid_icon) (was $home_icon)"
see "Nomi delle app nel cassetto" && tap "Nomi delle app nel cassetto"
wait_until 5 bpref drawer_labels false && ok "the drawer names switch is saved" || ko "the drawer names switch saved nothing"
tap "Annulla"
nolabels() { ! bpref drawer_labels false; }
wait_until 5 nolabels && ok "Undo takes the drawer names switch back" || ko "undo left drawer_labels false"
# its effect on the real pages: bigger icons on the app pages, the home page as it was
setp int drawer_icon 72
chrome1=$(chrome_h)
[ "$chrome1" -ge $((chrome0 + $(dp 18))) ] && ok "app page icons grew ($chrome0 → $chrome1 px)" || ko "app page cell $chrome0 → $chrome1 px, expected 24 dp more"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
near "$(cell_h Calendar)" "$cal0" "$(dp 2)" && ok "the home page keeps its icon size" || ko "home page cell was $cal0 px, now $(cell_h Calendar)"
setp boolean drawer_follow true
near "$(chrome_h)" "$chrome0" "$(dp 2)" && ok "following home again: app pages back to the home's size" || ko "following home left the app pages at another size"

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
    wait_until 5 clocked && ok "clock dragged in the preview: list_clock_y = $v dp, as “Posizione” shows" \
      || ko "drag in the preview: list_clock_y = ${v:-unset}, “Posizione” shows y ${sy:-nothing}; files: $(A exec-out run-as $PK ls shared_prefs | tr '\n' ' ')"
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
clock_bounds() { bounds "desc:Orologio, tocca per le sveglie"; }
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

step "Settings: List mode pills"
seed list
A shell input swipe 540 1500 540 1500 1500; tap "Impostazioni Ganimede"; tap "Personalizza"
wait_until 5 has "Orologio" && has "Data e meteo" && ok "List: clock and line have their own bricks" || ko "List clock or line brick missing"
pill "Temi"
expect "Temi"; hasnt "Cassetto" && ok "List: no Drawer pill" || ko "List shows a Drawer pill"

step "Settings: a stored value no choice matches (e.g. from a backup) marks no choice"
seed classic int accent -16744320 int dim_home 50 # teal and 50 %: no named colour, no tile
pg_settings
lookline() { dump | grep -q 'text="Classica · Sovrapposto · Predefinito"'; }  # mode · clock · font, and no colour name
wait_until 5 lookline && ok "custom accent: the summary names no colour" || ko "custom accent: summary names a colour"
stap "Personalizza"; tap "Tutto lo schermo"; see "✓ Medio"     # dim behind the list: 60, its default
hasnt "✓ Spento" && ok "dim_home 50: no tile marked" || ko "dim_home 50 marked “Spento”"
