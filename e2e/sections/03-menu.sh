step "Home menu and folder"
seed classic
home_menu "Scegli le app della Home"; shot 13-menu # long-press on an empty spot (the first gesture after boot can be dropped)
expect "Scegli le app della Home"
expect "Crea cartella pop-up"
A shell input keyevent KEYCODE_BACK
