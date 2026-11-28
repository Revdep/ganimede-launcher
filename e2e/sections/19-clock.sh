emu_only "clock: disables apps and sets accessibility" || return 0
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

step "Il tocco sull'orologio apre le sveglie protette in Lista e Classica"
fixture_install && ok "fixture sveglie compilata e installata" \
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

# ---- The date of the clock for a screen reader, and clicks that are not touches (a11y: lib.sh) ----
a11y_date_action() { A logcat -d -s $A11Y_TAG:I | grep -iEq 'action label=.*(data|agenda|calendario)'; }
DATE_ACTION='data|agenda|calendario'

step "Orologio con data: azione accessibile separata e click da accessibilità (Lista e Classica)"
# the fixture and the disabled Google Clock of the alarms step stay: one install, one disable (issue 048)
a11y_on
for mode in list classic; do
  [ "$mode" = list ] && mode_it=Lista || mode_it=Classica
  A shell pm grant "$PK" android.permission.READ_CALENDAR >/dev/null
  for style in dayofweek vertical horizontal; do
    seed "$mode" string clock_style "$style" boolean agenda true
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

  seed "$mode" string clock_style basic
  wait_until 5 clock_bounds >/dev/null
  a11y list
  a11y_date_action \
    && ko "$mode_it basic: azione per la data su uno stile che non la disegna" \
    || ok "$mode_it basic: nessuna azione per la data dove la data non è nell'orologio"

  # A long press on the date leaves the touch point in the date box without any click: a later click that is not a touch
  # (a screen reader's, a keyboard's) must open the alarms.
  seed "$mode" string clock_style dayofweek boolean agenda true
  read -r time_x date_x band_y <<<"$(dayofweek_points)"
  A shell input swipe "$date_x" "$band_y" "$date_x" "$band_y" 1300
  A shell input keyevent KEYCODE_BACK; wait_until 5 focus
  CLOCK_RESULT=; a11y click # the alarms step's result just before must not count
  wait_until 5 clock_result
  [ "$CLOCK_RESULT" = opened ] && ! has "La tua agenda" \
    && ok "$mode_it: dopo una pressione lunga sulla data, il click accessibile apre le sveglie" \
    || ko "$mode_it: dopo una pressione lunga sulla data, il click accessibile non apre le sveglie"
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
done

step "Blocchi: ridimensionare con un lettore di schermo"
BOTTOM_HANDLE="Ridimensiona bordo inferiore"
seed classic
wait_until 5 clock_bounds >/dev/null
read -r x1 y1 x2 y2 <<<"$(clock_bounds)"
A shell input swipe $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) $(((x1 + x2) / 2)) $(((y1 + y2) / 2)) 1300
tap "Ridimensiona"; wait_until 5 hasd "$BOTTOM_HANDLE"
a11y list . "$BOTTOM_HANDLE"
A logcat -d -s $A11Y_TAG:I | grep -q 'action label=Allarga' && A logcat -d -s $A11Y_TAG:I | grep -q 'action label=Restringi' \
  && ok "la maniglia espone Allarga e Restringi" || ko "la maniglia non espone Allarga/Restringi (solo: $(A logcat -d -s $A11Y_TAG:I | tr '\n' ' '))"
a11y label Restringi "$BOTTOM_HANDLE"
a11y_performed && wait_until 5 test -n "$(pref blocks | grep -o 'rowSpan&quot;:1')" \
  && ok "Restringi salva il blocco più basso di una cella" || ko "Restringi non cambia la misura (blocks: $(pref blocks))"
wait_until 5 hasd "$BOTTOM_HANDLE" && ok "il ridimensionamento resta attivo dopo l'azione" || ko "il ridimensionamento si è chiuso dopo l'azione"
wait_until 5 a11y_said "Larghezza" && ok "dopo Restringi l'app annuncia la misura" || ko "nessun annuncio «Larghezza…» dopo Restringi"
read -r _ y1 _ y2 <<<"$(clock_bounds)"; small=$((y2 - y1))
a11y label Restringi "$BOTTOM_HANDLE" # already one cell high: the minimum
wait_until 5 a11y_said "Limite raggiunto" && ok "al minimo Restringi annuncia «Limite raggiunto»" || ko "nessun annuncio «Limite raggiunto» al minimo"
[ -n "$(pref blocks | grep -o 'rowSpan&quot;:1')" ] && ok "al minimo la misura salvata non cambia" || ko "Restringi al minimo ha cambiato la misura (blocks: $(pref blocks))"
a11y label Allarga "$BOTTOM_HANDLE"
a11y_performed && wait_until 5 test -z "$(pref blocks | grep -o 'rowSpan&quot;:1')" \
  && ok "Allarga salva il blocco più alto di una cella" || ko "Allarga non cambia la misura (blocks: $(pref blocks))"
grown() { read -r _ y1 _ y2 <<<"$(clock_bounds)"; [ $((y2 - y1)) -gt "$small" ]; }
wait_until 5 grown && ok "Allarga: il blocco sullo schermo è più alto" || ko "Allarga: il blocco sullo schermo non è cresciuto"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
a11y_off; clock_fixture_cleanup

step "Data e ora dell'orologio aprono destinazioni distinte in Lista e Classica"
for mode in list classic; do
  [ "$mode" = list ] && mode_it=Lista || mode_it=Classica
  A shell pm grant "$PK" android.permission.READ_CALENDAR >/dev/null # granted before the start, as before
  seed "$mode" string clock_style dayofweek boolean agenda true
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

step "Un'attività rifiutata non scrive l'URI dei dati nel log"
# Calendar gone: the date tap's VIEW content://com.android.calendar/time/... has no handler, so Ui.start refuses it.
seed list string clock_style dayofweek boolean agenda false
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
