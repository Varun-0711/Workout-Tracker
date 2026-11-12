# Google Sheets setup

The app can create a private **Workout Tracker** spreadsheet in your Google Drive and manually replace its contents with the latest data saved on this phone. It creates `Workouts`, `Sets`, `Weigh-ins`, and `Routines` tabs. Sync is one-way: edits made in the spreadsheet are not imported into the app. The app stores only the spreadsheet ID and last-sync time; Google access tokens stay in memory.

## One-time Google Cloud setup

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create or select a project.
2. In **APIs & Services → Library**, enable **Google Sheets API**.
3. Configure the Google Auth Platform consent screen. While the app is in testing, add the Google account you will use on the phone as a test user.
4. In **APIs & Services → Credentials**, create an **OAuth client ID** with application type **Android**. Use package name `com.varun.workouttracker` and the SHA-1 certificate fingerprint for the APK you install.
5. To get the debug certificate fingerprint from this project, run in PowerShell:

   ```powershell
   .\gradlew signingReport
   ```

   Use the SHA-1 shown for the `debug` variant. If you later install a release-signed APK, register its signing certificate fingerprint too.

The app requests Google's `drive.file` scope, which limits access to files created by or explicitly opened with this app. It creates the spreadsheet itself, so it does not need broad access to your Drive. No OAuth client secret belongs in the app or repository.

## On your phone

After the OAuth client is configured and the app is installed, open **Settings → Google Sheets Backup → Connect Google Sheets**. Accept the Google access prompt. The first sync creates the spreadsheet; later taps on **Sync now** update it. Use **Open spreadsheet** to view it in a browser or Sheets app. Disconnecting removes the app's local link and revokes its Google authorization; it does not delete the spreadsheet from Drive.

The spreadsheet stays private in your Google Drive unless you choose to share it from Google Sheets.
