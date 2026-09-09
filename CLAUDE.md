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

Asking one invocation for two variants does it too, which is less obvious:
`:app:assembleCoreRelease :app:assembleCoreDebug` schedules `minifyCoreReleaseWithR8` and
`packageCoreDebug` alongside each other and the latter dies in
`PackageAndroidArtifact$IncrementalSplitterRunnable`, leaving a truncated APK on disk that
still looks like a build output. One variant per invocation.

**The build that ships here is not the build Play can take.** `coreDebug` is the one everyone
installs, and it is the `debug` build type: unminified, and signed with `debug.keystore` —
which is committed, with the password `android` in `build.gradle.kts`. Play refuses an upload
signed with a debug key, wants an `.aab` rather than an `.apk` for a new listing, and a
committed signing key means anyone with the repo can sign something that installs over the
real app. Publishing needs a real upload key in `keystore.properties` (already gitignored;
`hasSigningVars()` reads the same four values out of the environment for CI) and
`:app:bundleCoreRelease`.

`release` is the only variant that minifies, and until this audit nothing had ever run it.
It works — `assembleCoreRelease` succeeds, the APK launches, and it is **5.2 MB against the
debug build's 21.2 MB**. Run it before shipping, because R8 is where the keep rules below
are the difference between a working app and a crash.

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

**The keep rules are load-bearing, and they were pointing at nothing.** `proguard-rules.pro`
named `org.nova.messages.models.**` — a package this app has not had since the rename to
`com.texto.sms` — so the kotlinx-serialization rules matched no class at all. They cover the
backup format, and `Converters` additionally stores attachments and participants into Room
**as Gson JSON**, keyed on field *names*: a minified build that renamed those fields would
write rows the next build could not read. Commons' `SimpleContact` goes through the same
converter and needs the same rule. Nothing caught this because nothing built `release`.

**A confirmation names its subject through a plural, never a bare count.**
`deletion_confirmation` is "Are you sure you want to delete %s?" and the `%s` wants a noun
phrase, which is what `R.plurals.delete_messages` is for. `ThreadAdapter` passed `items.size`
straight in and the dialog read **"Are you sure you want to delete 1?"** — measured on
device. Every other adapter here already did it correctly; that one was the outlier. Its
restore branch was worse: it asked with `files_restored_successfully` ("Restored
successfully"), a *completion* message with no placeholder at all, so the question announced
a success that had not happened and dropped the count on the floor.

**The filter chips are counted before there is anything to count.** `filterCounts()` reads
`allConversations`, and `setupFilterChips()` runs in `onResume` — long before the list loads
on a cold start. The counts were computed once against an empty list and never recomputed, so
"All" opened on **0 with 393 conversations on screen**, and only corrected itself after
leaving the screen and coming back, because that ran `onResume` a second time.
`refreshFilterChipCounts()` now runs where `allConversations` is assigned.

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

**The status-bar icon is the mark with the T knocked out, and it is generated, not drawn.**
`drawable-*/ic_notification_bubble.png` is derived from `drawable-xxxhdpi/ic_launcher_foreground.png`:
that file is the white bubble *and* the navy T, so splitting it on luminance gives the bubble
as alpha and the T as a hole. Cropped to the mark's own box (124..306, 124..307 on the 432
canvas) and rendered at 22/24 of each density's 24dp icon. Android tints a small icon flat and
keeps only the alpha, so the T has to be a hole; painted as a second colour it would vanish.

This replaced a hand-drawn rounded rectangle with three dots in it : a chat bubble, but not
this app's, so the shade showed one mark and the launcher another. Regenerate from the
foreground whenever the artwork changes, rather than editing the PNGs.

`ic_launcher_monochrome.png` is generated the same way and for the same reason. It used to be
a solid bubble with no T, so an Android 13+ themed icon was a featureless blob : the one place
the launcher shows the mark with no colour to carry it is exactly where the letterform matters
most. Same luminance split, but written onto the full 108dp adaptive canvas at each density
(108 / 162 / 216 / 324 / 432 px) rather than cropped, so the mark keeps its 46dp box.

Both sets come from `ic_launcher_foreground.png` at the *matching* density, not downscaled
from one source, and both are alpha-only : the notification icon is white, the monochrome
layer is black, which is the convention each surface already used.

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
capsule (`setupOverlayBars`), the floating nav pill (`applyCustomColors`) and the filter
chips (`styleFilterChip`) -- **every** chip, the chosen one included. It used to be given no
background at all, so the chip you look at most was the only surface on that row the glass
slider did nothing to: measured, its neighbours carried the header's own `#171B22` while it
was bare accent wash over the page. Same tint (`topBarColor`, which `inputBarBackgroundColor`
aliases), same `rimAlpha` 0.20, same `opacity` from the glass slider, and the stroke through
`1.getScaledPx()` — two of them once used a raw `density`, which ignored the UI-scale slider
and left the header on a 2px hairline while the other two grew to 3. Measured on a light
theme the rim is `#EBEFF1` on a `#FBFEFF` fill; if one of the three ever differs, that is the
bug.

 The two full-width capsules also have to be the same *shape*, and a shared radius is not
enough for that. They are **true capsules**: the corner is half the bar's own height,
computed where each is painted, with only `TextoGlass.FLOATING_BAR_INSET_DP` (16dp) shared.
A fixed radius cannot hold, because they are not the same height: at 26dp the 58dp header
was 89% of the way to a capsule and the 76dp nav pill only 68%, so their corner profiles
measured identical row for row on bars that plainly did not match. Shortening the header
made it worse. Every app-bar on every screen is a capsule now, and so is the nav pill.

The composer is the one exception, on `TextoGlass.COMPOSER_RADIUS_DP` (26dp, the design's
`1.6rem`): it grows with the text it holds, and a capsule that tall reads as a lozenge.

The side inset had drifted too -- the header was 12dp against the pill's 16dp, so on a
1080px screen the header spanned 31..1049 and the pill 42..1038, and a comment here claimed
they matched. The optional top-bar outline in `applyOutlines` takes the same inset and the
same half-height radius, or it traces the full screen width on a fixed corner.

Both bars are painted from `doOnLayout` rather than once, because the radius comes from a
height that is not known before layout.

**A header's content must be padded past the capsule's own inset, not past the screen.** The
row inside each toolbar is the full screen width while the capsule painted behind it starts
16dp in, so any horizontal padding below 16dp puts the end tiles *outside* the bar. The
thread header sat at 12dp and did exactly that: measured on a 1080px screen the capsule ran
42..1038 while `thread_back_btn` started at 32 and `thread_menu_btn` ended at 1048, 10px past
the edge at each end. `texto_header_row` on the home screen was already right at 22dp, which
is the inset plus room to clear the corner, and the two must stay equal. Going to true
capsules is what made it visible, because the corner went from a fixed 26dp to half the bar's
height (35dp on the 70dp thread bar), so there is far more curve for a tile to fall off.

For a round tile the geometry is exact: a disc of radius `r` fits inside a capsule of radius
`R` only while its centre is within `R - r` of the corner's arc centre, which for the 40dp
tiles here puts the earliest safe left edge at exactly the 16dp inset. 22dp is that plus 6dp
of air.

**The two selection halos are one object and must stay identical.** The lozenge behind the
current nav tab and the one behind the chosen filter chip are both `TextoHalo`: accent at
.16 over accent at .25, a pill (`CHIP_PILL_RADIUS_DP`, clamped on draw to half the height),
and a stroke through `1.getScaledPx()`. The nav one had drifted to a fixed 27dp corner and a
raw `density` stroke, so the app's two selection markers -- meant to read as one idea at
opposite ends of the same screen -- were a rounded rectangle at the bottom and a pill at the
top, growing at different rates as the UI-scale slider moved. Measured after: both render
`#1C373E` over the bars' `#171B22`.

The chip halo is drawn in `onDrawOver`, not `onDraw`. Every chip carries glass now, so a
halo drawn underneath would simply be covered.

**Idle ink on both bars is `--txt2`, 58%.** The nav's idle labels sat at 68%, which is not a
value the design has; the filter chips and the conversation preview were already at 58%.
Both bars now measure `#97989D` on `#171B22`, 6.0:1.

**The conversation row is one 8dp grid, and it lives in `BaseConversationsAdapter`.** Card
padding 16/4, avatar 48 and 12 to the text, 4 between the name and the preview, 8 to
whatever ends either line, a 20dp badge, 4dp of margin between cards. The vertical values
are the grid's half step on purpose: at a full 8dp of padding and 8dp between cards every
row grew by 8dp, which is more screen than the shorter header gives back.

They are constants in the adapter rather than dp in `item_conversation_recent.xml` because
a dp in XML does not follow the UI-scale setting. The margins used to sit in the layout at
13/8/3 and were the one part of the row that never grew with the rest of the app: measured
at 140% the avatar-to-name gap stayed 34px while everything around it went to 44. The
layout still carries the same numbers as design-time defaults — change them in both places.

**A gone view keeps the margin of whatever points at it.** The preview is constrained to
the pin, the pin to the badge, and both are usually gone, so an unbadged row still held an
8dp gap against nothing: the preview stopped at 964px where the date above it ended at 985.
The gap is applied per row now, only when there is something there to clear. It is the
alignment most visible on a Persian row, because the text is right-aligned and the two
ragged edges sit directly above one another.

**Commons circle-crops contact photos.** `SimpleContactsHelper.loadContactImage()` finishes
its Glide request with `circleCropTransform()` — the bytecode, not the docs — so a real
photo or a company logo came back a circle and was drawn inside a view clipped to the
squircle, while every generated monogram beside it was a squircle. That is why the list's
avatars did not read as one set. `TextoAvatars.loadInto()` runs the same request without
the transform and lets `clipToSquircle()`'s outline give both the same silhouette. It keeps
`centerCrop`, which is right for the portraits most contact photos are, and paints no
gradient behind a real image — the accent ramp is for generated initials only.

**The home header is 58dp; every other bar is 70dp.** `setupScaledToolbar(toolbar, baseDp)`
takes it as a parameter. The 70dp bar left 16.4dp of empty space above and below a 37dp
wordmark, so the height was padding rather than content; at 58dp the wordmark and the gear
are untouched and keep about 10dp each side. The gear's disc is 40dp, under the platform's
48dp touch floor, so the header row carries a `TouchDelegate` that pads its hit rect out to
48dp — verified by tapping 6dp outside the disc.

**Sheets are `TextoDialog`.** `textoCapsuleDialog()` for a list of choices, `textoInputDialog()`
for one field. Commons' `setupDialogStuff` draws its title bar from the base theme and puts a
white strip and a foreign typeface on the dialog — that is what these replaced.

`JalaliRangePicker` is the app's own calendar; Android ships no Jalali picker. It draws a
Jalali grid under Persian and a Gregorian one under English, both from `TextoCalendar`.

Its header label is a button. One grid serves two modes: days, or the twelve months of the
year, with `pickingMonth` deciding which and the two arrows stepping a month or a year to
match. Before that the arrows were the only way to move, so a date a year back was twelve
taps of the same one and the year could not be changed at all.

**`TextoColorWheel` centres by padding, and two APIs quietly disagree with that.** The row
pads each end by half its width so the first and last swatch can reach the middle, and three
things have to line up with that padding or the ends break:

- `scrollToPositionWithOffset`'s offset is measured from the **padded** start edge
  (`LinearLayoutManager` lays the anchor at `getStartAfterPadding() + offset`), so once the
  padding centres a swatch the offset that centres is `0`. Passing the padding again put the
  chosen colour half a row right of the middle.
- `smoothScrollToPosition` scrolls **minimally** -- it brings the item just inside the nearest
  edge. Tapping a swatch near either end left it at the edge, `LinearSnapHelper` then settled
  on whatever really was nearest the middle, and the listener reported *that* index: the tap
  applied a different colour than the one touched. Centring needs a `LinearSmoothScroller`
  with `calculateDtToFit` returning box centre minus view centre.
- The swatches carry a `GAP_RATIO` margin on each side and that margin is inside the box the
  layout manager scrolls, so the padding must subtract it. Without that the row stopped a gap
  short at each end and the first and last colour never reached full scale -- they looked
  unselectable, which is how this was reported.

Verified by sampling the strip's pixels: the centred swatch measures 113px wide at x=540 in a
row spanning 84..996, and a tap three swatches right of centre applies exactly +45°.

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

## Publishing

What the store asks of *this* app, as distinct from what the code does. Measured against the
built artifact, not assumed.

**The permission list is the risky part.** `READ_SMS` / `WRITE_SMS` / `SEND_SMS` /
`RECEIVE_SMS` / `RECEIVE_MMS` are fine: being the user-selected default SMS handler is an
approved use case, and the app is one. It still needs the Permissions Declaration Form.

`READ_CALL_LOG` is not covered by that. The Call Log policy has its own list of approved
uses — default *phone* handler, caller ID, backup — and "default SMS handler" is not on it.
Here it does one thing: `getContactRecency` / `getSuggestedContacts` rank the new-conversation
list by recent calls as well as recent messages. Both already sit behind
`hasPermission(PERMISSION_READ_CALL_LOG)` and fall back to message history alone, so dropping
the permission costs the ranking and nothing else. It is the single likeliest reason for a
rejection.

`SCHEDULE_EXACT_ALARM` is correct as used — scheduled messages are not on the
`USE_EXACT_ALARM` allowlist, so the user-granted route is the right one, and
`askForExactAlarmPermissionIfNeeded` asks before scheduling and
`rescheduleAllScheduledMessages` wraps each call in `runCatching`. It needs a Console
declaration all the same.

Two entries were removed as pure listing noise: `USE_FINGERPRINT` (androidx.biometric's, for
an app lock that is unreachable — `isAppLockFeatureAvailable` is read by nothing in commons
6.1.5, checked against its bytecode, and this app draws its own settings screen) and
`android.provider.Telephony.SMS_RECEIVED`, which is a broadcast action rather than a
permission and so never named anything real.

**16 KB page size is already satisfied**, which is worth knowing before anyone goes looking.
The one native library is `libandroidx.graphics.path.so`; all four ABIs carry `p_align`
16384 on every `PT_LOAD` segment and sit on 16384-byte boundaries in the zip
(`zipalign -c -P 16`). `extractNativeLibs="false"` is already set.

**MMS does not touch the app's own network stack**, so the missing network-security config
is not the problem it looks like. `com.klinker.android.send_message.Transaction` calls
`SmsManager.sendMultimediaMessage` — verified in the AAR's bytecode — and the platform makes
the MMSC connection. targetSdk 36 blocking cleartext therefore does not break MMS.

**Still outstanding.** A privacy policy exists nowhere — not in the repo, not in the app, and
Play requires one for this permission set. The About screen is opened with `licenseMask = 0L`,
so no third-party licences are listed. The project is GPL-3.0 and distributing it carries the
source-availability obligation.

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
