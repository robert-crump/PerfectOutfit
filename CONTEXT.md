# Domain Context

A living glossary of the domain language used in PerfectOutfit. Architecture reviews
and refactors should use these terms.

## Glossary

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

### Weather severity
The three-band answer (`NONE` / `NOTABLE` / `HIGH`) to "how concerning is this hour's UV
index, wind speed, or rain probability?", owned by `WeatherThresholds`
(`feature/home/WeatherSeverity.kt`). Both the Home screen's warning cards (shown at
`HIGH`) and `WeatherStatusColors` (colours `NONE`/`NOTABLE`/`HIGH` as good/moderate/severe)
consult it, so a measurement's severity and its colour can never disagree.

Bands: UV `HIGH` is `>= 5` — this was already the threshold the Home screen's UV card
used (issue #1), so it was kept as the one true threshold rather than the mapper's unused
`>= 4`. Wind `HIGH` is `>= 20 km/h`. Rain `HIGH` is `>= 50 %` probability (rain currently
has no Home screen warning card, only a colour).

### Rating reminder
The notification that asks the user to rate an outfit entry after a workout (sport,
workout date, duration). Its capability is exactly "show a reminder for entry X" and
"cancel the reminder for entry X" — nothing else about notifications leaks past it.
Defined by the `RatingReminder` interface (`core/notification/RatingReminder.kt`), with
two adapters: `AndroidRatingReminder` (system notifications; channel creation is lazy, on
first `show()`, so constructing it has no side effects) and `FakeRatingReminder` (a
recording fake under `app/src/test`). `OutfitLogging` depends on the interface, never the concrete Android adapter —
this is the app's first hand-written DI seam (`@Binds` in `core/di/NotificationModule.kt`)
and the reason those ViewModels can be constructed in a plain JVM test.

### Export/import schema
Settings' backup/restore feature (`feature/settings/ExportImportManager.kt`) serializes
the four Room entities (`ClothingItem`, `WeatherSnapshot`, `OutfitEntry`, `OutfitItem`)
directly with `kotlinx.serialization` — there is no separate mirror DTO, so a field exists
in exactly one place. `ExportData.version` is a schema version the app can branch on for
future format changes; it defaults to `CURRENT_EXPORT_VERSION`, so files written before
the field existed still decode. Import decodes and validates the whole file before any
delete runs, and the delete-then-insert itself runs inside one `DatabaseTransactionRunner`
transaction, so neither a malformed file nor a failure partway through import can leave
the database partially emptied. `DatabaseTransactionRunner` is the same
interface-plus-Android-adapter seam as [Rating reminder](#rating-reminder) (`RoomTransactionRunner`
in production, a same-thread fake under `app/src/test`), which is why the round-trip test
runs on the plain JVM with fake DAOs instead of Robolectric.

### Recommendation
The outfit suggested for a sport at a [reference temperature](#reference-temperature),
owned by `Recommendations` (`feature/recommendation/`) — the single entry point for Home,
Explorer, and the Rate screen's "likely items". Callers pass sport, a raw reference
temperature and the apparent-vs-real preference; the module loads rated history
(`OutfitEntryDao.getRatedEntriesWithDetails(sport)` — one query, matching happens in
memory), rounds, and matches in three tiers over rated entries: (1) exact rounded
temperature, newest wins regardless of rating; (2) within ±1 °C, best rating
(0 > 1 > -1) then newest; (3) within ±2 °C, same. `RecommendationPolicy` is `internal` to
the module. `likelyItemIds` are the items of perfect-rated (0) entries within ±2 °C. The
Explorer **stops** are the distinct rounded reference temperatures with rated history.

**Workout window:** for a workout of N hours, the first N forecast hours are the window;
the *coldest* and *warmest* hours (by reference temperature, ties going to the later hour)
each get their own recommendation. A one-hour window has coldest == warmest. A result is
discarded (`null`) if the caller's sport or duration changed while the query ran.

### Outfit entry
The persisted record of one outfit worn for one workout (`OutfitEntry`): sport, its
clothing items, a `WeatherSnapshot` of the workout hour, an optional comfort rating
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
