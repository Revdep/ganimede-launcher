emu_only "notices: grants the notification listener" || return 0
step "Notifications pop-up shows a preview, doesn't open anything"
seed list
A shell cmd notification allow_listener $PK/$PK.NotifService >/dev/null 2>&1
wait_until 5 listening
A shell cmd notification post -t "Prova" e2etag "Anteprima" >/dev/null   # (the shell sends one word)
posted() { A shell dumpsys notification | grep -q e2etag; }; wait_until 5 posted
home_menu "Notifiche"; tap "Notifiche"  # long-press an empty spot (list mode)
expect "Anteprima" && shot 29-notifs
focus && ok "still on the home screen (preview, not opened)" || ko "left the home screen"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Anteprima"

step "List: a favourite's notifications open with a swipe; no preview under its name by default"
timer # the Clock app posts a timer notification
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
wait_until 5 has "Clock"
clocks() { screen | grep -o 'text="Clock"' | wc -l; } # the timer has no title: a preview shows the app's name again
# the badge and the preview come together, from the listener's callback, after the notification is posted: wait for the badge, then look for the preview
badged() { screen | grep -qE 'text="[0-9]+\+?"'; }
wait_until 5 badged && ok "the badge counts the notification" || ko "no badge"
[ "$(clocks)" -eq 1 ] && ok "no preview under the name" || ko "a preview under the name (“Clock” $(clocks) times)"
# swipe_opens what x y dx: a sideways swipe from (x, y) opens the pop-up, Back closes it, the notification stays
swipe_opens() {
  A shell input swipe "$2" "$3" $(($2 + $4)) "$3" 300
  wait_until 5 has "Notifiche" && ok "$1: the pop-up opened" || ko "$1: no pop-up"
  A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
  timer_up && ok "$1: the notification is still there" || ko "$1: the notification was dismissed"
}
read -r _ cky <<<"$(cx Clock)"
edge=$(dp 45) # inside the favourite (40dp start) and the opposite alphabet column (58dp)
swipe_opens "swipe right from the overlapping left edge" "$edge" "$cky" 400
setp boolean left_handed true; wait_until 5 has "Clock"
read -r _ cky <<<"$(cx Clock)"
edge=$((W - $(dp 45)))
swipe_opens "left-handed: swipe left from the overlapping right edge" "$edge" "$cky" -400
setp del left_handed
# Rows that aren't favourites (search, drawer, folders, suggestions) have no swipe: they keep the preview to reach the messages
A shell input swipe 540 1900 540 600 150; expect "Cerca app"
typ "clo"; wait_until 5 has "Clock"
wait_until 5 settled && stable=1 || { stable=0; ko "search did not settle (the preview moves)"; } # coordinates are read only once it stopped moving
[ "$(clocks)" -eq 2 ] && ok "search: the Clock row keeps its preview" || ko "search: no preview on the Clock row (“Clock” $(clocks) times)"
read -r bx1 by1 bx2 by2 <<<"$(screen | grep -o 'text="Clock"[^>]*bounds="[^"]*"' | sed -n 2p | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' ')"
if [ "$stable" = 1 ] && [ -n "${by2:-}" ]; then
  A shell input tap $((bx1 + 10)) $(((by1 + by2) / 2))
  wait_until 5 has "Notifiche" && ok "search: a tap on the preview opens the pop-up" || ko "search: no pop-up from the preview (tapped $((bx1 + 10)),$(((by1 + by2) / 2)) in [$bx1,$by1][$bx2,$by2])"
  A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
fi
start_home

step "List: with the preview switched on it is under the name; a swipe on it opens the pop-up, a tap too"
setp boolean notif_preview true; wait_until 5 has "Clock"
[ "$(clocks)" -eq 2 ] && ok "the preview is under the name" || ko "no preview under the name (“Clock” $(clocks) times)"
read -r bx1 by1 bx2 by2 <<<"$(screen | grep -o 'text="Clock"[^>]*bounds="[^"]*"' | sed -n 2p | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' ')"
if [ -n "${by2:-}" ]; then
  swipe_opens "swipe right on the preview" $((bx1 + 10)) $(((by1 + by2) / 2)) 400
  A shell input tap $((bx1 + 10)) $(((by1 + by2) / 2))
  wait_until 5 has "Notifiche"; shot 30-bubble-tap
  has "Notifiche" && ok "a tap on the preview opens the pop-up" || ko "no pop-up after a tap on the preview"
  focus && ok "the Clock app did not open" || ko "it opened the app directly"
  A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
else ko "no preview to swipe"; fi
noclock

step "Classic: the badge is whole on the grid and in the dock (not cut by the cell)"
seed classic; setp string dock "com.google.android.deskclock/com.android.deskclock.DeskClock"
timer; A shell input keyevent KEYCODE_HOME; wait_until 5 focus
badges() { screen | grep -oE 'text="[0-9]+\+?"[^>]*bounds="[^"]*"' | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' '; }
two() { [ "$(badges | wc -l)" -ge 2 ]; }
wait_until 5 two && ok "a badge on the grid's Clock and on the dock's" || ko "badges: $(badges | wc -l)"
while read -r x1 y1 x2 y2; do
  near $((y2 - y1)) "$(dp 20)" 1 && [ $((x2 - x1)) -ge $(($(dp 20) - 1)) ] && ok "badge whole: $((x2 - x1))x$((y2 - y1)) px" \
    || ko "badge cut: $((x2 - x1))x$((y2 - y1)) px, expected $(dp 20) high and wide"
done <<<"$(badges)"
# TalkBack reads the icon's description, not the badge: the count is in it, and leaves with the notification
screen | grep -qE 'content-desc="Clock, [0-9]+ notific' && ok "the description says the count" || ko "no count in the Clock description"
noclock
named() { screen | grep -q 'content-desc="Clock"' && ! screen | grep -q 'content-desc="Clock, '; }
wait_until 10 named && ok "notification gone: the description is just “Clock” again" || ko "a count left in the Clock description"
