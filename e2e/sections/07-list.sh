step "List mode: favourites, positions, reorder"
seed list
expect "Calendar"
read -r lx ly <<<"$(lx Calendar)"; read -r dax day <<<"$(cx "desc:Orologio, tocca per le sveglie")"
setp int apps_offset 80
read -r lx2 ly2 <<<"$(lx Calendar)"; read -r dax2 day2 <<<"$(cx "desc:Orologio, tocca per le sveglie")"
moved "apps" x "$lx" "$lx2" "$(dp 40)"; moved "clock (must stay)" y "$day" "$day2" 0
setp int list_apps_y -8
read -r lx3 ly3 <<<"$(cx Calendar)"; read -r dax3 day3 <<<"$(cx "desc:Orologio, tocca per le sveglie")"
moved "apps" y "$ly2" "$ly3" "-$(dp 8)"; moved "clock (must stay)" y "$day2" "$day3" 0
setp string last_moved list_apps_y; setp int list_apps_y -600                                  # far too much: stops right under the clock block
rt=$(bounds "desc:Calendar" | awk '{print $2}')
wb=$(dump | grep -o 'text="[^"]*meteo"[^>]*bounds="[^"]*"' | head -1 | grep -o '\]\[[0-9]*,[0-9]*\]' | grep -o '[0-9]*\]$' | tr -d ']')
[ -n "$rt" ] && [ -n "$wb" ] && [ "$rt" -ge $((wb - $(dp 2))) ] && [ "$rt" -le $((wb + $(dp 12))) ] \
  && ok "apps stop under the date line, no overlap ($wb / $rt)" || ko "apps overlap or gap wrong (line bottom $wb, apps top $rt)"
saved=$(A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -o 'list_apps_y" value="-*[0-9]*' | grep -o '\-*[0-9]*$')
[ -n "$saved" ] && [ "$saved" -gt -600 ] && ok "the stop is saved back ($saved dp)" || ko "offset not limited in settings ($saved)"
setp int list_apps_y -8
setp int list_clock_y -600                                 # at the top of its range: stays 4 dp under the status bar
ct=$(bounds "desc:Orologio, tocca per le sveglie" | awk '{print $2}')
[ -n "$ct" ] && [ "$ct" -ge "$(dp 24)" ] && [ "$ct" -le "$(dp 64)" ] && ok "clock stays on screen at the top ($ct px)" || ko "clock top at ${ct:-none}"
setp int list_clock_y 0
setp int list_clock_y -60
read -r lx4 ly4 <<<"$(cx Calendar)"; read -r dax4 day4 <<<"$(cx "desc:Orologio, tocca per le sveglie")"
moved "clock" y "$day3" "$day4" "-$(dp 60)"; moved "apps (must stay)" y "$ly3" "$ly4" 0
setp int home_bottom 112   # +48 dp: the full-height favourites panel leaves less room above
read -r lx5 ly5 <<<"$(cx Calendar)"
moved "everything (bottom edge)" y "$ly4" "$ly5" "-$(dp 48)"
for k in apps_offset list_apps_y list_clock_y home_bottom; do setp del $k; done
shot 27-list

step "List mode: drag a favourite to reorder"
read -r a1x a1y <<<"$(cx Calendar)"
favs=$(dump | grep -o 'text="Calendar"\|text="Clock"\|text="Contacts"\|text="Cartella"' | tr '\n' ' ')
echo "  favourites now: $favs"
last=$(dump | grep -o 'text="[^"]*"[^>]*bounds' | grep -E 'text="(Calendar|Clock|Contacts)"' | tail -1 | cut -d'"' -f2)
read -r b1x b1y <<<"$(cx "$last")"
if [ -n "$b1y" ] && [ "$last" != "Calendar" ]; then
  A shell input draganddrop "$b1x" "$b1y" "$a1x" "$a1y" 1500
  above() { read -r _ y1 <<<"$(cx Calendar)"; read -r _ y2 <<<"$(cx "$last")"; [ -n "$y1" ] && [ -n "$y2" ] && [ "$y2" -lt "$y1" ]; }
  wait_until 5 above
  read -r _ n1y <<<"$(cx Calendar)"; read -r _ n2y <<<"$(cx "$last")"
  [ "$n2y" -lt "$n1y" ] && ok "$last dragged above Calendar" || ko "reorder failed ($last y $n2y, Calendar y $n1y)"
else ko "not enough favourites to reorder"; fi
shot 28-reordered

SETTINGS=com.android.settings/com.android.settings.Settings
make_settings_often_used_favourite() {
  A shell input swipe 540 1900 540 1900 1500
  expect "Scegli i preferiti" && tap "Scegli i preferiti"
  tapn "Settings" 30; tap "Salva"
  wait_until 5 has "Settings" || ko "Settings did not become a favourite"
  for _ in 1 2 3; do
    tap "Settings"
    wait_until 5 eval '! focus' || ko "Settings did not open"
    A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  done
  uses_three() { uses=$(pref "use:$SETTINGS"); [ "${uses:-0}" -ge 3 ]; }
  wait_until 5 uses_three && ok "Settings was opened at least three times" || ko "Settings launch count is ${uses:-missing}"
}

step "List suggestions: removing a favourite from its menu keeps it off the home"
seed list; setp boolean suggest true
make_settings_often_used_favourite
read -r sx sy <<<"$(cx Settings)"
A shell input draganddrop "$sx" "$sy" "$sx" "$sy" 1500
expect "Rimuovi dai preferiti" && tap "Rimuovi dai preferiti"
wait_until 5 hasnt "Settings" && ok "removed favourite is neither a favourite nor a suggestion" \
  || ko "removed favourite came back as a suggestion"

step "List suggestions: removing a favourite from Scegli i preferiti keeps it off the home"
seed list; setp boolean suggest true
make_settings_often_used_favourite
A shell input swipe 540 1900 540 1900 1500
expect "Scegli i preferiti" && tap "Scegli i preferiti"
tapn "Settings" 30; tap "Salva"
wait_until 5 hasnt "Settings" && ok "unchecked favourite is neither a favourite nor a suggestion" \
  || ko "unchecked favourite came back as a suggestion"
seed list # restore the preferences changed by these scenarios

step "List mode: hidden alphabet keeps its touch column clear"
seed list
alpha_offset=12
setp boolean alpha_home false
setp int alpha_offset "$alpha_offset"
for handed in false true; do
  setp boolean left_handed "$handed"
  if read -r x1 _ x2 _ <<<"$(bounds "desc:Calendar")" && [ -n "$x1" ]; then
    edge=$(dp $((58 + alpha_offset)))
    if [ "$handed" = true ]; then
      [ "$x1" -ge "$edge" ] && ok "hidden alphabet, left-handed: favourites stay right of its touch column" \
        || ko "hidden alphabet, left-handed: favourite starts at $x1 px, inside the ${edge}px touch column"
    else
      [ "$x2" -le $((W - edge)) ] && ok "hidden alphabet, right-handed: favourites stay left of its touch column" \
        || ko "hidden alphabet, right-handed: favourite ends at $x2 px, past the $((W - edge))px touch boundary"
    fi
  else ko "hidden alphabet, left-handed=$handed: Calendar favourite missing"; fi
done

step "Font: the home text follows the chosen font and its weight"
frow() { local x1 y1 x2 y2; read -r x1 y1 x2 y2 <<<"$(bounds Calendar)"; fcrop "$((x2 - x1))x$((y2 - y1))+$x1+$y1" "$1"; } # the favourite's row, as drawn
fcrop() { A exec-out screencap -p | magick png:- -crop "$1" +repage "$2"; } # fcrop geometry out.png
setp boolean left_handed false; setp boolean alpha_home true; setp del alpha_offset; setp string font sans-serif
frow "$OUT/font-row-def.png"
acrop="$(dp 58)x1100+$((W - $(dp 58)))+1000"; fcrop "$acrop" "$OUT/font-alpha-def.png"
setp string font sans-serif-thin
frow "$OUT/font-row-thin.png"; fcrop "$acrop" "$OUT/font-alpha-thin.png"
setp string font monospace
frow "$OUT/font-row-mono.png"; fcrop "$acrop" "$OUT/font-alpha-mono.png"
# the text cursor and the clock's seconds blink: a few dozen pixels is noise, a font is hundreds
r=$(fdiff "$OUT/font-row-def.png" "$OUT/font-row-mono.png"); [ "${r:-0}" -gt 300 ] && ok "monospace changes the favourite ($r px differ)" || ko "monospace left the favourite alone (${r:-0} px differ)"
r=$(fdiff "$OUT/font-row-def.png" "$OUT/font-row-thin.png"); [ "${r:-0}" -gt 300 ] && ok "thin is not the default bold ($r px differ)" || ko "thin favourite looks like the default bold (${r:-0} px differ)"
a=$(fdiff "$OUT/font-alpha-def.png" "$OUT/font-alpha-mono.png")
[ "${a:-0}" -gt 300 ] && ok "the alphabet follows the font ($a px differ)" || ko "the alphabet ignores the font"
A shell input swipe 540 1900 540 600 150; expect "Cerca app"; typ "Zz%sProva%sZz%sProva"
read -r sx1 sy1 sx2 sy2 <<<"$(bounds "Zz Prova Zz Prova")"; sgeo="$(dp 170)x$((sy2 - sy1))+$((sx1 + $(dp 18)))+$sy1" # the typed text only: the rows under it change with the font too
fcrop "$sgeo" "$OUT/font-search-mono.png"
setp string font sans-serif
A shell input swipe 540 1900 540 600 150; expect "Cerca app"; typ "Zz%sProva%sZz%sProva"
fcrop "$sgeo" "$OUT/font-search-def.png"
s=$(fdiff "$OUT/font-search-def.png" "$OUT/font-search-mono.png")
[ "${s:-0}" -gt 300 ] && ok "the search text follows the font ($s px differ)" || ko "the search text ignores the font"
setp del font

step "Breezy weather broadcast: only its own action is read"
seed list
BW='nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER'
wx_place() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -c 'Zz Prova'; }
A shell am force-stop $PK
A shell "am broadcast -f 32 -n $PK/.BreezyReceiver -a nodomain.example.OTHER --es WeatherJson '{\"location\":\"Zz Prova\",\"currentTemp\":293}'" >/dev/null
sleep 2; [ "$(wx_place)" = 0 ] && ok "another action with the same extras is ignored" || ko "a foreign action changed the weather"
A shell "am broadcast -f 32 -a $BW -p $PK --es WeatherJson '{\"location\":\"Zz Prova\",\"currentTemp\":293}'" >/dev/null
wait_until 10 eval '[ "$(wx_place)" != 0 ]' && ok "the Breezy action stores the weather" || ko "the Breezy broadcast was not stored"
