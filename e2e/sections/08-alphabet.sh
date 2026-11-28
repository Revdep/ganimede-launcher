step "List mode: the alphabet wave and the Back gesture over it"
seed list
ax=$((W - $(dp 29))); ay=1500   # middle of the 58 dp letter column, halfway down the letters
A exec-out screencap -p > "$OUT/wave-rest.png"
A shell input motionevent DOWN $ax $ay; sleep 0.3; A shell input motionevent MOVE $ax $((ay + 40)); sleep 0.4   # finger held on the letters
A exec-out screencap -p > "$OUT/wave-held.png"
A shell input motionevent UP $ax $((ay + 40))
crop="$(dp 160)x900+$((W - $(dp 160)))+$((ay - 450))"
diff=$(compare -metric AE -fuzz 10% "$OUT/wave-rest.png[$crop]" "$OUT/wave-held.png[$crop]" null: 2>&1 | grep -oE '^[0-9]+' | head -1)
[ "${diff:-0}" -gt 2000 ] && ok "holding the letters bends them into the wave ($diff px changed)" || ko "the alphabet wave didn't move (${diff:-0} px changed)"
wait_until 3 settled
has "Impostazioni Ganimede" && { ko "a hold on the letters opened the home menu"; A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Impostazioni Ganimede"; } || ok "a hold on the letters stays with the letters"
A shell input keyevent KEYCODE_HOME   # touching the letters opened the app list: back to the home
wait_until 5 focus; wait_until 3 settled
A shell input swipe 540 1900 540 1900 1500
wait_until 5 has "Impostazioni Ganimede" && ok "a long-press under the favourites opens the home menu" || ko "no home menu to close"
A shell input swipe $((W - 2)) $ay 640 $ay 250   # the system Back swipe from the alphabet's edge
wait_until 5 hasnt "Impostazioni Ganimede" && ok "Back from the alphabet's edge closes the menu" || { ko "Back from the alphabet's edge didn't work"; A shell input keyevent KEYCODE_BACK; }

alphabet_at_start() { has "Cerca app" && has Calendar && ! kbd; }
# The G header's bounds mark the list's position: after a scroll back, uiautomator can miss the rows above it (Calendar).
# (Its top may differ by a few px: classic's drawer settles a little after the first dump.)
gy() { _tb G && TB=${TB#*,} && echo "${TB%%]*}"; }
at_g0() { local y; y=$(gy) && [ $((y - G0 < 0 ? G0 - y : y - G0)) -lt 60 ]; }
alphabet_moved() { has "Cerca app" && ! at_g0; }
open_browsing_at_start() {
  local mode="$1" handed="$2" x n=0
  wait_until 10 settled || return 1
  if [ "$mode" = list ]; then
    # Touch the drawn alphabet: unlike swipe-to-search this is browsing, so it opens without the keyboard.
    [ "$handed" = true ] && x=$(dp 29) || x=$((W - $(dp 29)))
    A shell input tap "$x" $((H * 6 / 10))
  else
    # Classic's drawer follows a slow pull and opens in browsing mode too.
    A shell input swipe $((W / 2)) $((H * 6 / 10)) $((W / 2)) $((H / 5)) 1000
  fi
  wait_until 10 has "Cerca app" || return 1
  while ! has Calendar && [ $n -lt 4 ]; do
    A shell input swipe $((W / 2)) $((H * 3 / 10)) $((W / 2)) $((H * 8 / 10)) 500
    n=$((n + 1))
  done
  wait_until 10 alphabet_at_start && wait_until 10 settled # G0 is read from here: a list still moving gives a wrong start
}

seed list
setp int apps_offset 4
edge=$(dp 20)
A shell input swipe "$edge" $((H / 2)) "$edge" $((H * 8 / 10)) 500
wait_until 10 has "Cerca app" && hasnt Calendar \
  && ok "old apps_offset 4: a swipe down inside the 32 dp strip scrolls the alphabet" \
  || ko "old apps_offset 4: a swipe down inside the 32 dp strip didn't scroll the alphabet"

for mode in list classic; do
  for handed in false true; do
    seed "$mode"; setp boolean left_handed "$handed"
    if ! open_browsing_at_start "$mode" "$handed"; then
      ko "$mode, left-handed=$handed: browsing didn't settle at Calendar without the keyboard"
      continue
    fi
    G0=$(gy) || { ko "$mode, left-handed=$handed: no G header at the start"; continue; }
    # Over the icons the drag belongs to the list: up can only be the list, never the alphabet (already at A).
    if [ "$handed" = true ]; then icon=$((W - $(dp 60))); else icon=$(dp 60); fi
    A shell input swipe "$icon" $((H * 8 / 10)) "$icon" $((H * 3 / 10)) 500
    wait_until 10 hasnt Calendar \
      && ok "$mode, left-handed=$handed: a vertical drag over an icon scrolls the list" \
      || ko "$mode, left-handed=$handed: a vertical drag over an icon didn't scroll the list"
    # A pull down from the top would close classic's drawer: judge each fling only once it has settled.
    # Swiping down on a list already at the top closes it: each retry covers only the gap left to G0 (all of it while G is off screen), and stops once the list is closed.
    for _ in 1 2 3; do
      at_g0 && break
      has "Cerca app" || break
      gap=$((H * 5 / 10)); y=$(gy) && [ $((G0 - y)) -gt 0 ] && gap=$((G0 - y))
      A shell input swipe "$icon" $((H * 3 / 10)) "$icon" $((H * 3 / 10 + gap)) 500; wait_until 10 settled
    done
    has "Cerca app" || ko "$mode, left-handed=$handed: the swipe back closed the app list"
    at_g0 || ko "$mode, left-handed=$handed: the list didn't come back to the start (G at $(gy), start $G0)" # a fling may stop short of the top
    # The opposite edge owns a fixed 32 dp strip, independent of where the rows begin.
    if [ "$handed" = true ]; then edge=$((W - $(dp 20))); side=right
    else edge=$(dp 20); side=left; fi
    A shell input swipe "$edge" $((H / 2)) "$edge" $((H * 8 / 10)) 500
    wait_until 10 alphabet_moved \
      && ok "$mode, left-handed=$handed: the opposite $side edge scrolls the alphabet" \
      || ko "$mode, left-handed=$handed: the opposite $side edge didn't scroll the alphabet"
  done
done
setp del left_handed
