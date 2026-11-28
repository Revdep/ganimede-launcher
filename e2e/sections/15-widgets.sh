# dock slots, clock block resize/remove, widgets add/remove
docked_n() { pref dock | sed 's/&#10;/\n/g' | grep -c .; } # filled dock slots
hostviews() { dump | grep -o 'class="android.appwidget.AppWidgetHostView"' | wc -l; }
longpress() { A shell input swipe "$1" "$2" "$1" "$2" 1300; }

step "Dock: 3 slots, a 4th app can't be added"
seed classic
setp string fav "com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;com.google.android.deskclock/com.android.deskclock.DeskClock&#10;com.google.android.contacts/com.android.contacts.activities.PeopleActivity&#10;com.android.settings/com.android.settings.Settings"
setp int dock_count 3
i=0
for app in Calendar Clock Contacts; do
  wait_until 5 has "$app" || ko "$app not on the home to drag" # setp restarts the home: read its place once drawn
  read -r ax ay <<<"$(cx "$app")"; [ -n "$ax" ] || continue
  A shell input draganddrop "$ax" "$ay" $((W * (2 * i + 1) / 6)) $((H - $(dp 90))) 1500
  i=$((i + 1)); n=$i
  wait_until 5 eval '[ "$(docked_n)" = "$n" ]'
done
[ "$(docked_n)" = 3 ] && ok "3 apps in the 3 dock slots" || ko "dock has $(docked_n) apps, expected 3"
shot 150-dock-full
read -r sx sy <<<"$(cx Settings)"
A shell input draganddrop "$sx" "$sy" $((W / 6)) $((H - $(dp 90))) 1500
wait_until 5 has "Calendar"   # Calendar, pushed out of slot 1, is back on the grid
[ "$(docked_n)" = 3 ] && ok "full dock after a 4th drop: still 3 apps" || ko "dock has $(docked_n) apps after the 4th drop"
[ "$(pref dock_count)" = 3 ] && ok "dock_count is still 3" || ko "dock_count $(pref dock_count)"
has "Calendar" && ok "the displaced app is back on the grid, not lost" || ko "Calendar vanished"

step "Blocks: clock Resize / Remove"
seed classic
desc="desc:Orologio, tocca per le sveglie"
read -r x1 y1 x2 y2 <<<"$(bounds "$desc")"
if [ -n "$x1" ]; then
  longpress $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
  tap "Ridimensiona" && wait_until 5 hasd "Ridimensiona bordo destro"
  # bottom edge up: right edge sits on the system back-gesture zone, so shrink the height (2 rows → 1)
  read -r hx hy <<<"$(cx "desc:Ridimensiona bordo inferiore")"
  A shell input swipe "$hx" "$hy" "$hx" $((hy - $(dp 120))) 800
  wait_until 5 nod "Ridimensiona bordo inferiore"
  pref blocks | grep -q 'rowSpan&quot;:1' && ok "blocks pref stores the new size (rowSpan 1)" || ko "blocks pref: $(pref blocks)"
  shot 151-clock-resized
  seed classic # a fresh clock: the menu on a block just resized is not reliable to hit on a 1-row clock
  longpress $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
  tap "Rimuovi"
  wait_until 5 pref_lacks blocks 'id&quot;:&quot;clock'
  pref_lacks blocks 'id&quot;:&quot;clock' && ok "Rimuovi: the clock block is gone" || ko "clock still in blocks (or prefs unreadable)"
  longpress $((W / 2)) 1900
  expect "Aggiungi orologio" && A shell input keyevent KEYCODE_BACK
else ko "no clock block"; fi

step "Widgets: grant bind, add the Clock widget, remove it"
# Stopwatch: the Clock app's widgets Analog/Digital/World open a style screen of the Clock app (system UI, not driven here).
noclock
seed classic
A shell appwidget grantbind --package $PK
before=$(hostviews)
longpress $((W / 2)) 1900
tap "Aggiungi widget"   # the in-app picker lists "App · widget"
expect "Clock · Stopwatch" && tap "Clock · Stopwatch"
wait_until 10 eval '[ "$(hostviews)" -gt "$before" ]'
[ "$(hostviews)" -gt "$before" ] && ok "Stopwatch widget block appears on the home" || ko "no widget on the home after picking it"
pref blocks | grep -q 'id&quot;:&quot;[0-9]' && ok "blocks pref holds the widget" || ko "no widget id in blocks: $(pref blocks)"
shot 152-widget-added
read -r wx1 wy1 wx2 wy2 <<<"$(bounds "desc:Stopwatch")"
longpress $(((wx1 + wx2) / 2)) $(((wy1 + wy2) / 2))
tap "Rimuovi"
wait_until 5 eval '[ "$(hostviews)" -le "$before" ]'
[ "$(hostviews)" -le "$before" ] && ok "Rimuovi: the widget block is gone" || ko "widget still on the home"
pref_lacks blocks 'id&quot;:&quot;[0-9]' && ok "its cells are free again (no widget in blocks)" || ko "widget still in blocks (or prefs unreadable)"
