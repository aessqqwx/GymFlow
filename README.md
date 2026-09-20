# GymFlow 1.0

Android fitness companion built with Kotlin, Jetpack Compose, Material 3 and AndroidX Media3.

## Core features
- multi-step RU/EN onboarding for gym/home training, equipment, schedule, date of birth, sex, height/weight, program, per-day exercises, profile, nutrition targets and required agreements
- snapping Material wheel pickers for date of birth, height and weight; date of birth is stored as ISO `YYYY-MM-DD`
- personalized gym/home workout plans with persistent workout state, swipe-to-complete, Rest screen, post-workout processing, history and personal records
- nutrition tracking for protein, calories and water with saved daily goals and quick adjustments
- measurements, goals, records and four-week muscle-load summaries
- profile with persistent photo picker avatar and editable name/description/date of birth
- local music library using SAF + AndroidX Media3/MediaSessionService, mini player, Now Playing, seek, queue, shuffle/repeat, playlists and background playback
- notification categories for workouts, protein/nutrition, motivation and measurements
- System/Light/Dark themes and app text scaling layered on top of the Android accessibility font scale
- structured JSON export/import/reset; audio files themselves are never embedded in backup or physically deleted by GymFlow

## Verification without Android SDK
This source checkpoint includes reproducible platform-independent checks:

```sh
./tools/pure-tests/run.sh
./tools/final-checks/run.sh
```

A full Android Gradle build still requires a working Gradle Wrapper/system Gradle and Android SDK.

Training and nutrition features are general-purpose and are not medical advice.
