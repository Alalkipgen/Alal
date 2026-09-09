# Changelog

All notable changes to **Alal** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses semantic versioning.

## [1.2.0] — Phase 2.1

### Added
- **Export** from the editor menu: Markdown (`.md`), plain text (`.txt`) or a paginated A4 **PDF** rendered with the selected Myanmar font (Pyidaungsu / Noto Sans Myanmar). *Save file* opens the system picker (any folder, including Google Drive); *Share* hands the file to Gmail, Telegram, Viber, etc. Fully offline.
- **Reading mode**: distraction-free rendered view (headings, quotes, lists, checkboxes, inline bold/italic/underline/strike/highlight/code, tappable links). Keyboard hidden, chrome hides on tap, pinch or A-/A+ to resize, Back returns to the editor.
- **Outline** navigator: lists `#`/`##`/`###` headings (or paragraphs when a note has no headings); tap to jump the caret there. Current section is highlighted.
- Unit tests: `OutlineTest`.

### Changed
- The three “Coming soon” entries in the editor menu are now real features; the placeholder message is gone.
- Version code 3.

### Not in this release
- Home-screen widgets, shared-element transitions, Lottie empty state, cloud sync (by design — offline only).

## [1.1.0] — Phase 2

### Added
- **Backup & Restore** (More → Backup). Export every note, category, tag, template and your settings as one `.alal` JSON file: *Save backup file…* (system file picker) or *Share backup…* (Drive, Gmail, Telegram, any app). **Automatic daily backup** to a folder you pick — including a Google Drive folder — keeps the newest 7 files (WorkManager, runs with the app closed). Restore lets you **Merge** (skip duplicates) or **Replace everything**, optionally re-applying appearance/editor settings. No INTERNET permission is used; the sharing is done by the apps you already have.
- **Stats** tab is real: words written today vs daily goal, streak, 14-day bar chart with best day, notes by status and by category, longest note, totals. Daily figures come from a new `daily_stats` table updated on every save.
- **Reminders**: pick a time from the editor (quick chips: in 1 h, in 3 h, tonight, tomorrow morning, next week — or date + time pickers). Delivered as a notification that opens the note; Android 13+ asks for the notification permission the first time. Reminders are re-scheduled after a restore.
- **Lock**: PIN (4–8 digits, salted SHA-256, never stored in backups) with optional fingerprint/face unlock. Lock the **whole app** (re-lock after leaving for a chosen time) or **single notes** from the editor menu. Locked notes hide their text on cards, in search results and in reminder notifications. 5 wrong attempts → 30 s cool-down.
- **Version history**: snapshots are taken automatically (every 10 minutes of editing, or on large changes), up to 50 per note. Editor menu → *Version history* lists them with word-count deltas; preview any version and restore it (the current text is snapshotted first). Manual *Save snapshot now* and *Delete all versions*.
- Note cards show lock and reminder badges; notification tap deep-links into the note.

### Changed
- Toolchain: Gradle **8.11.1**, Android Gradle Plugin **8.9.3**, `compileSdk` **36**, Compose BOM **2025.08.00** (Compose 1.9.0, Material 3 1.3.2). `targetSdk` stays 35. CI installs `platforms;android-36`.
- Live inline Markdown styling (bold/italic/heading/quote/list marks, paragraph-focus dimming) is **enabled** again — Compose Foundation 1.9 ships `TextFieldBuffer.addStyle`. `docs/pending/` was removed.
- Room database version 2 (`isLocked`, `reminderAt` on notes; new `note_versions`, `daily_stats` tables) with an in-place migration — existing notes are preserved.
- `versionCode` 2 · `versionName` 1.1.0.

### Not in this release
- Export (Markdown/TXT/PDF), reading mode, outline navigator, widgets → Phase 2.1.

## [1.0.0] — Phase 1

### Fixed (device testing)
- Home: the "Write" FAB was hidden behind the bottom navigation bar (root Scaffold ignored its content padding). Root now reserves space for the tab bar and consumes the inset so nested screens do not double-pad.
- Home: empty state now shows a "Write" button as a second, obvious entry point.
- Editor: Find & Replace now auto-scrolls to the line of the current match when using the previous/next arrows or typing a new query.
- Editor: leaving a note that has no title and no body deletes it instead of leaving an "Untitled" note behind (late auto-saves are blocked so it cannot reappear).

### Changed (Phase 2 kick-off)
- Fonts: Pyidaungsu 2.5.3 and Noto Sans Myanmar 2.001 (Regular + Bold) are now bundled in `res/font/` instead of being fetched in CI; `.gitignore` only excludes the CI-fetched Latin fonts.
- App icon: new adaptive icon — black background with an "Alal." serif wordmark (vector outlines, teal full stop). Monochrome (themed) icon and splash icon updated to match; splash shows the mark on a black disc.

### Fixed (pre-release audit)
- Compile: added missing `setTextAndPlaceCursorAtEnd` / `placeCursorAtEnd` imports in `EditorViewModel`.
- Compile: removed `MarkdownOutputTransformation` (needs `TextFieldBuffer.addStyle`, Compose Foundation 1.9+; not in BOM 2025.05.00). Inline Markdown styling and paragraph-focus dimming are therefore off in this build; the code is parked in `docs/pending/`.
- Fonts: more download mirrors for Noto Sans Myanmar (google/fonts path returned 404).
- Workflows: moved `secrets` check out of step-level `if` (not allowed by GitHub Actions) into a job-level `HAS_KEYSTORE` env flag.
- Workflows: generate `gradle-wrapper.jar` in CI and use `./gradlew`.
- Gradle: migrated deprecated `kotlinOptions` to `kotlin { compilerOptions { } }`.

### Added
- Notes home: staggered grid / list / compact views, category chips, pinned + recent sections,
  swipe left to archive, swipe right to pin, long-press multi-select, extended “Write” FAB.
- Editor built on `BasicTextField(TextFieldState)` for unlimited note length (100k+ characters).
- Live Myanmar-aware word count (ICU `BreakIterator` for `my` with syllable fallback),
  characters, sentences (`.` `?` `!` `။`), paragraphs, read time, selection statistics.
- Word goal with progress bar, confetti + haptic on reaching the goal.
- In-note Find & Replace: case sensitivity, whole word, regex, n/total counter, prev/next,
  replace one / replace all, inline regex error messages.
- Inline Markdown styling (headings, bold, italic, `++underline++`, strikethrough,
  `==highlight==`, quotes, lists, checklists, links, horizontal rules) with typing shortcuts
  and automatic list continuation. Markdown is the storage format.
- Auto-title from the first body line, unlimited coalesced undo/redo, auto-save (debounced
  + on pause/back) with a “Saved ✓” indicator.
- Per-note background: 12 pastels, 6 gradients, custom hex, paper texture, show-on-card toggle,
  automatic darkening in dark themes.
- Focus mode with typewriter scrolling and paragraph focus; pinch-to-zoom font size.
- Full-text search (Room FTS4 + LIKE fallback for Myanmar), snippet highlighting,
  category / status filters, recent searches, tag cloud.
- 11 themes (paper, white, sepia, mint, sky, rose, ink, slate, forest, AMOLED black, system),
  Material You dynamic colour with 8 accent presets + custom hex, Literata / Inter /
  Pyidaungsu / Noto Sans Myanmar fonts, paper textures drawn on Canvas.
- Templates: Blank, Feature Article, Interview, Research, Idea, plus custom templates.
- Statuses (Idea → Published), categories, tags, pin, favourite, archive, trash with
  30-day purge via WorkManager.
- More tab, Appearance / Editor / General settings, About, Stats placeholder.
- App shortcuts (New note, Search), adaptive + monochrome launcher icon, Splash API,
  edge-to-edge, predictive back.
- English and Burmese (`values-my`) localisation.
- GitHub Actions: build, release (on `v*` tags) and keystore generation workflows.
- Unit tests: `WordCounterTest`, `FindReplaceTest`, `AutoTitleTest`, `MarkdownToggleTest`.

### Deferred to Phase 2 / 3
- Reminders, app lock, backup/restore, export, reading mode, version history, outline,
  snippets, writing statistics dashboard. Menu entries show “Coming soon”.
