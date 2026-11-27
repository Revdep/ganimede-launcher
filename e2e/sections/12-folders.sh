# Folders, the app menu and the hidden-apps round trip; in Classic the menu acts on grid and dock.
# Local helpers (could move to lib.sh): pstr, nfold, hold, clear_text.
pstr() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -o "name=\"$1\">[^<]*" | sed 's/^[^>]*>//'; } # a string pref, XML-escaped
nfold() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -c 'name="folder:'; }                         # stored folders
fid() { A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -o 'name="folder:[0-9]*' | head -1 | grep -o '[0-9]*$'; }
APPS="Calendar:com.google.android.calendar Clock:com.google.android.deskclock Contacts:com.google.android.contacts"

step "Folders: made by a drop, renamed, opened"
seed classic
read -r ax ay <<<"$(cx Clock)"; read -r tx ty <<<"$(cx Contacts)"
A shell input draganddrop "$ax" "$ay" "$tx" "$ty" 1500
wait_until 5 has "Cartella"
[ "$(nfold)" = 1 ] && ok "one folder stored" || ko "folders stored: $(nfold)"
id=$(fid)
read -r fx fy <<<"$(cx Cartella)"; hold "$fx" "$fy"
tap "Rinomina"; wait_until 5 has "Salva"
tap "Cartella" # focus the field (the grid label behind the dialog is hidden)
clear_text; A shell input text Lavoro; tap "Salva"
wait_until 5 has "Lavoro"
has "Lavoro" && hasnt "Cartella" && ok "rename: the grid shows “Lavoro”, not “Cartella”" || ko "folder name on the grid after rename"
pstr "folder:$id" | grep -q 'Lavoro' && ok "the new name is stored" || ko "folder:$id = $(pstr "folder:$id")"
tap "Lavoro"; expect "Clock"; expect "Contacts"; shot 120-folder-renamed
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Clock"

step "Folders: delete returns the apps"
read -r fx fy <<<"$(cx Lavoro)"; hold "$fx" "$fy"
tap "Elimina cartella"
wait_until 5 has "Contacts"
has "Clock" && has "Contacts" && hasnt "Lavoro" && ok "delete: Clock and Contacts are back on the grid, the folder is gone" || ko "after delete: Clock/Contacts back, folder gone?"
[ "$(nfold)" = 0 ] && ok "the folder is no longer stored" || ko "folders stored after delete: $(nfold)"
shot 121-folder-deleted

step "Folders: a deleted folder's key is not revived by a drop on its empty grid spot"
seed classic; setp int grid_cols 4
g=$(pstr grid); g2="${g%\}},&quot;13&quot;:&quot;f:777&quot;}" # cell 13: right of Contacts (cells 10-12), a folder id that no longer exists
setp string grid "${g2//&/\\&}"
read -r ax ay <<<"$(cx Calendar)"; read -r _ cy <<<"$(cx Contacts)"
colw=$(((W - 2 * $(dp 8)) / 4)); tx=$(($(dp 8) + colw + colw / 2)) # column 2 of Contacts' row
A shell input draganddrop "$ax" "$ay" "$tx" "$cy" 1500
right() { read -r x _ <<<"$(cx Calendar)"; [ -n "$x" ] && near "$x" "$tx" "$(dp 24)"; }
wait_until 5 right && ok "Calendar sits on the empty spot" || ko "Calendar not on the empty spot"
[ "$(nfold)" = 0 ] && hasnt "Cartella" && ok "no folder revived on the grid" || ko "a folder came back: stored $(nfold)"

step "Folders: same for the dock"
seed classic
setp string dock "f:777"
read -r ax ay <<<"$(cx Calendar)"
A shell input draganddrop "$ax" "$ay" "$((W / 10))" "$((H - $(dp 90)))" 1500
wait_until 5 hasd "Calendar" # (the grid icon's label is text, the dock icon's is a content-desc)
docked() { read -r _ y <<<"$(cx "desc:Calendar")"; [ -n "$y" ] && [ "$y" -gt $((H - $(dp 200))) ]; }
wait_until 5 docked && ok "Calendar is in the dock slot" || ko "Calendar not docked"
[ "$(nfold)" = 0 ] && hasnt "Cartella" && ok "no folder revived in the dock" || ko "a folder came back: stored $(nfold)"

step "Folders: one made on page 2 lands in the pressed cell of page 2"
seed classic; setp int grid_pages 2; wait_until 5 has Calendar
A shell input swipe $((W - $(dp 40))) $((H / 2)) $(dp 40) $((H / 2)) 250; wait_until 5 hasnt Calendar; wait_until 5 settled
hold $((W / 2)) $((H / 2))
tap "Crea cartella pop-up"; wait_until 5 has "Nome cartella"
A shell input text Pag2; tap "Crea"; wait_until 5 has "Più tardi" # "add apps now?": Back says no and opens nothing
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Più tardi"; wait_until 5 settled
wait_until 5 has Pag2 && hasnt Calendar && ok "the folder is on page 2" || ko "no “Pag2” on page 2"
read -r px _ <<<"$(cx Pag2)"; near "${px:-0}" $((W / 2)) $((W / 10)) && ok "in the pressed cell" || ko "Pag2 at x=$px, pressed $((W / 2))"
A shell input swipe $(dp 40) $((H / 2)) $((W - $(dp 40))) $((H / 2)) 250; wait_until 5 has Calendar
hasnt Pag2 && ok "page 1 has no “Pag2”" || ko "“Pag2” went to page 1"

step "Folders: Settings defaults, one-folder look, then changed defaults"
seed classic
setp string folder:look '{"name":"Tre","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}'
setp string folder:other '{"name":"Altra","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}'
setp string fav 'f:look\&#10;f:other'
setp del grid
LAVENDER=-4609803 # #FFB9A8F5
SKY=-7686920      # #FF8AB4F8

folder_columns() { # folder_columns 1|2|3: the three apps occupy that many columns
  local want=$1 x1 y1 x2 y2 x3 y3
  read -r x1 y1 <<<"$(cx "desc:Calendar")"; read -r x2 y2 <<<"$(cx "desc:Clock")"; read -r x3 y3 <<<"$(cx "desc:Contacts")"
  [ -n "$x1" ] || return 1
  case $want in
    1) near "$x1" "$x2" "$(dp 4)" && near "$x2" "$x3" "$(dp 4)" && [ "$y1" -lt "$y2" ] && [ "$y2" -lt "$y3" ] ;;
    2) [ "$y1" = "$y2" ] && [ "$y2" -lt "$y3" ] ;;
    3) [ "$y1" = "$y2" ] && [ "$y2" = "$y3" ] ;;
  esac
}
folder_pixel() { # folder_pixel name: RGB safely inside the card's left padding, beside its title
  local b x1 y1 x2 y2
  b=$(bounds "$1"); set -- $b; x1=${1:-}; y1=${2:-}; x2=${3:-}; y2=${4:-}
  [ -n "$x1" ] || return 1
  shot 126-folder-look
  magick "$OUT/126-folder-look.png" -crop "1x1+$((x1 - $(dp 10)))+$(((y1 + y2) / 2))" -depth 8 txt:- \
    | sed -n 's/.*#\([0-9A-Fa-f]\{6\}\).*/#\1/p' | tr '[:lower:]' '[:upper:]'
}
folder_settings() { # home → Personalizza → Cartelle (own pill), from a fresh Settings (a reopened one keeps its scroll)
  start_home; wait_until 5 settled
  # A long-press on a home that has just restarted can be lost: press again until the home menu shows.
  for _ in 1 2 3; do A shell input swipe 540 1900 540 1900 1500; wait_until 3 has "Impostazioni Ganimede" && break; done
  tap "Impostazioni Ganimede"; tap "Personalizza"
  wait_until 5 has "Dock"
  pill "Cartelle"
  tap "Cartelle"; see "Opacità dello sfondo (%)"; wait_until 5 hasd "Automatico"
}
last_desc_center() { # center of the last visible node with this description (folder colours repeat the theme colours)
  local b
  b=$(screen | grep -o "content-desc=\"$1\"[^>]*bounds=\"[^\"]*\"" | tail -1 \
    | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' ')
  [ -n "$b" ] || return 1; set -- $b; echo $((($1 + $3) / 2)) $((($2 + $4) / 2))
}
press_desc() { # press_desc description [times]
  local xy n=${2:-1}; xy=$(find_desc "$1") || return 1; set -- $xy
  while [ "$n" -gt 0 ]; do A shell input tap "$1" "$2"; n=$((n - 1)); done
}
checked() { screen | grep -o "content-desc=\"$1\"[^>]*" | grep -q 'checked="true"'; }
folder_value() { pstr "folder:$1" | sed 's/&quot;/"/g' | grep -q "\"$2\":$3"; }

# First defaults: Lavender, 93% (one step below the 94% default with light text) and three columns, all through the Settings card.
folder_settings
# issue 064: the settings' pills, − / + buttons and slider rows are 48 dp touch targets
target48 "Cartelle" && ok "Personalizza's pills are 48 dp high" || ko "Personalizza's pill “Cartelle” is under 48 dp"
find_desc "Colonne: Diminuisci" >/dev/null
target48 "desc:Colonne: Diminuisci" && target48 "desc:Colonne: Aumenta" && ok "− and + are 48 dp targets" || ko "− / + are under 48 dp"
b=$(bounds "desc:Colonne"); set -- ${b:-0 0 0 0}
[ $(($4 - $2)) -ge $(($(dp 48) - 1)) ] && ok "the Colonne slider row is 48 dp high" || ko "the Colonne slider is $(($4 - $2)) px high, under 48 dp"
if xy=$(last_desc_center "Lavanda"); then set -- $xy; A shell input tap "$1" "$2"; else ko "no folder Lavender control"; fi
press_desc "Opacità dello sfondo (%): Diminuisci"
press_desc "Colonne: Aumenta" 2
first_defaults() { [ "$(pref folder_bg)" = "$LAVENDER" ] && [ "$(pref folder_alpha)" = 93 ] && [ "$(pref folder_cols)" = 3 ]; }
wait_until 5 first_defaults && ok "Settings controls saved Lavender, 93% and three folder columns" \
  || ko "folder defaults: bg=$(pref folder_bg), alpha=$(pref folder_alpha), cols=$(pref folder_cols)"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus
tap Tre; wait_until 5 hasd Calendar
wait_until 5 folder_columns 3 && ok "first defaults render Tre in three columns" \
  || ko "first defaults did not render three columns"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt Calendar

# Give Tre all three overrides with Cielo, a tile visible without scrolling the dialog's colour strip. The checked
# accessibility state must move with the colour tick.
read -r fx fy <<<"$(cx Tre)"; hold "$fx" "$fy"
wait_until 5 has "Personalizza cartella" && tap "Personalizza cartella"
wait_until 5 checked "Lavanda" && ok "folder colour starts selected on inherited Lavender" \
  || ko "inherited Lavender is not checked"
if xy=$(cx "desc:Cielo"); then set -- $xy; A shell input tap "$1" "$2"; else ko "no folder Cielo control"; fi
wait_until 5 checked "Cielo" && ok "tapping Cielo moves the folder colour selection to Cielo" \
  || ko "Cielo is not checked after tapping it"
press_desc "Opacità dello sfondo (%): Diminuisci"
press_desc "Colonne: Diminuisci" 2
override_saved() { folder_value look bg "$SKY" && folder_value look alpha 92 && folder_value look cols 1; }
wait_until 5 override_saved && ok "Tre stores Sky, 92% and one column as its own look" \
  || ko "Tre look is $(pstr folder:look)"
tap "Chiudi"; wait_until 5 has Tre
tap Tre; wait_until 5 hasd Calendar
wait_until 5 folder_columns 1 && ok "Tre renders its one-column override before defaults change" \
  || ko "Tre did not render its one-column override"
tre_override_pixel=$(folder_pixel Tre)
[ -n "$tre_override_pixel" ] && ok "Tre renders its Sky 92% background before defaults change" \
  || ko "Tre override background pixel is missing"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt Calendar

# Change every default again through Settings. Tre must stay put; Altra must follow all three.
folder_settings
if xy=$(last_desc_center "Automatico"); then set -- $xy; A shell input tap "$1" "$2"; else ko "no folder Automatico control"; fi
press_desc "Opacità dello sfondo (%): Aumenta" 7
press_desc "Colonne: Diminuisci"
second_defaults() { [ "$(pref folder_bg)" = 0 ] && [ "$(pref folder_alpha)" = 100 ] && [ "$(pref folder_cols)" = 2 ]; }
wait_until 5 second_defaults && ok "Settings controls changed the defaults to Automatic, 100% and two columns" \
  || ko "changed defaults: bg=$(pref folder_bg), alpha=$(pref folder_alpha), cols=$(pref folder_cols)"
A shell input keyevent KEYCODE_HOME; wait_until 5 focus

tap Tre; wait_until 5 hasd Calendar
wait_until 5 folder_columns 1 && ok "Tre keeps its one-column override after defaults change" \
  || ko "Tre followed the changed default columns"
pixel=$(folder_pixel Tre)
[ "$pixel" = "$tre_override_pixel" ] && ok "Tre keeps its Sky 92% background after defaults change" \
  || ko "Tre background pixel changed $tre_override_pixel → ${pixel:-missing}"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt Calendar

tap Altra; wait_until 5 hasd Calendar
wait_until 5 folder_columns 2 && ok "Altra follows the changed two-column default" \
  || ko "Altra did not follow the changed default columns"
pixel=$(folder_pixel Altra)
[ "$pixel" = "#201A33" ] && ok "Altra follows the changed automatic background at 100%" \
  || ko "Altra background pixel is ${pixel:-missing}, expected #201A33 (automatic colour, light text)"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt Calendar

# The same shared folder renderer must work in List mode too.
setp string mode list
tap Altra; wait_until 5 hasd Calendar
wait_until 5 folder_columns 2 && ok "List renders Altra's two-column folder look" \
  || ko "List did not render Altra's two-column look"
pixel=$(folder_pixel Altra)
[ "$pixel" = "#201A33" ] && ok "List renders Altra's automatic folder background" \
  || ko "List Altra background pixel is ${pixel:-missing}, expected #201A33 (automatic colour, light text)"
A shell input keyevent KEYCODE_BACK

step "Folders: an untouched one-column folder keeps its 24 dp margins and wraps its content"
seed classic
# Up to 40 installed (15 rows already overflow the screen) launcher activities as "package/class" (query-activities --brief may print ".Class" short names)
comps=$(A shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  | tr -d '\r' | sed 's/^ *//' | grep '^[A-Za-z0-9_.]*/' | awk -F/ '{c=$2; if (c ~ /^\./) c=$1 c; print $1"/"c}' | sort -u | head -40)
n=$(echo "$comps" | grep -c .)
[ "$n" -ge 15 ] && ok "$n launcher activities to fill a tall folder" || ko "only $n launcher activities installed: not enough for a tall folder"
many=$(echo "$comps" | sed 's/.*/"&"/' | paste -sd, -)
setp string folder:big "{\"name\":\"Grande\",\"where\":\"fav\",\"apps\":[$many]}"
setp string folder:few '{"name":"Pochi","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}'
setp string fav 'f:big\&#10;f:few\&#10;f:two'
setp string folder:two "{\"name\":\"Doppia\",\"where\":\"fav\",\"cols\":2,\"apps\":[$many]}" # same apps, 2 columns: still taller than the screen when n >= 30
setp del grid
inset() { # inset statusBars|navigationBars: the inset's frame "x1 y1 x2 y2" from the window dump, empty when absent
  A shell dumpsys window | grep -o "type=$1 frame=\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]" | head -1 \
    | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' '
}
card() { # the pop-up card: the last ScrollView of the dump (the pop-up window comes after the home), "x1 y1 x2 y2"
  screen | grep -o 'class="android.widget.ScrollView"[^>]*bounds="[^"]*"' | tail -1 \
    | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr -d '[' | tr ']' ' ' | tr ',' ' '
}
card_settled() { [ -n "$(card)" ] && settled && [ -n "$(card)" ]; } # the pop-up is in the dump and its open animation is over
read -r _ _ _ top <<<"$(inset statusBars)"; top=${top:-0}
read -r _ navtop _ _ <<<"$(inset navigationBars)"; navtop=${navtop:-$H}

tap Grande; wait_until 10 card_settled
read -r _ cy1 _ cy2 <<<"$(card)"; shot 127-folder-tall
[ -n "$cy1" ] && [ $((cy1 - top)) -ge $(($(dp 24) - 2)) ] \
  && ok "tall one-column folder: 24 dp below the status bar" || ko "tall folder card top ${cy1:-missing}, status bar ends at $top, wants a gap of $(dp 24) px"
[ -n "$cy2" ] && [ $((navtop - cy2)) -ge $(($(dp 24) - 2)) ] \
  && ok "tall one-column folder: 24 dp above the navigation bar" || ko "tall folder card bottom ${cy2:-missing}, navigation bar starts at $navtop, wants a gap of $(dp 24) px"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Calendar"

[ "$n" -ge 20 ] && ok "$n launcher activities: a 2-column folder is still taller than the screen" || ko "only $n launcher activities: a 2-column folder would fit the screen"
tap Doppia; wait_until 10 card_settled
read -r cy1 cy2 <<<"$(card | awk '{print $2, $4}')"; shot 129-folder-tall-2cols
[ -n "$cy1" ] && [ $((cy1 - top)) -ge $(($(dp 24) - 2)) ] \
  && ok "tall 2-column folder: 24 dp below the status bar" || ko "tall 2-column folder card top ${cy1:-missing}, status bar ends at $top, wants a gap of $(dp 24) px"
[ -n "$cy2" ] && [ $((navtop - cy2)) -ge $(($(dp 24) - 2)) ] \
  && ok "tall 2-column folder: 24 dp above the navigation bar" || ko "tall 2-column folder card bottom ${cy2:-missing}, navigation bar starts at $navtop, wants a gap of $(dp 24) px"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Calendar"

tap Pochi; wait_until 10 card_settled
read -r cx1 _ cx2 _ <<<"$(card)"; shot 128-folder-short
[ -n "$cx1" ] && [ $((cx2 - cx1)) -lt $((W - $(dp 48) - $(dp 8))) ] \
  && ok "short one-column folder wraps its content: $((cx2 - cx1)) px wide, narrower than the screen less 48 dp" \
  || ko "short folder card is ${cx1:+$((cx2 - cx1))}${cx1:-missing} px wide, not narrower than $((W - $(dp 48)))"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Calendar"

step "Folders: a 3-app, 2-column pop-up is a small centred card, not nearly full width (051)"
seed classic
setp string folder:duo '{"name":"Duo","where":"fav","cols":2,"apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}'
setp string fav 'f:duo'
setp del grid
tap Duo; wait_until 10 card_settled
read -r cx1 _ cx2 _ <<<"$(card)"; shot 130-folder-compact
[ -n "$cx1" ] && [ $(((cx2 - cx1) * 100)) -lt $((W * 60)) ] \
  && ok "2-column pop-up is $((cx2 - cx1)) px wide, under 60% of the $W px screen" || ko "2-column pop-up is $([ -n "$cx1" ] && echo $((cx2 - cx1)) || echo missing) px wide, wants < 60% of $W"
[ -n "$cx1" ] && near $(((cx1 + cx2) / 2)) $((W / 2)) $(dp 4) \
  && ok "the pop-up is centred" || ko "pop-up centre $([ -n "$cx1" ] && echo $(((cx1 + cx2) / 2))) vs screen centre $((W / 2))"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Calendar"

step "Folders: long-press and drag reorders the icons inside the pop-up, never out of it (082)"
order() { pstr "folder:ord" | grep -o '\(calendar\|deskclock\|contacts\)[\\/]' | tr -d '\\/' | paste -sd, -; } # the stored order, short names
card_text() { screen | awk -v RS='class="android.widget.ScrollView"' 'END { printf "%s", $0 }'; } # what follows the card's ScrollView
in_card() { card_text | grep -q "text=\"$1\""; }
shown() { # the pop-up's icons in view-tree order (the dump's, TalkBack's), same short names
  card_text | grep -o 'text="\(Calendar\|Clock\|Contacts\)"' \
    | cut -d'"' -f2 | sed 's/Clock/deskclock/' | tr 'A-Z' 'a-z' | paste -sd, -
}
calendar_up() { A shell dumpsys window | grep mCurrentFocus | grep -q calendar; }
for variant in classic:1 list:1 classic:2; do # a 2-column pop-up: Calendar and Contacts are on different rows
  mode=${variant%:*}; cols=${variant#*:}; mode="$mode/$cols col"
  seed ${variant%:*}
  setp string folder:ord '{"name":"Ordine","where":"fav","cols":'$cols',"apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}'
  setp string fav 'f:ord'
  setp del grid
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
  back() { [ "$(order)" = "calendar,deskclock,contacts" ]; }
  wait_until 5 back && ok "$mode: the moved Calendar answers a long-press: dragged back first" || ko "$mode: the moved Calendar ignored the long-press drag, order $(order)"
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
  [ "$(pstr fav)" = 'f:ord' ] && ok "$mode: nothing dropped on the home" || ko "$mode: fav = $(pstr fav)"
  # a drag cut short (the pop-up closed under the finger) saves nothing and leaves no floating icon behind
  stored=$(order); read -r ax ay <<<"$(cx Calendar)"; read -r tx ty <<<"$(cx Clock)"
  A shell input draganddrop "$ax" "$ay" "$tx" "$ty" 5000 &
  sleep 3; A shell input keyevent KEYCODE_BACK; wait # mid-gesture: input holds still for the long-press timeout, then moves for 5 s; there is no screen state to wait on (a dump takes ~2 s)
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

step "Folders: an icon's menu in the pop-up takes it out with Rimuovi dalla cartella, an app or a pinned shortcut (082)"
seed classic
setp string folder:sc '{"name":"Scorciatoie","where":"fav","apps":["com.google.android.deskclock/com.android.deskclock.DeskClock","s:org.ganimede.e2e|zz1|Zz Posta","com.google.android.calendar/com.android.calendar.AllInOneActivity"]}'
setp string fav 'f:sc'
setp del grid
tap Scorciatoie; expect "Zz Posta"; wait_until 5 settled
read -r ax ay <<<"$(cx "Zz Posta")"; hold "$ax" "$ay"
wait_until 5 has "Rimuovi dalla cartella" && ok "the shortcut's menu has Rimuovi dalla cartella" || ko "no Rimuovi dalla cartella in the shortcut's menu"
tap "Rimuovi dalla cartella"
gone() { ! pstr folder:sc | grep -q "$1"; }
wait_until 5 gone zz1 && ok "the shortcut is no longer stored" || ko "folder:sc = $(pstr folder:sc)"
wait_until 5 has "Calendar"; wait_until 5 settled # the pop-up opens again with the result
hasnt "Zz Posta" && has "Clock" && ok "the pop-up shows it gone, Clock still there" || ko "pop-up after removing the shortcut"
read -r ax ay <<<"$(cx Clock)"; hold "$ax" "$ay"
wait_until 5 has "Rimuovi dalla cartella" && ok "Clock's menu has Rimuovi dalla cartella" || ko "no Rimuovi dalla cartella in Clock's menu"
tap "Rimuovi dalla cartella"
wait_until 5 gone deskclock && ok "Clock is no longer stored" || ko "folder:sc = $(pstr folder:sc)"
# a new icon from the same menu: the open pop-up draws it once the app list has reloaded (apps carry their icon)
wait_until 5 in_card Calendar; wait_until 5 settled
read -r c1 c2 c3 c4 <<<"$(card)"; crop="$((c3 - c1))x$((c4 - c2))+$c1+$c2"
A exec-out screencap -p | magick png:- -crop "$crop" +repage "$OUT/folder-icon-before.png"
read -r ax ay <<<"$(cx Calendar)"; hold "$ax" "$ay"
tap "Icona dell'app…"; tap "Arcticons (integrato)"
wait_until 5 has "calendar" && tap "calendar" # the search field starts with the app's name
clear_text; A shell input text okay
wait_until 10 hasd okay; read -r ox oy <<<"$(cx "desc:okay")"; [ -n "$oy" ] && A shell input tap "$ox" "$oy"
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
clear_text; A shell input text AgendaZz; tap "Salva"
wait_until 5 in_card AgendaZz && ok "renamed from the in-folder menu: the open pop-up shows AgendaZz" || ko "the open pop-up still shows the old name"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "AgendaZz"

step "Folders: another app's update doesn't rebuild the open pop-up; one of its apps removed mid-drag waits for the finger (082)"
FIXAPK="$HERE/fixture/build/outputs/apk/debug/fixture-debug.apk" # e2e/fixture: an app with a launcher activity, ours to reinstall
"$HERE/../gradlew" -p "$HERE/.." -q -Pe2e :fixture:assembleDebug >/dev/null 2>&1 && [ -s "$FIXAPK" ] \
  && ok "fixture app built" || ko "fixture app not built (./gradlew -Pe2e :fixture:assembleDebug): the checks below can't pass"
update_other() { A install -r "$FIXAPK" >/dev/null; } # a package event (added/changed): Ganimede reloads its app list
popwin() { A shell dumpsys window windows | grep -o "Window{[0-9a-f]* u0 [^}]*$PK[^}]*}" | sort | paste -sd' ' -; } # its windows
seed classic
setp string folder:ord '{"name":"Ordine","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity"]}'
setp string fav 'f:ord'
setp del grid
tap Ordine; expect "Contacts"; wait_until 5 settled
wins=$(popwin) # (not "before": run.sh counts checks in that name)
update_other
sleep 2 # the reload: 250 ms debounce, then the load thread; nothing on screen marks its end, the point is that nothing changes
[ -n "$wins" ] && [ "$(popwin)" = "$wins" ] && ok "the pop-up is the same window after another app's update" || ko "the pop-up was rebuilt: windows $(popwin), were $wins"
has "Contacts" && ok "the pop-up is still open" || ko "the pop-up closed"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Contacts"
# An app IN the folder goes away while a finger drags an icon: the pop-up waits for the finger (FolderDrag.busy), the
# drop is saved, and only then is it built again without that app. Rebuilt under the finger, the drag would be lost.
setp string folder:ord '{"name":"Ordine","where":"fav","apps":["com.google.android.calendar/com.android.calendar.AllInOneActivity","com.google.android.deskclock/com.android.deskclock.DeskClock","com.google.android.contacts/com.android.contacts.activities.PeopleActivity","org.ganimede.fixture/org.ganimede.fixture.Open"]}'
tap Ordine; expect "Fixture"; wait_until 5 settled
read -r ax ay <<<"$(cx Calendar)"; read -r tx ty <<<"$(cx Contacts)"
A shell input motionevent DOWN "$ax" "$ay"; sleep 0.9 # past the long-press timeout: the menu opens, the icon is held
A shell input motionevent MOVE "$ax" $((ay + $(dp 16))) # past the touch slop: the menu closes, the drag starts
A uninstall org.ganimede.fixture >/dev/null
fixture_gone() { ! A shell pm path org.ganimede.fixture >/dev/null 2>&1; }
wait_until 10 fixture_gone || ko "the fixture app is still installed"
sleep 1 # Ganimede's reload: 250 ms debounce, then the load thread; under the finger nothing may change, so nothing to wait on
dump | grep -q 'text="Fixture' && ok "while the finger is down the pop-up is not rebuilt (the removed app still shows)" || ko "the pop-up was rebuilt under the finger"
A shell input motionevent MOVE "$tx" "$ty"; A shell input motionevent UP "$tx" "$ty"
wait_until 5 reordered && ok "the drag held across the uninstall saved its order" || ko "the drag was lost: order $(order)"
wait_until 5 hasnt "Fixture" && ok "after the lift the pop-up is built again without the removed app" || ko "the pop-up still shows the removed app"
has "Contacts" && ok "the pop-up is open" || ko "the pop-up closed"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Contacts"
A install -r "$FIXAPK" >/dev/null || ko "the fixture app could not be reinstalled" # other sections use it

step "Folders: Choose apps lists the folder's pinned shortcuts; unchecking one takes it out, the order stays (082)"
seed classic
setp string folder:sc '{"name":"Scorciatoie","where":"fav","apps":["com.google.android.deskclock/com.android.deskclock.DeskClock","s:org.ganimede.e2e|zz1|Zz Posta","com.google.android.calendar/com.android.calendar.AllInOneActivity"]}'
setp string fav 'f:sc'
setp del grid
tap Scorciatoie; expect "Zz Posta"
tap "✎ Modifica pop-up"; tap "Scegli app"; wait_until 5 has "Zz Posta"
tap "Zz Posta"; tap "Salva"
wait_until 5 has "Calendar" # the pop-up opens again with the result
hasnt "Zz Posta" && ok "the unchecked shortcut is gone from the pop-up" || ko "the shortcut is still in the pop-up"
pstr folder:sc | grep -q 'zz1' && ko "the shortcut is still stored: $(pstr folder:sc)" || ok "the shortcut is no longer stored"
pstr folder:sc | grep -q 'deskclock.*calendar' && ok "Clock and Calendar keep their order" || ko "folder:sc = $(pstr folder:sc)"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Calendar"

step "App menu from the drawer (List): add to favourites"
seed list
drawer_find() { # drawer, search $1; leaves Settings in the list
  A shell input swipe 540 1900 540 600 150; wait_until 5 has "Cerca app"; A shell input text "$1"; wait_until 5 has "Settings"; }
drawer_find sett
read -r sx sy <<<"$(cx Settings)"; hold "$sx" "$sy"
expect "Aggiungi ai preferiti" && tap "Aggiungi ai preferiti"
wait_until 5 test -n "$(pstr fav | grep com.android.settings)"
pstr fav | grep -q 'com.android.settings' && ok "Settings is in the favourites pref" || ko "fav = $(pstr fav)"
wait_until 5 has 'sett"' && ok "the search is still open behind the menu" || ko "the menu closed the search"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt 'sett"' # the menu took the keyboard down: one Back closes the search
wait_until 5 has "Settings" && ok "Settings appears on the home screen" || ko "no Settings on home"
shot 122-fav-added

step "App menu: Hide leaves the list, search finds it under Nascoste"
drawer_find sett
has "Nascoste" && ko "Settings listed as hidden before hiding" || ok "visible app: no “Nascoste” section"
read -r sx sy <<<"$(cx Settings)"; hold "$sx" "$sy"
expect "Rimuovi dai preferiti"; tap "Nascondi"
wait_until 5 hasnt "Rimuovi dai preferiti"
wait_until 5 has 'sett"' && ok "the search is still open behind the menu" || ko "the menu closed the search"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt 'sett"' # the menu took the keyboard down: one Back closes the search
drawer_find_hidden() { A shell input swipe 540 1900 540 600 150; wait_until 5 has "Cerca app"; A shell input text sett; wait_until 5 has "Nascoste"; }
drawer_find_hidden
wait_until 5 has "Nascoste" && ok "after Hide a search shows the “Nascoste” section" || ko "no Nascoste section"
has "Settings" && ok "…and it still finds Settings" || ko "Settings not found in search once hidden"
pstr fav | grep -q 'com.android.settings' && ko "hidden app stayed in the favourites" || ok "Hide also took it out of the favourites"
shot 123-hidden
A shell input keyevent KEYCODE_BACK; A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt 'sett"' # keyboard, then search
has "Settings" && ko "hidden app still on the home screen" || ok "Settings left the home screen"

step "App menu: Unhide from Settings"
A shell input swipe 540 1500 540 1500 1500; tap "Impostazioni Ganimede"
wait_until 5 has "Personalizza"; A shell input swipe 150 2120 150 600 300; tap "App nascoste"
expect "Settings"; shot 124-hidden-page
tap "Mostra di nuovo"
wait_until 5 hasnt "Mostra di nuovo"
A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -A3 'name="hidden"' | grep -q 'settings' && ko "hidden set still holds Settings" || ok "the hidden set is empty again"
start_home; drawer_find sett
has "Nascoste" && ko "still under Nascoste after Unhide" || ok "after Unhide a search lists it normally"
A shell input keyevent KEYCODE_BACK; A shell input keyevent KEYCODE_BACK

step "Classic: Remove from favourites takes the icon off the grid"
seed classic
read -r ax ay <<<"$(cx Calendar)"; hold "$ax" "$ay"
expect "Rimuovi dai preferiti" && tap "Rimuovi dai preferiti"
wait_until 5 hasnt "Calendar"
has "Calendar" && ko "Calendar icon still on the grid" || ok "the grid icon is gone"
pstr fav | grep -q 'calendar' && ko "still a favourite" || ok "no longer a favourite"
pstr grid | grep -q 'calendar' && ko "grid pref still holds Calendar" || ok "grid pref lost it too"
has "Clock" && has "Contacts" && ok "the other icons stay" || ko "other icons disappeared"

step "Classic: Hide removes the app from the grid and the dock"
seed classic
read -r ax ay <<<"$(cx Clock)"; hold "$ax" "$ay"
expect "Aggiungi al dock" && tap "Aggiungi al dock"
wait_until 5 hasd "Clock"
hasd "Clock" && ok "Clock is in the dock" || ko "Clock not in the dock"
read -r dx dy <<<"$(cx "desc:Clock")"; hold "$dx" "$dy"
expect "Nascondi" && tap "Nascondi"
wait_until 5 nod Clock
hasd "Clock" || has "Clock" && ko "Clock still on the home screen (dock or grid)" || ok "Hide: gone from dock and grid"
pstr dock | grep -q 'deskclock' && ko "dock pref still holds it" || ok "dock pref cleared"
pstr grid | grep -q 'deskclock' && ko "grid pref still holds it" || ok "grid pref cleared"
read -r ax ay <<<"$(cx Contacts)"; hold "$ax" "$ay"
expect "Nascondi" && tap "Nascondi"
wait_until 5 hasnt "Contacts"
has "Contacts" && ko "Contacts still on the grid after Hide" || ok "Hide on a grid icon removes it from the grid"
shot 125-classic-hidden
