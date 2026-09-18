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
lives in exactly one place: `HourlyWeather.referenceTemp(useApparent)`, which returns a
raw `Double`; callers round at the edge.

Note: the recommendation queries in `OutfitEntryDao` still keep parallel apparent/real
variants because Room cannot parameterize a column name — that split is not the same
duplication as the reference-temperature rule and is expected to remain.

### Apparent vs real temperature
A user preference toggling whether the app reasons in feels-like ("apparent") or dry-bulb
("real") temperature. It is reactive — toggling it in Settings re-resolves the
[reference temperature](#reference-temperature) on any open screen.

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
recording fake under `app/src/test`). `HomeViewModel`, `RateOutfitViewModel`, and
`RatingActionHandler` all depend on the interface, never the concrete Android adapter —
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
