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
