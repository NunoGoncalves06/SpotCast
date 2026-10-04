# SpotCast

Android app for the SWMAD-01 group project. A signed-in user saves outdoor spots at the
current GPS position, sees them on a map, and checks the weather forecast for each spot.

Package name: `com.swmad.spotcast`

## Demo, report and synopsis

- [Demo video](docs/SpotCast_demo.mp4) (38 seconds, recorded on the Pixel 10a emulator)
- [Project report](docs/Report.pdf)
- [Synopsis](docs/Synopsis.pdf)

## What is in the project

| Requirement | Where |
|---|---|
| Kotlin, coroutines | all of `app/src/main/java/com/swmad/spotcast` |
| Jetpack Compose | `ui/` |
| Compose Navigation | `ui/navigation/Navigation.kt` |
| Firebase Authentication | `integration/auth/AuthFireBaseService.kt` |
| Firestore | `integration/firestore/FireStoreSpotService.kt` |
| HTTP API with Ktor (Open-Meteo) | `integration/weather/KtorWeatherService.kt` |
| HTTP API with Ktor (Nominatim address search) | `integration/geocoding/KtorGeocodingService.kt` |
| GPS | `integration/location/LocationService.kt`, `ui/spots/AddSpotScreen.kt` |
| Save a place you are not at | `ui/spots/AddSpotScreen.kt` ("Another place": address or coordinates, confirmed on a map) |
| Map (OpenStreetMap with osmdroid) | `ui/components/OsmMap.kt`, used in `ui/map/MapScreen.kt` and `ui/details/DetailsScreen.kt` |
| Light, dark or system theme | `ui/theme/ThemeViewModel.kt`, chosen in `ui/profile/ProfileScreen.kt`, applied in `MainActivity.kt` |
| Firestore real-time updates | `FireStoreSpotService.observeSpots`, collected in `ui/spots/SpotsViewModel.kt` |

The map comes from OpenStreetMap. It needs no API key, no Google Cloud project and no
billing account. The weather API needs no key either. The only account the app depends on
is the Firebase project.

## Setup

### 1. Firebase

1. Create a project at https://console.firebase.google.com (analytics can stay off).
2. Add an Android app with the package name `com.swmad.spotcast`.
3. Download `google-services.json` and put it in `app/`. This file is not in the
   repository (it is listed in `.gitignore`), so the project does not build until it is
   there.
4. Authentication: enable the provider "Email/Password".
5. Firestore Database: create the database, then paste the content of `firestore.rules`
   into the Rules tab and publish.
6. Create the test user for the report under Authentication > Users, or sign up in the app.

### 2. Run

Open the `SpotCast` folder in Android Studio and run the `app` configuration, or:

```
gradlew :app:assembleDebug
```

In the emulator the GPS position is set under Extended controls > Location.

## Hand-in

- Clean zip: run `gradlew clean`, then zip the folder without `.gradle`, `.idea`, `.kotlin`,
  `build`, `app/build` and `local.properties`. Keep `app/google-services.json` in the zip,
  otherwise the app cannot sign in on the examiner's machine.
- A signed APK is only asked for when Google Maps is used, which this app does not.

## Data

Firestore: `users/{uid}/spots/{spotId}` with the fields `name`, `category`, `note`,
`location` (geopoint) and `createdAt` (server timestamp).

Weather: `GET https://api.open-meteo.com/v1/forecast` with `latitude`, `longitude`,
`current`, `daily`, `timezone` and `forecast_days`. No key is needed.

Address search: `GET https://nominatim.openstreetmap.org/search` with `q`, `format=jsonv2`
and `limit=1`. No key is needed; the service asks for a User-Agent and at most one request
per second.

Map tiles: `https://tile.openstreetmap.org`, (c) OpenStreetMap contributors.
