# SpotCast

SpotCast tells you where to go today. Every spot you save gets a **Go**, **Maybe** or **Skip** for a beach day,
surfing, hiking, photos and a picnic, worked out from the forecast of that exact place. The app opens on a map
where each pin has the colour of today's answer. Tap a pin for the reason, a better activity for that spot, and the
best day of the week.

It is an Android app in Kotlin and Jetpack Compose, made for the course SWMAD-01 Mobile Application Development
(Aarhus University). Package name: `com.swmad.spotcast`.

<p align="center">
  <img src="docs/previews/demo.gif" width="300" alt="SpotCast in use: the Today map, a spot's report and its best day, adding a spot by address, deleting a spot, and the dark theme">
</p>

## Try it

- **[Download SpotCast.apk](https://github.com/NunoGoncalves06/SpotCast/releases/latest/download/SpotCast.apk)**
  (Android 8.0 or newer, about 75 MB). All versions are on the
  [Releases](https://github.com/NunoGoncalves06/SpotCast/releases) page.
- [Demo video](docs/SpotCast_demo.mp4) (1 minute, recorded on the Pixel 10a emulator)
- [Project report](docs/Report.pdf) (the version in this repository leaves out the test user line)
- [Synopsis](docs/Synopsis.pdf)

To install the APK: open the file on the phone and allow installs from the browser or file manager when Android asks,
then press Install and sign in or create an account. The APK is a debug build signed with the debug key, so Play
Protect may warn about an unknown developer; choose to install anyway.

[![Watch the SpotCast demo video](docs/previews/video_poster.jpg)](docs/SpotCast_demo.mp4)

## Preview

| Today | Report card | Spots |
|:---:|:---:|:---:|
| <img src="docs/previews/today.jpg" width="240" alt="Today map with Go, Maybe and Skip pins"> | <img src="docs/previews/report.jpg" width="240" alt="Report card for a spot in Photos mode: not a photo day, with the best day of the week"> | <img src="docs/previews/spots.jpg" width="240" alt="Spots list with today's best activity per spot"> |
| **Spot details** | **Delete, with confirmation** | **Dark theme** |
| <img src="docs/previews/details.jpg" width="240" alt="Spot details with the verdict for every activity"> | <img src="docs/previews/delete.jpg" width="240" alt="Delete confirmation dialog"> | <img src="docs/previews/dark.jpg" width="240" alt="Today map in the dark theme"> |

## Run it yourself

The app needs one account of your own: a Firebase project. It has no other key. The map (OpenFreeMap tiles with
MapLibre) and both weather services (Open-Meteo) and the address search (Nominatim) need no key.

### 1. Create the Firebase project

1. Open https://console.firebase.google.com and create a project (analytics can stay off).
2. Add an Android app with the package name **`com.swmad.spotcast`**. No SHA-1 is needed.
3. Download `google-services.json` and put it in the `app/` folder. It is not in this repository (it is listed in
   `.gitignore`); `app/google-services.json.example` shows its shape.
4. **Authentication > Sign-in method:** enable **Email/Password**.
5. **Firestore Database:** create the database, open the **Rules** tab, paste the content of
   [`firestore.rules`](firestore.rules) and press **Publish**.

Without step 3 the project does not build. To only compile it, copy the example file to
`app/google-services.json`; the app then starts, but sign-in does not work until the file comes from your own project.

### 2. Build and run

Open this folder in Android Studio and run the `app` configuration on an emulator or a phone, or from a terminal:

```
gradlew :app:assembleDebug
```

In the emulator, set the GPS position under **Extended controls > Location** before you use "Where I am now". Sign up
in the app with any e-mail and a password of six characters or more.

### 3. Tests

```
gradlew :app:testDebugUnitTest
```

The unit tests cover the Go, Maybe and Skip rules, the two Ktor services (with fake server answers, including broken
ones), the summary line of the map and the view model of the spots.

## How it decides

The rules are fixed and the same for everybody; they are in [`domain/Activity.kt`](app/src/main/java/com/swmad/spotcast/domain/Activity.kt).
For example, surfing is a Go with waves of 0.8 m or more and wind under 40 km/h, and photos are a Go when broken
clouds can light up the sunset. A Skip always says why. Only beach spots are rated for beach days and surfing, because
the wave model returns data for any point and would otherwise rate a city park.

## What is in the project

| Part | Where |
|---|---|
| Kotlin and coroutines | all of `app/src/main/java/com/swmad/spotcast` |
| Jetpack Compose | `ui/` |
| Compose Navigation | `ui/navigation/Navigation.kt` |
| Firebase Authentication | `integration/auth/AuthFireBaseService.kt` |
| Firestore, with a real-time listener | `integration/firestore/FireStoreSpotService.kt`, collected in `ui/spots/SpotsViewModel.kt` |
| Weather with Ktor (Open-Meteo forecast and marine) | `integration/weather/KtorWeatherService.kt` |
| Address search with Ktor (Nominatim) | `integration/geocoding/KtorGeocodingService.kt` |
| GPS | `integration/location/LocationService.kt`, `ui/spots/AddSpotScreen.kt` |
| Map with MapLibre, recoloured for light and dark | `ui/components/SpotMap.kt` |
| Today map, activity bar and report card | `ui/map/MapScreen.kt` |
| Save a place you are not at (address or coordinates, confirmed on a map) | `ui/spots/AddSpotScreen.kt` |
| Delete a spot, always with a confirmation | `ui/components/DeleteSpotDialog.kt` |
| Light, dark or system theme | `ui/theme/ThemeViewModel.kt`, `ui/profile/ProfileScreen.kt` |
| Security rules | `firestore.rules` |

## Data and services

- **Firestore:** `users/{uid}/spots/{spotId}` with `name`, `category`, `note`, `location` (geopoint) and `createdAt`
  (server timestamp). The rules let a user read and delete only their own spots, and accept a new spot only with
  these five fields, a name of up to 100 characters and a note of up to 1000.
- **Forecast:** `GET https://api.open-meteo.com/v1/forecast` (temperature, rain chance, wind, cloud cover, sunset,
  seven days) and, for beach spots, `GET https://marine-api.open-meteo.com/v1/marine` (`daily=wave_height_max`).
- **Address search:** `GET https://nominatim.openstreetmap.org/search` with `format=jsonv2` and `limit=1`. The service
  asks for a User-Agent that names the app, and for at most one request per second.
- **Map:** style `https://tiles.openfreemap.org/styles/positron`, &copy; OpenMapTiles, data &copy; OpenStreetMap
  contributors.
