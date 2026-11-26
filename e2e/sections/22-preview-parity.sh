# The Settings preview draws what the home draws: same favourites (folders included), same places, and a block that is on but
# empty (next event, media player) is a marked dashed outline that takes no room. Fictional data only.
# (setp feeds sed: a literal & is written \&)
# nx / ny: a point as thousandths of its own frame (the whole screen for the home, the miniature's phone for the preview).
FAV="com.google.android.calendar/com.android.calendar.AllInOneActivity\&#10;com.google.android.deskclock/com.android.deskclock.DeskClock\&#10;f:alpha"
FOLDER='{\&quot;name\&quot;:\&quot;Folder Alpha\&quot;,\&quot;where\&quot;:\&quot;fav\&quot;,\&quot;apps\&quot;:[\&quot;com.google.android.contacts/com.android.contacts.activities.PeopleActivity\&quot;]}'
nx() { echo $((($1 - FX) * 1000 / FW)); }
ny() { echo $((($1 - FY) * 1000 / FH)); }
# parity "what" home-value preview-value: within 15 thousandths (1.5 %) of the frame
parity() { [ -n "$2" ] && [ -n "$3" ] && near "$2" "$3" 15 && ok "$1: home $2, preview $3" || ko "$1: home $2, preview $3 (thousandths of the frame)"; }
# frame home: the whole screen
home_frame() { FX=0; FY=0; FW=$W; FH=$H; }
# frame preview: the miniature's phone inside the box named by its description (4 dp above, 8 dp under, centred)
preview_frame() {
  local b; b=$(bounds "desc:Anteprima della tua schermata Home"); [ -n "$b" ] || return 1
  set -- $b
  FY=$(($2 + $(dp 4))); FH=$(($4 - $(dp 8) - FY)); FW=$((FH * W / H)); FX=$((($1 + $3 - FW) / 2))
}
# top/bottom/left of a node by description, as thousandths of the current frame
top_of() { local b; b=$(bounds "$1"); [ -n "$b" ] && { set -- $b; ny "$2"; }; }
bottom_of() { local b; b=$(bounds "$1"); [ -n "$b" ] && { set -- $b; ny "$4"; }; }
left_of() { local b; b=$(bounds "$1"); [ -n "$b" ] && { set -- $b; nx "$1"; }; }

for mode in list classic; do
  step "Preview parity ($mode): the miniature matches the home"
  seed $mode
  setp string fav "$FAV"; setp string "folder:alpha" "$FOLDER"
  setp boolean event_preview true; setp boolean media_player true
  wait_until 10 has "Folder Alpha" && ok "the home shows the folder" || ko "no Folder Alpha on the home"
  hasnt "Prossimo evento" && hasnt "Lettore musicale" && ok "the empty blocks are not on the home" || ko "an empty block shows on the home"
  home_frame
  if [ $mode = list ]; then
    h_apps_top=$(top_of "desc:Calendar"); h_apps_bottom=$(bottom_of "desc:Folder Alpha"); h_apps_left=$(left_of "desc:Calendar")
    h_clock_top=$(top_of "desc:Orologio, tocca per le sveglie")
  else
    h_apps_top=$(top_of "desc:Orologio, tocca per le sveglie"); h_apps_left=$(left_of "desc:Orologio, tocca per le sveglie") # the clock block sits in the grid's first cell
  fi
  A shell input swipe 540 1900 540 1900 1500; tap "Impostazioni Ganimede"; stap "Personalizza"
  wait_until 10 settled
  preview_frame || ko "no preview"
  if [ $mode = list ]; then
    pill "Alfabeto"; tap "Alfabeto"; wait_until 5 settled; preview_frame
    p_apps_top=$(top_of "desc:Anteprima: preferiti"); p_apps_bottom=$(bottom_of "desc:Anteprima: preferiti"); p_apps_left=$(left_of "desc:Anteprima: preferiti")
    p_clock_top=$(top_of "desc:Anteprima: orologio")
    parity "favourites top" "$h_apps_top" "$p_apps_top"
    parity "favourites bottom (the folder row is there)" "$h_apps_bottom" "$p_apps_bottom"
    parity "favourites start" "$h_apps_left" "$p_apps_left"
    parity "clock top" "$h_clock_top" "$p_clock_top"
    hasd "Segnaposto: Prossimo evento" && hasd "Segnaposto: Lettore musicale" && ok "empty blocks are marked placeholders in the preview" || ko "placeholders not marked"
    hasd "Segnaposto: App musicali" && ko "music apps placeholder with no apps chosen" || ok "no music placeholder without chosen apps"
  else
    p_apps_top=$(top_of "desc:Anteprima: griglia"); p_apps_left=$(left_of "desc:Anteprima: griglia")
    parity "grid top" "$h_apps_top" "$p_apps_top"
    parity "grid start" "$h_apps_left" "$p_apps_left"
    hasd "Segnaposto: Prossimo evento" && ko "placeholder in Classic" || ok "no placeholders in Classic"
  fi
  shot "22-preview-$mode"
  A shell input keyevent KEYCODE_HOME; wait_until 5 focus
  setp del fav; setp del "folder:alpha"
done
