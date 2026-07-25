# Mijn Medicijnen

Een Android-app om medicijnen bij te houden: dagelijkse herinneringen én een waarschuwing dat je
op tijd moet **bijbestellen**, rekening houdend met de levertijd van de apotheek.

Alles staat lokaal op de telefoon. Geen account, geen server, geen internet nodig.

## Wat de app doet

- **Zelf medicijnen toevoegen** - niets zit vastgebakken in de app. Naam, sterkte, tijdstippen en
  schema vul je zelf in.
- **Vier soorten schema's**
  - elke dag
  - om de zoveel dagen
  - vaste weekdagen
  - **kuur**: X dagen slikken, Y dagen pauze (de anticonceptiepil, standaard 21/7). Je zet de
    eerste dag van je huidige strip en de app loopt vanzelf mee, ook over maandgrenzen heen.
    Voor strips met placebopillen kun je ook tijdens de pauze herinnerd worden.
- **Herinneringen die aankomen** - exacte alarmen die een herstart, een tijdzonewissel en de
  slaapstand van de telefoon overleven. In de melding zitten knoppen voor *Ingenomen*, *Snooze*
  en *Overslaan*, dus je hoeft de app niet te openen.
- **Bijbestellen vóórdat je zonder zit** - vul je voorraad, het aantal per verpakking en de
  levertijd van de apotheek in. De app rekent uit wanneer je voorraad op is, trekt de levertijd
  en een marge eraf, en waarschuwt op dát moment.
- **Widget op het startscherm** met wat je vandaag nog moet innemen; aantikken is genoeg om het
  af te vinken.
- **Geschiedenis** met een maandkalender en therapietrouw per medicijn.

De voorraadberekening houdt rekening met het schema: een 21/7-pil verbruikt 21 pillen per 28
dagen, niet 28. Een simpele "waarschuw onder de 10 pillen" zou daardoor te vroeg of te laat zijn.

## De app op je telefoon zetten

Er is geen Play Store nodig - GitHub Actions bouwt bij elke push een APK.

1. Ga naar het tabblad **Actions** in deze repository en open de laatste gelukte run.
2. Download onder **Artifacts** het bestand `medicijntracker-apk`.
3. Pak het uit en open de `.apk` op de telefoon (installeren uit onbekende bron toestaan).

Voor een vaste downloadlink kun je een tag pushen (`git tag v1.0.0 && git push origin v1.0.0`);
de APK wordt dan ook aan een GitHub Release gehangen.

### Belangrijk bij de eerste keer

De app laat op het Vandaag-scherm een rode kaart zien zolang Android iets tegenhoudt. Regel die
drie dingen even, anders komen meldingen niet of te laat aan:

- meldingen toestaan
- exacte alarmen toestaan
- **de app vrijstellen van batterijoptimalisatie** - op Samsung en Xiaomi is dit de meest
  voorkomende reden dat herinneringen na een paar dagen stoppen

### Ondertekening instellen (eenmalig, optioneel)

Zonder keystore wordt de APK met de debug-sleutel ondertekend. Dat werkt prima, maar een update
kan dan niet over een eerdere installatie heen als die met een andere sleutel is getekend. Voor
een vaste sleutel:

```bash
keytool -genkeypair -v -keystore keystore.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias medicijntracker
base64 -w0 keystore.jks
```

Zet daarna in *Settings → Secrets and variables → Actions* deze secrets:
`KEYSTORE_BASE64` (de base64-uitvoer), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Bewaar `keystore.jks` goed en commit hem niet - hij staat in `.gitignore`.

## Zelf bouwen

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug-APK
./gradlew installDebug        # rechtstreeks op een aangesloten telefoon
```

Android Studio openen op de projectmap werkt ook gewoon.

## Hoe het in elkaar zit

Kotlin, Jetpack Compose, Room, WorkManager en Glance voor de widget. Eén module, geen
DI-framework: de afhankelijkheden hangen in `di/AppContainer.kt`.

```
domain/schedule/ScheduleCalculator   wanneer is een medicijn aan de beurt (pure Kotlin, getest)
domain/stock/StockForecaster         wanneer moet er besteld worden (pure Kotlin, getest)
data/db, data/repo                   Room-database en repositories
notify/                              alarmen, meldingen, herstart-herstel, dagelijks onderhoud
ui/                                  Compose-schermen
widget/                              Glance-widget
```

Twee dingen zijn bewust zo gebouwd:

**Meldingskanalen per medicijn.** Android laat een kanaal na aanmaken niet meer wijzigen, dus een
ander geluid instellen betekent een nieuw kanaal. Het kanaal-id bevat daarom een versienummer
(`med_3_v2`). Bijkomend voordeel: elk medicijn heeft zijn eigen kanaal in de Android-instellingen,
dus je kunt daar nu al per medicijn een eigen geluid en trilpatroon kiezen.

**Alarmen worden bijgehouden.** Android kan niet opsommen welke alarmen openstaan. De app houdt
daarom zelf bij welke doses een alarm hebben, zodat een dosis die is ingenomen, gewijzigd of
verwijderd geen spookmelding meer geeft. `BootReceiver` en een dagelijkse `WorkManager`-taak
zetten alles opnieuw klaar na een herstart of een dag zonder de app te openen.

## Nog te doen

- Een eigen meldingsgeluid kiezen ín de app (kan nu via de Android-instellingen per medicijn).
- Export/import van de gegevens als back-up bij een nieuwe telefoon.
