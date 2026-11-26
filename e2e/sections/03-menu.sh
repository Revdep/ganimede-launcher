step "Home menu and folder"
seed classic
A shell input swipe 540 1300 540 1300 1500; shot 13-menu # long-press on an empty spot
expect "Scegli le app della Home"
expect "Crea cartella pop-up"
A shell input keyevent KEYCODE_BACK
