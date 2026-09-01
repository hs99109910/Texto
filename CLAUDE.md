# Texto

A Persian/RTL SMS-MMS app for Android, forked from Fossify Messages and reskinned onto a
Claude Design mockup. Package `com.texto.sms`, version 2.4.1 (versionCode 42).

Reply to the user in Persian.

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

## Build and run

```bash
cd /d/texto && ./gradlew.bat :app:assembleCoreDebug --console=plain
```

Output: `app/build/outputs/apk/core/debug/Texto-sms-42-core-debug.apk`

Do not run two builds at once — it corrupts the KSP cache and locks the dex output. If that
happens: `./gradlew.bat --stop && rm -rf app/build/kspCaches app/build/intermediates/dex`.

A phone is usually on adb at `~/AppData/Local/Android/Sdk/platform-tools/adb.exe`. Its screen
sleeps quickly, so verification runs get cut off; ask the user to wake it rather than
assuming the app crashed.

## Traps

Every one of these has cost a real bug in this codebase.

**Colours are not in XML.** Every colour is read from `Config` at runtime and applied in
`onResume` via `SimpleActivity.applyCustomColors()` / `setupOverlayBars()` / `updateAppFonts()`.
A colour hardcoded in a layout ignores the user's theme. `@color/colorPrimary` is the Fossify
red and has no business on any surface.

**Commons' colour getters are the wrong ones.** `getProperTextColor()` and friends resolve
against the *base* (light) theme, not the user's skin, so they produce dark ink on a dark
ground. Read `config.mainTextColor` instead.

**A new screen must be registered.** `setupOverlayBars` finds the app bar and toolbar through
`APP_BAR_IDS` / `TOOLBAR_IDS` in `SimpleActivity`. A screen missing from those lists keeps the
base theme's flat rectangle instead of the floating capsule. Four screens sat like that for
weeks.

**`updateAppFonts()` overwrites every TextView's colour** in `onResume` unless its id is in
`excludedIds` in `SimpleActivity`. A view coloured anywhere else is silently reset. A screen
that never calls it keeps commons' colours, which is the same bug from the other direction.

**Sizes must go through `Int.getScaledPx()` / `getScaledTextSize()`** or they ignore the app's
UI-scale setting.

**LayoutParams cast hazard.** Moving a view to a different parent in XML breaks every
`updateLayoutParams<XLayoutParams>` for it in Kotlin with a `ClassCastException` at runtime,
not at compile time. After moving a view, grep for its binding name and check every typed
`updateLayoutParams`.

**Two conversation row layouts exist.** The new UI (`config.useNewUi`, always on) inflates
`item_conversation_recent.xml` via `setupRecentView()`. `item_conversation.xml` /
`setupDefaultView()` is the legacy path and is **not** what is on screen. Edits to the wrong
one silently do nothing.

**Room refuses the main thread.** Several menu actions wrote to the SMS provider and then to
Room straight from a click handler and killed the process. Anything touching
`conversationsDB` or a `ContentResolver` goes in `ensureBackgroundThread { }`, with the UI
work back on `runOnUiThread`.

**Locale is hard-locked to `fa-IR`** in `SimpleActivity.attachBaseContext`, and
`resConfigs("en")` strips all 81 `values-*` locale dirs. **All Persian text goes in
`values/strings.xml`.** A `org.fossify.commons.R.string.*` reference reaches the screen in
English — several did, and had to be replaced with app strings.

**`String.format` emits Persian digits under this locale.** Calling `toPersianDigits()` on
such a string used to crash, because `Char.isDigit()` is true for Persian digits too. It now
maps ASCII only; format with `Locale.US` when you mean to shape explicitly.

**Persian matching needs `String.containsPersian()` / `foldPersian()`**
(`extensions/String.kt`). Carriers send the Arabic forms of letters a Persian keyboard never
types, so a raw `LIKE` or `contains` misses most bank and service SMS.

**Double-counted insets.** Both the thread list and the search results list were padded for a
bar that the layout had already offset them below, opening them hundreds of pixels down an
empty screen. Before adding padding for a floating bar, check whether the view is already
laid out below it.

## The design

The mockup is the source of truth for colour and geometry. Its colours are `oklch(...)`:
resolve them exactly, do not eyeball. Key values (dark): `--bg #090C12`, `--glass #171B22`,
`--bubble-in #1F242E`, `--fg #F4F5F8`, accent gradient `#3BCFD0 → #55ADFF → #A67DF2` (**three
stops** — a two-stop blend passes through a dull mauve and looks wrong), background halos
`#009696 / #1A588F / #733EA4`.

These live in `AppThemes.kt` as the **Neon** theme (dark + light), which is the default.

**`TextoGlass` is the single surface painter.** `panel()` (cards, bubbles), `bar()` (capsules,
chips), `accent()` (gradient emphasis), `rimFor()` (a hairline that works on light and dark
grounds). Route new surfaces through it.

**Sheets are `TextoDialog`.** `textoCapsuleDialog()` for a list of choices, `textoInputDialog()`
for one field. Commons' `setupDialogStuff` draws its title bar from the base theme and puts a
white strip and a foreign typeface on the dialog — that is what these replaced.

`JalaliRangePicker` is the app's own Persian calendar; Android ships no Jalali picker.

## Verify by measuring

Do not report a colour or a crash from reading the code.

```bash
ADB=~/AppData/Local/Android/Sdk/platform-tools/adb.exe
"$ADB" exec-out screencap -p > shot.png          # then sample the pixels
"$ADB" shell "uiautomator dump /sdcard/ui.xml"   # then read the bounds
"$ADB" logcat -b crash -d -v brief               # crashes, not guesses
```

Pixel sampling found four colour bugs that static reading had missed, uiautomator bounds
found the double-counted insets, and logcat answered in seconds two crashes that two rounds
of guessing had not. `ThreadActivity` is not exported, so drive the UI with `input tap`
rather than `am start`.

## Conventions

- XML comments must not contain `--` (aapt2 rejects it). Use `:` instead.
- Files are CRLF. A multi-line regex patch must normalise line endings or it will not match.
- Layouts use `start`/`end`, never `left`/`right`.
- No hardcoded strings or colours in layouts.
- Commit only when the user asks ("کامیت کن"). Nothing is pushed; the user pushes.

## Naming

The word **nova** was removed. Classes are `TextoGlass`, `TextoAvatars`, `TextoFonts`,
`TextoGlideModule`; view ids and drawables are `texto_*`.

Four occurrences are **deliberately kept** and commented in place — do not "fix" them:

| Kept | Why |
|---|---|
| `NOTIFICATION_CHANNEL_ID = "nova_messages"` | Android keys the user's per-channel sound and importance by this string; changing it strands their settings. |
| `FONT_FAMILY` / `FONT_FAMILY_TEXTO = "font_family_nova"` | SharedPreferences key; renaming drops the font the user picked. |
| `"org.nova.contacts"` | Another app's package id. |
| `values-pt` / `values-sk` strings | Real Portuguese and Slovak words. |

## Known gaps

- **Search covers SMS only, not MMS.** MMS bodies live in a separate provider table and are
  not queried. The user knows.
- 27 of the 49 extracted `ic_ph_*` Phosphor icons are unused.
- The header wordmark still carries the original bubble; the launcher icon's was widened and
  its line thinned, so the two now differ slightly.
- `_incoming/` is gitignored: it is where the user drops source artwork. Images pasted into
  chat never reach disk — ask for a file there.
