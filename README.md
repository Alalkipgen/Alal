# Alal — အလင်း · Android note-taking for feature writers

**Alal** is an offline-first Android notebook for journalists who write long-form pieces in
Burmese and English. Unlimited note length, live Myanmar-aware word count and word goals,
in-note Find & Replace, per-note fonts and backgrounds, and a polished native
Jetpack Compose / Material 3 interface. No WebView, no HTML, no internet permission,
no analytics.

Package: `com.alal.notes` · minSdk 26 (Android 8) · target 35 / compile 36 · Kotlin 2.1 · JDK 17

> **Phase 1 + Phase 2 + Phase 2.1** are delivered in this repository (export, reading mode, outline, backup & restore, stats, reminders,
> PIN/biometric lock, version history, live Markdown styling). See `CHANGELOG.md` for details and
> the roadmap at the bottom of this file for what is left.

---

## Build without local tools (GitHub Actions)

The project is designed to build entirely on GitHub Actions. You do not need Android Studio,
the Android SDK or Gradle on your machine.

### 1. Push the project

```bash
unzip alal-phase1.zip && cd alal
git init -b main
git add . && git commit -m "Alal v1.2.0"
git remote add origin https://github.com/<you>/alal.git
git push -u origin main
```

The **Build APK** workflow (`.github/workflows/build.yml`) runs automatically on every push to
`main`. It sets up JDK 17, the Android SDK, Gradle 8.11.1, fetches the Latin fonts, runs the
unit tests and assembles a release APK. Download it from the workflow run → **Artifacts →
`alal-release`**. Without a keystore the APK is signed with the debug key so it installs
immediately.

### 2. (Once) create a release keystore — no local `keytool` needed

1. GitHub → **Actions** → **Generate Keystore** → **Run workflow**.
   Enter an alias, a store password and a key password.
2. Open the run log. It prints `KEYSTORE_BASE64` and the instructions below.
   The `alal.jks` file is also uploaded as an artifact — **download and keep it safe**;
   losing it means you cannot update the app on the Play Store later.
3. Repository → **Settings → Secrets and variables → Actions → New repository secret**,
   add these four:

   | Secret | Value |
   |---|---|
   | `KEYSTORE_BASE64` | the base64 blob from the log |
   | `KEYSTORE_PASSWORD` | the store password you entered |
   | `KEY_ALIAS` | the alias you entered |
   | `KEY_PASSWORD` | the key password you entered |

4. Re-run **Build APK**. The APK is now signed with your release key.

### 3. Publish a release

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The **Release** workflow builds the signed APK and creates a GitHub Release with the APK and a
source ZIP attached.

---

## Repository layout

```
.github/workflows/    build.yml · release.yml · keystore.yml
app/
  build.gradle.kts    Compose, Hilt, Room+FTS4, DataStore, WorkManager, R8
  proguard-rules.pro
  schemas/            Room exported schemas
  src/main/
    AndroidManifest.xml
    res/              strings (values + values-my), themes, launcher icons, shortcuts
    java/com/alal/notes/
      AlalApp.kt  MainActivity.kt
      data/       db · entity · dao · repository · prefs · work
      domain/     model · wordcount · markdown · findreplace · template
      di/         Hilt modules
      ui/         theme · navigation · home · editor · search · more · settings · components · util
  src/test/           WordCounterTest · FindReplaceTest · AutoTitleTest · MarkdownToggleTest
gradle/libs.versions.toml
scripts/fetch-fonts.sh
```

### Pinned versions

| Component | Version |
|---|---|
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.3 |
| Kotlin / Compose compiler | 2.1.20 |
| KSP | 2.1.20-1.0.32 |
| Compose BOM | 2025.08.00 (Compose 1.9.0, Material 3 1.3.2) |
| Hilt | 2.56.2 |
| Room | 2.7.1 |
| Navigation Compose | 2.8.9 |
| DataStore | 1.1.4 |
| WorkManager | 2.10.0 |
| Biometric | 1.1.0 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 26 |

---

## Things to know

### Gradle wrapper jar

`gradle/wrapper/gradle-wrapper.jar` is a binary and is **not** included in this source drop.
The CI workflows install Gradle 8.11.1 with `gradle/actions/setup-gradle`, then run
`gradle wrapper --gradle-version 8.11.1` to generate the jar before calling `./gradlew`, so
nothing needs to be committed. Locally, run `gradle wrapper --gradle-version 8.11.1` once
(or let Android Studio do it) and the jar is created in place.

### Fonts
**Burmese fonts are committed** in `app/src/main/res/font/`:

| Resource | Font | Version | Licence |
|---|---|---|---|
| `pyidaungsu_regular` / `pyidaungsu_bold` | Pyidaungsu (Myanmar Computer Federation) | 2.5.3 | Free to use and redistribute (Myanmar Unicode national font) |
| `notosansmyanmar_regular` / `notosansmyanmar_bold` | Noto Sans Myanmar | 2.001 | SIL OFL 1.1 — `docs/licenses/OFL-NotoSansMyanmar.txt` |

The Latin fonts (Literata, Inter — both OFL) are downloaded during the CI build by
`scripts/fetch-fonts.sh`; the script skips any file that already exists. The app resolves
fonts by resource name at runtime and falls back to system fonts if a file is missing, so the
build never fails because of fonts.

### Empty-state animation
The spec suggested a small Lottie JSON. To keep the project dependency-light and
binary-free, the empty state uses a Compose `Canvas` pen animation
(`ui/components/Effects.kt`) instead. Swapping in Lottie later is a one-file change.

### Rich-text rendering in the editor
Inline Markdown styling is applied with `OutputTransformation` + `TextFieldBuffer.addStyle`
(Compose Foundation 1.9 — BOM 2025.08.00 or newer is required). If a future BOM changes that API, remove the
`outputTransformation` argument in `ui/editor/EditorScreen.kt`; everything else keeps
working because Markdown is the storage format.

### Backup files
A backup is a single UTF-8 JSON file with the `.alal` extension (schema version 1: notes,
categories, tags, note↔tag links, templates, settings). It is written with `kotlinx.serialization`
by `data/backup/BackupManager.kt`. Automatic backups use the Storage Access Framework
(`ACTION_OPEN_DOCUMENT_TREE`) so any provider that appears in the Android file picker — including
Google Drive — works without the app needing network access. The PIN hash/salt is **never** exported.

### Lock
The PIN is stored as `SHA-256(salt + pin)` in DataStore. Biometrics go through
`androidx.biometric.BiometricPrompt` (class 2/3 authenticators) and are only offered as a
shortcut; the PIN always works. Forgotten PIN → clear the app's data (notes are lost — keep a backup).

### Reminders
One `OneTimeWorkRequest` per note (`ReminderWorker`), tagged by note id; cancelled when the
reminder is removed or the note is trashed. Android 13+ needs `POST_NOTIFICATIONS`, requested
in-app the first time a reminder is set. Exact alarms are not used, so delivery may drift by a
few minutes under Doze.

### Export

Editor menu → **Export**. Markdown keeps the formatting marks, plain text strips them, PDF is an A4 document drawn with `PdfDocument` using the note's Myanmar font. *Save file* uses the system document picker (works with Google Drive, Downloads, SD card); *Share* writes to the app cache (`exports/`, served through `FileProvider`) and opens the share sheet.

### Internet
The manifest declares **no** `INTERNET` permission; nothing in the app touches the network.

---

## Roadmap

**Phase 2 (done)** — reminders, PIN/biometric lock, backup & restore, version history,
writing statistics dashboard, live Markdown styling.

**Phase 2.1 (done)** — export (Markdown / TXT / PDF), reading mode, outline navigator.
- **Later** — home-screen widgets, shared-element transitions, Lottie empty state.

**Phase 3** — snippets & text expansion, cross-note linking, advanced search operators,
Tasker/Intent API, tablet & foldable layouts, share-sheet ingestion, optional cloud sync
adapter (still no telemetry).

---

## Licence
App source: MIT. Bundled fonts: SIL Open Font License 1.1 (see `app/FONT_LICENSES_OFL.txt`
after the fonts have been fetched).

