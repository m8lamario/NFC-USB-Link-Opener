# NFC USB Operator

App Android per leggere in sequenza tag NFC tramite un ESP32-S3 collegato in USB-OTG. Il firmware invia una riga per tag:

```text
URL:https://esempio.it/tags/P04a
```

L’app apre il link nel browser, mostra il codice (`P04a`) in grande e resta pronta per la lettura successiva. Non usa server, account o cloud. Il firmware non va modificato.

## Aprire il progetto in Android Studio

1. **File → Open** e seleziona questa cartella.
2. Attendi il sync di Gradle. Servono **Android SDK Platform 36** e **Build-Tools 35**.
3. Imposta il JDK di Gradle su **17 o 21** (Settings → Build Tools → Gradle → Gradle JDK). Il JDK 25 di Android Studio non è compatibile con Gradle 8.13.
4. Collega il telefono con il debug USB attivo e premi **Run**.

## Compilare l’APK debug

Da Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

Da terminale, con `JAVA_HOME` su JDK 17 o 21 e l’SDK Android configurato:

```bash
./gradlew assembleDebug
./gradlew test
```

L’APK è in `app/build/outputs/apk/debug/`. Installazione:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Collegare l’ESP32 e autorizzare il reader

1. Collega l’ESP32 al telefono con un adattatore USB-OTG. La porta deve essere CDC/ACM, oppure FTDI, CP210x, CH340 o Prolific, a **115200** baud, 8N1.
2. Se Android chiede con quale app aprire il dispositivo, scegli **NFC USB Operator** e, se compare, **Sempre**.
3. L’app si apre da sola se **Avvia quando colleghi il reader** è attivo (è il valore predefinito, in Impostazioni). Altrimenti premi **Avvia reader**.
4. Accetta il dialogo di sistema del permesso USB. Compare la notifica **Reader NFC USB attivo**.
5. Su Android 13+ accetta le notifiche: servono per il pulsante **Apri link** quando l’app non è in primo piano.
6. Avvicina i tag. Una riga `URL:https://...` valida apre il link, vibra e colora di verde il codice.

L’USB Serial/JTAG nativo di alcuni firmware ESP32-S3 non è CDC/ACM e non compare tra i dispositivi seriali.

**Ferma reader** è nella notifica e in Impostazioni. Scollegare il cavo mostra **Reader disconnesso**; ricollegandolo, con l’avvio automatico attivo, la porta si riapre.

## Schermata operatore

- Stato grande: **Reader pronto**, **Collega il reader USB**, **In attesa del tag**, **Reader disconnesso**, **Errore di lettura**.
- Ultimo codice, ora ed esito: verde se il link è valido e aperto, blu se il duplicato immediato è ignorato, rosso se l’URL, il codice o il dominio non è accettato.
- Contatori di sessione: validi, duplicati, errori. Si azzerano a ogni nuovo avvio del reader.
- **Ultime letture** e l’icona impostazioni. Baud, domini, log e dispositivi non sono sulla schermata principale.

Il codice è l’ultimo segmento dell’URL. Il formato predefinito è un gruppo di lettere, una o più cifre e una lettera finale: `P04a`, `P7B`, `C02b`. Lo stesso URL non si apre due volte entro 1500 ms; un duplicato ignorato non allunga la finestra.

## Impostazioni

- **Reader USB**: dispositivo, baud, riconnetti, stato tecnico, ferma reader.
- **Sicurezza URL**: whitelist, elenco domini (esempio `esempio.it`, inclusi i sottodomini), scelta se aprire o rifiutare i domini fuori elenco, formato del codice.
- **Comportamento**: apertura automatica, avvio al collegamento, debounce, vibrazione, suono, browser esterno o WebView interna, cancella cronologia, esporta CSV.
- **Diagnostica**: log seriale, copia, condividi e modalità simulazione.

La cronologia è in Room. Le impostazioni sono in DataStore, sul telefono.

## Modalità simulata

Impostazioni → **Diagnostica**:

- **Lettura valida**: `URL:https://esempio.it/tags/P04a`
- **Duplicato immediato**: la stessa riga due volte
- **URL non valido**
- **Dominio non autorizzato**
- **Simula scollegamento**

Poi torna alla schermata operatore per vedere colore, codice e contatori. La cronologia conserva l’evento.

## Link con l’app in background

Il servizio in primo piano resta in ascolto. Se Android blocca l’apertura diretta, arriva la notifica **Link NFC rilevato** con **Apri link**. Non si usano overlay né accessibilità.
