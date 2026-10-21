# Workout Tracker

A local-first Android workout tracker. Log sets in a running session, pause and resume the session clock, and keep completed workouts in local history.

The interface uses a grayscale palette. Color is reserved for workout timers and, when added, calendar status markers.

## Open the app

Open this folder in Android Studio and let Gradle sync. The project targets Android API 36, compiles with API 37, and supports Android 8.0 (API 26) and later. Android Studio must have the Android SDK platform and build tools installed.

## Current slice

- Native Kotlin and Jetpack Compose app
- Activity-first workout setup for strength, running, cycling, and jogging
- Cardio interval countdowns with setup, repeating work/rest and audible cues, plus a continuous stopwatch mode; rep and optional weight sets for strength
- Offline active workout and history persistence on device
- Starter and custom exercises, editable reps and optional weight, and quick set steppers
- Copy the previous set, complete sets with one tap, and use the rest timer
- Create strength routines from the Workout chooser or save a current workout, including set entries and the rest timer setting
- Pause and resume the session clock and rest timer
- Finish workouts and browse saved sessions

Google Drive sync, planning, reminders, and sharing remain future slices from the product spec.
