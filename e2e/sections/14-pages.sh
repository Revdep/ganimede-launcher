emu_only "pages: changes font scale and the notification listener" || return 0
# The settings pages (each opens from its row, one control per page changes its pref) and the Ganimede button
# (tap opens its pop-up, dragging it to the left edge saves fab_left=true).

# pv key: the stored value of any pref (lib.sh `pref` only reads ints); empty when unset
pv() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | tr -d '\r' | grep " name=\"$1\"" | sed -E 's/.*value="([^"]*)".*/\1/; s/.*">([^<]*)<.*/\1/'; }
# pv_is "what" key expected [action…]: runs the action, then waits for the pref to hold the value.
# With an action the pref must not hold it before: a read that beats the app's write would pass on the old value.
pv_is() {
  local w="$1" k="$2" e="$3"; shift 3; pv_eq() { [ "$(pv "$k")" = "$e" ]; }
  [ $# -gt 0 ] && { pv_eq && { ko "$w: $k is already $e before the action"; return 1; }; "$@"; }
  wait_until 5 pv_eq && ok "$w: $k = $e" || ko "$w: $k = '$(pv "$k")', expected $e"
}
pg_back() { # one level up, to the settings home
  A shell input keyevent KEYCODE_BACK
  wait_until 5 has 'Impostazioni"' && ok "back on the settings home" || ko "Back did not return to the settings home" # its title (the hint may be scrolled off)
}
# pg_open row content: opens the page from its row on the settings home and waits for something only the page has
pg_open() {
  stap "$1" && { wait_until 5 has "$2" && ok "“$1” opens its page (“$2”)" || ko "“$1” did not open (no “$2”)"; }
}
# pg_pick row: scroll to a row of a long page and tap it
pg_pick() { see "$1" && tap "$1"; }

HIDE=com.android.chrome/com.google.android.apps.chrome.Main
step "Settings pages: each opens from its row and one control changes its pref"
seed classic
setp set hidden "$HIDE"
pg_settings

pg_open "Personalizza" "Tutto lo schermo"
pill "Temi"
tap "Temi"; wait_until 5 has "TEMI" && ok "Customize opens its Themes pill" || ko "Themes pill did not open"
pv_is "look: theme preset" clock_style vertical pg_pick "Bosco nebbioso"; pv_is "look: theme preset" font sans-serif-condensed
pv_is "look: font choice" font monospace pg_pick "Monospaziato"
pg_back

# The folder look has its own pill next to Temi, and Temi no longer carries it
stap "Personalizza"; pill "Cartelle" # pg_back lands on the settings home; the strip reopens scrolled to the last pill
tap "Cartelle" && see "Colore dello sfondo" && see "Opacità dello sfondo (%)" && see "Colonne" \
  && ok "Customize has a Cartelle pill with the folder controls" || ko "no Cartelle pill with the folder controls"
pg_back
stap "Personalizza"; pill "Temi" # pg_back lands on the settings home; the strip reopens scrolled to the last pill
tap "Temi"; wait_until 5 has "TEMI"
folders_in_themes() { # down to the page's end: two scrolls in a row that change nothing (one can be swallowed)
  local n=0 prev= cur same=0
  while [ $n -lt 25 ]; do
    has "Colonne" && return 0; has "Colore dello sfondo" && return 0
    cur=$(screen); if [ -n "$cur" ] && [ "$cur" = "$prev" ]; then same=$((same + 1)); [ $same -ge 2 ] && return 1; else same=0; fi; prev=$cur
    _scroll; n=$((n + 1))
  done; return 1; }
folders_in_themes && ko "Temi still shows the folder controls (Colore dello sfondo / Colonne)" || ok "Temi no longer shows the folder controls"
pg_back
for _ in 1 2 3 4; do A shell input swipe 540 700 540 2000 200; done # the settings home keeps the scroll of the Temi search above

# One place configures the button; Personalizza only links to it
has "Dock" || pg_open "Personalizza" "Dock" # Back from a pill may stay on Personalizza
read -r _ pill_y <<<"$(cx "Dock")"; A shell input swipe 150 "$pill_y" 900 "$pill_y" 500 # pills back to the first one
tap "Tutto lo schermo"
see "Uso con la mano sinistra (specchiato)" # the "Whole screen" card's rows are on screen
hasnt "Mostra il pulsante Ganimede" && hasnt "Pulsante rapido" && ok "Customize has no second switch for the button" || ko "Customize still has its own button switch"
tap "Pulsante Ganimede"; wait_until 5 has "Mostra il pulsante Ganimede" && ok "Customize's “Pulsante Ganimede” link opens the button page" || ko "the Customize link did not open the button page"
at_home() { A shell input keyevent KEYCODE_BACK; has 'Impostazioni"'; }
wait_until 10 at_home || ko "Back did not return to the settings home" # one Back from the linked page, maybe two

pg_open "Pulsante Ganimede" "Mostra il pulsante Ganimede" # the Misty forest preset above turned the button off
pv_is "button: toggle" fab true tap "Mostra il pulsante Ganimede"
see "Doppio tocco"; tap "Doppio tocco"; pv_is "button: double-tap gesture" fab_double popup stap "Apri il pop-up del pulsante" # the gestures sit below the icon choosers
pg_back

pg_open "Meteo" "Mostra °F invece di °C"
pv_is "weather: unit" fahrenheit true tap "Mostra °F invece di °C"
pg_back

pg_open "Calendario e produttività" "Mostra il prossimo evento nella schermata Home"
pv_is "calendar: next event" event_preview false tap "Mostra il prossimo evento nella schermata Home"
pg_back

pg_open "Ricerca e scorciatoie" "Scorri in su nella schermata Home per cercare"
pv_is "search: engine" engine "Brave Search" pg_pick "Brave Search"
pg_pick "Le mie scorciatoie"
wait_until 5 has "Aggiungi una scorciatoia" && ok "search → Le mie scorciatoie opens the shortcuts page" || ko "shortcuts page did not open"
fs=$(A shell settings get system font_scale | tr -d '\r'); [ "$fs" = null ] && fs=1.0
for s in 1.15 "$fs"; do # a configuration change makes Android build the settings again: they reopen where they were
  setting system font_scale "$s" || continue; sleep 2; wait_until 5 settled
  has "Aggiungi una scorciatoia" && ok "font scale $s: still on the shortcuts page" || ko "font scale $s: the shortcuts page was lost"
done
A shell input keyevent KEYCODE_BACK
wait_until 5 has "Scorri in su nella schermata Home per cercare" && ok "Back from the shortcuts returns to the search page" || ko "Back from the shortcuts did not return to search"
pg_back

pg_open "Gesti" "Vibrazione al tocco"
pv_is "gestures: vibration" haptics false tap "Vibrazione al tocco"
pg_back

A shell cmd notification allow_listener $PK/$PK.NotifService >/dev/null 2>&1 # access granted: the toggles turn on with no explanation
pg_open "Notifiche e benessere" "Riepilogo notifiche"
pv_is "wellbeing: summary" summary true tap "Riepilogo notifiche"
pv_is "wellbeing: preview under favourites" notif_preview true pg_pick "Anteprima delle notifiche sotto i preferiti"
pg_back

pg_open "App nascoste" "Mostra di nuovo"
has "Chrome" && ok "hidden: the app hidden by prefs is listed (Chrome)" || ko "hidden: Chrome not listed"
tap "Mostra di nuovo"
wait_until 5 has "Nessuna app nascosta" && ok "hidden: Unhide empties the list" || ko "hidden: list not empty after Unhide"
pg_back

pg_open "Privacy e permessi" "App Home predefinita"
for r in "Accesso alle notifiche" "Calendario" "Contatti" "Info app"; do see "$r"; done # the page is longer than the screen
pg_back

pg_open "Backup e ripristino" "Crea un backup"
# The screen turns while the restore confirmation is open: the dialog is still there afterwards (issue 063).
printf '%s' '{}' > "$OUT/rot.json"; A push "$OUT/rot.json" /sdcard/Download/ganimede-rot.json >/dev/null
tap "Ripristina un backup"; pick_file ganimede-rot.json
wait_until 8 has "Ripristinare il backup?" || ko "no restore confirmation to rotate"
A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 1
landscape() { screen | grep -q '<hierarchy[^>]*rotation="[13]"'; } # the dump's root says the display is turned
wait_until 8 landscape || ko "the screen did not turn"
wait_until 5 has "Ripristinare il backup?" && ok "the restore confirmation survives a rotation" || ko "the restore confirmation vanished on rotation"
A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1
tap "Annulla"; wait_until 5 hasnt "Ripristinare il backup?"
A shell rm -f /sdcard/Download/ganimede-rot.json
pg_back

pg_open "Lingua" "Tutto il launcher cambia lingua all'istante."
pv_is "language: English" lang en tap "English"
wait_until 5 has "The whole launcher switches language right away." && ok "the page switched to English at once" || ko "page still Italian after choosing English"
pv_is "language: back to Italian" lang it tap "Italiano"
wait_until 5 has "Tutto il launcher cambia lingua all'istante." && ok "and back to Italian" || ko "page not Italian again"
pg_back

pg_open "Informazioni" "Rifai la configurazione guidata"
tap "Rifai la configurazione guidata"
wait_until 5 has "Benvenuto" && ! has "Tocca una parte dell'anteprima" && ok "“Rifai la configurazione guidata” opens the wizard" || ko "wizard did not open from About"

step "Ganimede button: tap opens its pop-up, drag to the left edge saves fab_left"
seed classic
# a pop-up saved by an older build kept its default name: it must read as today's single name
setp string folder:button '{"name":"Pulsante rapido","apps":[]}'
btn() { cx "✳"; }
read -r bx by <<<"$(btn)"
if [ -z "$bx" ]; then ko "no Ganimede button on the classic home"; else
  A shell input tap "$bx" "$by"
  wait_until 5 has "Pulsante Ganimede" && ok "tap opens the button's pop-up" || ko "tap did not open the pop-up"
  hasnt "Pulsante rapido" && ok "an old pop-up shows no old name" || ko "the pop-up still shows its old name “Pulsante rapido”"
  A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Pulsante Ganimede"
  [ -z "$(pv fab_left)" ] && ok "starts anchored on the right (fab_left unset)" || ko "fab_left already $(pv fab_left)"
  A shell input swipe "$bx" "$by" 60 "$by" 800
  pv_is "drag to the left edge" fab_left true
  read -r nx ny <<<"$(btn)"
  [ -n "$nx" ] && [ "$nx" -lt $((W / 2)) ] && ok "the button now sits on the left (x=$nx)" || ko "button not on the left (x=${nx:-none})"
fi

# The settings home on first run (notification access missing) has no notification-access row
step "Settings home: no notification-access row on first run"
A shell cmd notification disallow_listener $PK/$PK.NotifService >/dev/null 2>&1
seed classic
pg_settings
hasnt "Accesso alle notifiche" && ok "no “Accesso alle notifiche” row on the settings home" || ko "the settings home offers “Accesso alle notifiche”"

# issue 064: the media player's buttons are announced by TalkBack (fixture app plays a session)
step "Media player: its buttons have a content description"
"$HERE/../gradlew" -p "$HERE/.." -q -Pe2e :fixture:assembleDebug >/dev/null 2>&1 \
  && A install -r "$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" >/dev/null && ok "fixture app built and installed" \
  || ko "fixture app not built or installed (./gradlew -Pe2e :fixture:assembleDebug)"
seed list
A shell cmd notification allow_listener $PK/$PK.NotifService >/dev/null 2>&1
A shell am broadcast -n org.ganimede.fixture/.Media >/dev/null
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
wait_until 10 hasd "Pausa" && ok "the playing session shows a Pause button with a description" || ko "no media button described “Pausa”"
hasd "Precedente" && hasd "Avanti" && ok "Previous and Next are described too" || ko "Precedente/Avanti missing"
A shell cmd notification disallow_listener $PK/$PK.NotifService >/dev/null 2>&1
A shell am force-stop org.ganimede.fixture >/dev/null
