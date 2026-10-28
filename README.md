# NFC USB Link Opener

App Android nativa che si comporta come un lettore NFC esterno. Un ESP32-S3 collegato in USB-OTG invia righe seriali:

```text
URL:https://esempio.it/tags/P04a
```

Quando la riga è valida, l’app apre il link nel browser predefinito con `Intent.ACTION_VIEW`, come se il telefono avesse letto un tag NFC. Il firmware non va modificato: l’app si limita a leggere e reagire.

## Requisiti

- Android 8.0 (API 26) o successivo, con USB Host / OTG.
- Android Studio recente (AGP 8.13 è supportato dalle versioni correnti).
- JDK 17.
- Cavo USB-OTG e un dispositivo seriale compatibile: CDC/ACM (anche ESP32-S3 in modalità USB CDC), FTDI, CP210x, CH340 o Prolific.
- Velocità predefinita: **115200** baud, 8N1. Si cambia dalla schermata e vale dalla connessione successiva.

L’USB Serial/JTAG nativo di alcuni firmware ESP32-S3 **non** è CDC/ACM. In quel caso la porta non compare tra i dispositivi seriali: serve un firmware che esponga CDC/ACM, oppure un convertitore USB-UART. Questa app non cambia il firmware.

Non servono permessi NFC né servizi cloud.

## Aprire il progetto in Android Studio

1. Android Studio → **File → Open** e seleziona questa cartella.
2. Attendi il sync di Gradle. Se manca un SDK, installa **Android SDK Platform 36** e **Build-Tools 35**.
3. Collega il telefono con il debug USB attivo, oppure usa un emulatore per la sola modalità simulata.
4. Premi **Run**.

## Compilare e installare l’APK

Da Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**. L’APK di debug è in `app/build/outputs/apk/debug/`.

Da terminale, con `ANDROID_HOME` configurato:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

I test di parser, debounce e buffer di riga:

```bash
./gradlew test
```

## Autorizzazione OTG

1. Collega l’ESP32 al telefono con l’adattatore OTG. Se il telefono chiede quale app aprire, scegli **NFC USB Link Opener**.
2. Nell’app premi **Avvia reader**.
3. Accetta il dialogo di sistema del permesso USB. Lo stato diventa **Reader attivo** e compare la notifica persistente **Reader NFC USB attivo**.
4. Su Android 13+ accetta anche le notifiche: servono per il reader in background e per il pulsante **Apri link**.
5. Avvicina un tag. Il firmware invia `URL:https://...` e, se l’app è in primo piano e l’apertura automatica è attiva, si apre il browser.

Per fermare: **Ferma reader** nella schermata o nella notifica. La porta seriale, la coroutine di lettura e il servizio vengono chiusi.

Se scolleghi il cavo lo stato diventa **Reader disconnesso**. Ricollegando, con il reader ancora avviato, l’app chiede di nuovo il permesso se serve e riapre la porta. Dopo un kill del processo il servizio non riparte da solo (`START_NOT_STICKY`): riapri l’app oppure ricollega il dispositivo se il reader era rimasto armato.

## Schermata

- Stato: **Reader non collegato**, **In attesa autorizzazione USB**, **Reader attivo**, **Reader disconnesso**.
- Avvio e arresto. Con più seriali USB si sceglie il dispositivo.
- Baud rate modificabile.
- **Apri automaticamente i link**. Se è spento, l’URL viene solo registrato.
- **Domini consentiti**, uno per riga. Vuoto = tutti gli `http`/`https` validi. `esempio.it` include anche i sottodomini, per esempio `tag.esempio.it`. `*.esempio.it` è accettato. Le righe che non sono nomi host vengono ignorate.
- Ultime righe ricevute, con **Copia log** e **Condividi log**.

## Regole sulle righe

- È valida solo una riga che inizia esattamente con `URL:`.
- L’URL si legge senza spazi iniziali e finali e deve iniziare con `http://` o `https://` e avere un host.
- URL malformati e domini fuori elenco si registrano e non si aprono.
- Le altre righe (debug dell’ESP32) si ignorano e compaiono nel log come diagnosi.
- Lo stesso URL non si apre due volte entro **1500 ms**. Il log indica `Lettura duplicata ignorata`. Un duplicato ignorato non allunga la finestra: passato il tempo, la lettura è di nuovo valida.
- I byte si accumulano finché arriva `\n` (va bene anche `\r\n`), così una riga spezzata in più letture USB resta integra.

## App in background

Il servizio in primo piano parte solo da **Avvia reader**, dopo il consenso USB. La notifica persistente mostra lo stato e **Ferma reader**.

Se l’app è visibile, il link si apre subito. Se non è visibile, Android può bloccare l’apertura di un’activity: l’app tenta comunque l’apertura e, in ogni caso, mostra la notifica ad alta priorità **Link NFC rilevato** con **Apri link**. Quel tocco è il gesto che il sistema accetta. Non vengono usati overlay né servizi di accessibilità.

## Prova senza ESP32

Premi **Simula lettura**. Vengono elaborate, in ordine:

1. `URL:https://esempio.it/tags/P04a` — link valido, apre il browser se l’apertura automatica è attiva;
2. la stessa riga — duplicato immediato, ignorato;
3. `URL:non-e-un-url` — URL non valido;
4. `DEBUG: lettura tag senza URL` — riga ignorata.

Per vedere il duplicato diventare valido, aspetta almeno 1,5 secondi e premi di nuovo **Simula lettura**.

## Passi che restano sul telefono

La build e i test unitari non sostituiscono la prova hardware:

- collegare l’adattatore OTG e accettare il dialogo USB di Android;
- accettare le notifiche su Android 13+;
- verificare che l’ESP32 esponga CDC/ACM (o un chip USB-UART supportato) a 115200 baud;
- controllare che, con l’app in background, il link arrivi come notifica **Link NFC rilevato** se il sistema blocca l’apertura diretta.
