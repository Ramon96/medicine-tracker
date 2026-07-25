# Mijn Medicijnen — a medicine tracker for Android

Daily medication reminders, plus something most trackers miss: it tells you when to **reorder**,
working backwards from how long your pharmacy takes to deliver.

Everything stays on the phone. No account, no server, no analytics. The only time it touches the
internet is to check whether a newer version of the app exists, and you can turn that off.

> **The app's interface is in Dutch.** The code and this README are in English. If you'd find an
> English UI useful, the strings all live in `app/src/main/res/values/strings.xml`.

---

## Installing it

There's no Play Store listing. Grab the APK directly:

1. Open the [Releases](../../releases) page and download the `.apk` from the latest release.
2. Open it on the phone. Android will ask permission to install from an unknown source — allow it
   for your browser or file manager.
3. Done. Later updates can be installed from inside the app.

### Set this up on first launch

The Vandaag (Today) screen shows a red card while anything would stop reminders arriving. Three
things need to be allowed, and the card links straight to each:

| Permission | Why |
|---|---|
| Notifications | Otherwise reminders are silently dropped |
| Exact alarms | Without it Android batches reminders and they drift by up to 15 minutes |
| **Ignore battery optimisation** | The big one — on Samsung and Xiaomi phones this is the usual reason reminders just stop after a few days |

---

## How it works

### Your medicines, your definitions

Nothing is hard-coded. You add each medicine yourself: name, strength, how many units per dose,
and one or more times of day.

### Four kinds of schedule

- **Every day**
- **Every N days**
- **Fixed weekdays** — e.g. only Monday and Thursday
- **Cycle** — X days on, Y days off, repeating. This is the contraceptive-pill case (21/7 by
  default). You set the first day of your current strip and the app follows along on its own,
  including across month boundaries. Strips with placebo pills can keep reminding through the
  pause week.

### Reminders that actually arrive

Reminders use exact alarms, and are re-armed after a reboot, a clock change or a timezone change,
plus a nightly maintenance pass. Each notification carries **Ingenomen** (taken), **Snooze** and
**Overslaan** (skip) buttons, so a dose can be logged without opening the app.

Each medicine can have its own notification sound and vibration setting, with a preview button —
so you can tell from the sound alone which medicine is due.

### Reordering before you run out

This is the part worth explaining. Fill in three things per medicine: how many units you have,
how many come in a package, and how long your pharmacy takes to deliver.

```
days of supply = stock ÷ average units used per day
run-out date   = today + days of supply
order by       = run-out date − delivery time − safety margin
```

You get the warning on the *order by* date, not when the box is empty.

The average is derived from the schedule rather than assumed, which matters more than it sounds:
a 21/7 contraceptive pill consumes **21 pills per 28 days**, not 28. A naive "warn me below 10
pills left" threshold gets that wrong in both directions depending on where you are in the cycle.

### Home-screen widget

Shows what's still due today. Tapping a row logs it as taken without opening the app. A banner
appears when something needs reordering.

### History

A month calendar colour-coded per day, plus an adherence percentage per medicine. Only doses that
have actually been resolved count, so today's pending doses don't drag the number down all day.

### Making it readable

Under Instellingen → Weergave:

- Light, dark, or follow the system
- Eight colours, each tuned separately for light, dark and high-contrast so text stays legible
- Or Material You, taking colours from your wallpaper
- A high-contrast mode (black on white / white on black, stronger outlines)
- **Text size from 85% to 160%** with a live preview — useful for older eyes
- Three font choices

The widget follows the same colour.

### Updates

Instellingen → Updates shows your version, checks for a newer release on demand, and can check
once a week in the background. It never downloads anything without being asked.

---

## Privacy

The database lives on the phone and nowhere else. There is no account, no telemetry, and no
cloud sync. The `INTERNET` permission is used for exactly one thing: asking GitHub whether a
newer release exists. Turn off the weekly check and the app makes no network requests at all
unless you press the button yourself.

---

## Building it yourself

Kotlin, Jetpack Compose, Room, WorkManager, and Glance for the widget. Single module, no DI
framework — the object graph is in `di/AppContainer.kt`.

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK
./gradlew installDebug        # straight onto a connected phone
```

Opening the project folder in Android Studio also just works.

```
domain/schedule/ScheduleCalculator   when a medicine is due (pure Kotlin, unit-tested)
domain/stock/StockForecaster         when to reorder (pure Kotlin, unit-tested)
update/AppVersion                    release-tag → version comparison (pure Kotlin, unit-tested)
data/db, data/repo                   Room database and repositories
notify/                              alarms, notifications, reboot recovery, daily maintenance
update/                              in-app updater
ui/                                  Compose screens
widget/                              Glance widget
```

The scheduling and forecasting logic is deliberately free of Android types so the awkward parts —
21/7 cycles crossing month boundaries, run-out dates, version comparison — are covered by plain
JVM tests rather than needing a device.

### Two decisions that look odd until they don't

**One notification channel per medicine, with a version in the id** (`med_3_v2`). Android makes
a channel immutable once it has seen it — a sound can only be changed by registering a *new*
channel. So changing a sound bumps the version and retires the old channel. A side benefit is
that every medicine gets its own entry in Android's notification settings.

**The app tracks its own armed alarms.** Android can't enumerate pending alarms, so without a
record of what's armed, a dose that was taken, edited or deleted would still fire later. A
`BootReceiver` and a daily `WorkManager` job rebuild the whole set after a restart or a day the
app was never opened.

### If you fork this

Two things point at this repository and will need changing:

- `UPDATE_REPO` in `app/build.gradle.kts` — where the in-app updater looks for releases.
- Signing. Without a keystore the CI build falls back to a debug key, which differs per machine,
  so updates can't install over each other. Generate one, then add `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` as Actions secrets:

  ```bash
  keytool -genkeypair -v -keystore keystore.jks -keyalg RSA -keysize 2048 \
    -validity 10000 -alias medicijntracker
  base64 -w0 keystore.jks    # paste as KEYSTORE_BASE64
  ```

  Use the same password for the keystore and the key — the PKCS12 format Java produces doesn't
  really support them being different, and the error if they are ("Password is not ASCII") points
  nowhere useful. Keep the file safe: lose it and you can never update an existing install again.

Releasing is a tag:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

CI builds the APK with that version number baked in, verifies its signature, and attaches it to a
GitHub Release, where the in-app updater picks it up.

---

## Not there yet

- Export/import, for moving to a new phone without losing history.
- A free colour picker alongside the eight presets.
- An English translation of the interface.
