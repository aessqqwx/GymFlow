# Verification — 1.5 source candidate

Current changes: versionName 1.5/versionCode 4, settings label corrected, playback scrubbing reset keyed to mediaId, motion updates documented in MOTION_AUDIT.md. CI artifact and smoke-test paths target GymGlow_v1.5.apk.

Executed for 1.5: XML/resources and existing static guards PASS; Kotlin lexical delimiter check PASS (not a compiler); shell syntax, workflow YAML parsing, version consistency and git whitespace PASS.

Local Gradle cannot download its distribution (network unreachable). Kotlin compiler and Android SDK are absent. Compilation, Kotlin logic tests, lint and emulator checks remain unexecuted for 1.5. The owner approved public publication; aessqqwx/GymGlow-Test has been created. CI execution is pending upload.

# Verification — 1.1.1 candidate

## Available requirements

`GymFlow_chat_handoff.txt` is a context handoff, explicitly not a full chat export. It lists build failures and APK delivery requirements, but does not include original PROMPT 1–3. Feature intent was additionally checked against the provided source README/CHANGELOG. Full equivalence to missing prompts cannot be asserted.

## Executed locally

- XML parsing, RU/EN resource parity and reference checks: PASS.
- Duplicate resource detection: PASS.
- Existing static checks for backup schema, player service, file permissions and removed exercise media: PASS.
- Git whitespace check and shell syntax checks: PASS.
- Latest existing failed Actions log inspected; reported Compose/Kotlin errors addressed in source.

## Prepared, not yet executed

- Nine pure Kotlin test groups: progression, normalization, history idempotency, personal records, generated plans, dates, pause/resume, saved custom plans, statistics.
- Full Android compilation and lint.
- Android emulator installation, startup, force-stop/relaunch, UI dump, screenshot, crash-log check.

Local runtime has Java but no Kotlin compiler or Android SDK; Gradle distribution download is unavailable. No successful APK build or device test is claimed.

## Fixes from code review

- State callbacks replace assignments to immutable screen parameters.
- Corrected Compose annotations, icon, labels, imports and composable scope.
- Custom session plans persist alongside progress and appear as resumable on Home.
- Workout completion freezes pre-session history so the just-saved workout is not counted twice on the completion screen.
- Active session preference updates become immediately readable for export and reopen.
- Startup waits for persisted lists before allowing edits.
- Swipe completion uses current callback/enabled state after its animation.
- Save errors preserve the session and expose a retry action.
- Music restoration respects a queue selected while asynchronous loading was running.
- Avatar URI changes invalidate the displayed bitmap after a new photo.

## Manual/device checks still required

- Complete onboarding in RU/EN; back navigation, agreements, dates, equipment and schedule.
- Complete a workout, pause/resume rest, background/reopen, repeat history, rename/delete templates, confirm saved notes and mood.
- Exercise animations/videos were intentionally removed by the source specification. UI transitions, button scale, swipe, rest and completion animations remain; visual smoothness has not been measured on a device.
- Verify notification permission, reboot scheduling, monthly/recovery reminders on a real device.
- Import music, background playback, audio focus/headphones, seek/shuffle/repeat/playlists, missing-file handling.
- Export/import/reset and photo replacement with real content-provider permissions.
- Review emulator screenshot, large-font layouts, accessibility and dark/light palettes.

## Test-app packaging

Automatic safety review rejected publishing the existing debug keystore. It is excluded from this repository; debug uses the standard generated signing key and applicationIdSuffix .test, installs alongside the original app and leaves its private data untouched. Smoke checks target com.aess.gymflow.test.
