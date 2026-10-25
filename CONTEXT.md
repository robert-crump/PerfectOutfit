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
(`core/model/WeatherReading.kt`), which returns a raw `Double`; callers round at the edge.
Both forecast hours (`HourlyWeather`) and persisted snapshots (`WeatherSnapshot`)
implement `WeatherReading`, so they are judged by the same rule.

### Apparent vs real temperature
A user preference toggling whether the app reasons in feels-like ("apparent") or dry-bulb
("real") temperature. It is reactive — Home and Explorer collect it, so toggling it in
Settings re-resolves the [reference temperature](#reference-temperature) (and, in Explorer,
the stops and recommendation) while they are open. The Rate screen also collects it, so
its likely-items follow the preference.

### UV warning threshold
When Home's result step shows its UV warning card: `WeatherThresholds.isUvHigh`
(`feature/home/WeatherThresholds.kt`), UV `>= 5`. That was already the threshold the
Home screen's UV card used (issue #1), so it was kept rather than the WHO "high" band of
`>= 6`. Wind and rain have no warning and no colour bands (issue #23 removed the wind card
and the good/moderate/severe status colours); the weather card shows their values plainly.

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
Settings' backup/restore feature (`feature/settings/ExportImportManager.kt`) serializes
the five Room entities (`ClothingItem`, `ClothingItemActivityType`, `WeatherSnapshot`,
`OutfitEntry`, `OutfitItem`) directly with `kotlinx.serialization` — there is no separate mirror DTO, so a field exists
in exactly one place. `ExportData.version` is a schema version the app can branch on for
future format changes; it defaults to `CURRENT_EXPORT_VERSION`, so files written before
the field existed still decode. Version 2 renamed `sport` to `activityType` and added the
item ↔ activity-type rows; a v1 file (or one without a version) is rewritten on import, each
item's single `sport` becoming one row. Files newer than the app are rejected.
Import decodes and validates the whole file before any
delete runs, and the delete-then-insert itself runs inside one `DatabaseTransactionRunner`
transaction, so neither a malformed file nor a failure partway through import can leave
the database partially emptied. `DatabaseTransactionRunner` is the same
interface-plus-Android-adapter seam as [Rating reminder](#rating-reminder) (`RoomTransactionRunner`
in production, a same-thread fake under `app/src/test`), which is why the round-trip test
runs on the plain JVM with fake DAOs instead of Robolectric.

### Recommendation
The outfit suggested for an [activity type](#activity-type) at a [reference temperature](#reference-temperature),
owned by `Recommendations` (`feature/recommendation/`) — the single entry point for Home,
Explorer, and the Rate screen's "likely items". Callers pass the activity type, a raw reference
temperature and the apparent-vs-real preference; the module loads rated history
(`OutfitEntryDao.getRatedEntriesWithDetails(activityType)` — one query, matching happens in
memory), rounds, and matches in three tiers over rated entries: (1) exact rounded
temperature, newest wins regardless of rating; (2) within ±1 °C, best rating
(0 > 1 > -1) then newest; (3) within ±2 °C, same. `RecommendationPolicy` is `internal` to
the module. `likelyItemIds` are the items of perfect-rated (0) entries within ±2 °C. The
Explorer **stops** are the distinct rounded reference temperatures with rated history.

**Workout window:** for a workout of N hours, the first N forecast hours are the window;
the *coldest* and *warmest* hours (by reference temperature, ties going to the later hour)
each get their own recommendation. A one-hour window has coldest == warmest. A result is
discarded (`null`) if the caller's activity type or duration changed while the query ran.

### Outfit entry
The persisted record of one outfit worn for one workout (`OutfitEntry`): its
[activity type](#activity-type), its clothing items, a `WeatherSnapshot` of the workout hour, an optional comfort rating
(`ratedAt` set when rated) and notes.

**`createdAt` rule:** `createdAt` is the epoch millis of the workout hour (not the wall
clock at save time), for live and past logs alike. History sorts by it, so a past-dated log
sorts by when the workout happened.

### Outfit logging
The act of recording an outfit entry, owned entirely by `OutfitLogging`
(`feature/outfit/OutfitLogging.kt`): save the snapshot from the hour, create the entry with
its items (one transaction), then decide on the [rating reminder](#rating-reminder). Also
owns rate, update (items + notes + rating atomically), delete and restore (original id).
Callers (Home accept, the wizard, History, notification rating action) never build snapshots
or touch the reminder themselves.

**Reminder rule:** a reminder is scheduled only if the log is live (`LogMode.LIVE`) or the
entry is still unrated; a past log that already carries a rating gets none. Rating an entry
cancels its reminder. A blank location name is stored as "Current Location".

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

**Widget forecast:** the widget never uses `Forecast`. Home saves
the last resolved place and its hours to DataStore (`WidgetForecast`: location, hours,
fetch time), a disposable cache outside Room and outside the
[export/import schema](#exportimport-schema). A 30-minute worker (`WidgetForecastRefresh`)
refetches. With background location granted (opt-in: Settings → Widget → "Follow my location",
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
