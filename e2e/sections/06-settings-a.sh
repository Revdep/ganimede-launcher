# Settings, first half (the second is 06-settings-b: arrows, offsets). On a configured app: only the chosen mode's controls; each brick of the home screen is picked by its name
# under the preview, its position arrows change the preview and the setting, and holding an arrow repeats; in List
# mode a brick is also dragged in the preview, and Undo takes that back.
step "Settings: only the chosen mode's controls"
seed classic
pg_settings
stap "Personalizza"
wait_until 5 has "Griglia delle app" && has "Dock" && ok "classic bricks offered by name" || ko "classic bricks missing"
hasnt "Alfabeto" && ok "no list-only bricks in classic" || ko "list bricks shown in classic"
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
# The undo steps outlive the rebuild too (issue 063)
if read -r ux uy <<<"$(cx "desc:Dock: Sposta su")" && [ -n "$ux" ]; then
  A shell input tap "$ux" "$uy"; wait_until 5 moved4
  for s in 1.15 "$fs"; do setting system font_scale "$s" || continue; wait_until 5 settled; done
  wait_until 5 has "Annulla" && tap "Annulla"; wait_until 5 undone
  [ -z "$(pref classic_dock_y)" ] && ok "Undo still takes the move back after Android rebuilt the settings" || ko "undo lost on a rebuild: classic_dock_y = $(pref classic_dock_y)"
else ko "dock arrows missing after the rebuild"; fi
# The Drawer pill is the app pages: the drawer off, every page after the first holds apps. Switch the drawer off here.
pill "Tutto lo schermo"; tap "Tutto lo schermo"; see "Attiva cassetto app" && tap "Attiva cassetto app"
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
