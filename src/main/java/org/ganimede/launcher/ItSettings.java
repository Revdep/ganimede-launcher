package org.ganimede.launcher;

import java.util.HashMap;
import java.util.Map;

/** Italian for every text of the settings screen, looked up by its English text (see T.t). Informal "tu". */
final class ItSettings {
    static final Map<String, String> IT = new HashMap<>();

    private static void it(String en, String it) {
        IT.put(en, it);
    }

    static {
        // Pages and categories
        it("Customize", "Personalizza");
        it("Calendar & productivity", "Calendario e produttività");
        it("Search & shortcuts", "Ricerca e scorciatoie");
        it("Notifications & wellbeing", "Notifiche e benessere");
        it("Privacy & permissions", "Privacy e permessi");
        it("Hidden apps", "App nascoste");
        it("Backup & restore", "Backup e ripristino");
        it("Language", "Lingua");
        it("About", "Informazioni");
        it("My shortcuts", "Le mie scorciatoie");
        it("Preview of your home screen", "Anteprima della tua schermata Home");
        it("Preview of your app pages", "Anteprima delle pagine delle app");
        it("Preview: favourites", "Anteprima: preferiti");
        it("Preview: clock", "Anteprima: orologio");
        it("Preview: grid", "Anteprima: griglia");
        it("Placeholder: Next event", "Segnaposto: Prossimo evento");
        it("Placeholder: Media player", "Segnaposto: Lettore musicale");
        it("Placeholder: Music apps", "Segnaposto: App musicali");

        // Settings home
        it("Finish setting up", "Completa la configurazione");
        it("Default home app", "App Home predefinita");
        it("Make Ganimede your home screen", "Rendi Ganimede la tua schermata Home");
        it("Notification access", "Accesso alle notifiche");
        it("Waiting for Breezy Weather", "In attesa di Breezy Weather");
        it("received at %s", "ricevuto alle %s");
        it("Next event", "Prossimo evento");
        it("Media player", "Lettore musicale");
        it("Music apps", "App musicali");
        it("All off", "Tutto disattivato");
        it("%s · %d custom shortcut", "%s · %d scorciatoia personale");
        it("%s · %d custom shortcuts", "%s · %d scorciatoie personali");
        it("Quick Lock on", "Blocco rapido attivo");
        it("Quick Lock off", "Blocco rapido disattivato");
        it("vibration on", "vibrazione attiva");
        it("vibration off", "vibrazione disattivata");
        it("Summary on, next around %s", "Riepilogo attivo, il prossimo verso le %s");
        it("Summary off", "Riepilogo disattivato");
        it("Everything you use is allowed", "Tutto ciò che usi è consentito");
        it("Something needs your OK", "Manca qualche autorizzazione");
        it("No hidden apps", "Nessuna app nascosta");
        it("%d hidden", "%d nascoste");
        it("Save or restore everything with a file", "Salva o ripristina tutto con un file");
        it("Version %s · free and open source", "Versione %s · libero e open source");

        // Themes and look controls
        it("Themes", "Temi");
        it("Lavender wave", "Onda lavanda");
        it("Overlapping clock, round tinted icons", "Orologio sovrapposto, icone tonde colorate");
        it("Night rain", "Pioggia notturna");
        it("Weekday clock, white icons, no labels", "Orologio col giorno, icone bianche, senza nomi");
        it("Sakura", "Sakura");
        it("Simple clock, red dots, Ganimede button", "Orologio semplice, pallini rossi, pulsante Ganimede");
        it("Misty forest", "Bosco nebbioso");
        it("Tall vertical clock, square icons", "Orologio alto e verticale, icone quadrate");
        it("Copy my theme", "Copia il mio tema");
        it("Share it in a message", "Condividilo in un messaggio");
        it("Paste a copied theme", "Incolla un tema copiato");
        it("Theme applied. Pick a matching wallpaper to finish the look.", "Tema applicato. Scegli uno sfondo adatto per completare l'aspetto.");
        it("Theme copied: paste it in a message to share it", "Tema copiato: incollalo in un messaggio per condividerlo");
        it("Could not copy the theme", "Impossibile copiare il tema");
        it("The clipboard doesn't hold a Ganimede theme", "Negli appunti non c'è un tema di Ganimede");
        it("Folders", "Cartelle");
        it("Background colour", "Colore dello sfondo");
        it("Automatic", "Automatico");
        it("Background opacity (%)", "Opacità dello sfondo (%)");
        it("Columns", "Colonne");
        it("Overlap", "Sovrapposto");
        it("Basic", "Semplice");
        it("Day of week", "Giorno della settimana");
        it("Vertical", "Verticale");
        it("Horizontal", "Orizzontale");
        it("Words", "A parole");
        it("Flip", "A palette");
        it("Analog", "Analogico");
        it("No clock", "Nessun orologio");
        it("24-hour clock", "Orologio a 24 ore");
        it("Default", "Predefinito");
        it("Light", "Leggero");
        it("Thin", "Sottile");
        it("Medium", "Medio");
        it("Condensed", "Condensato");
        it("Serif", "Con grazie");
        it("Monospace", "Monospaziato");
        it("Casual", "Informale");
        it("Handwriting", "Corsivo");
        it("Small caps", "Maiuscoletto");
        it("Colours", "Colori");
        it("Text colour", "Colore del testo");
        it("Light, for dark wallpapers", "Chiaro, per sfondi scuri");
        it("Dark, for light wallpapers", "Scuro, per sfondi chiari");
        it("Theme colour", "Colore del tema");
        it("Lavender", "Lavanda");
        it("Sky", "Cielo");
        it("Mint", "Menta");
        it("Leaf", "Foglia");
        it("Amber", "Ambra");
        it("Coral", "Corallo");
        it("Red", "Rosso");
        it("White", "Bianco");
        it("Icon style", "Stile delle icone");
        it("Plate", "Piastra");
        it("Fill", "Riempimento");
        it("Solid", "Pieno");
        it("Soft", "Tenue");
        it("Colour", "Colore");
        it("Line, Bold, Sharp, Duotone and Hollow change the stroke only for Arcticons (built-in) icons; the others take the same colour and plate.",
                "Linea, Marcato, Spigoloso, Bicolore e Contorno cambiano il tratto solo per le icone di Arcticons (integrate); le altre prendono lo stesso colore e la stessa piastra.");
        it("Hide app names of favourites", "Nascondi i nomi delle app preferite");
        it("None (apps' own icons)", "Nessuno (icone delle app)");
        it("Arcticons (built-in)", "Arcticons (integrato)");
        it("Category icons", "Icone per categoria");
        it("A glyph for apps that have no icon in the set", "Un glifo per le app che non hanno un'icona nel set");
        it("Change wallpaper", "Cambia sfondo");
        it("Pick one that suits your theme", "Scegline uno adatto al tuo tema");

        // Home screen
        it("Show date", "Mostra la data");
        it("Show weather", "Mostra il meteo");
        it("Show battery percentage", "Mostra la percentuale della batteria");
        it("Show the alphabet", "Mostra l'alfabeto");
        it("Touch the letters to jump through your apps", "Tocca le lettere per scorrere tra le tue app");
        it("A round button in the bottom corner with its own pop-up", "Un pulsante tondo nell'angolo in basso con il suo pop-up");
        it("Hide status bar", "Nascondi la barra di stato");
        it("Allow home screen rotation", "Consenti la rotazione della schermata Home");
        it("Dim the wallpaper behind the app list", "Scurisci lo sfondo dietro l'elenco app");
        it("Darken the wallpaper on the home screen", "Scurisci lo sfondo della Home");
        it("Strong", "Forte");
        it("Widgets, widget stacks and pop-up folders: long-press an empty part of the home screen.",
                "Widget, pile di widget e cartelle pop-up: tieni premuto un punto vuoto della schermata Home.");

        // Weather
        it("No weather received yet", "Ancora nessun meteo ricevuto");
        it("Switch on sharing in Breezy Weather, as explained below.", "Attiva la condivisione in Breezy Weather, come spiegato qui sotto.");
        it("%s · received at %s", "%s · ricevuto alle %s");
        it("How it works", "Come funziona");
        it("Ganimede never connects to the internet. The weather comes only from the Breezy Weather app on this phone, which passes it to Ganimede through Android's internal sharing every time it updates.",
                "Ganimede non si collega mai a internet. Il meteo arriva solo dall'app Breezy Weather su questo telefono, che lo passa a Ganimede tramite la condivisione interna di Android ogni volta che si aggiorna.");
        it("To switch it on, in Breezy Weather open:\n1. Settings\n2. Widgets & notifications (or “Data sharing”)\n3. “Send Gadgetbridge data”\n4. Choose Ganimede\nThe names may be slightly different in your version of Breezy.",
                "Per attivarlo, in Breezy Weather apri:\n1. Impostazioni\n2. Widget e notifiche (o “Condivisione dati”)\n3. “Invia dati a Gadgetbridge”\n4. Scegli Ganimede\nI nomi possono essere un po' diversi nella tua versione di Breezy.");
        it("Open Breezy Weather", "Apri Breezy Weather");
        it("Get Breezy Weather", "Scarica Breezy Weather");
        it("Free and open source. Opens the download page in your browser.", "Libera e open source. Apre la pagina di download nel browser.");
        it("Options", "Opzioni");
        it("Show weather on the home screen", "Mostra il meteo nella schermata Home");
        it("Show °F instead of °C", "Mostra °F invece di °C");
        it("Tapping the weather opens", "Toccando il meteo si apre");
        it("Forecast", "Previsioni");

        // Calendar & productivity
        it("Calendar access", "Accesso al calendario");
        it("Needed for the agenda and the next event", "Serve per l'agenda e il prossimo evento");
        it("Agenda when tapping the date", "Agenda toccando la data");
        it("Show the next event on the home screen", "Mostra il prossimo evento nella schermata Home");
        it("Choose calendars", "Scegli i calendari");
        it("All calendars shown", "Tutti i calendari visibili");
        it("Calendars to show", "Calendari da mostrare");
        it("No calendars on this phone", "Nessun calendario su questo telefono");
        it("Music", "Musica");
        it("Media player on the home screen", "Lettore musicale nella schermata Home");
        it("Needed for the media player", "Serve per il lettore musicale");
        it("Needs notification access", "Serve l'accesso alle notifiche");
        it("Show music apps when headphones or speakers connect", "Mostra le app musicali quando colleghi cuffie o altoparlanti");
        it("Also for hearing aids", "Anche per gli apparecchi acustici");
        it("Choose music apps", "Scegli le app musicali");
        it("Smart lists", "Elenchi intelligenti");
        it("Suggest often-used apps under favourites", "Suggerisci le app più usate sotto i preferiti");
        it("Sort the app list by usage within each letter", "Ordina l'elenco app per utilizzo dentro ogni lettera");
        it("Pop-up folders can hold apps and widgets; the Ganimede button has its own. Long-press an empty part of the home screen to make one.",
                "Le cartelle pop-up possono contenere app e widget; il pulsante Ganimede ha la sua. Tieni premuto un punto vuoto della schermata Home per crearne una.");
        it("None chosen", "Nessuna scelta");
        it("%d chosen", "%d scelte");

        // Search & shortcuts
        it("Swipe up on the home screen to search", "Scorri in su nella schermata Home per cercare");
        it("Find contacts", "Trova i contatti");
        it("Asks to read your contacts", "Chiede di leggere i tuoi contatti");
        it("Search inside apps' shortcuts", "Cerca nelle scorciatoie delle app");
        it("Like “new message” or “navigate home” from your apps", "Come “nuovo messaggio” o “naviga verso casa” dalle tue app");
        it("Search Android settings", "Cerca nelle impostazioni di Android");
        it("Find pages like Wi-Fi or Bluetooth by name", "Trova pagine come Wi-Fi o Bluetooth per nome");
        it("%d saved · web pages, numbers, places, app links", "%d salvate · pagine web, numeri, luoghi, link alle app");
        it("Web search engine", "Motore di ricerca web");
        it("Web searches open in your browser. Ganimede itself never goes online.", "Le ricerche web si aprono nel browser. Ganimede non va mai online.");
        it("Your own shortcuts show up when you search. One can open a web page, call someone, write an email, show a place on the map or open any app link.",
                "Le tue scorciatoie compaiono quando cerchi. Possono aprire una pagina web, chiamare qualcuno, scrivere un'email, mostrare un luogo sulla mappa o aprire qualsiasi link di un'app.");
        it("No shortcuts yet.", "Ancora nessuna scorciatoia.");
        it("Add a shortcut", "Aggiungi una scorciatoia");
        it("Edit shortcut", "Modifica scorciatoia");
        it("Name, e.g. Home", "Nome, per esempio Casa");
        it("Link", "Link");
        it("Examples:\nhttps://example.org — a web page\ntel:+390212345678 — call a number\nmailto:me@example.org — write an email\ngeo:0,0?q=Milano — a place on the map\nor any app link, like spotify:… or whatsapp://…\nhttps://it.wikipedia.org/w/index.php?search=%s — a search shortcut: %s becomes what you type in search",
                "Esempi:\nhttps://example.org — una pagina web\ntel:+390212345678 — chiama un numero\nmailto:io@example.org — scrivi un'email\ngeo:0,0?q=Milano — un luogo sulla mappa\no qualsiasi link di un'app, come spotify:… o whatsapp://…\nhttps://it.wikipedia.org/w/index.php?search=%s — una scorciatoia di ricerca: %s diventa quello che scrivi nella ricerca");
        it("Delete", "Elimina");
        it("Give it a name", "Dagli un nome");
        it("That doesn't look like a link. See the examples below.", "Non sembra un link. Guarda gli esempi qui sotto.");

        // Gestures
        it("Double-tap the home screen or the alphabet to turn the screen off", "Tocca due volte la schermata Home o l'alfabeto per spegnere lo schermo");
        it("Accessibility for Quick Lock", "Accessibilità per il blocco rapido");
        it("It only runs system actions (lock, screenshot, recent apps…) and reads nothing on screen", "Esegue solo azioni di sistema (blocco, screenshot, app recenti…) e non legge niente sullo schermo");
        it("Vibration feedback", "Vibrazione al tocco");

        // Notifications & wellbeing
        it("Needed for the summary and the notification dots", "Serve per il riepilogo e i pallini delle notifiche");
        it("Non-urgent notifications arrive together, about every six hours", "Le notifiche non urgenti arrivano tutte insieme, circa ogni sei ore");
        it("Notification preview under favourites", "Anteprima delle notifiche sotto i preferiti");
        it("Otherwise slide a favourite sideways to see them", "Altrimenti fai scorrere di lato un preferito per vederle");
        it("Calls, alarms, reminders and other urgent notifications always come through at once. %d notifications batched so far.",
                "Chiamate, sveglie, promemoria e altre notifiche urgenti arrivano sempre subito. Finora %d notifiche raggruppate.");
        it("Next summary around %s.", "Prossimo riepilogo verso le %s.");
        it("Deliver conversations right away", "Consegna subito le conversazioni");
        it("Also summarize silent notifications", "Raggruppa anche le notifiche silenziose");
        it("Apps to summarize", "App da raggruppare nel riepilogo");
        it("Remind me to take a break after", "Ricordami di fare una pausa dopo");
        it("%d min", "%d min");
        it("1 hour", "1 ora");
        it("Apps for break reminders", "App per i promemoria pausa");
        it("Post notifications", "Invio notifiche");
        it("Needed to show break reminders", "Serve per mostrare i promemoria pausa");

        // Privacy & permissions
        it("Permissions", "Permessi");
        it("Dots, previews, media player and summary", "Pallini, anteprime, lettore musicale e riepilogo");
        it("Agenda and next event", "Agenda e prossimo evento");
        it("Find contacts in search", "Trova i contatti nella ricerca");
        it("Accessibility (Quick Lock and button actions)", "Accessibilità (blocco rapido e azioni del pulsante)");
        it("Turns the screen off with a double-tap", "Spegne lo schermo con un doppio tocco");
        it("Ganimede never connects to the internet: it doesn't even have permission to. No accounts, no ads, no analytics. The weather comes from Breezy Weather on your phone, and backups stay where you save them.",
                "Ganimede non si collega mai a internet: non ha nemmeno il permesso di farlo. Niente account, niente pubblicità, niente statistiche. Il meteo arriva da Breezy Weather sul tuo telefono e i backup restano dove li salvi.");
        it("Voice notes in notifications are played by Ganimede, not by the chat app, so no “listened” receipt is sent. A copy of each note stays on this phone, out of backups, while its notification is shown or held for the summary, then it is deleted.",
                "Le note vocali nelle notifiche le riproduce Ganimede, non l'app di chat: nessuna conferma di ascolto viene inviata. Una copia di ogni nota resta su questo telefono, fuori dai backup, finché la sua notifica è mostrata o in attesa del riepilogo, poi viene cancellata.");
        it("Android's page for Ganimede: permissions and restricted settings", "La pagina di Android per Ganimede: permessi e impostazioni con restrizioni");
        it("Allowed", "Consentito");
        it("Allow", "Consenti");
        it("Allow it under Permissions in App info", "Consentilo in Info app → Permessi");
        it("Switch on \"Ganimede\" on the next screen. Message text never leaves your phone.",
                "Attiva \"Ganimede\" nella prossima schermata. Il testo dei messaggi non lascia mai il tuo telefono.");
        it("Switch on \"%s\" under Accessibility. It only runs system actions (lock, screenshot, recent apps…) and reads nothing on screen.",
                "Attiva \"%s\" in Accessibilità. Esegue solo azioni di sistema (blocco, screenshot, app recenti…) e non legge niente sullo schermo.");
        it("1. Open and switch it on.\n2. If Android blocks it, press OK.\n3. App info → ⋮ (top right) → \"Allow restricted settings\".\n4. Come back and switch it on again.\nAndroid asks this of every app installed from a file instead of a store.",
                "1. Apri e attivala.\n2. Se Android la blocca, premi OK.\n3. Info app → ⋮ (in alto a destra) → \"Consenti impostazioni con restrizioni\".\n4. Torna qui e attivala di nuovo.\nAndroid lo chiede per ogni app installata da un file invece che da uno store.");

        // Hidden apps
        it("No hidden apps. Long-press an app and choose Hide. Hidden apps still show up when you search for them.",
                "Nessuna app nascosta. Tieni premuta un'app e scegli Nascondi. Le app nascoste compaiono comunque quando le cerchi.");

        // Backup & restore
        it("Saves favourites, pop-ups, names, hidden apps and every setting to a file you choose. Widgets and pinned shortcuts have to be added again after a restore. Keep the file private: it lists your apps.",
                "Salva preferiti, pop-up, nomi, app nascoste e tutte le impostazioni in un file che scegli tu. Dopo un ripristino widget e scorciatoie fissate vanno aggiunti di nuovo. Tieni il file per te: contiene l'elenco delle tue app.");
        it("Create backup", "Crea un backup");
        it("Choose where to save the file", "Scegli dove salvare il file");
        it("Restore a backup", "Ripristina un backup");
        it("Replaces your current setup", "Sostituisce la configurazione attuale");
        it("No file picker on this phone", "Su questo telefono non c'è un selettore di file");
        it("Wait until the file operation under way has finished", "Aspetta che finisca l'operazione sui file in corso");
        it("Backup created", "Backup creato");
        it("Could not write the backup", "Impossibile scrivere il backup");
        it("Restore backup?", "Ripristinare il backup?");
        it("This replaces your current setup and can't be undone.", "Sostituisce la configurazione attuale e non si può annullare.");
        it("Restore", "Ripristina");
        it("Backup restored", "Backup ripristinato");
        it("That file isn't a Ganimede backup", "Quel file non è un backup di Ganimede");

        // Language and about
        it("Phone language", "Lingua del telefono");
        it("The whole launcher switches language right away.", "Tutto il launcher cambia lingua all'istante.");
        it("Version %s · GPL-3.0 licence", "Versione %s · licenza GPL-3.0");
        it("A free, minimalist home screen without Google: no ads, no tracking, no internet.",
                "Una schermata Home libera e minimalista, senza Google: niente pubblicità, niente tracciamento, niente internet.");
        it("Weather by Breezy Weather, sent from your own phone.", "Meteo di Breezy Weather, inviato dal tuo stesso telefono.");
        it("Built-in icons: Arcticons by Donnnno and contributors (github.com/Arcticons-Team/Arcticons), CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/), converted and algorithmically restyled.",
                "Icone integrate: Arcticons di Donnnno e collaboratori (github.com/Arcticons-Team/Arcticons), CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/), convertite e ridisegnate in modo algoritmico.");
        // Home screen: per-mode positions, edges and sizes
        it("Home screen pages", "Pagine della schermata Home");
        it("Grid sizes start from a size that suits your screen. On the home screen, long-press an icon and drag it to any cell, onto another app to make a folder, into the dock, or to Remove at the top.",
                "Le misure della griglia partono da una dimensione adatta al tuo schermo. Sulla Home tieni premuta un'icona e trascinala in qualsiasi cella, sopra un'altra app per creare una cartella, nel dock, oppure su Rimuovi in alto.");
        it("Grid sizes start from a size that suits your screen. Widgets and the clock occupy cells too; long-press them to move, resize or remove them.",
                "Le misure della griglia partono da una dimensione adatta al tuo schermo. Anche widget e orologio occupano celle; tienili premuti per spostarli, ridimensionarli o rimuoverli.");
        it("Position", "Posizione");
        it("Date and weather", "Data e meteo");
        it("Widgets", "Widget");
        it("App grid", "Griglia delle app");
        it("Dock", "Dock");
        it("Apps column: most of the screen it takes (%)", "Colonna delle app: quanto schermo può occupare (%)");
        it("Dock background (%, 0 = transparent)", "Sfondo del dock (%, 0 = trasparente)");
        it("Left edge", "Bordo sinistro");
        it("Right edge", "Bordo destro");
        it("Top edge", "Bordo superiore");
        it("Bottom edge", "Bordo inferiore");
        it("Bottom edge: lift everything", "Bordo inferiore: alza tutto");
        it("Clock size (%)", "Dimensione orologio (%)");
        it("Widget height (0 = automatic)", "Altezza widget (0 = automatica)");
        it("Move left", "Sposta a sinistra");
        it("Move right", "Sposta a destra");
        it("Reset position", "Ripristina posizione");

        // Home screen: the bricks
        it("Your home", "La tua Home");
        it("What it shows", "Cosa mostra");
        it("How it behaves", "Come si comporta");
        it("This phone", "Questo telefono");
        it("Touch a part of the preview to adjust it", "Tocca una parte dell'anteprima per regolarla");
        it("Undo", "Annulla");
        it("Decrease", "Diminuisci");
        it("Increase", "Aumenta");
        it("The app drawer", "Cassetto delle app");
        it("Drawer", "Cassetto");
        it("Follow home", "Segui home");
        it("Use the home's icons, app names and font", "Usa icone, nomi delle app e carattere della Home");
        it("Grid", "Griglia");
        it("Home grid size", "Dimensione griglia Home");
        it("Whole screen", "Tutto lo schermo");
        it("Position and size", "Posizione e dimensione");
        it("Inside this brick", "Dentro il mattoncino");
        it("Line it up", "Allinea");
        it("Left", "Sinistra");
        it("Centre", "Centro");
        it("Right", "Destra");
        it("Space between apps", "Spazio tra le app");
        it("Apps move sideways up to the alphabet, never under it.", "Le app si spostano di lato fino all'alfabeto, mai sotto.");
        it("No widgets yet. Long-press an empty part of the home screen and choose “Add widget”; then move and size them here.",
                "Ancora nessun widget. Tieni premuto un punto vuoto della Home e scegli “Aggiungi widget”; poi spostali e ridimensionali qui.");
        it("Your home screen is made of bricks. Touch one in the preview to pick it, drag it to move it, pull its round knob to size it.",
                "La tua Home è fatta di mattoncini. Toccane uno nell'anteprima per sceglierlo, trascinalo per spostarlo, tira il suo pomello tondo per ridimensionarlo.");
        it("Your home screen is made of bricks. Icons, widgets and the clock are arranged on the home screen itself; here you set the grid they sit on and the dock: touch one in the preview.",
                "La tua Home è fatta di mattoncini. Icone, widget e orologio si sistemano direttamente sulla Home; qui regoli la griglia su cui poggiano e il dock: toccane uno nell'anteprima.");
        it("Arrange icons, widgets and the clock", "Sistema icone, widget e orologio");
        it("Right on the home screen: long-press one to move it, resize it or remove it",
                "Direttamente sulla Home: tienine premuto uno per spostarlo, ridimensionarlo o rimuoverlo");
        it("Long-press an icon, a widget or the clock, then drag it. Its menu resizes or removes it.",
                "Tieni premuta un'icona, un widget o l'orologio, poi trascina. Dal suo menu lo ridimensioni o lo rimuovi.");
    }

    private ItSettings() {}
}
