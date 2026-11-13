# Workout Tracker PC

A view-only browser companion to the Android app. It reads the spreadsheet created by the phone and shows workout totals, a calendar, searchable history, every recorded set, saved routines, exercise progress, lifting volume, and body-weight trends. It runs locally without a build step or external npm dependencies.

## Start

Install Node.js if it is not already available. In PowerShell, from the project folder:

```powershell
cd 'D:\Workout Tracker\pc'
npm start
```

Open **http://localhost:4173**. Click **Explore demo** to try the dashboard before configuring Google. Demo data is labeled and never mixed with your workouts. Stop the server with Ctrl+C.

## Developer Google setup (once for this PC companion)

Use the same Google Cloud project as the Android app:

1. Enable **Google Sheets API**, **Google Drive API**, and **Google Picker API**.
2. Create an OAuth client with type **Web application**. Add `http://localhost:4173` as an authorized JavaScript origin. This is a separate client from the Android OAuth client, within the same Cloud project. Add testing accounts to your consent screen while the project is in testing.
3. Create an API key for Google Picker. Apply website restrictions for `http://localhost:4173/*` and `https://docs.google.com/*` (the latter is required for Picker's iframe), and restrict the key to the Google Picker API. Check Google's current Picker configuration guide linked below when configuring restrictions.
4. Copy `config.example.js` to `config.js` and fill in the web OAuth client ID, browser API key, and numeric Cloud project number as `appId`. These are public client identifiers, not a client secret. `config.js` is ignored by Git; never put a client secret or access token in it.

```powershell
Copy-Item config.example.js config.js
```

Users of a configured installation only click **Connect Google Sheets**, sign into their account, and choose the **Workout Tracker** spreadsheet previously synced from their phone. They do not need to create their own Google Cloud project. For another origin or a future hosted distribution, register that origin and adjust API-key restrictions as the developer.

## Sync behavior and privacy

- Finish workouts and tap **Sync now** on the phone first. On PC, click **Refresh** to read that snapshot. Refresh does not trigger a phone sync. The displayed time is when the PC read Sheets, not the time of the last phone sync.
- The PC makes read requests only. It requests `drive.file` for the user-selected file through Google Picker; that scope itself permits edits, but this companion does not send write requests. It avoids broad access to every spreadsheet in your Drive.
- Google access tokens and loaded workout data stay in page memory. There is no server-side Google login, browser health-data cache, or token saved on disk. Reloading requires reconnecting. Disconnect clears the page's data and token; it does not revoke the Android app's shared project authorization or delete the sheet.
- The local HTTP server binds only to `127.0.0.1` and serves an explicit allowlist of dashboard assets. It does not expose the rest of the Android repository.
- Step history and daily goals are not in the current Android Sheets export, so they are not shown as synced PC metrics.

## Reading progress

Date filters use your PC's local calendar dates. History and routines retain their original units (kg, metres, seconds). Strength volume includes completed sets with external weight only; bodyweight reps and jump counts are excluded. Exercise records are within the selected range, with the Epley estimated one-rep max limited to weighted sets of 1–10 reps; a single rep uses its recorded weight. These are estimates, not tested maximums. Body-weight charts use daily averages with a trailing seven-calendar-day average of recorded daily values. Missing days are not filled in.

## Checks

```powershell
npm test
```

Tests cover snapshot parsing, completed strength volume, records, date boundaries, and weight trends. No Google credentials are needed for tests.

Google reference: [web Picker integration](https://developers.google.com/workspace/drive/picker/guides/web-picker) and [Picker app ID requirement](https://developers.google.com/workspace/drive/picker/reference/picker.pickerbuilder.setappid).
