step "Animations: end states (ANIM=${ANIM:-0}); with ANIM=1 also the enter and landing animations"
appfocus() { A shell dumpsys window | grep -q "mCurrentFocus.*calendar"; }
wall_is() { [ "$(wall)" = "$1" ]; }
wall_not() { [ "$(wall)" != "$1" ]; }

step "Home role starts one MainActivity"
seed classic
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
n=$(A shell dumpsys activity activities | grep -o "Hist *#[0-9]*: ActivityRecord{[0-9a-f]* u[0-9]* $PK/.MainActivity" | sort -u | wc -l) # live task entries only: the dump also lists past orientation sources
[ "$n" -eq 1 ] && ok "one home activity after Home" || ko "$n home activities after Home, expected one"
wait_until 5 settled
w0=$(wall)

step "Pop-up: open shows its text, Back removes text and dim"
A shell input swipe 540 1300 540 1300 1500   # long-press on an empty spot: the home menu
expect "Scegli le app della Home"
wait_until 3 wall_not "$w0" && ok "the pop-up dims the wallpaper" || ko "the wallpaper isn't dimmed behind the pop-up"
A shell input keyevent KEYCODE_BACK
wait_until 5 hasnt "Scegli le app della Home" && ok "Back: the pop-up text is gone" || ko "the pop-up text is still there after Back"
wait_until 3 wall_is "$w0" && ok "Back: the dim is removed (wallpaper pixels as before)" || ko "the wallpaper stays dimmed after Back"

step "Pop-up: open and immediately Back"
A shell input swipe 540 1300 540 1300 1500
wait_until 5 has "Scegli le app della Home"   # long-press fired: the pop-up is opening
A shell input keyevent KEYCODE_BACK
wait_until 5 hasnt "Scegli le app della Home" && ok "open + immediate Back: closed" || ko "pop-up not closed after an immediate Back"
wait_until 3 wall_is "$w0" && ok "open + immediate Back: no dim left" || ko "dim left after open + immediate Back"

step "Drawer: open and close end states"
A shell input swipe 540 1900 540 600 150
expect "Cerca app"
A shell input keyevent KEYCODE_BACK
wait_until 5 hasnt "Cerca app" && ok "drawer closed by Back" || ko "drawer still open after Back"
wait_until 3 wall_is "$w0" && ok "drawer closed: home as before" || ko "home differs after the drawer closed"

step "Drawer: released mid-way, still, glides to the nearer end"
# Drags up by $1 px in 1.5 s (slow: no fling) and lets go there: under 30% of the screen it falls back, past it it opens.
release_at() { A shell input swipe 540 1900 540 $((1900 - $1)) 1500; }
release_at 300
wait_until 3 settled; wait_until 3 wall_is "$w0" && ok "short release: back home, no dim" || ko "short release didn't fall back home"
hasnt "Cerca app" && ok "short release: drawer shut" || ko "short release left the drawer open"
release_at 1200
wait_until 5 has "Cerca app" && ok "long release: the drawer opens" || ko "long release didn't open the drawer"
A shell input keyevent KEYCODE_BACK
wait_until 5 hasnt "Cerca app"; wait_until 3 wall_is "$w0" || ko "home differs after closing the drawer"

step "Alphabet wave: end states"
seed list
ax=$((W - $(dp 29))); ay=1500
crop="$(dp 160)x900+$((W - $(dp 160)))+$((ay - 450))"
wait_until 3 settled
A exec-out screencap -p > "$OUT/anim-wave-rest.png"
A shell input motionevent DOWN $ax $ay; sleep 0.3; A shell input motionevent MOVE $ax $((ay + 40)); sleep 0.6   # held: the wave is full
A exec-out screencap -p > "$OUT/anim-wave-held.png"
A shell input motionevent UP $ax $((ay + 40))
d=$(FUZZ=10% fdiff "$OUT/anim-wave-rest.png" "$OUT/anim-wave-held.png" "$crop")
[ "${d:-0}" -gt 2000 ] && ok "held letters: the wave is there ($d px changed)" || ko "no wave while holding ($d px changed)"
A shell input keyevent KEYCODE_HOME
wait_until 5 focus; wait_until 3 settled

if [ "${ANIM:-0}" = 1 ]; then
  step "ANIM=1: Home while home plays the enter animation, then the icons rest"
  seed classic
  wait_until 5 settled
  c="1000x300+40+1000"   # the three favourites: icons and labels
  A exec-out screencap -p > "$OUT/anim-enter-rest.png"
  A shell "input keyevent KEYCODE_HOME; screencap -p /data/local/tmp/enter.png"   # on the device: a pull from here would come after the 250 ms
  A pull /data/local/tmp/enter.png "$OUT/anim-enter-start.png" >/dev/null
  d=$(FUZZ=5% fdiff "$OUT/anim-enter-start.png" "$OUT/anim-enter-rest.png" "$c")
  [ "${d:-0}" -gt 300 ] && ok "icons are mid-enter right after Home ($d px differ from rest)" || ko "no enter animation after Home ($d px differ)"
  # the enter animation is 250 ms + stagger: within 1 s the icons must be at rest again
  differs() { # differs <reference png> <new png>: sets d, succeeds when the favourites are as in the reference
    A exec-out screencap -p > "$2"
    d=$(FUZZ=5% fdiff "$1" "$2" "$c"); [ "${d:-999999}" -lt 300 ]
  }
  wait_until 1 differs "$OUT/anim-enter-rest.png" "$OUT/anim-enter-1s.png" && ok "icons at rest within 1 s after home ($d px differ from the settled screen)" || ko "icons still moving/faded 1 s after home ($d px differ)"

  step "ANIM=1: after launching an app and Home, its icon is back within 2 s"
  seed classic
  wait_until 3 settled
  A exec-out screencap -p > "$OUT/anim-land-before.png"
  A shell am start -W -n com.google.android.calendar/com.android.calendar.AllInOneActivity >/dev/null
  wait_until 10 appfocus
  A shell input keyevent KEYCODE_HOME
  wait_until 10 focus
  wait_until 2 differs "$OUT/anim-land-before.png" "$OUT/anim-land-after.png" && ok "icons back within 2 s after Home ($d px differ from before)" || ko "icons not back 2 s after Home ($d px differ)"
fi
