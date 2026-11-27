emu_only "perms: revokes the notification listener" || return 0
# Features that need special access (notifications) explain the restricted setting before sending the person to Android.
# Every step runs with the notification listener revoked, as on a first install.
nonotif() { A shell cmd notification disallow_listener $PK/$PK.NotifService >/dev/null 2>&1; }
# perm_settings page: classic home -> Ganimede settings -> the page (the "Finish setting up" box can push its row far down)
perm_settings() {
  nonotif; seed classic
  A shell input swipe 540 1900 540 1900 1500; tap "Impostazioni Ganimede"
  expect "Tocca una parte dell'anteprima per regolarla"; tapn "$1" 8
}
# one node (the dialog message is one) whose text holds all the fragments in order (the dump wraps a text holding " in single quotes)
shows() { local f re='text=["'"'"'][^>]*'; for f in "$@"; do re="$re$f[^>]*"; done; screen | grep -q "$re"; }
noshows() { ! shows "$@"; }
bpref() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "name=\"$1\" value=\"true\""; }
NP="Anteprima delle notifiche sotto i preferiti"

step "Notification preview: asking for access explains the restricted setting first"
perm_settings "Notifiche e benessere"
see "$NP" && tap "$NP"
wait_until 5 shows "Consenti impostazioni con restrizioni" && ok "the explanation mentions “Consenti impostazioni con restrizioni”" || ko "no explanation of restricted settings when turning on the preview"

step "Notification preview: the explanation gives numbered steps ending in App info"
shows "1\." "Info app" && ok "the explanation lists steps (“1.” … “Info app”)" || ko "the explanation has no numbered steps (“1.” … “Info app”)"

step "Notification preview: Cancel leaves the switch off and the pref false"
tap "Annulla"; wait_until 5 noshows "Consenti impostazioni con restrizioni"
bpref notif_preview && ko "Cancel left notif_preview = true" || ok "Cancel: notif_preview is not true"
unchecked() { # the Switch on the preview row (same height as its label) is off; other switches on the page do not count
  local y; read -r _ y <<<"$(cx "Anteprima delle notifiche sotto i preferiti")" || return 1
  screen | grep -o 'class="android.widget.Switch"[^>]*' | while read -r n; do
    set -- $(echo "$n" | grep -o 'bounds="[^"]*"' | tr -c '0-9\n' ' ')
    [ "$2" -le "$y" ] && [ "$4" -ge "$y" ] && echo "$n"
  done | grep -q 'checked="false"'
}
wait_until 5 unchecked && ok "Cancel: the switch shows off" || ko "Cancel: the switch still shows on"

step "Media player is on without access: its page says why it is missing"
perm_settings "Calendario e produttività"
see "Lettore musicale nella schermata Home"
has "Accesso alle notifiche" && ok "the Music card shows the notification-access row" || ko "the Music card hides that the player needs notification access"

step "Menu: Notification summary on an app without access shows the explanation"
nonotif; seed list
read -r mx my <<<"$(cx Calendar)"; A shell input draganddrop "$mx" "$my" "$mx" "$my" 1500 # long-press: the app's menu
expect "Rimuovi dai preferiti"; stap "Riepilogo notifiche"
wait_until 5 shows "Consenti impostazioni con restrizioni" && ok "the menu's summary asks with the explanation" || ko "the menu's “Riepilogo notifiche” gave no explanation"

# Regression guards: the code already did this when they were written.
step "Quick Lock: Cancel in the explanation leaves quick_lock false"
perm_settings "Gesti"
see "Blocco rapido" && tap "Blocco rapido"
wait_until 5 shows "Consenti impostazioni con restrizioni" && ok "Quick Lock explains the restricted setting" || ko "Quick Lock gave no explanation"
tap "Annulla"; wait_until 5 noshows "Consenti impostazioni con restrizioni"
bpref quick_lock && ko "Cancel left quick_lock = true" || ok "Cancel: quick_lock is not true"

step "Menu: Cancel in the summary's explanation leaves summary and summary_apps untouched"
nonotif; seed list
read -r mx my <<<"$(cx Calendar)"; A shell input draganddrop "$mx" "$my" "$mx" "$my" 1500
expect "Rimuovi dai preferiti"; stap "Riepilogo notifiche"
wait_until 5 shows "Consenti impostazioni con restrizioni"
tap "Annulla"; wait_until 5 noshows "Consenti impostazioni con restrizioni"
bpref summary && ko "Cancel left summary = true" || ok "Cancel: summary is not true"
A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -A3 'name="summary_apps"' | grep -q "com.google.android.calendar" \
  && ko "Cancel left Calendar in summary_apps" || ok "Cancel: Calendar is not in summary_apps"

step "Permission: closing the system dialog with Back is not a refusal, so the next tap asks again (no App info)"
A shell pm revoke $PK android.permission.READ_CONTACTS; A shell pm clear-permission-flags $PK android.permission.READ_CONTACTS user-set user-fixed
setp del asked:android.permission.READ_CONTACTS
nonotif; seed classic
A shell input swipe 540 1900 540 1900 1500; tap "Impostazioni Ganimede"
expect "Tocca una parte dell'anteprima per regolarla"
A shell input swipe 150 2120 150 600 300 # one long scroll: the Privacy row is near the end of the list
tapn "Privacy e permessi" 6
see "Contatti" && tap "Contatti"
asking() { screen | grep -q 'package="[a-z.]*permissioncontroller"'; }
wait_until 8 asking && ok "the permission dialog opens" || ko "no permission dialog for Contacts"
A shell input keyevent KEYCODE_BACK; wait_until 5 has "Contatti"
tap "Contatti"
wait_until 8 asking && ok "the second tap asks again" || ko "the second tap did not ask again (App info?)"
A shell input keyevent KEYCODE_BACK
A shell pm grant $PK android.permission.READ_CONTACTS; A shell pm revoke $PK android.permission.READ_CONTACTS
