step "Drawer and search"
seed classic
A shell input swipe 540 1900 540 600 150; shot 11-drawer
expect "Cerca app"
typ "cal"
wait_until 5 has "Calendar" && ok "search finds Calendar" || ko "search misses Calendar"
shot 12-search

step "Back with text: the first hides the keyboard, the results stay; the second goes home (both modes)"
for m in classic list; do
  seed $m; A shell input swipe 540 1900 540 600 150; expect "Cerca app"
  A shell input keyevent KEYCODE_BACK # empty search: one back closes keyboard and search
  wait_until 5 hasnt "Cerca app" && ok "$m, empty: one back goes home" || ko "$m, empty: search still open after one back"
  A shell input swipe 540 1900 540 600 150; expect "Cerca app"; typ "cal"; wait_until 5 kbd || ko "$m: no keyboard while typing"
  A shell input keyevent KEYCODE_BACK
  wait_until 5 eval '! kbd' && has 'cal"' && has "Calendar" && ok "$m, text: first back hides the keyboard, results stay" \
    || ko "$m, text: first back left the results"
  A shell input keyevent KEYCODE_BACK
  wait_until 5 hasnt 'cal"' && ok "$m, text: second back goes home" || ko "$m, text: search still open after the second back"
done

step "An app's menu over an empty search: the menu takes the keyboard down, the search stays open behind it"
seed list; A shell input swipe 540 1900 540 600 150; expect "Cerca app"; wait_until 5 kbd || ko "no keyboard on the search"
read -r mx my <<<"$(cx Calendar)"; A shell input draganddrop "$mx" "$my" "$mx" "$my" 1500 # long-press: the app's menu
expect "Rimuovi dai preferiti"
A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt "Rimuovi dai preferiti"
wait_until 3 has "Cerca app" && ok "the search is still open after the menu" || ko "the menu closed the search behind it"

step "Restart after typing: no focus box over the home"
touch_mode() { A shell dumpsys window | grep -q 'mInTouchMode=true'; }
A shell input keyevent KEYCODE_DPAD_DOWN  # a key press leaves touch mode, as typing does (a modifier like Shift does not)
seed classic; wait_until 5 settled
touch_mode && ko "precondition: already in touch mode" || ok "precondition: outside touch mode"
wait_until 5 settled; home=$(screen) # settled just read the screen: an empty dump was retried
if [ -z "$home" ]; then
  ko "no UI dump to check the home focus"
elif echo "$home" | grep -q 'class="android.widget.HorizontalScrollView"[^>]*focused="true"'; then
  ko "the pager has the focus: a box over the home"
else
  ok "the pager has no focus box over the home"
fi
A shell input tap 540 1600; wait_until 3 touch_mode || ko "touch mode not restored" # a touch on an empty cell: back in touch mode

emu_only "drawer: test contacts" || return 0
step "Search: every matching contact, any case, in order"
CONTACTS=content://com.android.contacts
# the test contacts and the permission go away at the end, and before inserting in case an earlier run stopped halfway
prova_clean() {
  A shell "content delete --uri '$CONTACTS/raw_contacts?caller_is_syncadapter=true' --where \"account_type LIKE 'org.ganimede.e2e%'\""
  A shell pm revoke $PK android.permission.READ_CONTACTS
}
prova_clean
names=("Zz Prova H" "Zz Prova G" "ZZ PROVA F" "Zz Prova E" "Zz Prova D" "Zz Prova C" "Zz Prova B" "Zz Prova A")
for n in "${names[@]}"; do # a raw contact of the e2e account with a name and a number, C an email instead
  A shell "content insert --uri $CONTACTS/raw_contacts --bind account_type:s:org.ganimede.e2e --bind account_name:s:e2e"
  id=$(A shell content query --uri $CONTACTS/raw_contacts --projection _id --sort "'_id DESC'" | head -1 | grep -o '_id=[0-9]*' | cut -d= -f2)
  A shell "content insert --uri $CONTACTS/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$n'"
  if [ "$n" = "Zz Prova C" ]; then # email only: listed, without a call button
    A shell "content insert --uri $CONTACTS/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/email_v2 --bind data1:s:zz.prova@example.org --bind data2:i:2"
  else A shell "content insert --uri $CONTACTS/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:555010$id --bind data2:i:2"; fi
done
A shell pm grant $PK android.permission.READ_CONTACTS
seed classic; setp boolean contacts_search true
A shell input swipe 540 1900 540 600 150; expect "Cerca app"
typ "Zz Prova"
expect "Zz Prova A"
calls() { [ "$(screen | grep -o 'text="✆"' | wc -l)" -eq "$1" ]; } # call buttons: one per contact shown with a number
wait_until 5 calls 4 && ok "first five: the four with a number have their call button" || ko "first five: call buttons wrong"
tap "Mostra tutti (8)"
wait_until 5 kbd; A shell input keyevent KEYCODE_BACK; wait_until 5 eval '! kbd' # the keyboard covers the last rows; Back hides it and keeps the search
wait_until 5 calls 7 && ok "Show all: the seven with a number have their call button" || ko "Show all: call buttons wrong"
top() { local b; b=$(bounds "$1"); echo "${b#* }" | cut -d' ' -f1; }
ya=$(top "Zz Prova A"); yu=$(top "Zz Prova H")
[ -n "$ya" ] && [ -n "$yu" ] && [ "$ya" -lt "$yu" ] && ok "contacts by name: A above H" || ko "contacts not by name (A y=$ya, H y=$yu)"
shot 13-contacts
for n in "${names[@]}"; do see "$n"; done
typ "o"; A shell input keyevent KEYCODE_DEL # Show all holds only for the text it was tapped for
wait_until 5 has "Mostra tutti (8)" && ok "another text and back: five again, with Show all" || ko "another text and back: still all, no Show all"
A shell input keyevent KEYCODE_BACK; A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt 'Zz Prova"' # keyboard, then search
A shell input swipe 540 1900 540 600 150; expect "Cerca app"; typ "Zz Prova"
wait_until 5 has "Mostra tutti (8)" && ok "search reopened: five again, with Show all" || ko "search reopened: still all, no Show all"

step "Search: a chat contact (WhatsApp, Telegram) that did not merge with the phone contact is not a second row"
# the same person twice: the phone contact, and a raw contact of a messenger account with a name and no number or email
# (aggregation disabled, as when the names differ slightly and the provider keeps them apart)
for acct in org.ganimede.e2e org.ganimede.e2e.chat; do
  mode=0; [ "$acct" = org.ganimede.e2e.chat ] && mode=3
  A shell "content insert --uri $CONTACTS/raw_contacts --bind account_type:s:$acct --bind account_name:s:e2e --bind aggregation_mode:i:$mode"
  id=$(A shell content query --uri $CONTACTS/raw_contacts --projection _id --sort "'_id DESC'" | head -1 | grep -o '_id=[0-9]*' | cut -d= -f2)
  A shell "content insert --uri $CONTACTS/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'Zz Prova Chat'"
  [ "$mode" = 3 ] || A shell "content insert --uri $CONTACTS/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:555010$id --bind data2:i:2"
done
A shell input keyevent KEYCODE_BACK; A shell input keyevent KEYCODE_BACK; wait_until 5 hasnt 'Zz Prova"'
A shell input swipe 540 1900 540 600 150; expect "Cerca app"; typ "Chat"
expect "Zz Prova Chat"
rows() { [ "$(screen | grep -o 'text="Zz Prova Chat"' | wc -l)" -eq "$1" ]; }
wait_until 5 rows 1 && ok "the person is listed once" || ko "the person is listed $(screen | grep -o 'text="Zz Prova Chat"' | wc -l) times"
shot 14-chat
prova_clean
