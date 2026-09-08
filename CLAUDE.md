# Texto

A bilingual (Persian/English) SMS-MMS app for Android, forked from Fossify Messages and
reskinned onto a Claude Design mockup. Package `com.texto.sms`, version 2.4.1 (versionCode 42).

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
`item_conversation_recent.xml` via `setupRecentView()` for the conversations list, so edits
meant for that list must go there. But `item_conversation.xml` / `setupDefaultView()` is
**not** dead: `ContactsAdapter` inflates it for the new-conversation screen. This file used
to claim it was never on screen, and a hardcoded `@color/color_primary` on its draft marker
sat there in commons' green for exactly that reason. Confirm with `uiautomator dump` before
believing either layout is unused.

**Room refuses the main thread.** Several menu actions wrote to the SMS provider and then to
Room straight from a click handler and killed the process. Anything touching
`conversationsDB` or a `ContentResolver` goes in `ensureBackgroundThread { }`, with the UI
work back on `runOnUiThread`.

**The app speaks two languages, and one object decides which.** `TextoLocale.wrap()` is
called from `attachBaseContext` in both `App` and `SimpleActivity`; it reads
`config.appLanguage` (`SYSTEM` / `PERSIAN` / `ENGLISH`, default follow-the-phone) straight
out of SharedPreferences — the Application has no `applicationContext` that early, so
`context.config` would NPE there — and rebases the Context on that locale.

**English is `values/`, Persian is `values-fa/`, and `resConfigs("en", "fa")` keeps both.**
It used to be the other way round: the locale was pinned to fa-IR and every language folder
but `values/` was stripped, so `values/` held Persian. **The two files must carry the same
keys** — a key present in one and missing from the other falls back mid-sentence. Check with
a `grep -o 'name="[^"]*"' | sort -u` diff of the two.

A `org.fossify.commons.R.string.*` reference now resolves against commons' own `en`/`fa`,
which is usually right; the app still overrides a long list of them so the wording matches
the rest of the screen.

**A calendar, a digit shape and a weekday name are not resources.** The resource system
cannot choose between Jalali and Gregorian, so they branch on `TextoLocale.isPersian`
instead, in `extensions/JalaliDate.kt`: `TextoCalendar` (month names, month lengths, the
grid's first column, civil-date conversion), `String.toUiDigits()`, `uiPercentSign`,
`formatUiDateOrTime()`, `formatUiTimeOnly()`, `formatUiDayLabel(context)`, `toUiDateText()`.
Both date pickers and the range picker are built from `TextoCalendar`, so an English user
gets a Gregorian grid rather than a Jalali one with Latin numerals in it.

Persian typeface names and the theme names are the same story from the other side:
`TextoFonts.displayNames` picks between two maps, and `AppThemes.ThemeFamily` carries a
`labelRes` rather than a literal.

**`toUiDigits()` maps ASCII only, deliberately.** `Char.isDigit()` is true for Persian
digits too, so shaping an already-shaped string — or one that came out of `String.format`
under fa-IR, which emits Persian digits by itself — indexed the array with `'۱' - '0'` =
1729 and took the process down. Format with `Locale.US` when you mean to shape explicitly.

This bites hardest where the string is not display text at all. `".IconH%02d".format(i)`
built a component name as `IconH۰۷`, and the package manager quite correctly said no such
class exists. Identifiers, keys and component names get assembled by hand, not formatted.

**Anything mirrored by hand has to read the layout direction.** `start`/`end` in XML flip on
their own; code that names a physical side does not. Three places did, and were all written
assuming Persian: the bubble tails (`GradientDrawable.cornerRadii` is physical TL/TR/BR/BL),
the caret rotation in `JalaliRangePicker`'s month bar, and the `fullScroll` that anchors the
date-chip row. Each now checks `resources.configuration.layoutDirection`. Grep for
`LAYOUT_DIRECTION` before adding a fourth.

**Persian matching needs `String.containsPersian()` / `foldPersian()`**
(`extensions/String.kt`). Carriers send the Arabic forms of letters a Persian keyboard never
types, so a raw `LIKE` or `contains` misses most bank and service SMS. This is content
matching, not UI, and stays on regardless of the interface language.

**The brand mark is the one surface the tonality strip does not reach.** Everything else in
the app follows `accentHueShift`; the logo and the launcher icon are fixed blue artwork, on
purpose, so the app is still recognisable at whatever hue the rest of it is wearing.

That used to be the other way round, and the machinery it needed is gone: twelve pre-rendered
hue rotations of the icon, twelve `activity-alias` entries, and a `TextoLauncherIcon` that
enabled one and disabled the rest as the app went to the background. Deleted, along with the
`ActivityLifecycleCallbacks` in `App` that existed only to time the swap.

**The twelve aliases themselves stay declared**, all pointing at the same `@mipmap/ic_launcher`.
They are not dead weight: a launcher shortcut is rooted at the alias it was created from, so
an install that had `.IconH07` enabled would lose its home-screen entry if that name stopped
resolving. They cost nothing and can only be removed once no install is still sitting on one.

**The icon is two layers, and has to be.** `ic_launcher_background.xml` is a full-bleed vector
gradient (`#4FDCED → #116AEA → #151B95`, sampled from the artwork) and the foreground is the
white speech bubble alone. Painting the whole badge — circle included — into the foreground
instead leaves bare corners wherever a launcher masks to a squircle rather than a circle.

The bubble fills **46dp of the 108dp** canvas, not the 62dp of the safe zone. In the source
artwork the bubble is 239px across inside a 400px circle, so it covers 60% of the badge; the
tile's masked circle is about 72dp, and 60% of that is ~44dp. Sized to the safe zone it
swelled to fill the whole tile and stopped matching the logo in the header.

**The wordmark is a bitmap with one filter left on it.** `styleAppTitle()` no longer rotates
its hue. What remains applies only on a dark ground: the mark is deep blue on white, and on
the dark skins that navy sits within a few percent of the background — the badge still reads
but the word all but disappears. RGB is scaled up to lift the lettering into a legible blue,
leaving the white bubble at white since those channels were already clamped. Brightness, not
hue: the mark stays on-brand and is only exposed for the ground it sits on.

**`GridLayout.columnCount` is validated against the children already attached.** Setting it
to 3 while the seven-wide day row is still in the grid throws
`IllegalArgumentException: columnCount must be greater than or equal to the maximum of all
grid indices`. `removeAllViews()` first, then set the count. `JalaliRangePicker` reuses one
grid for the days and the twelve months and crashed on the first toggle for exactly this.

**Two functions that call each other need explicit return types.** `renderMonth()` and
`renderMonthChooser()` are `= with(activity) { ... }`, and once each could reach the other
Kotlin gave up with *"type checking has run into a recursive problem"*. `: Unit` on both.

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

**Theme ids are stored in prefs, so a removed theme is not simply deleted.** Aurora is gone,
but ids 1 and 2 are permanently spoken for — `RETIRED_AURORA` / `RETIRED_AURORA_LIGHT` — and
`App` carries a one-shot migration moving an install still on either onto the matching Neon
variant. Skip that and the picker names a skin the user cannot select or leave, while their
stored colours stay Aurora's. Any future removal needs the same two pieces: a reserved id and
a migration behind its own flag.

**Classic's text is one ink, `#16213A`, not three blues.** Its `mainTextColor` used to be
`#324C9B`, which the conversation list fades to 58% for the preview line: that measured
2.88:1 on white. Blue also collided with the accent, which is blue, so nothing marked
selection. Measured on device after the change: name 13.7:1, preview 6.1:1, timestamp 3.1:1.

**`TextoGlass` is the single surface painter.** `panel()` (cards, bubbles), `bar()` (capsules,
chips), `accent()` (gradient emphasis), `rimFor()` (a hairline that works on light and dark
grounds). Route new surfaces through it.

Three surfaces are meant to read as one material and must be painted alike: the header
capsule (`setupOverlayBars`), the floating nav pill (`applyCustomColors`) and the inactive
filter chips (`styleFilterChip`). Same tint (`topBarColor`, which `inputBarBackgroundColor`
aliases), same `rimAlpha` 0.20, same `opacity` from the glass slider, and the stroke through
`1.getScaledPx()` — two of them once used a raw `density`, which ignored the UI-scale slider
and left the header on a 2px hairline while the other two grew to 3. Measured on a light
theme the rim is `#EBEFF1` on a `#FBFEFF` fill; if one of the three ever differs, that is the
bug.

**Sheets are `TextoDialog`.** `textoCapsuleDialog()` for a list of choices, `textoInputDialog()`
for one field. Commons' `setupDialogStuff` draws its title bar from the base theme and puts a
white strip and a foreign typeface on the dialog — that is what these replaced.

`JalaliRangePicker` is the app's own calendar; Android ships no Jalali picker. It draws a
Jalali grid under Persian and a Gregorian one under English, both from `TextoCalendar`.

Its header label is a button. One grid serves two modes: days, or the twelve months of the
year, with `pickingMonth` deciding which and the two arrows stepping a month or a year to
match. Before that the arrows were the only way to move, so a date a year back was twelve
taps of the same one and the year could not be changed at all.

**The tonality strip rotates the accent, and it does so inside `Config`.** `accentHueShift`
is applied on the way *out* of `accentGradientStart/Mid/End`, `auroraAccentColor` and the
halo slots, never written back. That is the hook: roughly fifty surfaces read those getters
and follow with no code of their own, a skin change rewrites the stored colours with the
shift riding along untouched, and dragging back to centre restores the skin exactly.

`0` in `accentGradientMid` and the halo slots is **not a colour** — it is the unset marker
those are tested against with `== 0`. Rotating it yields a transparent black that no longer
compares equal, so `Config.tinted()` returns 0 unchanged. Anything else reading those must
keep the same guard.

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
- The wordmark and the launcher icon are cut from one supplied JPEG, `_incoming/wordmark.png`,
  by flood-filling its white page away (see the brand-mark note above), so the two now match
  exactly, which the previous pair did not. The supplied `icon_fg.png` was not usable: it is a
  mockup render of a white bubble on a white page, and the bubble interior and the page
  measure the same value, so no threshold separates them.
- Both are keyed from a JPEG, so their edges carry a little compression halo. A transparent
  PNG or an SVG of the mark would replace them with no code change.
- `provider_paths.xml` declares `cache-path path="."` beside the two specific paths it
  already lists, so the whole cache directory is grantable. Nothing here calls
  `getUriForFile` — commons does — so which roots it needs is not readable from this side,
  and removing the broad one risks breaking attachment sharing. Defence in depth rather than
  a live hole: the provider is unexported and no URI is built from untrusted input.
- `_incoming/` is gitignored: it is where the user drops source artwork. Images pasted into
  chat never reach disk — ask for a file there.
