# Perfect Outfit

What to wear for your ride or run — from the forecast and the outfits you've rated.

<table>
  <tr>
    <td><img src="docs/screenshots/home.png" width="200" alt="Home: the cycling outfit for this hour, with feels-like temperature, UV, wind and rain"></td>
    <td><img src="docs/screenshots/workout.png" width="200" alt="Home: a 3-hour run, outfit for its warmest hour, with UV and wind warnings"></td>
    <td><img src="docs/screenshots/rate.png" width="200" alt="Rate: the items you wore and too cold, perfect or too hot"></td>
    <td><img src="docs/screenshots/history.png" width="200" alt="History: past outfits with their comfort rating"></td>
    <td><img src="docs/screenshots/explorer.png" width="200" alt="Explorer: what you wore at 1 °C"></td>
  </tr>
</table>

- **Recommends** an outfit from the hourly forecast at your location — no API key, via Open-Meteo
- **Plans** a workout window: what to wear for its coldest and warmest hour
- **Rates** each outfit too cold, perfect or too hot, with a reminder after your workout
- **Learns** from your ratings: matches the closest temperatures you've rated
- **Explores** your history temperature by temperature
- **Cycling and running**, each with its own editable clothing catalog
- **Backs up** daily to Google Drive, or export and import a JSON file

<sub>Screenshots use generated demo outfits and a synthetic forecast for Aachen at noon;
regenerate with `./gradlew readmeScreenshots` (needs a running emulator; wipes the app's data on it).</sub>

## Build

Android 8.0+ (API 26), Android Studio, JDK 17. Clone, open, run.
Google Drive backup needs an OAuth client ID as `drive.oauth.client.id` in `local.properties`;
everything else works without it.

Built with Kotlin, Jetpack Compose, Room, Hilt, WorkManager and Retrofit.
Developed with [Claude Code](https://claude.ai/code).
