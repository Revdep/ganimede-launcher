# Folders, second half: the pop-up's order (drag, screen reader, menu), another app's update, Choose apps. Starts from its own seeds.
step "Folders: long-press and drag reorders the icons inside the pop-up, never out of it (082)"
order() { pref "folder:ord" | grep -o '\(calendar\|deskclock\|contacts\)[\\/]' | tr -d '\\/' | paste -sd, -; } # the stored order, short names
card_text() { screen | awk -v RS='class="android.widget.ScrollView"' 'END { printf "%s", $0 }'; } # what follows the card's ScrollView
in_card() { card_text | grep -q "text=\"$1\""; }
shown() { # the pop-up's icons in view-tree order (the dump's, TalkBack's), same short names
  card_text | grep -o 'text="\(Calendar\|Clock\|Contacts\)"' \
    | cut -d'"' -f2 | sed 's/Clock/deskclock/' | tr 'A-Z' 'a-z' | paste -sd, -
}
calendar_up() { A shell dumpsys window | grep mCurrentFocus | grep -q calendar; }
for variant in classic:1 list:1 classic:2; do # a 2-column pop-up: Calendar and Contacts are on different rows
  mode=${variant%:*}; cols=${variant#*:}; mode="$mode/$cols col"
  seed ${variant%:*} string folder:ord '{"name":"Ordine","where":"fav","cols":'$cols',"apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}' string fav 'f:ord' del grid
  tap Ordine; expect "Contacts"
  [ "$(order)" = "calendar,deskclock,contacts" ] && ok "$mode: starting order stored" || ko "$mode: start order $(order)"
  read -r ax ay <<<"$(cx Calendar)"; read -r tx ty <<<"$(cx Contacts)"
  A shell input draganddrop "$ax" "$ay" "$tx" "$ty" 1500
  reordered() { [ "$(order)" = "deskclock,contacts,calendar" ]; }
  wait_until 5 reordered
  [ "$(order)" = "deskclock,contacts,calendar" ] && ok "$mode: dragging Calendar onto Contacts' place saved the new order" || ko "$mode: order after drag $(order)"
  has "Contacts" && ok "$mode: the pop-up stays open after the drop" || ko "$mode: the pop-up closed"
  wait_until 5 settled
  [ "$(shown)" = "$(order)" ] && ok "$mode: the pop-up's views (TalkBack's reading order) follow the saved order" || ko "$mode: views in the order $(shown), saved $(order)"
  shot "131-folder-reordered-${variant/:/-}"
  # the moved icons still answer: in 2 columns Calendar changed row, and a view moved only by translation can't be touched
  read -r ax ay <<<"$(cx Calendar)"; read -r tx ty <<<"$(cx Clock)"
  A shell input draganddrop "$ax" "$ay" "$tx" "$ty" 1500
  order_back() { [ "$(order)" = "calendar,deskclock,contacts" ]; }
  wait_until 5 order_back && ok "$mode: the moved Calendar answers a long-press: dragged back first" || ko "$mode: the moved Calendar ignored the long-press drag, order $(order)"
  wait_until 5 settled
  [ "$(shown)" = "$(order)" ] && ok "$mode: views follow the saved order again" || ko "$mode: views in the order $(shown), saved $(order)"
  tap Calendar
  wait_until 10 calendar_up && ok "$mode: a tap on the moved Calendar opens it" || ko "$mode: a tap on the moved Calendar did nothing"
  A shell input keyevent KEYCODE_HOME; wait_until 10 has Ordine
  tap Ordine; wait_until 5 has "Contacts"; wait_until 5 settled
  read -r ax ay <<<"$(cx Clock)"
  A shell input draganddrop "$ax" "$ay" $((W - 4)) $((H - 4)) 1500
  wait_until 5 settled # input returns when the finger lifts; the icons then glide for 150 ms
  [ "$(order | tr , '\n' | sort | paste -sd, -)" = "calendar,contacts,deskclock" ] && ok "$mode: dragged to the screen corner, every app is still in the folder ($(order))" || ko "$mode: apps after drag to the corner: $(order)"
  has "Contacts" && has "Calendar" && ok "$mode: the icons are still in the open pop-up" || ko "$mode: the pop-up lost its icons"
  [ "$(pref fav)" = 'f:ord' ] && ok "$mode: nothing dropped on the home" || ko "$mode: fav = $(pref fav)"
  # a drag cut short (the pop-up closed under the finger) saves nothing and leaves no floating icon behind
  stored=$(order); read -r ax ay <<<"$(cx Calendar)"; read -r tx ty <<<"$(cx Clock)"
  A shell input draganddrop "$ax" "$ay" "$tx" "$ty" 5000 &
  sleep 3; A shell input keyevent KEYCODE_BACK; wait # a gesture hold, not a wait: mid-gesture, input holds still for the long-press timeout, then moves for 5 s; there is no screen state to wait on (a dump takes ~2 s)
  wait_until 5 hasnt "Calendar"
  [ "$(order)" = "$stored" ] && ok "$mode: a cancelled drag keeps the stored order ($stored)" || ko "$mode: order after cancel $(order), was $stored"
  tap Ordine; wait_until 5 has "Calendar"; wait_until 5 settled # the pop-up opens with an animation
  read -r ax ay <<<"$(cx Calendar)"
  has "Clock" && has "Contacts" && [ -n "$ax" ] && ok "$mode: the pop-up opens again with all its icons, nothing left floating" || ko "$mode: the reopened pop-up lost an icon"
  # pressed and released in place: the icon's menu, as on the home, with "Rimuovi dalla cartella"; nothing is taken out
  still=$(order); hold "$ax" "$ay"
  wait_until 5 has "Rimuovi dalla cartella" && ok "$mode: long-press in place opens Calendar's menu, with Rimuovi dalla cartella" || ko "$mode: no menu after a long-press in place"
  [ "$(order)" = "$still" ] && ok "$mode: long-press without moving keeps the order ($still)" || ko "$mode: order after a still long-press $(order), was $still"
  A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Rimuovi dalla cartella"
  has "Calendar" && has "Clock" && has "Contacts" && ok "$mode: menu closed, all apps still in the open pop-up" || ko "$mode: the pop-up lost an icon or closed"
done

step "Folders: a screen reader's Sposta su/giù reorders the icons in the pop-up (FolderDrag actions)"
fixture_install && ok "fixture installed" || ko "fixture not built or installed (./gradlew -Pe2e :fixture:assembleDebug)"
defer "A uninstall $FIX >/dev/null 2>&1"
a11y_on
seed classic string folder:ord '{"name":"Ordine","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}' string fav 'f:ord' del grid
tap Ordine; expect "Contacts"; wait_until 5 settled
a11y list . Calendar
{ A logcat -d -s $A11Y_TAG:I | grep -q 'action label=Sposta giù' && ! A logcat -d -s $A11Y_TAG:I | grep -q 'action label=Sposta su'; } \
  && ok "the first icon offers Sposta giù and not Sposta su" || ko "first icon actions: $(A logcat -d -s $A11Y_TAG:I | tr '\n' ' ')"
a11y label "Sposta giù" Calendar
sr_moved() { [ "$(order)" = "deskclock,calendar,contacts" ]; }
a11y_performed && wait_until 5 sr_moved && ok "Sposta giù saves Calendar one place later" || ko "order after Sposta giù: $(order)"
wait_until 5 settled
[ "$(shown)" = "$(order)" ] && ok "the pop-up shows the new order" || ko "views in the order $(shown), saved $(order)"
wait_until 5 a11y_said "Posizione 2 di 3" && ok "the move is announced" || ko "no «Posizione 2 di 3» announcement"
a11y label "Sposta su" Calendar
sr_back() { [ "$(order)" = "calendar,deskclock,contacts" ]; }
a11y_performed && wait_until 5 sr_back && ok "Sposta su puts Calendar back" || ko "order after Sposta su: $(order)"
wait_until 5 settled
[ "$(shown)" = "$(order)" ] && ok "the pop-up shows the order back" || ko "views in the order $(shown), saved $(order)"
a11y_off; A shell input keyevent KEYCODE_BACK

step "Folders: an icon's menu in the pop-up takes it out with Rimuovi dalla cartella, an app or a pinned shortcut (082)"
seed classic string folder:sc '{"name":"Scorciatoie","where":"fav","apps":["com.google.android.deskclock/com.android.deskclock.DeskClock","s:org.ganimede.e2e|zz1|Zz Posta","com.google.android.calendar/com.android.calendar.AllInOneActivity"]}' string fav 'f:sc' del grid
tap Scorciatoie; expect "Zz Posta"; wait_until 5 settled
read -r ax ay <<<"$(cx "Zz Posta")"; hold "$ax" "$ay"
wait_until 5 has "Rimuovi dalla cartella" && ok "the shortcut's menu has Rimuovi dalla cartella" || ko "no Rimuovi dalla cartella in the shortcut's menu"
tap "Rimuovi dalla cartella"
gone() { ! pref folder:sc | grep -q "$1"; }
wait_until 5 gone zz1 && ok "the shortcut is no longer stored" || ko "folder:sc = $(pref folder:sc)"
wait_until 5 has "Calendar"; wait_until 5 settled # the pop-up opens again with the result
hasnt "Zz Posta" && has "Clock" && ok "the pop-up shows it gone, Clock still there" || ko "pop-up after removing the shortcut"
read -r ax ay <<<"$(cx Clock)"; hold "$ax" "$ay"
wait_until 5 has "Rimuovi dalla cartella" && ok "Clock's menu has Rimuovi dalla cartella" || ko "no Rimuovi dalla cartella in Clock's menu"
tap "Rimuovi dalla cartella"
wait_until 5 gone deskclock && ok "Clock is no longer stored" || ko "folder:sc = $(pref folder:sc)"
# a new icon from the same menu: the open pop-up draws it once the app list has reloaded (apps carry their icon)
wait_until 5 in_card Calendar; wait_until 5 settled
read -r c1 c2 c3 c4 <<<"$(card)"; crop="$((c3 - c1))x$((c4 - c2))+$c1+$c2"
A exec-out screencap -p | magick png:- -crop "$crop" +repage "$OUT/folder-icon-before.png"
read -r ax ay <<<"$(cx Calendar)"; hold "$ax" "$ay"
tap "Icona dell'app…"; tap "Arcticons (integrato)"
# the search starts with the app's name: its first result, whichever icon the built-in set ranks first
first_icon() { screen | awk -v RS='class="android.widget.GridView"' 'END { printf "%s", $0 }' \
  | grep -o 'content-desc="[^"][^"]*"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 \
  | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   '; }
picker_ready() { [ -n "$(first_icon)" ]; }
wait_until 10 picker_ready
read -r ix1 iy1 ix2 iy2 <<<"$(first_icon)"; [ -n "$iy2" ] && A shell input tap $(((ix1 + ix2) / 2)) $(((iy1 + iy2) / 2))
picked() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q 'name="icon:com.google.android.calendar/'; }
wait_until 5 picked && ok "the icon picked from the in-folder menu is saved" || ko "no icon saved for Calendar"
redrawn() { # once the pick is saved, the picker gone and nothing moves (the pop-up rebuilt): the card differs from before
  picked && hasnt "Arcticons (integrato)" && settled || return 1
  A exec-out screencap -p | magick png:- -crop "$crop" +repage "$OUT/folder-icon-after.png"
  [ "$(fdiff "$OUT/folder-icon-before.png" "$OUT/folder-icon-after.png")" -gt 0 ] 2>/dev/null
}
wait_until 15 redrawn && ok "the open pop-up draws the new icon" || ko "the open pop-up still draws Calendar's old icon"
# renamed from the same menu: the pop-up still open underneath shows the new name once the dialog closes
wait_until 5 in_card Calendar; wait_until 5 settled
read -r ax ay <<<"$(cx Calendar)"; hold "$ax" "$ay"
tap "Rinomina"; wait_until 5 has "Salva"
field=$(screen | grep -o 'class="android.widget.EditText"[^>]*bounds="[^"]*"' | tail -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
read -r fx1 fy1 fx2 fy2 <<<"$field"; A shell input tap $(((fx1 + fx2) / 2)) $(((fy1 + fy2) / 2)) # focus the name field
clear_text; typ AgendaZz; tap "Salva"
renamed() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q 'name="rn:com.google.android.calendar/[^"]*">AgendaZz<'; }
wait_until 5 renamed && wait_until 5 in_card AgendaZz && ok "renamed from the in-folder menu: the open pop-up shows AgendaZz" || ko "not renamed, or the open pop-up still shows the old name"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "AgendaZz"

step "Folders: another app's update doesn't rebuild the open pop-up; one of its apps removed mid-drag waits for the finger (082)"
FIXAPK="$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" # e2e/fixture: an app with a launcher activity, ours to reinstall
fixture_install && ok "fixture app installed" || ko "fixture app not built or installed (./gradlew -Pe2e :fixture:assembleDebug): the checks below can't pass"
update_other() { cpu0=$(cpu_ticks); A install -r "$FIXAPK" >/dev/null; } # a package event (added/changed): Ganimede reloads its app list
# The reload (250 ms debounce, then the load thread) leaves nothing on screen, in the logs or in dumpsys to wait for (the app
# logs nothing of it), so the signal is Ganimede's own CPU: its utime+stime must rise by RELOAD_TICKS over cpu0 (taken before
# the event) and then stand still for two reads. Idle Ganimede with the pop-up open costs 0-1 ticks (10 ms each) a second,
# a reload (the package list, an icon per app) tens of them: 3 sits between. A rise below it is a ko, not a pass.
# (3 is an estimate from that reasoning, not yet read off a device: raise or lower it from the ko message's delta.)
RELOAD_TICKS=3
cpu_ticks() { A shell "cat /proc/\$(pidof $PK)/stat" | awk '{print $14 + $15}'; }
reloaded() { local t; t=$(cpu_ticks); [ $((${t:-0} - ${cpu0:-0})) -ge $RELOAD_TICKS ] && [ "$t" = "${cpu_last:-}" ] && return 0; cpu_last=$t; return 1; }
reload_ko() { ko "Ganimede never reloaded its app list after $1 (cpu rose $(($(cpu_ticks) - ${cpu0:-0})) ticks, needs $RELOAD_TICKS)"; }
popwin() { A shell dumpsys window windows | grep -o "Window{[0-9a-f]* u0 [^}]*$PK[^}]*}" | sort | paste -sd' ' -; } # its windows
seed classic string folder:ord '{"name":"Ordine","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}' string fav 'f:ord' del grid
tap Ordine; expect "Contacts"; wait_until 5 settled
wins=$(popwin) # (not "before": run.sh counts checks in that name)
cpu_last=; update_other
wait_until 10 reloaded || reload_ko "the update"
[ -n "$wins" ] && [ "$(popwin)" = "$wins" ] && ok "the pop-up is the same window after another app's update" || ko "the pop-up was rebuilt: windows $(popwin), were $wins"
has "Contacts" && ok "the pop-up is still open" || ko "the pop-up closed"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Contacts"
# An app IN the folder goes away while a finger drags an icon: the pop-up waits for the finger (FolderDrag.busy), the
# drop is saved, and only then is it built again without that app. Rebuilt under the finger, the drag would be lost.
setp string folder:ord '{"name":"Ordine","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity","org.ganimede.fixture/org.ganimede.fixture.Open"]}'
tap Ordine; expect "Fixture"; wait_until 5 settled
read -r ax ay <<<"$(cx Calendar)"; read -r tx ty <<<"$(cx Contacts)"
A shell input motionevent DOWN "$ax" "$ay"; sleep 0.9 # a gesture hold, not a wait: past the long-press timeout the menu opens and the icon is held
A shell input motionevent MOVE "$ax" $((ay + $(dp 16))) # past the touch slop: the menu closes, the drag starts
cpu0=$(cpu_ticks); cpu_last=; A uninstall org.ganimede.fixture >/dev/null
fixture_gone() { ! A shell pm path org.ganimede.fixture >/dev/null 2>&1; }
wait_until 10 fixture_gone || ko "the fixture app is still installed"
wait_until 10 reloaded || reload_ko "the uninstall" # then: under the finger nothing may change
dump | grep -q 'text="Fixture' && ok "while the finger is down the pop-up is not rebuilt (the removed app still shows)" || ko "the pop-up was rebuilt under the finger"
A shell input motionevent MOVE "$tx" "$ty"; A shell input motionevent UP "$tx" "$ty"
wait_until 5 reordered && ok "the drag held across the uninstall saved its order" || ko "the drag was lost: order $(order)"
wait_until 5 hasnt "Fixture" && ok "after the lift the pop-up is built again without the removed app" || ko "the pop-up still shows the removed app"
has "Contacts" && ok "the pop-up is open" || ko "the pop-up closed"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Contacts"
A install -r "$FIXAPK" >/dev/null || ko "the fixture app could not be reinstalled" # other sections use it

step "Folders: Choose apps lists the folder's pinned shortcuts; unchecking one takes it out, the order stays (082)"
seed classic string folder:sc '{"name":"Scorciatoie","where":"fav","apps":["com.google.android.deskclock/com.android.deskclock.DeskClock","s:org.ganimede.e2e|zz1|Zz Posta","com.google.android.calendar/com.android.calendar.AllInOneActivity"]}' string fav 'f:sc' del grid
tap Scorciatoie; expect "Zz Posta"
tap "✎ Modifica pop-up"; tap "Scegli app"; wait_until 5 has "Zz Posta"
tap "Zz Posta"; tap "Salva"
wait_until 5 has "Calendar" # the pop-up opens again with the result
hasnt "Zz Posta" && ok "the unchecked shortcut is gone from the pop-up" || ko "the shortcut is still in the pop-up"
pref folder:sc | grep -q 'zz1' && ko "the shortcut is still stored: $(pref folder:sc)" || ok "the shortcut is no longer stored"
pref folder:sc | grep -q 'deskclock.*calendar' && ok "Clock and Calendar keep their order" || ko "folder:sc = $(pref folder:sc)"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Calendar"


step "Folders: New folder made from an open pop-up replaces it, never stacks over it (c3 P2-1)"
seed classic string folder:ord '{"name":"Ordine","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock"]}' string fav 'f:ord' del grid
tap Ordine; expect "Clock"; wait_until 5 settled
scrolls() { screen | grep -c 'class="android.widget.ScrollView"'; }
one=$(scrolls)
read -r ax ay <<<"$(cx Calendar)"; hold "$ax" "$ay"
wait_until 5 has "Aggiungi a una cartella pop-up" && tap "Aggiungi a una cartella pop-up"
wait_until 5 has "Nuova cartella" && tap "Nuova cartella"
wait_until 5 has "Nome cartella" && typ Altra && tap "Crea"
wait_until 5 has "Più tardi" && tap "Più tardi"
wait_until 5 settled
[ "$(scrolls)" = "$one" ] && ok "one pop-up card on screen after the new folder opened (as before: $one ScrollView)" || ko "ScrollViews: $(scrolls), with one pop-up open there were $one"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Clock"
hasnt "Clock" && ok "Back closes the only pop-up, none is left under it" || ko "an older pop-up was left open under the new one"

step "Folders: a tap on a shortcut closes the pop-up, as a tap on an app does (c3 M2)"
seed classic string folder:sc '{"name":"Scorciatoie","where":"fav","apps":["com.google.android.deskclock/com.android.deskclock.DeskClock","s:org.ganimede.e2e|zz1|Zz Posta"]}' string fav 'f:sc' del grid
tap Scorciatoie; expect "Zz Posta"; wait_until 5 settled
tap "Zz Posta"
wait_until 8 hasnt "Zz Posta" && ok "the pop-up closed on the shortcut's tap" || ko "the pop-up stayed open after tapping the shortcut"

step "Folders: Home closes an open pop-up (c3 M1)"
seed classic string folder:ord '{"name":"Ordine","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock"]}' string fav 'f:ord' del grid
tap Ordine; expect "Clock"; wait_until 5 settled
A shell input keyevent KEYCODE_HOME
wait_until 5 hasnt "Clock" && ok "Home closed the pop-up" || ko "the pop-up stayed open after Home"
read -r ax ay <<<"$(cx Ordine)"; hold "$ax" "$ay"
wait_until 5 has "Rinomina"
A shell input keyevent KEYCODE_HOME
wait_until 5 hasnt "Rinomina" && ok "Home closed the folder's menu" || ko "the menu stayed open after Home"
