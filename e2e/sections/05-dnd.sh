step "Classic: drag and drop (edit mode)"
seed classic; setp int grid_cols 4
read -r ax ay <<<"$(cx Calendar)"
colw=$(((W - 2 * $(dp 8)) / 4)); tx=$(($(dp 8) + colw * 2 + colw / 2)); ty=$((ay + $(dp 260))) # column 3, further down
A shell input draganddrop "$ax" "$ay" "$tx" "$ty" 1500
lower() { read -r _ y <<<"$(cx Calendar)"; [ -n "$y" ] && [ "$y" -gt $((ay + $(dp 150))) ]; }
wait_until 5 lower
read -r bx by <<<"$(cx Calendar)"
near "$bx" "$tx" $(dp 24) && [ "$by" -gt $((ay + $(dp 150))) ] && ok "Calendar dropped on an empty cell ($ax,$ay → $bx,$by)" || ko "Calendar at $bx,$by, expected column at $tx, lower than $ay"
shot 22-dropped
read -r ax ay <<<"$(cx Clock)"; read -r tx ty <<<"$(cx Contacts)"
A shell input draganddrop "$ax" "$ay" "$tx" "$ty" 1500
wait_until 5 has "Cartella"
has "Cartella" && ok "app dropped on app makes a folder" || ko "no folder made"
has "Clock" && ko "Clock still loose on the grid" || ok "both apps went into the folder"
shot 23-folder
tap "Cartella"; expect "Clock"; expect "Contacts"; shot 24-folder-open; A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Clock"
read -r ax ay <<<"$(cx Calendar)"
A shell input draganddrop "$ax" "$ay" "$((W / 10))" "$((H - $(dp 90)))" 1500
wait_until 5 docked
wait_until 5 settled # the dock icon has stopped sliding in: a drag started on it mid-move misses (seen once in ~25 runs)
read -r dx dy <<<"$(cx "desc:Calendar")"
[ -n "$dy" ] && [ "$dy" -gt $((H - $(dp 200))) ] && ok "Calendar moved into the dock (y $dy)" || ko "Calendar not in the dock (y ${dy:-none})"
shot 25-dock
A shell input draganddrop "$dx" "$dy" "$((W / 4))" "$(dp 90)" 1500   # onto Remove, top left
wait_until 5 nod Calendar
hasd "Calendar" && ko "Calendar still on the home screen" || ok "dropped on Remove: off the home screen"
read -r fx fy <<<"$(cx Cartella)"
A shell input draganddrop "$fx" "$fy" "$fx" "$fy" 1500       # dropped where it was: its menu
expect "Scegli app" && A shell input keyevent KEYCODE_BACK
