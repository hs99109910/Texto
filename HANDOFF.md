# Texto — handoff

Context for continuing work on this app in a fresh session.

## What this is

**Texto** — a Persian/RTL SMS-MMS Android app, forked from Fossify Messages, being reskinned
onto a Claude Design mockup. Package `com.texto.sms`, version 2.4.1 (versionCode 42).

The app name is **Texto**. The name "nova" was removed from the codebase in this session and
must not be reintroduced — see "Naming" below.

## Stack

| | |
|---|---|
| Language | Kotlin 2.3.10, JVM target 17 |
| UI | **XML layouts + ViewBinding. No Compose anywhere.** |
| Base library | Fossify Commons 6.1.5 (`org.fossify.commons.*`), `App : FossifyApp` |
| SDK | minSdk 26, target/compile 36 |
| Build | Gradle 9.4.1, AGP 9.2.1, Kotlin DSL, version catalog |
| Data | Room 2.8.4 + KSP (DB v12), EventBus 3.3.1, kotlinx.serialization, Glide |
| Flavors | `core` / `foss` / `gplay`; the shipped build is `coreDebug` (`isDebuggable = false`) |

## Current state

- Branch: `claude/github-new-design-6rdgaz`
- Last commit: `8ecca2f`
- **~49 files are modified and uncommitted.** Commit before doing anything destructive.
- Nothing has been pushed to origin (git auth does not work from the agent environment;
  the user pushes manually).

## Non-obvious architecture — read before touching UI

**Colours are not in XML.** Every colour is read from `Config` at runtime and applied in
`onResume` via `SimpleActivity.applyCustomColors()` / `setupOverlayBars()` / `updateAppFonts()`.
Hardcoding a colour in a layout produces a view that ignores the user's theme.

**`TextoGlass` is the single surface painter.** `panel()` (cards, bubbles), `bar()` (capsules,
chips), `accent()` (gradient emphasis surfaces), `rimFor()` (hairline that works on light and
dark grounds). Route new surfaces through it rather than building drawables by hand.

**`updateAppFonts()` overwrites every TextView's colour** in `onResume` unless its id is in the
`excludedIds` list in `SimpleActivity`. A view coloured anywhere else will be silently reset.

**Sizes must go through `Int.getScaledPx()` / `getScaledTextSize()`** or they ignore the app's
UI-scale setting.

**LayoutParams cast hazard.** Moving a view to a different parent in XML breaks every
`updateLayoutParams<XLayoutParams>` for it in Kotlin with a `ClassCastException` at runtime,
not at compile time. This has caused two separate crashes already. After moving a view, grep
for its binding name and check every typed `updateLayoutParams`.

**Two conversation row layouts exist.** The new UI (`config.useNewUi`, always on) inflates
`item_conversation_recent.xml` via `setupRecentView()`. `item_conversation.xml` /
`setupDefaultView()` is the legacy path and is **not** what you see on screen. Edits to the
wrong one silently do nothing.

**Locale is hard-locked to `fa-IR`** in `SimpleActivity.attachBaseContext`, and
`resConfigs("en")` strips all 81 `values-*` locale dirs. **All Persian text goes in
`values/strings.xml`.**

**`String.format` emits Persian digits under this locale.** Calling `toPersianDigits()` on such
a string used to crash (`Char.isDigit()` is true for Persian digits too). `toPersianDigits()`
now only maps ASCII `0`-`9`; format with `Locale.US` when you intend to shape explicitly.

**Persian text matching needs `String.containsPersian()` / `foldPersian()`**
(`extensions/String.kt`). Carriers send Arabic yeh/kaf where a Persian keyboard types the
Farsi forms, so a raw `LIKE` or `contains` misses most bank and service SMS. All search paths
must fold both sides.

## Design source of truth

The mockup is in the uploaded bundle, unpacked at:

```
<scratchpad>/design_zip/Texto.dc.html
```

Colours in it are `oklch(...)`. **Do not eyeball them** — resolve exactly by serving the file
and reading values from the browser, or with the converter at `<scratchpad>/oklch.js`.
`<scratchpad>/serve.js` is a tiny static server for that (`node serve.js <dir> 8935`).

Design tokens already transcribed into `AppThemes.kt` as the **Neon** theme (dark + light),
which is now the default and has a one-time migration flag (`neonRefreshApplied`).

Key values (dark): `--bg #090C12`, `--glass #171B22`, `--bubble-in #1F242E`, `--fg #F4F5F8`,
accent gradient `#3BCFD0 → #55ADFF → #A67DF2` (**three stops** — the middle one matters; a
two-stop blend passes through a dull mauve and looks wrong), background halos
`#009696 / #1A588F / #733EA4` (deliberately much darker than the accent).

## Verifying visual work — do this instead of guessing

A device is usually connected over adb. Verify colours by measurement, not by eye:

```bash
ADB=~/AppData/Local/Android/Sdk/platform-tools/adb.exe
"$ADB" exec-out screencap -p > shot.png
node <scratchpad>/pxsample.js shot.png "label:X:Y" "label2:X:Y"
```

`pxsample.js` decodes the PNG and prints the hex at each coordinate. This found four separate
colour bugs that static reading had missed.

For crashes, capture rather than theorise:

```bash
"$ADB" logcat -c && "$ADB" logcat -b crash -v time
```

`ThreadActivity` is not exported, so it cannot be launched directly with `am start`; drive the
UI with `input tap` instead.

## Build

```bash
cd D:/texto && ./gradlew.bat :app:assembleCoreDebug --console=plain
```

Output: `app/build/outputs/apk/core/debug/Texto-sms-42-core-debug.apk`

Do not run two builds concurrently — it corrupts the KSP cache and locks the dex output. If
that happens: `./gradlew.bat --stop && rm -rf app/build/kspCaches app/build/intermediates/dex`.

## Conventions

- XML comments must not contain `--` (aapt2 rejects it). Use `:` instead.
- Files are CRLF.
- Layouts use `start`/`end`, never `left`/`right`.
- No hardcoded strings or colours in layouts.

## Naming

The word **nova** was removed from the app. Classes are `TextoGlass`, `TextoAvatars`,
`TextoFonts`, `TextoGlideModule`; view ids and drawables are `texto_*`; styles are `Texto*`.

Four occurrences were **deliberately kept** and are commented in place — do not "fix" them:

| Kept | Why |
|---|---|
| `NOTIFICATION_CHANNEL_ID = "nova_messages"` | Android keys the user's per-channel sound/importance by this string; changing it strands their settings and creates a second channel. |
| `FONT_FAMILY` / `FONT_FAMILY_TEXTO = "font_family_nova"` | SharedPreferences key; renaming drops the font the user picked. |
| `"org.nova.contacts"` | Another app's package id, not ours. |
| `values-pt` / `values-sk` strings | Real Portuguese/Slovak words (`novamente`, `znova`). |

Renaming the first two properly needs a preference-migration step; ask the user first.

## Outstanding work

### Blocked on the user

1. **New logo** — the user has sent it three times as an inline image; it never lands on disk,
   so its bytes are unreachable. Needs to be **attached as a file**. Target:
   `app/src/main/res/drawable-nodpi/img_texto_wordmark.png` (current one is the old design's).
2. **New app icon** — same problem, same fix. Will need mipmap densities + adaptive icon.

### Not yet verified on device

The last build (`Textonova23`) is installed but the screen locked before these could be seen:

- The top search panel: slide-in animation, filter chips, Jalali date chip, combined
  query + filter + date filtering, back-to-close, "گفتگوها" tab closing search.
- In-thread search (magnifier in the thread header).
- Flat cards and bubbles (`panel()` fill is now flat, bubble elevation 0).
- Thread messages using the full screen height (`clipToPadding=false`).

### Known gaps, already reported to the user

- **Search covers SMS only, not MMS.** MMS bodies live in a separate provider table and are
  not queried. The user was told; they have not asked for it yet.
- **The custom date-range picker is the platform (Gregorian) one**, with Jalali labels. A real
  Jalali picker would be its own screen. The user was told and has not asked for it.
- 27 of the 49 extracted `ic_ph_*` Phosphor icons are unused.
- The app was observed flipping to the light theme during automated testing; the cause was
  never identified. It may have been an errant automated tap. A real bug was found and fixed
  nearby (the dark-mode row toggled even when its switch was disabled), but that is not proven
  to be the same thing. Worth watching.

## Working style the user expects

- Persian replies.
- Measure, don't guess — screenshots and logcat over theorising. Two rounds were wasted
  guessing at a crash that a single logcat answered in seconds.
- Report honestly what was and was not verified.
- Do not commit unless asked ("کامیت کن").
