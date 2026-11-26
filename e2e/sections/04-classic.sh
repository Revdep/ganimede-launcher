# deep checks: every modifier has its effect
step "Classic grid: positions and columns"
seed classic
read -r c1x c1y <<<"$(cx Calendar)"; read -r c2x c2y <<<"$(cx Clock)"; read -r c3x c3y <<<"$(cx Contacts)"
[ -n "$c1x" ] && [ "$c1y" = "$c2y" ] && [ "$c2y" = "$c3y" ] && ok "three apps on the first grid row" || ko "first row wrong: $c1x,$c1y $c2x,$c2y $c3x,$c3y"
cols=$(A exec-out run-as $PK cat shared_prefs/ganimede.xml | grep -o 'grid_cols" value="[0-9]*' | grep -o '[0-9]*$'); cols=${cols:-5}
near $((c2x - c1x)) $(((W - $(dp 16)) / cols)) $(dp 6) && ok "cells are 1/$cols of the width" || ko "cell width $((c2x - c1x)), expected about $(((W - $(dp 16)) / cols))"
setp int grid_cols 4
read -r n1x _ <<<"$(cx Calendar)"; read -r n2x _ <<<"$(cx Clock)"
near $((n2x - n1x)) $(((W - $(dp 16)) / 4)) $(dp 6) && ok "4 columns: cells widen" || ko "4 columns: cell width $((n2x - n1x))"
shot 20-grid-4cols

step "Classic: the clock is a block of the grid"
# The grid fills the space between the status bar and the dock; the clock is a block that can be resized or removed.
b=$(bounds "desc:Orologio, tocca per le sveglie")
if [ -n "$b" ]; then set -- $b; ok "the clock is on the grid"
  A shell input swipe $((($1 + $3) / 2)) $((($2 + $4) / 2)) $((($1 + $3) / 2)) $((($2 + $4) / 2)) 1300
  wait_until 5 has "Ridimensiona" && has "Rimuovi" && ok "long-press: Resize and Remove" || ko "no block menu on the clock"
  A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Ridimensiona"
else ko "no clock on the grid"; fi
shot 21-classic-blocks

step "Classic: labels on/off"
setp boolean hide_labels true
wait_until 5 hasd "Calendar"   # the icons are up
has "Calendar" && ko "labels still shown" || ok "labels hidden"
hasd "Calendar" && ok "icons still there (named for accessibility)" || ko "icons gone with the labels"
setp boolean hide_labels false

step "Classic: the default grid is the same in landscape"
seed classic
setp del grid_cols; setp del grid_rows; setp boolean rotate true; setp boolean hide_labels true # landscape clips the labels: find the icons by name
wait_until 5 hasd "Calendar"
read -r p1x _ <<<"$(cx desc:Calendar)"; read -r p2x _ <<<"$(cx desc:Clock)"
pcols=$((((W - $(dp 16)) + (p2x - p1x) / 2) / (p2x - p1x)))
setting system accelerometer_rotation 0; setting system user_rotation 1
wait_until 10 settled; wait_until 5 hasd "Calendar"
read -r l1x _ <<<"$(cx desc:Calendar)"; read -r l2x _ <<<"$(cx desc:Clock)"
[ -n "$l1x" ] && near $((l2x - l1x)) $(((H - $(dp 16)) / pcols)) $(dp 8) && ok "landscape keeps $pcols columns" || ko "landscape cell width $((l2x - l1x)), expected about $(((H - $(dp 16)) / pcols)) ($pcols columns)"
shot 22-classic-landscape
setting system user_rotation 0
wait_until 10 settled
setp del rotate; setp boolean hide_labels false
