# PerfectOutfit

An Android app that recommends what to wear for cycling and running based on real-time weather conditions. Rate your outfits after each ride or run to build a personalized recommendation database that learns your preferences over time.

## Features

- **Smart outfit recommendations** — fetches live weather for your location and suggests clothing based on temperature, wind, rain, and past ratings
- **Cycling & running support** — separate clothing catalogs and recommendations for each sport
- **Personal outfit history** — log past and present outfits with comfort ratings (too cold / perfect / too hot)
- **Weather timeline** — scrollable 24-hour weather view; pick any hour to see what to wear
- **Custom clothing catalog** — add, edit, or remove items per body part; changes reflect in future recommendations
- **Export & import** — back up your outfit history to JSON and restore it on any device
- **Instant rating notifications** — get a reminder to rate your outfit after finishing a ride or run

## Screenshots

<!-- Add screenshots here -->

## Tech Stack

- **Language:** Kotlin 2.x
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM with Hilt dependency injection
- **Database:** Room (local outfit history)
- **Networking:** Retrofit + kotlinx-serialization → [Open-Meteo API](https://open-meteo.com/) (no API key required)
- **Location:** FusedLocationProviderClient + Geocoder
- **Persistence:** Jetpack DataStore (preferences)
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 36

## Getting Started

### Prerequisites

- Android Studio Narwhal (2025.1) or newer
- JDK 17+
- Android device or emulator running Android 8.0+

### Build & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/robert-crump/PerfectOutfit.git
   cd PerfectOutfit
   ```

2. Open the project in Android Studio.

3. Sync Gradle and run on a device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

No API keys are required — weather data comes from the free [Open-Meteo](https://open-meteo.com/) API.

## How It Works

1. **Home screen** — your current location is detected and weather is fetched automatically. Select an hour to see recommended clothing items for that time.
2. **Log an outfit** — tap "Custom outfit" or "Edit outfit" to record exactly what you wore, or use the History FAB to log a past outfit.
3. **Rate comfort** — after your activity, rate the outfit as too cold, perfect, or too hot.
4. **Better recommendations** — the app widens its temperature search and prioritizes items from outfits you rated as perfect in similar conditions.

## Development

This project was developed with assistance from [Claude Code](https://claude.ai/code) by Anthropic.
