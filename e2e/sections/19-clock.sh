emu_only "clock: disables apps and sets accessibility" || return 0
# Every clock style has an on-screen view and exposes its complete value in List and a user-resized Classic block.
# ClockFitTest checks the draw bounds themselves; screenshots cannot separate translucent ink from a moving wallpaper.
clock_bounds() {
  local b
  b=$(bounds "desc:Orologio, tocca per le sveglie")
  [ -n "$b" ] || return 1
  set -- $b
  [ "$1" -ge 0 ] && [ "$2" -ge 0 ] && [ "$3" -le "$W" ] && [ "$4" -le "$H" ] \
    && [ "$3" -gt "$1" ] && [ "$4" -gt "$2" ] || return 1
  echo "$b"
}

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
  seed "$mode"
  setp string text_color dark
  if [ "$mode" = list ]; then
    setp int list_clock_size 160
    setp int clock_offset 240 # the furthest user-selectable position leaves the least width
  elif resize_classic_clock; then
    ok "classic clock uses the smallest user-resized block"
  else
    ko "classic clock could not be resized"
  fi
  setp boolean clock24 true

  for hand in ltr rtl; do
    [ "$hand" = rtl ] && setp boolean left_handed true || setp boolean left_handed false
    if [ "$mode" = classic ] && [ "$hand" = ltr ]; then
      setp int list_clock_size 40; setp string clock_style basic
      read -r small_x1 small_y1 small_x2 small_y2 <<<"$(clock_bounds)"
      setp int list_clock_size 160; setp string clock_style basic
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
      setp string clock_style "$style"
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

step "Il tocco sull'orologio apre le sveglie protette in Lista e Classica"
FIX=org.ganimede.fixture
"$HERE/../gradlew" -p "$HERE/.." -q -Pe2e :fixture:assembleDebug >/dev/null 2>&1 \
  && A install -r "$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" >/dev/null && ok "fixture sveglie compilata e installata" \
  || ko "fixture sveglie non compilata o installata (./gradlew -Pe2e :fixture:assembleDebug)"
clock_fixture_cleanup() {
  A shell pm enable --user 0 com.google.android.deskclock >/dev/null 2>&1
  A uninstall "$FIX" >/dev/null 2>&1
}
defer clock_fixture_cleanup
A shell pm disable-user --user 0 com.google.android.deskclock >/dev/null \
  && ok "Orologio Google disabilitato durante la prova" || ko "Orologio Google non disabilitato"

launcher_has_alarm_permission() {
  A shell dumpsys package "$PK" | grep -q 'com.android.alarm.permission.SET_ALARM: granted=true'
}
fixture_alarms_front() {
  A shell dumpsys window | grep -q 'mCurrentFocus.*org.ganimede.fixture.*Alarms'
}
cant_open_visible() {
  A shell dumpsys notification --noredact | grep -Fq 'Nessuna app può aprirlo'
}
clock_result() {
  if fixture_alarms_front; then CLOCK_RESULT=opened; return 0; fi
  if cant_open_visible; then CLOCK_RESULT=refused; return 0; fi
  return 1
}

launcher_has_alarm_permission \
  && ok "Ganimede ha il permesso SET_ALARM" || ko "Ganimede non ha il permesso SET_ALARM concesso"
for mode in list classic; do
  seed "$mode"
  CLOCK_RESULT=
  read -r clock_x clock_y <<<"$(cx "desc:Orologio, tocca per le sveglie")"
  A shell input tap "$clock_x" "$clock_y"
  wait_until 5 clock_result
  [ "$CLOCK_RESULT" = opened ] \
    && ok "$mode: la schermata sveglie della fixture è in primo piano" \
    || ko "$mode: la schermata sveglie della fixture non è in primo piano"
  [ "$CLOCK_RESULT" != refused ] \
    && ok "$mode: il messaggio «Nessuna app può aprirlo» non appare" \
    || ko "$mode: appare «Nessuna app può aprirlo»"
done

clock_fixture_cleanup

# DayOfWeek draws its time and date at the left and right ends of the middle band.
dayofweek_points() {
  local b x1 y1 x2 y2 band_from_bottom
  b=$(clock_bounds) || return 1
  read -r x1 y1 x2 y2 <<<"$b"
  band_from_bottom=$(((y2 - y1) / 2))
  [ "$band_from_bottom" -le "$(dp 70)" ] || band_from_bottom=$(dp 70)
  echo $((x1 + (x2 - x1) / 4)) $((x2 - (x2 - x1) / 4)) $((y2 - band_from_bottom))
}

google_calendar_front() { # Ganimede started the calendar's time view (offline, Calendar may show its own error page)
  A logcat -d -s ActivityTaskManager:I | grep -q 'START u0 {act=android.intent.action.VIEW dat=content://com.android.calendar/.*cmp=com.google.android.calendar/'
}
google_clock_front() {
  A shell dumpsys window | grep -q 'mCurrentFocus.*com.google.android.deskclock'
}

step "Data e ora dell'orologio aprono destinazioni distinte in Lista e Classica"
for mode in list classic; do
  [ "$mode" = list ] && mode_it=Lista || mode_it=Classica
  seed "$mode"
  setp string clock_style dayofweek
  A shell pm grant "$PK" android.permission.READ_CALENDAR >/dev/null

  setp boolean agenda true
  read -r time_x date_x band_y <<<"$(dayofweek_points)"
  A shell input tap "$date_x" "$band_y"
  wait_until 5 has "La tua agenda" \
    && ok "$mode_it: toccando la data si apre «La tua agenda»" \
    || ko "$mode_it: toccando la data non si apre «La tua agenda»"
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus

  setp boolean agenda false
  A shell am force-stop com.google.android.calendar; A logcat -b main -c
  read -r time_x date_x band_y <<<"$(dayofweek_points)"
  A shell input tap "$date_x" "$band_y"
  wait_until 5 google_calendar_front \
    && ok "$mode_it: con agenda disattivata la data apre Google Calendar" \
    || ko "$mode_it: con agenda disattivata la data non apre Google Calendar"
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus

  read -r time_x date_x band_y <<<"$(dayofweek_points)"
  A shell input tap "$time_x" "$band_y"
  wait_until 5 google_clock_front \
    && ok "$mode_it: toccando l'ora si apre Orologio" \
    || ko "$mode_it: toccando l'ora non si apre Orologio"
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
done

# ---- The date of the clock for a screen reader, and clicks that are not touches ----
# The fixture's ScreenReader service stands in for TalkBack: it lists, and performs, the actions of the clock node.
A11Y_TAG=FixtureA11y
A11Y_SERVICES_BEFORE=$(A shell settings get secure enabled_accessibility_services | tr -d '\r')
A11Y_ENABLED_BEFORE=$(A shell settings get secure accessibility_enabled | tr -d '\r')
a11y_off() { # gives back the device's own accessibility settings (a test phone may run TalkBack)
  if [ "$A11Y_SERVICES_BEFORE" = null ] || [ -z "$A11Y_SERVICES_BEFORE" ]; then A shell settings delete secure enabled_accessibility_services >/dev/null
  else A shell settings put secure enabled_accessibility_services "$A11Y_SERVICES_BEFORE" >/dev/null; fi
  A shell settings put secure accessibility_enabled "${A11Y_ENABLED_BEFORE/null/0}" >/dev/null
}
a11y_done() { A logcat -d -s $A11Y_TAG:I | grep -q 'done performed='; }
a11y() { # a11y list|click|label [regex]: asks the service to act on the clock node and waits for its answer
  local _ # the service binds some seconds after the setting (or after the install): no answer means not listening yet, ask again
  for _ in 1 2 3 4; do
    A logcat -b main -c
    A shell am broadcast -a org.ganimede.fixture.A11Y -p $FIX --es desc "Orologio" --es mode "$1" --es label "'${2:-.}'" >/dev/null # quoted again for the device shell, where | is a pipe
    wait_until 5 a11y_done && return 0
  done
  return 1
}
a11y_performed() { A logcat -d -s $A11Y_TAG:I | grep -q 'done performed=true'; }
a11y_date_action() { A logcat -d -s $A11Y_TAG:I | grep -iEq 'action label=.*(data|agenda|calendario)'; }
DATE_ACTION='data|agenda|calendario'

step "Orologio con data: azione accessibile separata e click da accessibilità (Lista e Classica)"
"$HERE/../gradlew" -p "$HERE/.." -q -Pe2e :fixture:assembleDebug >/dev/null 2>&1 \
  && A install -r "$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" >/dev/null && ok "fixture screen reader installata" \
  || ko "fixture screen reader non installata (./gradlew -Pe2e :fixture:assembleDebug)"
defer a11y_off; defer clock_fixture_cleanup
A shell pm disable-user --user 0 com.google.android.deskclock >/dev/null
A shell settings put secure enabled_accessibility_services $FIX/.ScreenReader >/dev/null
A shell settings put secure accessibility_enabled 1 >/dev/null
for mode in list classic; do
  [ "$mode" = list ] && mode_it=Lista || mode_it=Classica
  A shell pm grant "$PK" android.permission.READ_CALENDAR >/dev/null
  for style in dayofweek vertical horizontal; do
    seed "$mode"; setp string clock_style "$style"; setp boolean agenda true
    wait_until 5 clock_bounds >/dev/null
    a11y list
    a11y_date_action \
      && ok "$mode_it $style: l'orologio espone un'azione accessibile per la data" \
      || ko "$mode_it $style: nessuna azione accessibile per la data (solo: $(A logcat -d -s $A11Y_TAG:I | tr '\n' ' '))"
    a11y label "$DATE_ACTION"
    a11y_performed && wait_until 5 has "La tua agenda" \
      && ok "$mode_it $style: l'azione accessibile della data apre «La tua agenda»" \
      || ko "$mode_it $style: l'azione accessibile della data non apre «La tua agenda»"
    A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  done

  seed "$mode"; setp string clock_style basic
  wait_until 5 clock_bounds >/dev/null
  a11y list
  a11y_date_action \
    && ko "$mode_it basic: azione per la data su uno stile che non la disegna" \
    || ok "$mode_it basic: nessuna azione per la data dove la data non è nell'orologio"

  # A long press on the date leaves the touch point in the date box without any click: a later click that is not a touch
  # (a screen reader's, a keyboard's) must open the alarms.
  seed "$mode"; setp string clock_style dayofweek; setp boolean agenda true
  read -r time_x date_x band_y <<<"$(dayofweek_points)"
  A shell input swipe "$date_x" "$band_y" "$date_x" "$band_y" 1300
  A shell input keyevent KEYCODE_BACK; wait_until 5 focus
  a11y click
  wait_until 5 clock_result
  [ "$CLOCK_RESULT" = opened ] && ! has "La tua agenda" \
    && ok "$mode_it: dopo una pressione lunga sulla data, il click accessibile apre le sveglie" \
    || ko "$mode_it: dopo una pressione lunga sulla data, il click accessibile non apre le sveglie"
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
done
a11y_off; clock_fixture_cleanup

step "Un'attività rifiutata non scrive l'URI dei dati nel log"
# Calendar gone: the date tap's VIEW content://com.android.calendar/time/... has no handler, so Ui.start refuses it.
seed list; setp string clock_style dayofweek; setp boolean agenda false
A shell pm disable-user --user 0 com.google.android.calendar >/dev/null
defer 'A shell pm enable --user 0 com.google.android.calendar'
A logcat -b main -c
read -r time_x date_x band_y <<<"$(dayofweek_points)"
A shell input tap "$date_x" "$band_y"
wait_until 5 cant_open_visible \
  && ok "la data senza Calendar mostra «Nessuna app può aprirlo»" \
  || ko "la data senza Calendar non mostra «Nessuna app può aprirlo»"
A logcat -d -s Ganimede | grep -q 'content://' \
  && ko "il log Ganimede contiene l'URI dei dati (content://…)" \
  || ok "il log Ganimede non contiene l'URI dei dati"
A shell pm enable --user 0 com.google.android.calendar >/dev/null
