emu_only "popup: grants the notification listener" || return 0
# Notification pop-up. Posts come from `cmd notification post` (package com.android.shell, label "Shell"),
# the second app is the Clock timer. The shell command cannot set a category or a visibility, so a secret notification and an
# alarm-category one cannot be posted from here (see the notes at the two steps that would need them).
A shell cmd notification allow_listener $PK/$PK.NotifService >/dev/null 2>&1
bound() { A shell dumpsys notification | sed -n '/Live notification listeners/,/Snoozed notification listeners/p' | grep -q NotifService; }
tagged() { A shell dumpsys notification | grep -q "tag=$1 "; }                 # still in the system's list
untagged() { ! tagged "$1"; }
headers() { dump | grep -o "text=\"$1\"" | wc -l; }                            # how many group headers a name has
popup() { home_menu "Notifiche"; tap "Notifiche"; }       # long-press an empty spot (list mode)
# The pop-up is a snapshot; Android can list a post before the listener's throttled read has reached Ganimede.
_popup_has() { popup; has "$1" && return 0; A shell input keyevent KEYCODE_BACK; wait_until 3 hasnt "Notifiche"; return 1; }
expect_popup() {
  wait_until 10 _popup_has "$1" && { ok "sees “$1”"; return 0; }
  popup; ko "doesn't see “$1”"; return 1
}
post() { A shell cmd notification post -t "$1" "$2" "$3" >/dev/null; }         # post title tag word
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
  # a drag, not a flick: the card moves only on the finger's moves and judges the throw on the last one, and a busy
  # emulator sends few of them in 200 ms (run 46081674535: the card stayed)
  A shell input swipe 150 "$sy" 1000 "$sy" 600
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
setp del summary_held boolean summary true set summary_apps com.android.shell # del: no key held by an earlier run
wait_until 10 listening; wait_until 10 bound
c0=$(pref summary_count); c0=${c0:-0}
SUM=sum$(date +%s) # a fresh key: Android keeps an earlier run's snoozed key silent for hours, so a repeated tag is never seen
post "TitoloRiep" $SUM uno
summary_counted() { [ "$(pref summary_count)" = $((c0 + 1)) ]; }
wait_until 8 summary_counted && ok "the post was snoozed for the summary (counter +1)" || ko "post not held back by the summary"
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
# (FIX is in lib.sh) e2e/fixture: posts a MessagingStyle with two silent WAVs of 30 s and 40 s from its own provider
fixture_install && ok "fixture app built and installed" \
  || ko "fixture app not built or installed (./gradlew -Pe2e :fixture:assembleDebug): the voice checks below can't pass"
A shell pm grant $FIX android.permission.POST_NOTIFICATIONS
voice() { A shell am broadcast -n $FIX/.Post "$@" >/dev/null; fx() { A shell dumpsys notification | grep -q "pkg=$FIX"; }; wait_until 10 fx; }
players() { screen | grep -o 'text="\(Messaggio vocale\|[0-9]:[0-9][0-9] / [0-9]:[0-9][0-9]\)"' | cut -d'"' -f2 | paste -sd'|'; }
nplayers() { [ "$(players | awk -F'|' '{print NF}')" = "$1" ]; }
play_at() { screen | grep -o 'content-desc="Riproduci il messaggio vocale"[^>]*bounds="[^"]*"' | sed -n "$1p" \
  | grep -o '[0-9]*' | tail -4 | paste -sd' ' | awk '{print int(($1 + $3) / 2), int(($2 + $4) / 2)}'; }
# What plays is told by the row that shows "m:ss / m:ss" (only a row whose player is ready does), as the size its note's
# WAV has (header 44 + 16000 bytes a second): one note at a time, none once playback stops. The notes are played from
# the copy kept on arrival, so there is no other file to look at.
copies() { screen | grep -o 'text="[0-9]:[0-9][0-9] / [0-9]:[0-9][0-9]"' | sed 's/.*\/ \([0-9]\):\([0-9][0-9]\)"/\1 \2/' \
  | awk '{print 44 + 16000 * ($1 * 60 + $2)}' | paste -sd' '; }
copies_are() { [ "$(copies)" = "$1" ]; }
# A tap while the note loads does nothing ("Loading…", run 46083578007): a note plays once its row shows its length and the
# app's player is started in the audio service's list.
uid=$(A shell cmd package list packages -U $PK | grep -o 'uid:[0-9]*' | head -1 | cut -d: -f2)
plays() { copies_are "$1" && A shell dumpsys audio | grep -q "u/pid:$uid/.*state:started"; }
voice; expect_popup "FIXTURE"
wait_until 5 eval '[ "$(players)" = "Messaggio vocale|Messaggio vocale" ]' && ok "pop-up: two players" || ko "pop-up players: $(players)"
shot 41-popup-voice
target48 "desc:Riproduci il messaggio vocale" && ok "the voice play button is a 48 dp target" || ko "the voice play button is under 48 dp" # issue 064
target48 "desc:Ignora" && ok "the card's dismiss ✕ is a 48 dp target" || ko "the dismiss ✕ is under 48 dp" # issue 064
p1=$(play_at 1); p2=$(play_at 2)
A shell input tap $p2
wait_until 10 plays 640044 && ok "the second note (40 s) plays, alone" || ko "copies after tapping the second: $(copies)"
A shell input tap $p1
wait_until 10 plays 480044 && ok "the first (30 s) plays instead of the second" || ko "copies after tapping the first: $(copies)"
A shell input tap $p1
wait_until 5 copies_are "" && ok "paused: no row plays" || ko "copies after pausing: $(copies)"
read -r ax ay <<<"$(cx Anna)"; A shell input swipe "$ax" "$ay" "$ax" "$ay" 1500   # long-press the card: the message on its own
wait_until 5 nplayers 2 && ok "long-press view: two players" || ko "long-press view players: $(players)"
A shell input keyevent KEYCODE_BACK; wait_until 5 has "Cancella tutte"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
wipe; voice --ez secret true; expect_popup "FIXTURE"
nplayers 0 && ok "secret: no player" || ko "secret notification shows players: $(players)"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"

step "Voice notes after text and links: a player each, each plays (fixture app)"
# Telegram re-posts one chat notification with a message more at each arrival: the fixture does the same, shape by shape
# (Post.java: a text with a link before the notes, no link, a bare link right before note A, a link then one note, the
# app revoking its earlier notes' grants before each re-post, a link carrying a preview image). Issue 083.
kept() { A exec-out run-as $PK sh -c 'ls no_backup/voice 2>/dev/null' | grep -c .; } # notes copied on arrival
kept_is() { [ "$(kept)" = "$1" ]; }
play_note() { # $1: where, $2: which player, $3: its copy's size
  local p; p=$(play_at "$2"); A shell input tap $p
  # "plays", not copies_are: the copy is whole while the player still loads, and a pause tap then does nothing, so
  # the note kept playing and note 2's button was not there to tap (run 46105280536: "copies after tapping note 2")
  wait_until 10 plays "$3" && ok "$1: note $2 plays" || ko "$1: copies after tapping note $2: $(copies)"
  A shell input tap $p; wait_until 5 copies_are "" || ko "$1: note $2 did not pause: $(copies)"
}
check_voice() { # $1: where, $2: players expected, $3: 1 if the chat has a link
  wait_until 5 nplayers "$2" && ok "$1: $2 players" || ko "$1: players: $(players)"
  [ "$3" = 1 ] && { has "Apri link" && ok "$1: Open link" || ko "$1: no Open link"; }
  play_note "$1" 1 480044; [ "$2" = 2 ] && play_note "$1" 2 640044
}
for shape in a b c d e f; do
  wipe; wait_until 10 kept_is 0
  case $shape in b) chat=tnn;; d) chat=tn;; *) chat=ttnn;; esac # t: a text, n: a note
  for k in $(seq 1 ${#chat}); do # each note kept before the next message arrives, as on a phone
    notes=$(echo "${chat:0:$k}" | tr -cd n | wc -c)
    voice --es shape $shape --ei upto "$k"; wait_until 10 kept_is "$notes" || ko "$shape, message $k: kept notes $(kept), not $notes"
  done
  want=$(echo "$chat" | tr -cd n | wc -c); link=1; [ $shape = b ] && link=0
  expect_popup "FIXTURE"
  shot "43-popup-voice-$shape"
  check_voice "$shape, pop-up" $want $link
  read -r ax ay <<<"$(cx Anna)"; A shell input swipe "$ax" "$ay" "$ax" "$ay" 1500
  check_voice "$shape, long-press" $want $link
  A shell input keyevent KEYCODE_BACK; wait_until 5 has "Cancella tutte"
  A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
done
kept_is 2 && ok "the last chat's two notes are kept privately" || ko "kept notes: $(kept)"
wipe; wait_until 10 kept_is 0 && ok "notification cancelled: its kept notes are deleted" || ko "kept notes after cancelling: $(kept)"
voice; wait_until 10 kept_is 2; voice --ez secret true
wait_until 10 kept_is 0 && ok "the chat turned secret: its kept notes are deleted" || ko "kept notes of a secret chat: $(kept)"

step "A note playing while its copy is kept and the chat is re-posted: its row stays on Pause (fixture app)"
wipe; wait_until 10 kept_is 0
voice --es shape s; expect_popup "FIXTURE"   # the slow note takes 5 s to copy: note A is not kept yet
wait_until 5 nplayers 2 || ko "players before playing: $(players)"
A shell input tap "$(play_at 2)"
wait_until 10 plays 480044 || ko "note A did not play (copied from the chat app): $(copies)"
wait_until 15 kept_is 2 || ko "the chat's notes were not kept while A plays: $(kept)"
voice --es shape s   # the re-post rebuilds the rows, now with kept copies
paused_rows() { screen | grep -c 'content-desc="Pausa"'; }
wait_until 10 eval '[ "$(paused_rows)" = 1 ]' && plays 480044 \
  && ok "A is still playing and its row still shows Pause after the re-post" || ko "the row lost its playing state: $(paused_rows) on Pause, $(copies)"
A shell input keyevent KEYCODE_BACK; wait_until 5 has "Cancella tutte"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"

step "Voice notes posted while the listener was off are kept when it reconnects (fixture app)"
wipe; voice; wait_until 10 kept_is 2 || ko "kept notes before the listener goes off: $(kept)"
A shell cmd notification disallow_listener $PK/$PK.NotifService >/dev/null 2>&1; wait_until 10 eval '! bound'
wait_until 10 kept_is 0 && ok "access off: the kept notes are deleted" || ko "kept after access went off: $(kept)"
voice --es shape e --ei upto 3 # text, link, note A: nobody listening
# no event marks "nothing copied": a bound listener's copy of note A takes well under these 3 s
wait_until 3 eval '! kept_is 0' && ko "kept while not listening: the listener was still bound ($(kept))" || ok "nothing kept while not listening"
A shell cmd notification allow_listener $PK/$PK.NotifService >/dev/null 2>&1; wait_until 10 bound
wait_until 10 kept_is 1 && ok "reconnected: note A is kept" || ko "kept notes after reconnecting: $(kept)"
voice --es shape e --ei upto 4; wait_until 10 kept_is 2 # B arrives, A's grant withdrawn
expect_popup "FIXTURE"
check_voice "reconnected, pop-up" 2 1
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"

step "A voice note whose stream never ends stalls nothing and leaves nothing (fixture app)"
wipe; wait_until 10 kept_is 0
voice --es shape h; post TitoloDopo tagdopo parola
expect_popup "TitoloDopo" # the notifications are still read while that copy hangs
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Notifiche"
wait_until 20 kept_is 0 && ok "the endless copy was cut off, no half left" || ko "kept after the endless copy: $(A exec-out run-as $PK sh -c 'ls -l no_backup/voice')"
voice # the chat's notification again, now with two good notes: the copying thread is free
wait_until 10 kept_is 2 && ok "after the cut-off, new notes are kept again" || ko "kept after the cut-off: $(kept)"

step "A note still being copied when its notification goes leaves no copy behind (fixture app)"
wipe; wait_until 10 kept_is 0
voice --es shape s; wait_until 5 kept_is 1 || ko "the slow note's copy did not start: $(kept)"
A shell am broadcast -n $FIX/.Post --ez cancel true >/dev/null # gone while the copy runs (about 5 s)
wait_until 15 kept_is 0 && ok "the copy that ended after its notification went is deleted" || ko "kept after the notification went: $(kept)"

step "A note whose app does not open it stalls nothing; that app's notes wait until the open returns (fixture app)"
wipe; wait_until 10 kept_is 0
voice --ei id 3; wait_until 10 kept_is 2 || ko "the other chat's notes were not kept: $(kept)"
voice --es shape x # its open fails only after 20 s
# Android holds a post for the assistant and lets a later cancel overtake it: cancelled only once it is posted, not enqueued
fixture_posted() { A shell dumpsys notification | sed -n '/^  Notification List:/,/^  [^ ]/p' | grep -q "pkg=$FIX user=[^ ]* id=$1 "; }
wait_until 5 fixture_posted 1 || ko "the hung note's notification was not posted"
voice --ei id 3 --ez cancel true # its sweep is queued behind that open
wait_until 15 kept_is 0 && ok "the queue gave up the hung open at about 11 s, not 20: the sweep behind it ran" || ko "the sweep waited for the hung open: $(kept)"
voice # the chat again, two good notes, queued after the open was given up
# no event marks "skipped": without the bound they are copied at once, well within 5 s (and before the open returns at 20 s)
wait_until 5 eval '! kept_is 0' && ko "copied while the app's open still hangs: $(kept)" || ok "the app's next notes are not copied while its open hangs"
wait_until 20 eval 'voice; kept_is 2' && ok "re-posted until its open returned: the app's notes are kept again" || ko "kept after the open returned: $(kept)"

step "Summary holds a chat with voice notes: they are kept before it is snoozed (fixture app)"
wipe; wait_until 10 kept_is 0
setp del summary_held boolean summary true boolean summary_skip_chats false set summary_apps $FIX
wait_until 10 listening; wait_until 10 bound
c0=$(pref summary_count); c0=${c0:-0}
NID=$((RANDOM + 2)) # a fresh id: Android keeps a snoozed key silent for hours
A shell am broadcast -n $FIX/.Post --es shape s --ei id $NID >/dev/null # snoozing takes the chat app's grant away (issue 083)
wait_until 8 summary_counted && ok "the chat was held for the summary" || ko "the chat was not held back by the summary"
wait_until 15 kept_is 2 && ok "both its notes were kept before it was snoozed" || ko "kept notes of the held chat: $(kept)"
# Not checked: the app cancelling it while held. Android still lists it as snoozed until the hold ends (SnoozeHelper.cancel
# only marks it), so its notes stay until then; the next step's access off deletes them.
bound_home

step "Notification access off: kept voice notes are wiped when the home starts"
wipe
A shell cmd notification disallow_listener $PK/$PK.NotifService >/dev/null 2>&1; wait_until 10 eval '! bound'
wait_until 10 kept_is 0 || ko "kept after access went off: $(kept)" # that deletion done before the leftover is planted
A shell "run-as $PK sh -c 'mkdir -p no_backup/voice && echo x > no_backup/voice/left'"   # left while the home was not running
kept_is 1 || ko "the leftover copy was not planted: $(kept)"
A shell am force-stop $PK; start_home
wait_until 10 kept_is 0 && ok "access off: no kept note survives the home's start" || ko "kept with access off: $(kept)"
A shell cmd notification allow_listener $PK/$PK.NotifService >/dev/null 2>&1; wait_until 10 listening; wait_until 10 bound

step "Favourite bubble (List, preview on): a player per voice note under the fixture's name, each plays, no overlap"
wipe; voice
setp string fav "$FIX/$FIX.Open&#10;com.google.android.calendar/com.android.calendar.AllInOneActivity&#10;com.google.android.deskclock/com.android.deskclock.DeskClock&#10;com.google.android.contacts/com.android.contacts.activities.PeopleActivity"
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
wait_until 10 plays 640044 && ok "bubble: the second note plays" || ko "bubble, copies after tapping the second: $(copies)"
A shell input tap $p1
wait_until 10 plays 480044 && ok "bubble: the first plays instead" || ko "bubble, copies after tapping the first: $(copies)"
A shell input tap $p1
wait_until 5 copies_are "" && ok "bubble: paused, no row plays" || ko "bubble, copies after pausing: $(copies)"
has "Notifiche" && ko "a tap on a player opened the pop-up" || ok "the player's taps stay in the player"
bound_home # the seeded favourites again, no preview
wipe; A uninstall $FIX >/dev/null
