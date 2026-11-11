# Domain Context

A living glossary of the domain language used in PerfectOutfit. Architecture reviews
and refactors should use these terms.

## Glossary

### Activity type
Anything the user dresses for — a sport or not (`ActivityType`, `core/model/ActivityType.kt`):
Cycling, Running, Bike commute and Walking, in that order (Home's 2-column grid follows it).
Each value carries its own `displayName` and icon, so no screen maps activity types to labels
or drawables itself. Outfit entries, recommendations, History's filter and the selected
activity type on Home (DataStore `selected_activity_type`, falling back to the pre-rename
`selected_sport`) are all per activity type.

**Items ↔ activity types:** a clothing item belongs to a *set* of activity types (at least
one), stored as rows of `clothing_item_activity_types` (`ClothingItemActivityType`: item id,
activity type). The Wardrobe lists an activity type's items through that join; Add pre-ticks
the activity type being viewed, Edit changes name and set. Deleting an item removes it
everywhere — from every activity type and every outfit entry. The Rate/Edit outfit screen
offers the entry's activity type's items plus any item already on the entry, so an item
later untagged from that activity type is never hidden or silently dropped.

**Default catalog:** a fresh install seeds each default item with its set of activity
types. The Cycling gloves and Running's Ski Mask are also tagged Bike commute and Walking
(the same rows), which share four jackets of their own. Upgrades seed nothing — new
activity types start empty on existing installs until the user tags items.

### Reference temperature
The single temperature the app reasons about for a given hour, chosen by the user's
**apparent-vs-real preference** (`PreferencesManager.useApparentTemperature`). When the
preference is "apparent", the reference temperature is the feels-like value; otherwise it
is the dry-bulb air temperature.

Every place that recommends, matches, or displays "the temperature" uses the reference
temperature — no screen ever judges an hour by both bases at once. The selection rule
lives in exactly one place: the `WeatherReading.referenceTemp(useApparent)` extension
(`core/model/WeatherReading.kt`), which returns a raw `Double`; callers round at the edge with the one rounding helper (`roundedTemp` / `roundedReferenceTemp`, same file).
Both forecast hours (`HourlyWeather`) and persisted snapshots (`WeatherSnapshot`)
implement `WeatherReading`, so they are judged by the same rule.

### Apparent vs real temperature
A user preference toggling whether the app reasons in feels-like ("apparent") or dry-bulb
("real") temperature. It is reactive — Home and Explorer collect it, so toggling it in
Settings re-resolves the [reference temperature](#reference-temperature) (and, in Explorer,
the [temperature chart](#temperature-chart)) while they are open. The Rate screen also collects it, so
its likely-items follow the preference.

### UV warning threshold
When Home's weather card shows its UV hint (and tints the UV metric's sun icon):
`WeatherThresholds.isUvHigh` (`feature/home/WeatherThresholds.kt`), UV `>= 5`. That was
already the threshold the Home screen's UV card used (issue #1), so it was kept rather than
the WHO "high" band of `>= 6`. Wind has no warning and no colour bands (issue #23 removed the
wind card and the good/moderate/severe status colours); the weather card shows wind and rain
values plainly.

### Rain ahead
When Home's weather card shows its rain hint: `WeatherThresholds.rainAhead`, the first
hour from now to the end of today where rain is likely (`isRainLikely`: probability `>= 50%`
as displayed, so a raw 45% counts; see [weather formatting](#weather-formatting)), with the
day's remaining peak. It is judged from the current hour, not the hour being dressed for
(a long workout's coldest/warmest hour), because it answers what to pack, not what to wear;
rain after midnight doesn't count. When the current hour itself is likely, the hint says
"Rain likely now" instead of a start time. The hint names its own time because the card's Rain
value above it is the dressed-for hour's probability, not today's.

### Rain class
How wet an hour is (`RainClass`, `core/model/RainClass.kt`, issue #51), from its precipitation
amount in mm (Open-Meteo's hourly `precipitation`, stored as `precipitationMm` on forecast hours
and snapshots), judged on the amount as displayed (rounded to 0.1 mm): **dry** < 0.1, **light**
0.1 to < 0.5, **moderate** 0.5 to 4, **heavy** > 4.
Classes are ordered; their distance is the number of steps between them (dry ↔ heavy is 3).

- **As worn** (`loggedRainClass`: a logged snapshot, or the hour being logged): its amount.
  Snapshots stored before amounts existed (null) count as light when rain was likely
  (`isRainLikely`), else dry.
- **As expected** (`expectedRainClass`: an hour being dressed for): its amount, raised to at
  least light when rain is likely. Probability never raises it further.

A log's rain class is its snapshot's, i.e. its first hour's, even for a long workout.

### Weather formatting
How weather values are rounded and written (issue #53), in one place:
`core/model/WeatherFormat.kt`, beside `roundedTemp`. Stored and fetched values stay raw;
screens round at the edge through these helpers. Where a rule judges a rounded quantity, it
judges the displayed value, so a screen never shows one thing while the app acts on another.

- **Percentages** (rain probability, cloud cover): nearest ten, half up, no space: "40%".
  Rain is likely (`isRainLikely`) when the displayed probability is at least 50%.
- **Precipitation amount**: one decimal and always a ".", whatever the phone's language:
  "0.0 mm", "1.3 mm"; "— mm" when unknown. [Rain class](#rain-class) is judged on this
  rounded value. `rainAmountLine` adds a class ("1.3 mm · light rain"); the caller picks it:
  expected on Home, logged in the log wizard.
- **Wind**: nearest whole km/h: "12 km/h".

The amount is shown on Home's [weather card](#weather-card) (under the Rain metric) and in the
log wizard (its hour details and summary). The history entry's Rate screen shares the card but
shows no amount.

### Weather card
Home's view of an hour. On the result step it is the hour being dressed for (the first hour,
or a long workout's coldest/warmest): its [reference temperature](#reference-temperature),
then "Feels like (Actual 7°C)" — the basis, with the other value in brackets — then the place
and "Now" (the current clock hour) or the hour's time; below, UV, wind and rain (with a small
line under rain: the amount and the hour's expected rain class, "1.3 mm · light rain"); below those,
one hint line per active warning ([UV](#uv-warning-threshold), [rain ahead](#rain-ahead)), with
no section when neither applies. The activity grid shows a compact card of the same top lines,
always for the current hour. Which hour that is — the dressed-for hour — is owned by the Home
ViewModel (`HomeUiState.activeHour`; `currentDisplayTemp` for the grid and the Explorer
shortcut, which therefore always gets the current hour's temperature). The long-workout
duration bounds (2–23 h) live there too.

### Rating reminder
The notification that asks the user to rate an outfit entry after a workout (activity
type, workout date, duration). Its capability is exactly "show a reminder for entry X" and
"cancel the reminder for entry X" — nothing else about notifications leaks past it.
Defined by the `RatingReminder` interface (`core/notification/RatingReminder.kt`), with
two adapters: `AndroidRatingReminder` (system notifications; channel creation is lazy, on
first `show()`, so constructing it has no side effects) and `FakeRatingReminder` (a
recording fake under `app/src/test`). `OutfitLogging` depends on the interface, never the concrete Android adapter —
this is the app's first hand-written DI seam (`@Binds` in `core/di/NotificationModule.kt`)
and the reason those ViewModels can be constructed in a plain JVM test.

### Place
Coordinates plus a name: the one `Place(lat, lon, name)` type (`core/model/Place.kt`) used by
Forecast, Outfit logging, recent places, `LocationResult.Found` and the widget forecast. A blank
name displays (and is stored) as `Place.DEFAULT_NAME`, "Current location" — the only spelling;
older rows saved as "Current Location" are still treated as the default (matched case-insensitively).

### Location source
Where the user is now, as Home's forecast needs it: coordinates plus a place name, or why
there are none (permission denied, location unavailable). Defined by the `LocationSource`
interface (`core/location/LocationSource.kt`); `AndroidLocationSource` asks the fused
location provider (last known location first, then a fresh fix) and reverse-geocodes the
name. `HomeViewModel` cancels the in-flight request when it starts a new one, so a slow
stale result never overwrites a newer one. Like `OpenMeteoApi` (`WeatherApiModule`) and the
`Clock` (`ClockModule`), it sits in its own Hilt module
so the README screenshot test can replace it with `@TestInstallIn`.

### Export/import schema
`ExportImportManager` (`core/exportimport/ExportImportManager.kt`), used by Settings' file
export/import and by Drive backup (`feature/backup/`), serializes
the five Room entities (`ClothingItem`, `ClothingItemActivityType`, `WeatherSnapshot`,
`OutfitEntry`, `OutfitItem`) directly with `kotlinx.serialization` — there is no separate mirror DTO, so a field exists
in exactly one place. `ExportData.version` is a schema version the app can branch on for
future format changes; it defaults to `CURRENT_EXPORT_VERSION`, so files written before
the field existed still decode. Version 2 renamed `sport` to `activityType` and added the
item ↔ activity-type rows; a v1 file (or one without a version) is rewritten on import, each
item's single `sport` becoming one row. Version 3 added snapshots' `precipitationMm`; older
files decode it as null. Files newer than the app are rejected. The
replacement runs through `OutfitLogging.replaceAllData` (see [Outfit logging](#outfit-logging)),
so import and Drive restore alike cancel the replaced entries' reminders and redraw the widget.
Import decodes and validates the whole file before any
delete runs, and the delete-then-insert itself runs inside one `DatabaseTransactionRunner`
transaction, so neither a malformed file nor a failure partway through import can leave
the database partially emptied. `DatabaseTransactionRunner` is the same
interface-plus-Android-adapter seam as [Rating reminder](#rating-reminder) (`RoomTransactionRunner`
in production, a same-thread fake under `app/src/test`), which is why the round-trip test
runs on the plain JVM with fake DAOs instead of Robolectric.
Settings reads and writes the picked file through `DocumentFiles` (`ContentResolverDocumentFiles`
in production, `FakeDocumentFiles` under `app/src/test`), so `SettingsViewModelTest` covers
export/import on the JVM too. The Drive backup screen state lives in `feature/backup/DriveBackupViewModel.kt`,
which reaches Drive backup only through the `DriveBackup` interface (`GoogleDriveBackup` in
production: sign-in and consent, connect/disconnect, back up now, snapshots, restore, and a `status`
flow that also picks up the scheduled backup's writes). `DriveBackupService.backup()` owns "a success
forgets the last failure", recorded reason and notification alike, for both back-up-now and the
scheduled `BackupRun`.

### Recommendation
The outfit suggested for an [activity type](#activity-type) at a [reference temperature](#reference-temperature),
owned by `Recommendations` (`feature/recommendation/`) — the single entry point for Home,
Explorer, and the Rate screen's "likely items". Callers pass the activity type, a raw reference
temperature, the target [rain class](#rain-class) and the apparent-vs-real preference; the module loads rated history
(`OutfitEntryDao.getRatedEntriesWithDetails(activityType)` — one query, matching happens in
memory), rounds, and matches (issue #50). `RecommendationPolicy` is `internal` to the module.

Only rated entries within ±2 °C of the rounded target count. A **combination** is an entry's
exact, non-empty item set (as in [mode comparison](#mode-comparison); a subset is a different
combination, an entry without items is none). An entry is **known wrong** for the target when
it was *too cold* at or above it, or *too hot* at or below it.

Rain is matched by weather similarity (issue #51): an entry's [rain class](#rain-class) as worn
against the target's as expected. Items carry no rain flag.

1. **Perfect first:** any surviving perfect entry beats every non-perfect one. Order: closest
   rain class, then closest degree, then how many perfect entries its combination has in the
   window *in the same rain class*, then newest.
2. **Cancelling:** a combination's perfect entries don't survive when the same combination has
   a newer (snapshot timestamp) known-wrong entry in the window *in the same rain class* — being
   soaked and cold says nothing about the same outfit on a dry day. A newer rating that agrees
   (e.g. *too hot* at a warmer degree) cancels nothing.
3. **Fallback** (no surviving perfect): non-perfect entries pointing toward the target (*too
   hot* worn warmer, *too cold* worn colder) before known-wrong ones (judged on temperature
   only); within each, closest rain class, then closest degree, then too hot over too cold,
   then newest. A cancelled combination's negative entry may win.

`likelyItemIds` are the items of the surviving perfect entries in the closest rain class that
has any (the Rate screen passes the logged hour's class as worn). A result exists exactly when
any rated entry lies within ±2 °C, which is all the widget's lit state needs.

**Workout window:** for a workout of N hours, the first N forecast hours are the window;
the *coldest* and *warmest* hours (by reference temperature, ties going to the later hour)
each get their own recommendation. A one-hour window has coldest == warmest. Both are matched for the window's
*wettest* expected rain class (`Recommendations.windowRainClass`): you dress once for the whole
workout. The widget's lit state doesn't depend on rain, since rain only ranks.

**Card:** the recommendation card's worn line names a wet class ("Worn at 9°C in light rain")
and, like a neighbor temperature, is emphasized with the difference when the entry's class isn't
the target's: "Worn at 9°C, dry (light rain expected)". The weather card shows no amounts. A result is
discarded (`null`) if the caller's activity type or duration changed while the query ran.

### Temperature chart
Explorer's view of an [activity type](#activity-type)'s rated history (issue #32), built by
`Recommendations.temperatureChart(activityType, useApparent)` — a flow over the activity type's
entries and the items tagged for it, so rating, editing or retagging updates it in place — with
the pure, `internal` `TemperatureChartPolicy` doing the work. Degrees are rounded
[reference temperatures](#reference-temperature).

- **Coverage strip:** drawn like an item row, a line from the coldest to the warmest *rated*
  degree with a dot per degree: filled = perfect (≥ 1 entry rated 0 there), hollow = rated
  (entries, none perfect), no dot = nothing rated. It counts outfits, every
  perfect one whatever its items are tagged for — so it can differ from the item rows, which
  may overlap at a degree no single outfit was perfect at.
- **Item rows:** one per item *currently tagged* for the activity type that is in at least one
  perfect entry: a bar from its coldest to its warmest perfect degree with a dot at each.
  Ordered by `BODY_PART_DISPLAY_ORDER` (no body-part labels on screen), coldest minimum first
  within a body part (then lower maximum, then name). Untagged items get no row even with
  perfect history; tagged items without one aren't shown.
- **Screen:** Explorer always opens on Cycling and has no basis toggle — the basis is Settings'
  [preference](#apparent-vs-real-temperature). The strip sits pinned under the scrolling rows,
  with its labels below it, as the chart's x-axis.
- **Selection:** tapping or dragging sideways anywhere from the chips down to the strip marks
  a degree (vertical drags over the rows still scroll; only a tap on the strip itself can clear it); rows not spanning it fade, and a
  caption gives its perfect-outfit count. It starts at Home's forecast temperature when that is
  on the axis, survives a basis switch while the new axis reaches it, and resets on an activity
  type change. For TalkBack the strip is one adjustable control.

### Mode comparison
Whether feels-like or actual temperature fits the user's ratings better (issue #28), shown
under Settings → Recommendations and on its own screen; it never flips the
[apparent-vs-real preference](#apparent-vs-real-temperature) itself. Worked out live from
rated history (`Recommendations.modeComparison()`, a flow over all entries); nothing about
the mode is stored. A **pair** is two rated entries of one activity type with exactly the
same non-empty item set and different ratings; pairs never cross activity types. Per mode,
with rounded temperatures as in matching, a pair is consistent (warmer entry, warmer rating:
too cold < perfect < too hot), contradictory, or tied (same degree, counting half). The
score is (contradictory + ½ tied) ÷ pairs, lower is better; `ModeComparisonLine` keeps it in
halves so the arithmetic is exact. There is one line per activity type plus an overall line
pooling their pairs. A verdict needs ≥ 20 pairs and a gap of ≥ 10 points, else "Not enough
data yet" or "No clear difference". Item sets are read as they are now, so editing an entry
or deleting an item can change the pairs. Pairs ignore [rain class](#rain-class): a dry entry
and a soaked one at the same degree are paired as if rain made no difference, a known limitation.

### Comfort rating
How the temperature felt for a logged outfit (`ComfortRating`): too cold, perfect or too hot.
The type owns everything about it: its labels, which stored values are valid, the
temperature order too cold < perfect < too hot (used by [mode comparison](#mode-comparison))
and the matching priority perfect > too hot > too cold (used by the
[recommendation](#recommendation)). Room and the export file keep the plain Int (-1 / 0 / 1);
`OutfitEntry.rating` is the typed view of it and no other code interprets the number.

### Outfit entry
The persisted record of one outfit worn for one workout (`OutfitEntry`): its
[activity type](#activity-type), its clothing items, a `WeatherSnapshot` of the workout hour, an optional comfort rating
(`ratedAt` set when rated) and notes.

**`createdAt` rule:** `createdAt` is the epoch millis of the workout hour (not the wall
clock at save time), for live and past logs alike. History sorts by it, so a past-dated log
sorts by when the workout happened. The hour-to-millis conversion, `ratedAt` and "the current clock hour" all come from the injected `Clock` (`core/time/ClockHours.kt`).

### Outfit logging
The act of recording an outfit entry, owned entirely by `OutfitLogging`
(`feature/outfit/OutfitLogging.kt`): save the snapshot from the hour, create the entry with
its items (one transaction), then decide on the [rating reminder](#rating-reminder). Also
owns rate, update (items + notes + rating atomically), delete and replace-all
(`replaceAllData`, below).
Callers (Home accept, the wizard, History, notification rating action) never build snapshots
or touch the reminder themselves.

**Reminder rule:** a reminder is scheduled only if the log is live (`LogMode.LIVE`) or the
entry is still unrated; a past log that already carries a rating gets none. Rating an entry
cancels its reminder; rating an entry that no longer exists writes nothing and reports
`RateResult.ENTRY_MISSING`, so the notification action says so instead of "Rated". Deleting
entries (one or a batch) cancels each one's reminder.
**Replace all data** is the single path for file import and Drive restore: the caller's
delete-then-insert runs in one transaction, then every replaced entry's reminder is cancelled
and the widget redrawn once. A blank location name is stored as "Current location".

### Forecast
The set of known hours for the current location, owned by `Forecast`
(`feature/forecast/Forecast.kt`): the hour cache (API window of past 2 days + today +
tomorrow, plus any per-date fetches merged in, always sorted and duplicate-free), the
location (`ForecastLocation`, or `null` = no location yet) and its display name. Callers
resolve a location themselves (GPS/geocoding stay in the Android layer) and hand it to
`refresh`. `hoursFor(date)` returns that date's hours and fetches and merges them only if
none are known. Time comes from an injected `Clock`.

**Wizard rule:** the outfit wizard can only pick hours up to today (the date picker's max
date); live logs start on the date of the hour Home handed off.

### Log wizard
How a new outfit is logged, owned by the pure `LogWizardState` (`feature/rate/LogWizardState.kt`);
`LogOutfitViewModel` only fetches weather and logs the result. Past logs go when and where →
items → summary → rating → save; live logs (handed off from Home) are only items → save
("Use outfit"), after which Home ends its flow like an accepted recommendation (the wizard reports
the save through `LiveOutfitHandoffStore`). `next()` / `back()` say whether to move, save, or
leave (back on the first step). Next needs a picked hour (with no weather loading) on when and
where, and at least one item where the outfit is confirmed: the summary of a past log, the items
step of a live one; a past log's items step can always go on. Next is ignored while the outfit
saves; a failed save stays in the wizard to be tried again. "Today" — for the [wizard rule](#forecast)'s max date, the
Today/Yesterday labels and the time picker's default hour — is the injected `Clock`'s current
hour when the wizard opens, never the system clock. Weather that arrives for a date or place
picked over in the meantime is dropped. Weather always arrives with the `Place` it was fetched
for (`PlacedHours`; for the current location, `Forecast.hoursFor` returns the forecast's place),
and that place, coordinates and name, is what gets logged and shown: a log's coordinates are
always those of its weather. There is no "unresolved place" state; a wizard with no weather
cannot save. Rating an already-logged entry is a separate screen
(`RateEntryViewModel`).

**Discard rule** (`rememberDiscardGuard`): leaving a screen with nothing at stake happens
straight away; otherwise a dialog asks first, whether the leave came from Back, Cancel or a
bottom-bar tab switch. The wizard always asks once it is up; the rate screen asks only with
unsaved edits.

### Display window
The 24 hours from the current clock hour that Home shows (`Forecast.displayWindow()`),
shorter if the known hours end sooner, empty if all known hours are in the past.

### Widget
A 1×1 home-screen tile per [activity type](#activity-type) (issue #29, replacing issue #22's
3×1 widget): a launcher into a [recommendation](#recommendation), never a display of one. Each
activity type has its own provider (`OutfitWidgetProvider` subclasses, `widgetProviderOf`), so
the picker lists four tiles and nothing is stored per tile. A tile shows its activity type's icon
over the current hour's [reference temperature](#reference-temperature) and a caption naming its
basis (`feels` / `actual`); there is no room for the place name. The icon is lit when
`Recommendations.find` has an entry for that activity type at that temperature: it then sits on
a filled chip; otherwise it is faint, with no chip (a shape difference, so a greyish wallpaper
colour can't hide it). Tapping the tile opens Home's result step for its activity type (1 h),
lit or not, through the same unsaved-edits confirmation as a tab tap.

**Grid widget:** a 2×1 widget (`GridWidgetProvider`, issue #30) of four separate tiles in two
rows of two, one per activity type in `ActivityType` order (Cycling, Running / Bike commute,
Walking), nothing stored per widget. Each tile is the 1×1 tile's background and icon without any text (no temperature,
caption or place), lit or faint by the same rule, and is its own tap target onto its activity
type's result step. Both widgets share the icon layout (`outfit_widget_icon`) and its binding
(`bindTile`); the grid adds each tile into its slot as a nested RemoteViews. With no saved place, or
when the saved hours don't cover now, all four tiles are faint; with no saved place a tap opens
Home's first step, and TalkBack reads "open app" for it.

**Widget forecast:** the widget never reads `Forecast`'s hours. Home saves
the last resolved place and its hours to DataStore (`WidgetForecast`: location, hours,
fetch time, serialized as is — no mirror of the forecast hour), a disposable cache outside Room
and outside the [export/import schema](#exportimport-schema); a cache that can't be read (an
older layout) counts as none saved and the next save replaces it. A 30-minute worker
(`WidgetForecastRefresh`) refetches through `Forecast.fetchAt`, the same fetch-and-extract path
as Home, without touching Home's forecast. With background location granted (opt-in: Settings → Widget → "Follow my location",
issue #27) it asks `LocationSource` first and saves the device's place, so the widget follows
the device; without it, or when no location comes back, it refetches the saved place, so the
widget follows the *last place the app was opened*. Redraws
that need no fetch (Home refresh, any [outfit logging](#outfit-logging) write, the
apparent-vs-real toggle, the theme) go through the `WidgetUpdater` interface, the same
interface-plus-adapter seam as [Rating reminder](#rating-reminder); it redraws every
provider's tiles. The refresh runs while any tile is placed: a provider losing its last tile
cancels it only when no other provider has one. With no saved place a tile shows "–°" and the
caption `open app`, and a tap opens Home's first step; when the saved hours don't cover now it
shows "–°", keeps its basis caption and dims the icon.

**Launches:** every intent from outside the app (rating reminder, backup notification,
widget) becomes one `LaunchTarget` (`MainActivity.launchTargetOf`). `MainActivity` is
`singleTop`, so a launch also arrives while the app is open (`onNewIntent`); one that lands
on an edit screen waits for the same confirmation as a tab tap. A launch onto the tab
already showing doesn't navigate.
