emu_only "backup: uninstalls the launcher and turns the screen" || return 0
# Backup & restore through the system file picker (DocumentsUI): the file is written to Downloads, restoring it brings a
# changed setting back, this phone's own state (stack, wx) survives, and a file that is not a backup changes nothing.
BK=/sdcard/Download/ganimede-backup.json
BAD=/sdcard/Download/ganimede-bad.json
OLD=/sdcard/Download/ganimede-old-backup.json
sval() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -o "name=\"$1\">[^<]*" | cut -d'>' -f2-; } # a string pref's raw text
backup_page() { # home screen → Settings → Backup & restore
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  home_menu; tap "Impostazioni Ganimede" # not pg_settings: Settings may come back on a sub-page
  wait_until 5 has "Personalizza"; wait_until 5 settled # a swipe sent while the page still opens is lost
  A shell input swipe 150 2120 150 600 300 # one long scroll near the end of the list, as for “App nascoste”
  tapn "Backup e ripristino" 6
  wait_until 5 has "Crea un backup"
}
restore_file() { # restore_file name: Settings → Restore → pick the file → confirm
  { has "Ripristina un backup" && settled; } || backup_page # already on the page (a refused file leaves it there): no second trip
  tap "Ripristina un backup"
  pick_file "$1" || return 1
  wait_until 8 has "Ripristinare il backup?" || { ko "no confirmation dialog for $1"; return 1; }
  tap "Ripristina"
}
state_unchanged() { # this phone's own state is still what setp put there
  [ "$(sval stack)" = "101,102" ] && [ "$(sval wx)" = "$WX" ]
}

step "Backup: export writes a file to Downloads"
WX='{}' # no & or quotes: setp pastes the value through sed into the XML
seed list int fav_gap 12 string stack "101,102" string wx "$WX" \
  string folder:x '{"name":"F","where":"fav","apps":[],"widgets":[7],"bg":-2093040,"alpha":73,"cols":4}' # widget ids stay local; look travels
state_unchanged && ok "precondition: stack and wx are on the phone" || ko "precondition: stack='$(sval stack)' wx='$(sval wx)'"
A shell rm -f "$BK" "$BAD"
backup_page
tap "Crea un backup"
wait_until 10 has "SAVE" && ok "the file picker opened in save mode" || ko "the Save picker did not open"
shot 40-backup-save
tap "SAVE"
filed() { A shell "test -s $BK"; }
wait_until 10 filed && ok "ganimede-backup.json exists in Downloads" || ko "no $BK after Save"
wait_until 5 has "Backup e ripristino" || ko "did not return to the backup page"
json=$(A shell cat $BK)
echo "$json" | grep -q '"app": "ganimede-launcher"' && ok "the file says it is a Ganimede backup" || ko "no app marker in the file"
echo "$json" | tr -d ' \n' | grep -q '"fav_gap":{"t":"i","v":12}' && ok "the file holds fav_gap = 12" || ko "fav_gap missing from the file"
echo "$json" | grep -q '"stack"\|"wx"' && ko "this phone's stack/wx leaked into the file" || ok "stack and wx are not in the file"
echo "$json" | grep -q '"folder:x"' && ok "the folder is in the file" || ko "folder:x missing from the file"
[[ $json == *'\"bg\":-2093040'* && $json == *'\"alpha\":73'* && $json == *'\"cols\":4'* ]] \
  && ok "the file holds the folder's exact bg, alpha and cols overrides" || ko "folder look missing from the file"
echo "$json" | grep -q 'widgets' && ko "this phone's folder widget ids leaked into the file" || ok "folder widget ids are not in the file"

step "Backup: restore brings the changed setting back, keeps this phone's state"
setp int fav_gap 20 string folder:x '{"name":"Changed","where":"fav","apps":[],"bg":-7686920,"alpha":12,"cols":2}'
[ "$(pref fav_gap)" = 20 ] && ok "changed fav_gap to 20" || ko "fav_gap = $(pref fav_gap)"
changed_folder_look() { local f; f=$(sval folder:x | sed 's/&quot;/"/g'); [[ $f == *'"bg":-7686920'* && $f == *'"alpha":12'* && $f == *'"cols":2'* ]]; }
changed_folder_look && ok "changed all three folder look values before restore" || ko "changed folder:x = $(sval folder:x)"
restore_file ganimede-backup.json
back() { [ "$(pref fav_gap)" = 12 ]; }
wait_until 10 back && ok "fav_gap is 12 again" || ko "restore left fav_gap = $(pref fav_gap)"
shot 41-backup-restored
restored_folder_look() { local f; f=$(sval folder:x | sed 's/&quot;/"/g'); [[ $f == *'"bg":-2093040'* && $f == *'"alpha":73'* && $f == *'"cols":4'* ]]; }
wait_until 10 restored_folder_look && ok "restore returns the folder's exact bg, alpha and cols overrides" \
  || ko "restored folder:x = $(sval folder:x)"
state_unchanged && ok "stack and wx survived the restore" || ko "after restore: stack='$(sval stack)' wx='$(sval wx)'"
[ "$(A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -c 'name="onboarded" value="true"')" = 1 ] && ok "still onboarded" || ko "onboarded lost"

step "Backup: old clock-only positions migrate to the independent line"
backup_with() { # backup_with key=value...: the exported backup, without line keys, plus these integer settings
  A shell cat $BK | python3 -I -c 'import json, sys
d = json.load(sys.stdin); v = d["values"]
for k in ("line_offset", "list_line_y"): v.pop(k, None)
for a in sys.argv[1:]: k, n = a.split("="); v[k] = {"t": "i", "v": int(n)}
print(json.dumps(d))' "$@"
}
backup_with clock_offset=76 list_clock_y=-28 > "$OUT/old-backup.json" # as written before the line had its own position
A push "$OUT/old-backup.json" "$OLD" >/dev/null
restore_file ganimede-old-backup.json
old_positions() { [ "$(pref clock_offset)" = 76 ] && [ "$(pref line_offset)" = 76 ] && [ "$(pref list_clock_y)" = -28 ] && [ "$(pref list_line_y)" = -28 ]; }
wait_until 10 old_positions && ok "old backup copied both clock axes to the line" \
  || ko "old restore: clock_offset=$(pref clock_offset), line_offset=$(pref line_offset), list_clock_y=$(pref list_clock_y), list_line_y=$(pref list_line_y)"

# a line already stored on its own is kept: PreferencesTest.testLineMigrationNeverOverwritesItsOwnPosition (same restore path)
A shell rm -f "$OLD"

step "Backup: a file that is not a backup is refused and changes nothing"
setp int fav_gap 20
for bad in '{broken' '{"app":"other","version":1,"values":{"fav_gap":{"t":"i","v":12}}}'; do
  printf '%s' "$bad" > "$OUT/bad.json"; A push "$OUT/bad.json" $BAD >/dev/null
  restore_file ganimede-bad.json
  # the refusal is a Toast: uiautomator does not dump toasts, so the proof is: dialog confirmed and gone, settings page back, prefs unchanged
  wait_until 8 hasnt "Ripristinare il backup?" && wait_until 5 settled && has "Backup e ripristino" && ok "confirmed and back on the settings page: $bad" || ko "restore flow did not finish for: $bad"
  [ "$(pref fav_gap)" = 20 ] && ok "fav_gap still 20" || ko "fav_gap = $(pref fav_gap) after a refused file"
  state_unchanged && ok "stack and wx untouched" || ko "stack/wx changed: '$(sval stack)' '$(sval wx)'"
done
A shell rm -f $BAD

step "Backup: one malformed folder:* pref on this phone must not block a restore (suspected bug)"
setp string folder:bad '{broken' int fav_gap 20
restore_file ganimede-backup.json
back2() { [ "$(pref fav_gap)" = 12 ]; }
wait_until 10 back2 && ok "restore worked despite a broken folder:bad" || ko "restore refused: fav_gap = $(pref fav_gap)"
setp del folder:bad
step "Backup: restore from the first wizard step"
onboarded() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -c 'name="onboarded" value="true"'; }
first_step_restore() { # first_step_restore name: clean install → Italian → Restore → pick the file → confirm
  fresh_wizard; tap "Italiano"; expect "Benvenuto in Ganimede"
  tap "Ripristina un backup"
  [ -z "${ROTATE:-}" ] || { A shell settings put system accelerometer_rotation 0; A shell settings put system user_rotation 1; } # turn the screen with the picker open
  pick_file "$1" || return 1
  wait_until 8 has "Ripristinare il backup?" || { ko "no confirmation dialog for $1"; return 1; }
  tap "Ripristina"
}
printf '%s' '{broken' > "$OUT/bad.json"; A push "$OUT/bad.json" $BAD >/dev/null
first_step_restore ganimede-bad.json
wait_until 8 hasnt "Ripristinare il backup?" && wait_until 5 settled && has "Benvenuto in Ganimede" && ok "a bad file leaves the first step in place" || ko "bad file: not on the first step"
[ "$(onboarded)" = 0 ] && ok "onboarded still absent after a bad file" || ko "bad file marked the wizard done"
A shell rm -f $BAD
# Ganimede is not the home app on this install, so a restore goes on to the "home app" step; the restored prefs are already saved.
restored_to_home_step() { # restored_to_home_step what
  expect "Rendila la tua Home" && ok "$1: lands on the home-app step, not on the home" || ko "$1: not on the home-app step"
  wait_until 10 back && ok "$1: fav_gap is 12 from the restored backup" || ko "$1: fav_gap = $(pref fav_gap)"
  [ -n "$(sval folder:x)" ] && ok "$1: folder:x restored" || ko "$1: folder:x missing"
  hasnt "Indietro" && ok "$1: no Back on the home-app step (nothing behind it)" || ko "$1: Back shown on the home-app step"
  tap "Avanti"; wait_until 10 focus && ok "$1: home in front after the home-app step" || ko "$1: home not in front"
  hasnt "Avanti" && ok "$1: no wizard step on screen" || ko "$1: still in the wizard"
  [ "$(onboarded)" = 1 ] && ok "$1: onboarded" || ko "$1: onboarded missing"
}
first_step_restore ganimede-backup.json
restored_to_home_step "restore"

# The screen turns while the file picker is open: the wizard comes back on its first step and the restore still goes through.
ROTATE=1 first_step_restore ganimede-backup.json
restored_to_home_step "restore after a rotation"
A shell settings put system user_rotation 0; A shell settings put system accelerometer_rotation 1

A shell rm -f $BK
