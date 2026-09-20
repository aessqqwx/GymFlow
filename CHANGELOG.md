# GymFlow 1.0

Final source checkpoint after PROMPT 1–3.

- Removed the legacy exercise animation/video pipeline and all embedded exercise media.
- Reworked workout progression around explicit exercise/set/rest/completion state with duplicate-completion protection and persistent rest end time.
- Added static exercise guidance, swipe-to-complete, dedicated Rest UI, post-workout processing, persistent workout history and personal-record updates.
- Rebuilt onboarding, Home, Nutrition, Measurements, Goals, Records, Profile and Settings in a consistent Material 3 / expressive visual language.
- Added Russian and English localization, localized weekdays/units, themes, accessibility-oriented text scaling and notification controls.
- Added date of birth stored as ISO YYYY-MM-DD plus snapping wheel pickers for date, height and weight, with leap-year/future-date validation and legacy backup migration.
- Added AndroidX Media3 background playback with MediaSessionService, SAF music import, album art, mini player, Now Playing, queue, shuffle/repeat and GymFlow playlists.
- Expanded structured JSON backup/import/reset to include profile, plan, nutrition, measurements, goals, records, workout history, settings and supported music metadata/player state without embedding or deleting user audio files.
- Version name remains 1.0.

## Release-candidate polish
- Expanded the first onboarding page into a fuller GymFlow introduction covering personalized training, workout flow, progress, nutrition, music, reminders, backups, themes, palettes and language.
- Added 30 RU and 30 EN motivation messages for both notifications and Home insights.
- Kept all existing color palettes (Monochrome, System, Blue, Brown, Green, Purple).
- Reordered Settings so legal links stay at the very bottom.
- Changed the Telegram entry to a paper-plane icon with the label “GymFlow” while keeping https://t.me/GymFlowOfficial.
- Expanded Terms of Use and Privacy Policy copy while keeping the app’s local-data behavior accurate.
- Kept versionName at 1.0.
