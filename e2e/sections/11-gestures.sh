# gestures: classic pages (swipe, create/delete, edge turn while dragging, drawer off), the drawer following the finger, home gestures.
# Local helpers (could move to lib.sh): texts, pages_n, swipe_h, hold_drag.
texts() { dump | grep -o ' text="[^"]\+"' | sort -u; }
pages_n() { pref grid_pages; }
pages_is() { [ "$(pages_n)" = "$1" ]; }
pages_ge2() { [ "$(pages_n)" -ge 2 ] 2>/dev/null; }
# horizontal swipe in the empty middle of the grid, then wait for the pager to stop
swipe_h() { A shell input swipe "$1" "$((H / 2))" "$2" "$((H / 2))" 250; wait_until 5 settled; }
# a held drag by hand: long-press at x1,y1, glide to x2,y2, (drift: then straight on down by $6 px, as a real finger)
# stay there $5 s, lift (input draganddrop cannot hold at the end). The glide starts once the long-press menu shows (the
# icon is armed): after a restart a loaded emulator can run the long-press late, and moves sent before it make a page
# swipe instead of a drag (rel1/6: Calendar unmoved, no menu, page 1; lib.sh home_menu, issue 067)
hold_drag() {
  local x1=$1 y1=$2 x2=$3 y2=$4 hold=$5 drift=${6:-0} i
  A shell input motionevent DOWN "$x1" "$y1"; rm -f "$SCR"
  wait_until 5 has "Rinomina" || echo "  WARN: long press lost (hold_drag): no menu before the glide"
  for i in 1 2 3 4 5 6 7 8; do A shell input motionevent MOVE $((x1 + (x2 - x1) * i / 8)) $((y1 + (y2 - y1) * i / 8)); done
  for i in 1 2 3 4; do [ "$drift" = 0 ] || A shell input motionevent MOVE "$x2" $((y2 + drift * i / 4)); done
  sleep "$hold" # a gesture hold, not a wait: the finger stays down
  A shell input motionevent MOVE "$x2" $((y2 + drift)); A shell input motionevent UP "$x2" $((y2 + drift))
}
# the app list (drawer) is open: its search field is on screen
listed() { has "Cerca" || hasd "Cerca"; }

step "Gestures: classic page swipe (drawer off puts apps on pages 2+)"
seed classic; setp boolean drawer false
wait_until 10 pages_ge2; n=$(pages_n)
[ "${n:-1}" -ge 2 ] && ok "drawer off made $n pages" || { ko "drawer off left ${n:-1} page(s)"; n=2; }
wait_until 5 has Calendar
p1=$(texts)
swipe_h $((W - $(dp 40))) $(dp 40)
hasnt Calendar && ok "swipe left: page 1 icons are gone" || ko "Calendar still shown after swiping left"
p2=$(texts); [ "$p2" != "$p1" ] && [ -n "$(echo "$p2" | grep -v 'Orologio')" ] && ok "swipe left shows a different page of apps" || ko "page 2 looks like page 1"
shot 40-page2
for _ in $(seq 2 "$n"); do swipe_h $((W - $(dp 40))) $(dp 40); done # to the last page
last=$(texts)
swipe_h $((W - $(dp 40))) $(dp 40)
[ "$(texts)" = "$last" ] && ok "swipe left at the last page stays" || ko "the last page moved on a swipe left"
for _ in $(seq 2 "$n"); do swipe_h $(dp 40) $((W - $(dp 40))); done
wait_until 5 has Calendar && ok "swipes right come back to page 1" || ko "no way back to page 1"

step "Gestures: drawer off, swipe up opens no drawer"
A shell input swipe $((W / 2)) $((H * 6 / 10)) $((W / 2)) $((H / 5)) 300
wait_until 5 settled
valid && ! listed && ok "swipe up opens nothing" || ko "swipe up opened the drawer with it switched off (or no screen read)"
has Calendar && ok "home screen still in front" || ko "home screen gone after the swipe up"

step "Gestures: Home on a side page goes to page 1, back from an app keeps the page"
seed classic; setp boolean drawer false; wait_until 10 pages_ge2; wait_until 5 has Calendar
swipe_h $((W - $(dp 40))) $(dp 40); wait_until 5 hasnt Calendar
side_app=$(texts | grep -E 'text="[A-Za-z]' | head -1 | sed 's/ text="//; s/"$//')
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
wait_until 5 has Calendar && ok "Home on page 2: page 1 again" || ko "Home on page 2 stayed on page 2"
swipe_h $((W - $(dp 40))) $(dp 40); wait_until 5 hasnt Calendar
tap "$side_app"; wait_until 10 eval '! focus'
A shell input keyevent KEYCODE_HOME; wait_until 10 focus; wait_until 5 settled
has "$side_app" && hasnt Calendar && ok "back from “$side_app”: still on page 2" || ko "back from “$side_app”: page 2 lost"
# (Home mid-swipe is not testable here: with an injected finger held down, Home recreates the activity on page 1)
# Home while resizing the clock ends the resizing, as a touch outside or Back does
seed classic
read -r x1 y1 x2 y2 <<<"$(bounds "desc:Orologio, tocca per le sveglie")"
A shell input swipe $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) 1300
tap "Ridimensiona"; wait_until 5 hasd "Ridimensiona bordo destro" && ok "resizing the clock" || ko "no resize handles"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
wait_until 5 nod "Ridimensiona bordo destro" && ok "Home ends the resizing" || ko "Home left the resize handles"

step "Gestures: page create / delete with confirm"
# page 2 gets apps through drawer off, then the drawer comes back on: deleting the page then takes its icons off the home screen
seed classic; setp boolean drawer false; wait_until 10 pages_ge2; setp boolean drawer true
n=$(pages_n); [ "${n:-1}" -ge 2 ] && ok "page 2 holds apps ($n pages)" || ko "no page 2 ($n)"
swipe_h $((W - $(dp 40))) $(dp 40)
gone_app=$(texts | grep -E 'text="[A-Za-z]' | head -1 | sed 's/ text="//; s/"$//')
[ -n "$gone_app" ] && ok "page 2 shows “$gone_app”" || ko "page 2 shows no app"
read -r dx dy <<<"$(cx "desc:Regola pagine")"; A shell input tap "$dx" "$dy"
expect "Pagina 2"
read -r nx ny <<<"$(cx "desc:Nuova pagina")"; A shell input tap "$nx" "$ny"
wait_until 5 pages_is $((n + 1)) && ok "New page: pages $n → $((n + 1))" || ko "New page: pages $(pages_n), expected $((n + 1))"
shot 41-arrange
read -r xx xy <<<"$(cx "desc:Elimina pagina")"; A shell input tap "$xx" "$xy"   # the cross of page 2
expect "Eliminare questa pagina?"
tap "Elimina"
wait_until 5 hasnt "Eliminare questa pagina?"
wait_until 5 pages_is "$n" && ok "Delete page (confirmed): pages back to $n" || ko "Delete page: pages $(pages_n), expected $n"
tap "Fatto"; wait_until 5 hasnt "Pagina 2"
A shell input keyevent KEYCODE_HOME; wait_until 5 has Calendar || ko "Home did not bring page 1 back" # the + left the pager on the last page
seen=0
for _ in $(seq 1 "$n"); do has "$gone_app" && seen=1; swipe_h $((W - $(dp 40))) $(dp 40); done
[ $seen = 0 ] && ok "“$gone_app” is gone from every page" || ko "“$gone_app” still on a page after deleting its page"
# an empty page goes without a question
seed classic; setp int grid_pages 3
read -r dx dy <<<"$(cx "desc:Regola pagine")"; A shell input tap "$dx" "$dy"; expect "Pagina 3"
read -r xx xy <<<"$(cx "desc:Elimina pagina")"; A shell input tap "$xx" "$xy"
wait_until 5 pages_is 2 && ok "an empty page is deleted without asking (3 → 2)" || ko "empty page: pages $(pages_n), expected 2"
hasnt "Eliminare questa pagina?" && ok "no confirm for an empty page" || ko "asked to delete an empty page"
tap "Fatto"

step "Gestures: edge page-turn while dragging an icon"
seed classic; setp int grid_pages 2
wait_until 5 has Calendar
read -r ax ay <<<"$(cx Calendar)"
hold_drag "$ax" "$ay" $((W - $(dp 16))) "$ay" 1.5
wait_until 6 hasnt Clock
hasnt Clock && ok "the grid turned to page 2 (page 1 icons gone)" || ko "held at the right edge: still on page 1"
hasnt Clock && has Calendar && ok "Calendar dropped on page 2" || ko "Calendar not on page 2"
shot 42-edge-turn
swipe_h $(dp 40) $((W - $(dp 40)))
has Clock && ok "page 1 again: Clock there" || ko "page 1 lost Clock"
hasnt Calendar && ok "Calendar left page 1" || ko "Calendar still on page 1"
# a real finger at the edge drifts: going down into the next cell must not cancel the turn
seed classic; setp int grid_pages 2
wait_until 5 has Calendar
read -r ax ay <<<"$(cx Calendar)"
# from the label's middle (low in its cell) 60 dp down: into the next row, still above the dots and dock (bottom 200 dp)
[ $((ay + $(dp 60))) -lt $((H - $(dp 200))) ] && ok "the drift ends inside the grid" || ko "the drift would reach the dots or dock (y $((ay + $(dp 60))))"
hold_drag "$ax" "$ay" $((W - $(dp 16))) "$ay" 1 "$(dp 60)"
wait_until 6 hasnt Clock
hasnt Clock && has Calendar && ok "drifting at the edge: turned to page 2, Calendar dropped there" || ko "drifting at the edge: no page turn"

step "Gestures: drawer follows the finger"
for handed in false true; do
  seed classic; setp boolean left_handed "$handed"
  edge=$(dp 29) # Classic is never mirrored (c49f5d8): the alphabet stays on the right, for both hands
  A shell input swipe "$edge" $((H * 6 / 10)) "$edge" $((H / 5)) 1500 # slow enough that no fling fallback can open it
  wait_until 5 listed \
    && ok "left-handed=$handed: a slow pull from the left edge opens the drawer" \
    || ko "left-handed=$handed: the left edge blocked the slow drawer pull"
done
setp del left_handed
seed classic
swipe_up() { A shell input swipe $((W / 2)) "$1" $((W / 2)) "$2" "$3"; }
swipe_up $((H * 6 / 10)) $((H / 5)) 200
wait_until 5 listed && ok "swipe up opens the drawer" || ko "swipe up: no drawer"
wait_until 5 settled
shot 43-drawer-open
A shell input swipe $((W / 2)) $((H / 2)) $((W / 2)) $((H / 2 + $(dp 70))) 1500 # a slow short pull down: springs back
wait_until 5 settled
listed && ok "short slow drag down: the drawer springs back open" || ko "short drag down closed the drawer"
A shell input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H * 4 / 5)) 600
wait_until 5 has Calendar && ok "drag down closes the drawer: home again" || ko "drag down did not close the drawer"
wait_until 5 settled
swipe_up $((H * 6 / 10)) $((H * 6 / 10 - $(dp 120))) 400 # short, below the flick speed: springs back to home
wait_until 5 settled
has Calendar && ok "short slow drag up: the drawer springs back shut" || ko "short drag up opened the drawer"
valid && ! listed && ok "no drawer after the short drag" || ko "drawer shows after a short drag (or no screen read)"

step "Gestures: swipe down shows the shade"
seed classic
A shell input swipe $((W / 2)) $((H * 4 / 10)) $((W / 2)) $((H * 8 / 10)) 200
shade() { A shell dumpsys window | grep -q "mCurrentFocus.*NotificationShade"; }
wait_until 5 shade && ok "swipe down: the notification shade has the focus" || ko "swipe down: no shade"
shot 44-shade
A shell input keyevent KEYCODE_BACK
wait_until 5 focus && ok "Back closes the shade: home again" || ko "shade still up after Back"

step "Gestures: a sheet pulled a quarter closes, a short pull springs back"
seed classic
read -r cx0 cy0 <<<"$(cx Calendar)"
A shell input swipe "$cx0" "$cy0" "$cx0" "$cy0" 1300
expect "Rinomina"
b=$(bounds "Rinomina"); set -- $b; ry=$((($2 + $4) / 2))
sheet_h=$((H - ry + $(dp 160))) # about the sheet: from its title to the screen bottom
A shell input swipe $((W / 2)) "$ry" $((W / 2)) $((ry + sheet_h / 10)) 1500 # a tenth: short, slow
wait_until 5 settled
has "Rinomina" && ok "short slow pull: the sheet springs back" || ko "short pull closed the sheet"
A shell input swipe $((W / 2)) "$ry" $((W / 2)) $((ry + sheet_h * 4 / 10)) 1500 # beyond a quarter, slow
wait_until 5 hasnt "Rinomina" && ok "pulled past a quarter: the sheet closes" || ko "the sheet stayed after a long pull"

step "Gestures: a torch switched on from quick settings goes off at the first tap of the flashlight button (issue 065)"
# Ganimede starts fresh (seed stops it) with the torch already on: the first tap must read the real state, not assume "off".
torch_tile() { screen | grep -o '<node [^>]*content-desc="Torcia"[^>]*>' | head -1; }
torch_on() { torch_tile | grep -q 'checked="true"'; }
torch_off() { torch_tile | grep -q 'checked="false"'; }
qs_torch() { read -r tx ty <<<"$(cx "desc:Torcia")"; A shell input tap "$tx" "$ty"; }
seed classic; setp string fab_tap torch
A shell cmd statusbar expand-settings
if ! wait_until 5 eval '[ -n "$(torch_tile)" ]'; then
  SKIPS=$((SKIPS + 1)); echo "  SKIP: no Torcia tile in quick settings (this device has no flashlight)"
  A shell cmd statusbar collapse
else
  torch_on || qs_torch
  torch_tile_up() { [ -n "$(torch_tile)" ]; }
  torch_off_again() { A shell cmd statusbar expand-settings; wait_until 5 torch_tile_up; torch_on && qs_torch; A shell cmd statusbar collapse; }
  defer torch_off_again # never leave the torch on
  wait_until 5 torch_on && ok "quick settings turned the torch on" || ko "the Torcia tile did not turn on"
  A shell cmd statusbar collapse; wait_until 5 focus
  read -r fx fy <<<"$(cx "desc:Pulsante Ganimede")"; A shell input tap "$fx" "$fy"
  A shell cmd statusbar expand-settings
  wait_until 5 torch_off && ok "first tap of the button: the torch is off" || ko "first tap of the button left the torch on (assumed it was off)"
  A shell cmd statusbar collapse; wait_until 5 focus
fi
setp del fab_tap

step "Gestures: a side key past the last column settles the pager on a page (c2-4)"
seed classic boolean drawer false; wait_until 10 pages_ge2; wait_until 5 has Calendar
read -r cx0 _ <<<"$(cx Calendar)"
A shell input keyevent KEYCODE_DPAD_DOWN # a key leaves touch mode: the home hands the focus to a view
for _ in 1 2 3 4 5 6 7 8; do A shell input keyevent KEYCODE_DPAD_RIGHT; done
wait_until 5 settled
# a pager stuck between two pages shows Calendar shifted sideways; settled it is on page 1 (same x) or gone (page 2)
aligned() { hasnt Calendar || { read -r cx1 _ <<<"$(cx Calendar)"; [ -n "$cx1" ] && [ "$((cx1 - cx0))" -ge -3 ] && [ "$((cx1 - cx0))" -le 3 ]; }; }
wait_until 5 aligned && ok "DPAD_RIGHT past the last column: the pager rests on a page" || ko "DPAD_RIGHT left the pager between pages (Calendar at x ${cx1:-?}, was $cx0)"

step "Gestures: a side key towards an empty page turns the whole page, never half (c3 P2-2)"
seed classic int grid_pages 2 boolean drawer true; wait_until 5 has Calendar # page 2 holds nothing to focus
A shell input keyevent KEYCODE_DPAD_DOWN # a key leaves touch mode: the home hands the focus to a view
for _ in 1 2 3 4 5 6 7 8; do A shell input keyevent KEYCODE_DPAD_RIGHT; done
wait_until 5 settled
wait_until 5 hasnt Calendar && ok "DPAD_RIGHT towards an empty page 2: the pager is on page 2, not half way" || ko "the pager stopped between the pages (Calendar still on screen)"
A shell input keyevent KEYCODE_HOME; wait_until 5 has Calendar

step "Gestures: left-hand Classic mirrors nothing, the dock's first slot is on the left (c3 M13)"
seed classic boolean left_handed true string dock "com.google.android.calendar/com.android.calendar.AllInOneActivity"
wait_until 5 hasd "Calendar" # the dock icon's label is a content-desc
read -r dx _ <<<"$(cx "desc:Calendar")"
[ -n "$dx" ] && [ "$dx" -lt $((W / 2)) ] && ok "left-handed Classic: the first dock slot is on the left (x $dx)" || ko "left-handed Classic: the first dock slot at x ${dx:-none}, not on the left"
setp del left_handed del dock

step "Gestures: left-hand Classic keeps pages and block edges as on screen (finding c1-2)"
# Left-hand use mirrors the home, not the classic pages: page 1 stays leftmost, a cell's column and a block's left edge
# are the ones on screen. Mirrored, Home showed page 2, a key gave the focus to the hidden page and the left handle moved the right edge.
seed classic boolean drawer false boolean left_handed true; wait_until 10 pages_ge2; wait_until 5 has Calendar
read -r cx0 _ <<<"$(cx Calendar)"
[ -n "$cx0" ] && [ "$cx0" -lt $((W / 2)) ] && ok "left-handed: Calendar in its left column" || ko "left-handed: Calendar at x ${cx0:-none}, not in the left column"
# the page dots run left to right for both hands (c2-3): on page 1 the lit dot is the left one, as right-handed.
# Two dots: the half of their strip that is brighter than the other is the lit one's (the faded one is 40% as strong).
dots_lit() { local b; b=$(bounds "desc:Regola pagine") || return 1; set -- $b; local c=$((($1 + $3) / 2)) y=$((($2 + $4) / 2)) h=$(dp 15)
  A exec-out screencap -p | magick png:- -crop "$((h * 2))x$(dp 8)+$((c - h))+$((y - $(dp 4)))" +repage -crop 2x1@ +repage -format '%[fx:mean]\n' info: 2>/dev/null \
    | awk 'NR==1{l=$1} NR==2{r=$1} END{print (l>r) ? "left" : "right"}'; }
dots_lh=$(dots_lit)
seed classic boolean drawer false boolean left_handed false; wait_until 10 pages_ge2; wait_until 5 has Calendar
dots_rh=$(dots_lit)
[ -n "$dots_lh" ] && [ "$dots_lh" = "$dots_rh" ] && ok "left-handed: page 1's lit dot is on the $dots_lh as right-handed" || ko "left-handed: lit dot on the ${dots_lh:-?}, right-handed on the ${dots_rh:-?}"
seed classic boolean drawer false boolean left_handed true; wait_until 10 pages_ge2; wait_until 5 has Calendar
swipe_h $((W - $(dp 40))) $(dp 40)
side_app=$(texts | grep -E 'text="[A-Za-z]' | head -1 | sed 's/ text="//; s/"$//')
hasnt Calendar && [ -n "$side_app" ] && ok "left-handed: swipe left shows page 2 (“$side_app”)" || ko "left-handed: swipe left did not show page 2"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
wait_until 5 has Calendar && ok "left-handed: Home on page 2 goes to page 1" || ko "left-handed: Home on page 2 did not show page 1"
swipe_h $((W - $(dp 40))) $(dp 40); wait_until 5 hasnt Calendar
A shell input keyevent KEYCODE_DPAD_DOWN # a key leaves touch mode: the home hands the focus to a view
wait_until 5 settled
hasnt Calendar && has "$side_app" && ok "left-handed: a key keeps the focus on page 2" || ko "left-handed: a key scrolled away from page 2"
fixture_install && ok "fixture installed (its ScreenReader acts on the handles)" || ko "fixture not built or installed (./gradlew -Pe2e :fixture:assembleDebug)"
defer "A uninstall $FIX >/dev/null 2>&1"
seed classic boolean left_handed true
wait_until 5 clock_bounds >/dev/null
read -r x1 y1 x2 y2 <<<"$(clock_bounds)"
A shell input swipe $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) 1300
tap "Ridimensiona"; wait_until 5 hasd "Ridimensiona bordo sinistro"
read -r lx _ <<<"$(cx "desc:Ridimensiona bordo sinistro")"; read -r rx _ <<<"$(cx "desc:Ridimensiona bordo destro")"
[ -n "$lx" ] && [ -n "$rx" ] && [ "$lx" -lt "$rx" ] && ok "left-handed: the left handle is on the left" || ko "left-handed: left handle at x ${lx:-none}, right one at ${rx:-none}"
a11y_on
a11y label Restringi "Ridimensiona bordo destro"; rm -f "$SCR"
# clock_bounds reads the screen again on every retry (wait_until), never the one from before the action
narrowed() { read -r x1 _ x2 _ <<<"$(clock_bounds)"; [ -n "$x2" ] && [ "$x1" -lt $((W / 10)) ] && [ "$x2" -lt $((W - W / 10)) ]; }
wait_until 5 narrowed && ok "left-handed: Restringi on the right edge pulls in the right edge" || ko "left-handed: Restringi on the right edge: clock at x $x1-$x2"
a11y label Restringi "Ridimensiona bordo sinistro"; rm -f "$SCR"
in_left() { read -r nx1 _ nx2 _ <<<"$(clock_bounds)"; [ -n "$nx1" ] && [ "$nx1" -gt $((x1 + W / 10)) ]; }
wait_until 5 in_left && ok "left-handed: Restringi on the left edge pulls in the left edge" || ko "left-handed: Restringi on the left edge: clock at x ${nx1:-?}-${nx2:-?}"
a11y label Allarga "Ridimensiona bordo sinistro"; rm -f "$SCR"
grown_left() { read -r gx1 _ gx2 _ <<<"$(clock_bounds)"; [ -n "$gx1" ] && [ "$gx1" -lt "$nx1" ] && [ "$gx2" -eq "$nx2" ]; }
wait_until 5 grown_left && ok "left-handed: Allarga on the left edge widens the left edge on screen" \
  || ko "left-handed: Allarga on the left edge: clock at x ${gx1:-?}-${gx2:-?}, was $nx1-$nx2"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
a11y_off
setp del left_handed del drawer
