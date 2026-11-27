emu_only "popup: grants the notification listener" || return 0
# Notification pop-up. Posts come from `cmd notification post` (package com.android.shell, label "Shell"),
# the second app is the Clock timer. The shell command cannot set a category or a visibility, so a secret notification and an
# alarm-category one cannot be posted from here (see the notes at the two steps that would need them).
A shell cmd notification allow_listener $PK/$PK.NotifService >/dev/null 2>&1
listening() { A shell settings get secure enabled_notification_listeners | grep -q NotifService; }
bound() { A shell dumpsys notification | sed -n '/Live notification listeners/,/Snoozed notification listeners/p' | grep -q NotifService; }
tagged() { A shell dumpsys notification | grep -q "tag=$1 "; }                 # still in the system's list
untagged() { ! tagged "$1"; }
headers() { dump | grep -o "text=\"$1\"" | wc -l; }                            # how many group headers a name has
popup() { A shell input swipe 540 1900 540 1900 1500; tap "Notifiche"; }       # long-press an empty spot (list mode)
# The pop-up is a snapshot; Android can list a post before the listener's throttled read has reached Ganimede.
_popup_has() { popup; has "$1" && return 0; A shell input keyevent KEYCODE_BACK; wait_until 3 hasnt "Notifiche"; return 1; }
expect_popup() {
  wait_until 10 _popup_has "$1" && { ok "sees “$1”"; return 0; }
  popup; ko "doesn't see “$1”"; return 1
}
post() { A shell cmd notification post -t "$1" "$2" "$3" >/dev/null; }         # post title tag word
timer() { A shell am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH 900 \
  --ez android.intent.extra.alarm.SKIP_UI true >/dev/null 2>&1; t() { A shell dumpsys notification | grep -q "pkg=com.google.android.deskclock.*Timers"; }; wait_until 10 t; }
# a string-set pref the way the app stores it (setp has no sets), then restart the home
setset() { local f="$OUT/prefs.xml"; A shell am force-stop $PK; A exec-out run-as $PK cat shared_prefs/ganimede.xml > "$f"
  sed -i "/ name=\"$1\"/d" "$f"; sed -i "s@</map>@    <set name=\"$1\"><string>$2</string></set>\n</map>@" "$f"
  A exec-in "run-as $PK sh -c 'cat > shared_prefs/ganimede.xml'" < "$f"; start_home; }
# every clearable notification away, through the pop-up's own button
wipe() { popup; if wait_until 3 has "Cancella tutte"; then tap "Cancella tutte"; else A shell input keyevent KEYCODE_BACK; fi
  wait_until 5 hasnt "Notifiche"; noclock; }
bound_home() { seed list; wait_until 10 listening; wait_until 10 bound; }

step "Two posts from one app are grouped under one header"
bound_home; wipe
post "TitoloUno" g1 uno; post "TitoloDue" g2 due
wait_until 5 tagged g2
expect_popup "TitoloDue"; has "TitoloUno" && ok "both messages listed" || ko "TitoloUno missing"
n=$(headers SHELL); [ "$n" = 1 ] && ok "one SHELL header for two posts" || ko "SHELL header count is $n, expected 1"
shot 40-popup-group
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"

step "Interleaved posts A, B, A: app A still has exactly one header"
wipe
post "TitoloA1" i1 uno; wait_until 5 tagged i1
timer                                                    # app B: the Clock timer, posted between the two
post "TitoloA2" i2 due; wait_until 5 tagged i2
expect_popup "TitoloA2"; has "TitoloA1" && ok "both of A's messages listed" || ko "TitoloA1 missing"
has "CLOCK" && ok "B listed" || ko "no CLOCK header"
n=$(headers SHELL)
[ "$n" = 1 ] && ok "one SHELL header between A, B, A" || ko "SHELL header count is $n, expected 1"
shot 40-popup-interleave
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"

step "Swiping a card away removes the notification"
wipe
post "TitoloSwipe" sw1 uno; wait_until 5 tagged sw1
if expect_popup "TitoloSwipe"; then
  read -r sx sy <<<"$(cx TitoloSwipe)"
  A shell input swipe 150 "$sy" 1000 "$sy" 200
  wait_until 5 untagged sw1 && ok "gone from dumpsys notification" || ko "still in the system's list after the swipe"
  wait_until 5 hasnt "TitoloSwipe" && ok "card gone from the pop-up" || ko "card still shown"
fi
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"

step "Clear all empties the pop-up and the system list"
post "TitoloX" cl1 uno; post "TitoloY" cl2 due; wait_until 5 tagged cl2
if expect_popup "TitoloY"; then
  tap "Cancella tutte"
  wait_until 5 untagged cl1 && wait_until 5 untagged cl2 && ok "both gone from dumpsys notification" || ko "notifications left after Clear all"
  wait_until 5 hasnt "Notifiche" && ok "the pop-up closed" || ko "pop-up still open"
fi
expect_popup "Nessuna notifica"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Nessuna notifica"

# Secret notification (only the app name in bubble and pop-up): `cmd notification post` has no visibility flag, so it cannot
# be posted from the shell. Covered by the core test of Safe.notice. Left out here.

step "Summary on: a non-urgent post of a chosen app is held back, other apps are not"
A shell am force-stop $PK; A exec-out run-as $PK sh -c "sed -i '/name=\"summary_held\" *\/>/d; /name=\"summary_held\">/,/<\/set>/d' shared_prefs/ganimede.xml" # no key held by an earlier run
setp boolean summary true; setset summary_apps com.android.shell
wait_until 10 listening; wait_until 10 bound
c0=$(pref summary_count); c0=${c0:-0}
SUM=sum$(date +%s) # a fresh key: Android keeps an earlier run's snoozed key silent for hours, so a repeated tag is never seen
post "TitoloRiep" $SUM uno
held() { [ "$(pref summary_count)" = $((c0 + 1)) ]; }
wait_until 8 held && ok "the post was snoozed for the summary (counter +1)" || ko "post not held back by the summary"
untagged_now() { ! tagged $SUM; }; wait_until 5 untagged_now && ok "it left the active list" || ko "still in the active list"
timer                                                    # Clock is not in summary_apps: shown at once
expect_popup "CLOCK"
hasnt "TitoloRiep" && ok "the held post is absent from the pop-up" || ko "the held post is shown"
shot 40-popup-summary
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
# The held key is stored (not only in memory): it is still there after Ganimede is stopped and restarted, so a re-post
# after a restart is recognised. The re-post itself cannot be driven from here: Android keeps a snoozed key silent when it
# is posted again, and `cmd notification unsnooze` is denied to the shell. Core: SummaryTest.testNotificationNotSnoozedTwice.
held_key() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "|$SUM|"; }
held_key && ok "the held key is stored in the prefs" || ko "no held key in the prefs"
A shell am force-stop $PK; A shell input keyevent KEYCODE_HOME; wait_until 10 focus
held_key && ok "and survives a restart of the process" || ko "the held key is lost on restart"
# Alarm-category (shown despite the summary): the shell cannot set a category, so this half is left out.
bound_home; wipe

step "Two voice notes in one chat notification: a player each, one plays at a time; none when secret (fixture app)"
FIX=org.ganimede.fixture   # e2e/fixture: posts a MessagingStyle with two silent WAVs of 30 s and 40 s from its own provider
"$HERE/../gradlew" -p "$HERE/.." -q -Pe2e :fixture:assembleDebug >/dev/null 2>&1 \
  && A install -r "$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" >/dev/null && ok "fixture app built and installed" \
  || ko "fixture app not built or installed (./gradlew -Pe2e :fixture:assembleDebug): the voice checks below can't pass"
A shell pm grant $FIX android.permission.POST_NOTIFICATIONS
voice() { A shell am broadcast -n $FIX/.Post "$@" >/dev/null; fx() { A shell dumpsys notification | grep -q "pkg=$FIX"; }; wait_until 10 fx; }
players() { screen | grep -o 'text="\(Messaggio vocale\|[0-9]:[0-9][0-9] / [0-9]:[0-9][0-9]\)"' | cut -d'"' -f2 | paste -sd'|'; }
nplayers() { [ "$(players | awk -F'|' '{print NF}')" = "$1" ]; }
play_at() { screen | grep -o 'content-desc="Riproduci il messaggio vocale"[^>]*bounds="[^"]*"' | sed -n "$1p" \
  | grep -o '[0-9]*' | tail -4 | paste -sd' ' | awk '{print int(($1 + $3) / 2), int(($2 + $4) / 2)}'; }
# While a clock ticks the screen never idles and can't be dumped: what plays is told by the private copy's size instead
# (WAV header 44 + 16000 bytes a second), which also shows one copy at a time, deleted when playback stops.
copies() { A exec-out run-as $PK sh -c 'ls -l cache/voice 2>/dev/null' | awk '/note/{print $5}' | paste -sd' '; }
copies_are() { [ "$(copies)" = "$1" ]; }
voice; expect_popup "FIXTURE"
wait_until 5 eval '[ "$(players)" = "Messaggio vocale|Messaggio vocale" ]' && ok "pop-up: two players" || ko "pop-up players: $(players)"
shot 41-popup-voice
target48 "desc:Riproduci il messaggio vocale" && ok "the voice play button is a 48 dp target" || ko "the voice play button is under 48 dp" # issue 064
p1=$(play_at 1); p2=$(play_at 2)
A shell input tap $p2
wait_until 10 copies_are 640044 && ok "the second note (40 s) plays, alone" || ko "copies after tapping the second: $(copies)"
A shell input tap $p1
wait_until 10 copies_are 480044 && ok "the first (30 s) plays instead, the second's copy is gone" || ko "copies after tapping the first: $(copies)"
A shell input tap $p1
wait_until 5 copies_are "" && ok "paused: no copy left" || ko "copies after pausing: $(copies)"
read -r ax ay <<<"$(cx Anna)"; A shell input swipe "$ax" "$ay" "$ax" "$ay" 1500   # long-press the card: the message on its own
wait_until 5 nplayers 2 && ok "long-press view: two players" || ko "long-press view players: $(players)"
A shell input keyevent KEYCODE_BACK; wait_until 5 has "Cancella tutte"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
wipe; voice --ez secret true; expect_popup "FIXTURE"
nplayers 0 && ok "secret: no player" || ko "secret notification shows players: $(players)"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"

step "Favourite bubble (List, preview on): a player per voice note under the fixture's name, each plays, no overlap"
wipe; voice
setp string fav "$FIX/$FIX.Open\&#10;com.google.android.calendar/com.android.calendar.AllInOneActivity\&#10;com.google.android.deskclock/com.android.deskclock.DeskClock\&#10;com.google.android.contacts/com.android.contacts.activities.PeopleActivity"
setp boolean notif_preview true
wait_until 10 eval '[ "$(players)" = "Messaggio vocale|Messaggio vocale" ]' && ok "bubble: two players" || ko "bubble players: $(players)"
shot 42-bubble-voice
row() { local b; b=$(bounds "desc:$1"'[^"]*'); echo "${b:-0 0 0 0}"; } # notification counts are appended to the row description
read -r _ ft _ fb <<<"$(row Fixture)"; read -r _ ct _ cb <<<"$(row Calendar)"
read -r _ p1t _ _ <<<"$(bounds "desc:Riproduci il messaggio vocale")"
p2b=$(screen | grep -o 'content-desc="Riproduci il messaggio vocale"[^>]*bounds="[^"]*"' | sed -n 2p | grep -o '[0-9]*\]"$' | tr -d ']"')
[ "$ft" -lt "${p1t:-0}" ] && [ "${p2b:-99999}" -le "$fb" ] && [ "$fb" -le "$ct" ] && [ "$ct" -lt "$cb" ] \
  && ok "the players sit inside the fixture's row, Calendar's row starts below it" \
  || ko "overlap: fixture row $ft-$fb, players $p1t-$p2b, Calendar row $ct-$cb"
p1=$(play_at 1); p2=$(play_at 2)
A shell input tap $p2
wait_until 10 copies_are 640044 && ok "bubble: the second note plays" || ko "bubble, copies after tapping the second: $(copies)"
A shell input tap $p1
wait_until 10 copies_are 480044 && ok "bubble: the first plays instead" || ko "bubble, copies after tapping the first: $(copies)"
A shell input tap $p1
wait_until 5 copies_are "" && ok "bubble: paused, no copy left" || ko "bubble, copies after pausing: $(copies)"
has "Notifiche" && ko "a tap on a player opened the pop-up" || ok "the player's taps stay in the player"
bound_home # the seeded favourites again, no preview
wipe; A uninstall $FIX >/dev/null
