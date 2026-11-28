package org.ganimede.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translations. Text is written in English in the code and looked up here by that English text.
 * Language comes from the "lang" setting: "en", "it", or "" for the phone's language. Changes apply at once
 * because every screen reads T on each rebuild.
 * Settings-screen strings live in ItSettings.IT; home-screen strings, and those both use, in IT below (once each).
 */
final class T {
    static final String[] LANGS = {"", "en", "it"};
    static final String[] LANG_NAMES = {"Phone language / Lingua del telefono", "English", "Italiano"};

    private static final Map<String, String> IT = new HashMap<>();

    /** The "lang" setting, read once and kept current by WATCH: every text on screen asks for it. */
    private static volatile String lang;
    // Held here: the settings file keeps only a weak reference to its listeners.
    private static final SharedPreferences.OnSharedPreferenceChangeListener WATCH = (p, k) -> {
        if (k == null || k.equals("lang")) lang = p.getString("lang", ""); // null: everything was cleared (a restore)
    };

    static boolean italian(Context c) {
        if (lang == null) {
            SharedPreferences p = Prefs.of(c);
            p.registerOnSharedPreferenceChangeListener(WATCH);
            lang = p.getString("lang", "");
        }
        return (lang.isEmpty() ? Locale.getDefault().getLanguage() : lang).equals("it");
    }

    static Locale locale(Context c) {
        return italian(c) ? Locale.ITALIAN : Locale.ENGLISH;
    }

    static void refresh(SharedPreferences p) {
        lang = p.getString("lang", "");
    }

    /** The text in the chosen language (falls back to the English text). */
    static String t(Context c, String en) {
        if (!italian(c)) return en;
        String s = IT.get(en);
        if (s == null) s = ItSettings.IT.get(en);
        return s == null ? en : s;
    }

    /** Translated format string: t(c, "Search %s for “%s”") then String.format. */
    static String f(Context c, String en, Object... args) {
        return String.format(locale(c), t(c, en), args);
    }

    private static void it(String en, String it) {
        IT.put(en, it);
    }

    static {
        // Home screen, list and search
        it("Search apps, contacts, shortcuts…", "Cerca app, contatti, scorciatoie…");
        it("Launcher settings", "Impostazioni launcher");
        it("Long-press any app in the list to add it here.\nSwipe up or touch the letters to see all apps.",
                "Tieni premuta un'app nell'elenco per aggiungerla qui.\nScorri in su o tocca le lettere per vedere tutte le app.");
        it("Hidden", "Nascoste");
        it("Contacts", "Contatti");
        it("Show all (%d)", "Mostra tutti (%d)");
        it("Shortcuts", "Scorciatoie");
        it("Settings pages", "Pagine impostazioni");
        it("Web", "Web");
        it("Search %s for “%s”", "Cerca “%2$s” con %1$s");
        it("No browser found", "Nessun browser trovato");
        it("Open agenda", "Apri agenda");
        it("Open calendar", "Apri calendario");
        it("No app can open this", "Nessuna app può aprirlo");
        it("Could not open %s", "Impossibile aprire %s");
        it("set weather", "imposta meteo");
        it("weather", "meteo");
        // App menu
        it("Add to favourites", "Aggiungi ai preferiti");
        it("Remove from favourites", "Rimuovi dai preferiti");
        it("Move up", "Sposta su");
        it("Move down", "Sposta giù");
        it("Pin a shortcut to favourites…", "Fissa una scorciatoia nei preferiti…");
        it("Add to pop-up folder…", "Aggiungi a una cartella pop-up…");
        it("Rename", "Rinomina");
        it("Hide", "Nascondi");
        it("Unhide", "Mostra di nuovo");
        it("Music app (show with headphones)", "App musicale (mostra con le cuffie)");
        it("Break reminder", "Promemoria pausa");
        it("Notification summary", "Riepilogo notifiche");
        it("App info", "Info app");
        it("Uninstall", "Disinstalla");
        it("Ganimede settings", "Impostazioni Ganimede");
        it("Pin shortcut", "Fissa scorciatoia");
        it("This shortcut can't be opened", "Questa scorciatoia non si può aprire");
        it("This shortcut is no longer available", "Questa scorciatoia non è più disponibile");
        it("Remove", "Rimuovi");
        it("Cancel", "Annulla");
        it("Save", "Salva");
        it("Close", "Chiudi");
        it("Original name", "Nome originale");
        it("%s is back in the list.", "%s è di nuovo nell'elenco.");
        it("%s hidden. Search still finds it.", "%s nascosta. La ricerca la trova comunque.");
        // Home menu, pop-ups, widgets
        it("Add widget", "Aggiungi widget");
        it("Add clock", "Aggiungi orologio");
        it("Widget", "Widget");
        it("Resize", "Ridimensiona");
        it("Resize left edge", "Ridimensiona bordo sinistro");
        it("Resize right edge", "Ridimensiona bordo destro");
        it("Resize top edge", "Ridimensiona bordo superiore");
        it("Resize bottom edge", "Ridimensiona bordo inferiore");
        it("Remove a widget", "Rimuovi un widget");
        it("Create pop-up folder", "Crea cartella pop-up");
        it("Notifications", "Notifiche");
        it("Wallpaper", "Sfondo");
        it("Settings", "Impostazioni");
        it("No widgets yet", "Nessun widget");
        it("Folder name", "Nome cartella");
        it("Folder", "Cartella");
        it("+ New folder", "+ Nuova cartella");
        it("Choose apps", "Scegli app");
        it("Customize folder", "Personalizza cartella");
        it("Use the defaults", "Usa i valori predefiniti");
        it("Delete folder", "Elimina cartella");
        it("Apps in this pop-up", "App in questo pop-up");
        it("This pop-up is empty. Add apps or a widget below.", "Questo pop-up è vuoto. Aggiungi app o un widget qui sotto.");
        it("✎ Edit pop-up", "✎ Modifica pop-up");
        it("%s removed from the pop-up", "%s tolta dal pop-up");
        it("Widget stack full: remove a widget first", "Pila di widget piena: togli prima un widget");
        it("Remove widget", "Rimuovi widget");
        it("Missing widget", "Widget mancante");
        // Notifications pop-up
        it("No notifications", "Nessuna notifica");
        it("%s, 1 notification", "%s, 1 notifica");
        it("%s, %d notifications", "%s, %d notifiche");
        it("Allow notification access in settings first", "Prima consenti l'accesso alle notifiche nelle impostazioni");
        it("Clear all", "Cancella tutte");
        it("Dismiss", "Ignora");
        it("now", "ora");
        it("%d min ago", "%d min fa");
        it("%d h ago", "%d h fa");
        // Calendar and weather
        it("Your agenda", "La tua agenda");
        it("No events in the next two weeks.", "Nessun evento nelle prossime due settimane.");
        it("New event", "Nuovo evento");
        it("Calendar", "Calendario");
        it("Today", "Oggi");
        it("Tomorrow", "Domani");
        it("Now", "Adesso");
        it("All day", "Tutto il giorno");
        it("Humidity %d%%", "Umidità %d%%");
        it("Wind %d km/h", "Vento %d km/h");
        it("Source: %s · updated %s", "Fonte: %s · aggiornato %s");
        it("Breezy Weather", "Breezy Weather");
        it("Weather", "Meteo");
        it("Clear", "Sereno");
        it("Mostly clear", "Poco nuvoloso");
        it("Partly cloudy", "Parzialmente nuvoloso");
        it("Cloudy", "Nuvoloso");
        it("Fog", "Nebbia");
        it("Drizzle", "Pioviggine");
        it("Rain", "Pioggia");
        it("Snow", "Neve");
        it("Thunderstorm", "Temporale");
        // Quick lock and break reminders
        it("Quick Lock", "Blocco rapido");
        it("Time for a break?", "È ora di una pausa?");
        it("You've been on %s for %d minutes.", "Sei su %s da %d minuti.");
        it("Break reminders", "Promemoria pausa");
        it("Break reminders need the notification permission", "I promemoria pausa richiedono il permesso notifiche");
        // Settings pages found by search
        it("Android settings", "Impostazioni Android");
        it("Mobile data", "Dati mobili");
        it("Airplane mode", "Modalità aereo");
        it("Hotspot and tethering", "Hotspot e tethering");
        it("Display and brightness", "Display e luminosità");
        it("Sound and vibration", "Suoni e vibrazione");
        it("Do Not Disturb", "Non disturbare");
        it("Battery", "Batteria");
        it("Storage", "Spazio di archiviazione");
        it("Apps", "App");
        it("Default apps", "App predefinite");
        it("App notifications", "Notifiche app");
        it("Location", "Posizione");
        it("Security", "Sicurezza");
        it("Privacy", "Privacy");
        it("Accessibility", "Accessibilità");
        it("Language and keyboard", "Lingua e tastiera");
        it("Date and time", "Data e ora");
        it("Accounts", "Account");
        it("Developer options", "Opzioni sviluppatore");
        it("About phone", "Info telefono");
        it("Wallpaper and style", "Sfondo e stile");
        it("Cast", "Trasmetti");
        it("Night Light", "Luminosità notturna");
        it("Data usage", "Utilizzo dati");
        // Menus, folders, pinned shortcuts and web apps
        it("More", "Altro");
        it("Create", "Crea");
        it("Add apps to this folder now?", "Vuoi aggiungere app a questa cartella adesso?");
        it("Yes, choose apps", "Sì, scegli le app");
        it("Later", "Più tardi");
        it("Added to %s", "Aggiunta a %s");
        it("Remove from this folder", "Togli da questa cartella");
        it("Add “%s”", "Aggiungi “%s”");
        it("Added", "Aggiunto");
        it("Add", "Aggiungi");
        it("Add widgets with a long-press on the home screen", "Aggiungi i widget tenendo premuto sulla schermata principale");
        // Ganimede button
        it("Ganimede button", "Pulsante Ganimede");
        it("Button icon", "Icona del pulsante");
        it("Default icon", "Predefinita");
        it("Gray", "Grigia");
        it("Your own image…", "Immagine personale…");
        it("That image could not be used", "Non è stato possibile usare quell'immagine");
        it("Tap", "Tocco");
        it("Double tap", "Doppio tocco");
        it("Triple tap", "Triplo tocco");
        it("Long press", "Pressione lunga");
        it("Nothing", "Niente");
        it("Open the button's pop-up", "Apri il pop-up del pulsante");
        it("Search", "Cerca");
        it("Notifications (Ganimede)", "Notifiche (Ganimede)");
        it("Notification shade", "Tendina notifiche");
        it("Quick settings", "Impostazioni rapide");
        it("Switch light / dark look", "Cambia aspetto chiaro / scuro");
        it("Screenshot", "Screenshot");
        it("Camera", "Fotocamera");
        it("Video camera", "Videocamera");
        it("Flashlight on / off", "Torcia accesa / spenta");
        it("Play / pause music", "Riproduci / metti in pausa la musica");
        it("Lock the screen", "Blocca lo schermo");
        it("Recent apps", "App recenti");
        it("Power menu", "Menu di spegnimento");
        it("Agenda", "Agenda");
        it("Weather forecast", "Previsioni meteo");
        it("Wi-Fi panel", "Pannello Wi-Fi");
        it("Volume panel", "Pannello volume");
        it("Open %s", "Apri %s");
        it("An app that is no longer installed", "Un'app non più installata");
        it("This phone has no flashlight", "Questo telefono non ha la torcia");
        it("Off", "Spento");
        it("Show the Ganimede button", "Mostra il pulsante Ganimede");
        it("A round button in the bottom corner", "Un pulsante rotondo nell'angolo in basso");
        it("Gestures", "Gesti");
        it("Double and triple tap wait a moment for the next tap; leave them on “Nothing” and a single tap answers instantly.",
                "Doppio e triplo tocco aspettano un attimo il tocco successivo; lasciali su “Niente” e il tocco singolo risponde subito.");
        it("Screenshot, lock, recent apps, power menu and quick settings use Ganimede's accessibility switch (Settings → Gestures → Quick Lock).",
                "Screenshot, blocco, app recenti, menu di spegnimento e impostazioni rapide usano l'interruttore di accessibilità di Ganimede (Impostazioni → Gesti → Blocco rapido).");
        it("Open an app…", "Apri un'app…");
        it("An app's shortcut…", "Una scorciatoia di un'app…");
        it("One of my shortcuts…", "Una delle mie scorciatoie…");
        it("This app has no shortcuts", "Questa app non ha scorciatoie");
        it("Make Ganimede your home app first to use app shortcuts", "Per usare le scorciatoie delle app imposta prima Ganimede come app home");
        it("No shortcuts yet: add some in Search & shortcuts → My shortcuts", "Ancora nessuna scorciatoia: aggiungile in Ricerca e scorciatoie → Le mie scorciatoie");
        // Layout and alphabet
        it("Ganimede is now your home screen", "Ganimede ora è la tua schermata Home");
        // Setup wizard
        it("Back", "Indietro");
        it("Next", "Avanti");
        it("Previous", "Precedente");
        it("Play", "Riproduci");
        it("(No title)", "(Senza titolo)");
        it("Needs Android 9 or newer", "Richiede Android 9 o successivo");
        it("Start", "Inizia");
        it("Welcome", "Benvenuto");
        it("Make it your home", "Rendila la tua Home");
        it("Choose a style", "Scegli uno stile");
        it("Make it yours", "Personalizzala");
        it("Your favourite apps", "Le tue app preferite");
        it("Icons", "Icone");
        it("Font", "Carattere");
        it("Clock", "Orologio");
        it("The Ganimede button", "Il pulsante Ganimede");
        it("All set", "Tutto pronto");
        it("Welcome to Ganimede", "Benvenuto in Ganimede");
        it("A fast, private home screen: no Google, no ads, no internet. Let's set it up in a minute. First, your language.",
                "Una schermata Home veloce e riservata: niente Google, niente pubblicità, niente internet. La configuriamo in un minuto. Per prima cosa, la lingua.");
        it("One quick permission. It opens a small window right here and brings you back.",
                "Un permesso veloce. Apre una piccola finestra qui e poi ti riporta indietro.");
        it("Ganimede opens when you press Home", "Ganimede si apre quando premi Home");
        it("You can skip this and allow it later in Settings.", "Puoi saltarlo e consentirlo più tardi nelle Impostazioni.");
        it("List keeps a few favourites and an A–Z list. Classic is a grid of icons with a dock and an app drawer. You can switch any time.",
                "Lista tiene pochi preferiti e un elenco A–Z. Classica è una griglia di icone con un dock e un cassetto delle app. Puoi cambiare quando vuoi.");
        it("Pick your apps, icons, font, clock and the Ganimede button now, or start with the default setup and change things later.",
                "Scegli adesso app, icone, carattere, orologio e pulsante Ganimede, oppure parti con la configurazione standard e cambia tutto dopo.");
        it("Customize now", "Personalizza adesso");
        it("Five short steps", "Cinque passi brevi");
        it("Use the default setup", "Usa la configurazione standard");
        it("Start right away; everything is in Settings", "Inizia subito; trovi tutto nelle Impostazioni");
        it("Tick the apps you use most. They go on the home screen; the rest are one swipe away.",
                "Spunta le app che usi di più. Vanno nella schermata Home; le altre sono a uno scorrimento di distanza.");
        it("How app icons look. The preview above changes as you choose.", "Come appaiono le icone delle app. L'anteprima qui sopra cambia mentre scegli.");
        it("Icon pack", "Pacchetto icone");
        it("Each one is shown in its own letters.", "Ognuno è mostrato con le sue lettere.");
        it("Nine styles, from a simple time to words and a flip clock.", "Nove stili, dall'ora semplice alle parole e all'orologio a palette.");
        it("A round button that does what you choose: one tap, a double or triple tap, or a long press can open an app or a shortcut, take a screenshot, turn on the flashlight, open the camera, switch light and dark, and more. Drag it anywhere; it snaps to the nearest edge.",
                "Un pulsante rotondo che fa quello che scegli: un tocco, un doppio o triplo tocco o una pressione lunga possono aprire un'app o una scorciatoia, fare uno screenshot, accendere la torcia, aprire la fotocamera, passare da chiaro a scuro e altro. Trascinalo dove vuoi: si aggancia al bordo più vicino.");
        it("Use the Ganimede button", "Usa il pulsante Ganimede");
        it("Leave it off for now and try it later in Settings → Ganimede button.", "Lascialo spento per ora e provalo più tardi in Impostazioni → Pulsante Ganimede.");
        it("Swipe up to search apps, contacts and shortcuts. Long-press the home screen for widgets, folders and settings. Long-press an app for its options.",
                "Scorri in su per cercare app, contatti e scorciatoie. Tieni premuto sulla Home per widget, cartelle e impostazioni. Tieni premuta un'app per le sue opzioni.");
        it("Everything can be changed in Settings", "Puoi cambiare tutto nelle Impostazioni");
        it("Long-press the home screen → Ganimede settings", "Tieni premuto sulla Home → Impostazioni Ganimede");
        it("Run the setup wizard again", "Rifai la configurazione guidata");
        // Classic mode and layout
        it("Mode", "Modalità");
        it("List", "Lista");
        it("Classic", "Classica");
        it("Favourites and an A–Z list, like a minimalist launcher", "Preferiti e un elenco A–Z, come un launcher minimalista");
        it("A grid of icons, a dock and an app drawer", "Una griglia di icone, un dock e un cassetto delle app");
        it("Home grid: columns", "Griglia Home: colonne");
        it("Home grid: rows", "Griglia Home: righe");
        it("Dock icons", "Icone nel dock");
        it("App drawer: columns", "Cassetto app: colonne");
        it("Icon size", "Dimensione icone");
        it("Clock above the grid", "Orologio sopra la griglia");
        it("App drawer", "Attiva cassetto app");
        it("App names in the app drawer", "Nomi delle app nel cassetto");
        it("Off: every app sits on the home pages, from page two in alphabetical order, free to move. Pages: long-press an empty spot.", "Spento: tutte le app stanno nelle pagine della Home, dalla seconda in ordine alfabetico, libere di essere spostate. Pagine: tieni premuto un punto vuoto.");
        it("Create page", "Crea pagina");
        it("Delete page", "Elimina pagina");
        it("Arrange pages", "Regola pagine");
        it("Delete this page?", "Eliminare questa pagina?");
        it("Its apps move to free spots on the other pages.", "Le sue app si spostano negli spazi liberi delle altre pagine.");
        it("Its icons leave the home screen (the apps stay in the drawer).", "Le sue icone escono dalla Home (le app restano nel cassetto).");
        it("Its apps move to free spots on the other pages; its blocks are removed.", "Le sue app si spostano negli spazi liberi delle altre pagine; i suoi blocchi vengono rimossi.");
        it("Its icons and blocks leave the home screen (the apps stay in the drawer).", "Le sue icone e i suoi blocchi escono dalla Home (le app restano nel cassetto).");
        it("Drag a page to move it, tap it to go there.", "Trascina una pagina per spostarla, toccala per andarci.");
        it("Done", "Fatto");
        it("New page", "Nuova pagina");
        it("Page %d", "Pagina %d");
        it("Widgets below the clock", "Widget sotto l'orologio");
        it("Off: the widget area sits above the clock", "Spento: l'area dei widget sta sopra l'orologio");
        it("App name size (%)", "Dimensione nomi delle app (%)");
        it("Original", "Originale");
        it("Squircle", "Squircle");
        it("Circle", "Cerchio");
        it("Rounded square", "Quadrato arrotondato");
        it("Teardrop", "Goccia");
        it("No plate", "Nessuna piastra");
        it("Line", "Linea");
        it("Bold", "Marcato");
        it("Sharp", "Spigoloso");
        it("Duotone", "Bicolore");
        it("Hollow", "Contorno");
        it("Dots (minimal)", "Puntini (minimal)");
        it("Remove from home screen", "Togli dalla Home");
        it("Add to home screen", "Aggiungi alla Home");
        it("Remove from dock", "Togli dal dock");
        it("Clock, tap for alarms", "Orologio, tocca per le sveglie");
        it("Tap to open", "Tocca per aprire");
        it("Open link", "Apri link");
        it("Voice message", "Messaggio vocale");
        it("Play voice message", "Riproduci il messaggio vocale");
        it("Pause", "Pausa");
        it("Loading…", "Caricamento…");
        it("Can't play this voice message", "Impossibile riprodurre questo messaggio vocale");
        it("Open", "Apri");
        it("The home screen is full: long-press an empty spot → Create page", "La Home è piena: tieni premuto un punto vuoto → Crea pagina");
        it("The home screen is full", "La Home è piena");
        it("Folder created: tap it to open, long-press to rename", "Cartella creata: toccala per aprirla, tienila premuta per rinominarla");
        it("Add to dock", "Aggiungi al dock");
        it("The dock is full: remove an app first", "Il dock è pieno: togli prima un'app");
        it("Choose home screen apps", "Scegli le app della Home");
        it("Choose favourites", "Scegli i preferiti");
        it("App icon…", "Icona dell'app…");
        it("Original icon", "Icona originale");
        it("Icon for %s", "Icona per %s");
        it("Icon…", "Icona…");
        it("All icon packs", "Tutti i pacchetti di icone");
        it("Search icons", "Cerca icone");
        it("Left-hand use (mirrored)", "Uso con la mano sinistra (specchiato)");
        it("Alphabet on the left, everything aligned to the right", "Alfabeto a sinistra, tutto allineato a destra");
        it("Alphabet", "Alfabeto");
        it("Letter size", "Dimensione lettere");
        it("Letter colour", "Colore lettere");
        it("Same as the text", "Come il testo");
        it("The alphabet answers touches inside its column, even when hidden. The very edge stays free for Back.",
                "L'alfabeto risponde ai tocchi dentro la sua colonna, anche se è nascosto. Il bordo estremo resta libero per Indietro.");
        it("Quick actions", "Azioni rapide");
        it("Search “%s” in %s", "Cerca “%s” in %s");
        it("Search “%s” on %s", "Cerca “%s” su %s");
        it("New alarm", "Nuova sveglia");
        it("New timer", "Nuovo timer");
        it("Alarms", "Sveglie");
        it("New contact", "Nuovo contatto");
        it("New email", "Nuova email");
        it("New message", "Nuovo messaggio");
        it("Phone dialer", "Tastierino telefono");
        it("Take a photo", "Scatta una foto");
        it("Record a video", "Registra un video");
    }

    private T() {}
}
