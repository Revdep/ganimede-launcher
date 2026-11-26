# Ganimede Launcher

**Versione 0.0.11 · beta**

Un launcher Android libero, senza Google e completamente offline. Ha due modi di mostrare la Home, che condividono
tutto il resto: **Lista** (orologio, preferiti in colonna, alfabeto a onda sul bordo) e **Classica** (griglia a pagine,
dock, cartelle, cassetto delle app). Italiano e inglese.

## Cosa fa

- **Home Lista**: orologio, data, meteo, prossimo evento, lettore musicale, preferiti in colonna e alfabeto a onda.
- **Home Classica**: griglia a pagine, dock, cartelle, widget e cassetto delle app (facoltativo).
- **Ricerca**: app, contatti, scorciatoie e impostazioni; i motori web aprono solo il browser.
- **Notifiche**: pallini, anteprime, popup completo, riepilogo a finestre di 6-7 ore e promemoria per le pause.
- **Aspetto**: forme e pacchetti di icone, caratteri, colori, temi pronti e disposizione libera della Home.
- **Meteo**: solo da [Breezy Weather](https://github.com/breezy-weather/breezy-weather); Ganimede non si collega mai a internet.
- **Backup**: tutte le impostazioni in un file JSON scelto da te.

La guida all'uso è in [docs/GUIDA.md](docs/GUIDA.md).

## Privacy e permessi

Nessun permesso INTERNET, nessun servizio Google, nessun tracciamento. Ogni permesso si chiede solo quando accendi la
funzione che lo usa; Ganimede funziona anche senza.

| Permesso | Serve a |
|---|---|
| Calendario, Contatti | prossimo evento; contatti nella ricerca |
| Accesso alle notifiche | pallini, anteprime, riepilogo, lettore musicale |
| Accessibilità | azioni di sistema: blocca schermo, screenshot, app recenti, menu di spegnimento, tendina, impostazioni rapide |
| Pubblicare notifiche | promemoria per le pause |
| Espandi barra di stato | swipe giù per aprire le notifiche |
| Richiesta disinstallazione | "Disinstalla" dal menu di un'app |
| Sveglie (`SET_ALARM`) | azioni "nuova sveglia" e "nuovo timer" |

## Installare

Usa [Obtainium](https://github.com/ImranR98/Obtainium): aggiungi l'indirizzo di questo repository e installa l'APK
delle [Release](../../releases). Poi scegli Ganimede come app Home. Se hai installato l'APK da file, Android 13+ può
bloccare notifiche e accessibilità: Info app → ⋮ → "Consenti impostazioni con restrizioni".

## Compilare

Serve l'SDK Android (compileSdk 35) e Java 17:

```
./gradlew assembleDebug assembleRelease
```

I test del nucleo (`coreTest`) girano prima di ogni build. Gli APK finiscono nella cartella principale; senza
`keystore.properties` la release è firmata con la chiave di debug.

## Licenza

Copyright (C) 2026 Revdep

Ganimede Launcher è software libero: puoi ridistribuirlo e modificarlo secondo i termini della GNU General Public
License pubblicata dalla Free Software Foundation, versione 3 o (a tua scelta) qualsiasi versione successiva
(GPL-3.0-or-later). È distribuito sperando che sia utile, ma SENZA ALCUNA GARANZIA. Il testo completo è nel file
[LICENSE](LICENSE).

Ganimede non è affiliato né collegato ad altri launcher o ai loro autori.
