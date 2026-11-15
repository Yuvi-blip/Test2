# Step Data Lab — Kotlin / Android Studio

This starter project has two safe components:

1. **Local synthetic-data sandbox:** a mock step counter (+100, +500, reset) held only in app memory.
2. **Health Connect inspector:** requests read-only step permission and displays the sum of step records visible to this app over the previous 24 hours.

It deliberately does not write fabricated health records to Health Connect, alter StepUp, or interact with rewards/leaderboards. It does not upload health data.

## Requirements
- Latest stable Android Studio
- JDK 17 (Android Studio includes a suitable JBR)
- Android SDK Platform 36
- Android 15+ physical phone or compatible emulator
- Health Connect provider available on the device

## Install Android Studio
1. Download Android Studio from https://developer.android.com/studio
2. Install using the default setup wizard.
3. In SDK Manager, install **Android SDK Platform 36** and a recent Android SDK Build-Tools.
4. Let Android Studio use its bundled JDK (17 or newer compatible with the selected Gradle/AGP setup).

## Open and run
1. Extract `StepDataLab_Android_Project.zip`.
2. Android Studio → **Open** → select the extracted `StepUpHealthConnectTester` folder.
3. Wait for Gradle sync. Accept prompts to install missing SDK components.
4. On your phone, enable Developer options → USB debugging, connect by USB, and accept the debugging prompt. Alternatively create an Android 15+ emulator.
5. Select the device in Android Studio and press **Run**.

## On Android 15+
Health Connect is part of Android on Android 14 and later. Open Settings and search for **Health Connect** (the exact Settings path varies by device). Ensure the provider is up to date and set a screen lock if requested.

## Use the app
1. Open **Step Data Lab**.
2. In Local synthetic-data sandbox, tap +100 or +500. This is only an in-app mock value and disappears when the process/app state is reset.
3. In Health Connect inspector, tap **Request step permissions** and grant read access.
4. Tap **Read last 24 hours** to read the visible records.
5. Compare the result with your phone's Health Connect Steps screen. Different apps can show different totals because of time windows, data origins, deduplication and aggregation rules.

## Important
The Health Connect reader sums raw records returned for the last 24 hours. It is an inspection aid, not a canonical daily total. Android's official Health Connect documentation recommends aggregated step reads for daily totals. The app intentionally asks only for READ_STEPS; it does not request WRITE_STEPS.


## Build an APK in GitHub Actions (no Android Studio on your computer)
1. Create a new GitHub repository.
2. Extract this ZIP and upload all its contents (including the hidden `.github` folder) to the repository's `main` branch.
3. Open the repository's **Actions** tab. If prompted, enable GitHub Actions.
4. Select **Build Android APK** and click **Run workflow** (or push to `main`).
5. When the workflow finishes, open its run and download the `step-data-lab-debug-apk` artifact.
6. Extract the downloaded artifact on your phone and tap `app-debug.apk` to install.

This produces a debug APK for personal testing. Android may ask you to allow installs from the browser or file manager you used to download it.
