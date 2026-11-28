emu_only "wizard: uninstalls the launcher" || return 0
# Fresh install -> setup wizard -> classic home. Tests the wizard itself (the other sections use `seed`).
step "Fresh install"
fresh_wizard

step "Wizard: language"
expect "Welcome to Ganimede" && shot 01-welcome
tap "Italiano"; expect "Benvenuto in Ganimede"
expect "Ripristina un backup"                 # restoring a backup is offered from the very first step
tap "Avanti"

step "Wizard: home app and notifications"
expect "Rendila la tua Home" && shot 02-home
# First setup never offers notification access (Android's "restricted setting" pop-up); features ask for it later
hasnt "Accesso alle notifiche" && ok "no notification-access row in the first setup" || ko "the wizard offers “Accesso alle notifiche” at first run"
tap "Consenti"                                # the system's own "default home app" window, inside Ganimede
tap "Ganimede"; tap "Set as default"
holder() { A shell cmd role get-role-holders android.app.role.HOME | grep -q ganimede; }
wait_until 10 holder && ok "Ganimede is the home app" || ko "home app not set"
expect "Rendila la tua Home"                  # still in the wizard afterwards
tap "Avanti"

step "Wizard: style"
expect "Scegli uno stile" && shot 03-mode
# Coming back to this step builds both miniatures again (a choice only marks its card, issue 062): the old ones must be freed,
# not kept alive by the window (GC'd view count, read on a still page).
views() { wait_until 5 settled; A shell dumpsys meminfo $PK | awk '/Views:/ {print $2; exit}'; }
v0=$(views)
for i in 1 2 3 4; do
  tap "Avanti"; wait_until 10 has "Personalizza adesso" || ko "next step not shown"
  tap "Indietro"; wait_until 10 has "Scegli uno stile" || ko "style step not shown again"
done
v1=$(views)
[ $((v1 - v0)) -lt 100 ] && ok "old miniatures freed (views $v0 → $v1)" || ko "old miniatures leak (views $v0 → $v1)"
tap "Classica"; wait_until 5 has "✓ Classica" && ok "classic chosen" || ko "classic not marked"
tap "Avanti"

step "Wizard: customise now"
expect "Personalizza adesso" && tap "Personalizza adesso"
expect "Le tue app preferite" && shot 04-favs
for app in Calendar Clock Contacts; do stap "$app" >/dev/null; done # not in the preview, so the list row is hit
# A tick changes its own row, not the whole page (issue 062): the keyboard focus stays on the row and its state flips.
A shell input keyevent KEYCODE_DPAD_DOWN; A shell input keyevent KEYCODE_DPAD_DOWN
row_focus() { screen | grep -o 'checkable="true" checked="[a-z]*"[^>]*focused="true"[^>]*bounds="[^"]*"' | head -1 | sed 's/^checkable="true" //; s/ clickable.*bounds=/ /'; }
f0=$(row_focus)   # checked="true|false" "[l,t][r,b]"
flipped() { local f; f=$(row_focus); [ -n "$f" ] && [ "${f#* }" = "${f0#* }" ] && [ "${f%% *}" != "${f0%% *}" ]; }
if [ -z "$f0" ]; then ko "no app row took the keyboard focus"
else
  A shell input keyevent KEYCODE_ENTER
  wait_until 3 flipped && ok "a tick keeps the focus on its row and flips it ($f0)" || ko "a tick lost the row's focus or state (was $f0, now $(row_focus))"
  A shell input keyevent KEYCODE_ENTER # back as it was: the three favourites above stay
fi
tap "Avanti"
expect "Stile delle icone" && shot 05-icons
# The selected tile's "✓ Originale" and the two-line "Quadrato arrotondato" are drawn whole, not cut after the tick (issue 088)
label_whole "✓ Originale" 80 && ok "tile “✓ Originale” shows its whole label" || ko "tile “✓ Originale” is cut"
hsee "Quadrato arrotondato" "✓ Originale" && tap "Quadrato arrotondato"
wait_until 5 has "✓ Quadrato arrotondato" && label_whole "✓ Quadrato arrotondato" 80 70 && ok "tile “Quadrato arrotondato” shows its whole label" || ko "tile “✓ Quadrato arrotondato” is cut"
stap "Squircle" # the Plate row, under the Style row (issue 078)
squircle() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -q "<string name=\"icon_container\">squircle</string>"; }
wait_until 5 squircle && ok "the wizard saves the Squircle plate" || ko "the wizard saved no Squircle plate"
tap "Avanti"
expect "Ognuno è mostrato" && shot 06-font; tap "Avanti"
expect "Nove stili" && shot 07-clock; tap "Avanti"
expect "Il pulsante Ganimede" && tap "Usa il pulsante Ganimede"; shot 08-button
tap "Avanti"
expect "Tutto pronto" && shot 09-done
tap "Inizia"

step "Classic home"
wait_until 10 focus && ok "home in front" || ko "home not in front"
shot 10-classic-home
wait_until 5 has "Clock" && ok "home grid shows chosen apps" || ko "chosen apps missing from the grid"
