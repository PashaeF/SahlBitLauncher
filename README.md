# SahlBit Launcher 0.3.0 — Build Candidate

A Persian RTL Android launcher designed for older adults and children, with simplified navigation, configurable contact pages, and an accessible call interface.

## Build Configuration

- **Application ID:** `ir.sahlbit.launcher`
- **Version:** `0.3.0`
- **Version Code:** `14`
- **Minimum Android:** API 29 (Android 10)
- **Compile SDK:** 35
- **Target SDK:** 35
- **JDK:** 17
- **Kotlin:** 2.0.21
- **Android Gradle Plugin:** 8.9.2
- **Gradle:** 8.11.1

## Features

### Launcher
- Persian RTL interface.
- Simplified home screen designed for older adults and children.
- Large analog clock.
- Battery percentage and charging status.
- Local PIN-protected management dashboard.
- Multiple home pages with swipe navigation.
- Local storage of launcher settings and card configurations.
- Screen timeout configuration, subject to Android special access permissions.

### Contact Management
- Add contacts from the Android Contacts provider.
- Support for multiple phone numbers.
- Custom contact photos.
- Contact card management and ordering.
- Open the Android system Contacts editor to modify actual contact information.
- Refresh contact information when returning to SahlBit.

### Page and Layout Management
- Create, delete, rename, duplicate, and reorder pages.
- Configure the number of rows on each page.
- Configure the number of cards independently for each row (1–4 cards).
- Configure the final row alignment:
  - Left
  - Center
  - Right
- Persist row layout settings independently for each page.
- Preview the configured row counts and final-row alignment.
- Home screen rendering uses the saved row layout configuration.

### Drag and Drop
- Card positions are measured on screen.
- Pointer hit-testing identifies the card under the dragged item.
- Cards can be moved to the selected drop target.
- Drag-and-drop still requires physical-device testing, particularly on dense grids and during page navigation.

### Calling and Telecom Integration
- Android Telecom `InCallService` integration.
- Dedicated call interface.
- Call control through Android Telecom APIs:
  - `Call.answer()`
  - `Call.reject()`
  - `Call.disconnect()`

#### Call Screen Colors

| Call state | Background |
|---|---|
| Incoming ringing | Blue (`#1976D2`) |
| Outgoing dialing / connecting | Yellow (`#FFC107`) |
| Connected call | Green (`#2E7D32`) |

- Incoming calls provide answer and decline controls.
- Outgoing calls provide a cancel control.
- Connected calls provide a hang-up control.
- Contact information and available contact photos are displayed during calls.
- Proximity sensor integration is intended to turn off the display while the phone is held near the user's ear.
- The InCallService tracks multiple Call objects and selects a foreground call when call states change.

**Note:** Multi-call tracking is preliminary groundwork. Full call-waiting behavior and multi-call controls are not certified.

### Permissions and Privacy
- Launcher settings and card configurations are stored locally.
- Contact access requires the relevant Android permissions or system selection flow.
- Screen timeout modification requires the user to grant Android's `WRITE_SETTINGS` special access.
- Telecom functionality depends on Android's default Dialer role and system permissions.
- No server-side account or synchronization service is included in this source.

## Build with GitHub Actions

The repository includes a GitHub Actions workflow that:

1. Checks out the source code.
2. Configures JDK 17.
3. Configures Gradle 8.11.1.
4. Runs `gradle assembleDebug`.
5. Uploads the debug APK as an artifact named `sahlbit-debug-apk`.

The expected APK output is:

`app/build/outputs/apk/debug/app-debug.apk`

The workflow can be triggered by pushing to the `main` branch or manually through GitHub Actions.

## Verification Status

**This is a build candidate, not a verified production release.**

The source has not been confirmed through a successful Android Gradle Plugin build or comprehensive physical-device testing.

The following areas require additional verification:

- Compilation and runtime behavior on Android 10–15.
- Row layout behavior on different screen sizes and densities.
- Drag-and-drop reliability.
- Incoming calls while the screen is locked.
- Default Dialer role eligibility.
- Ringtone and call notification behavior.
- Bluetooth and audio routing.
- Dual-SIM selection and call waiting.
- Proximity sensor behavior across device manufacturers.
- Contact photo availability and permission handling.
- Samsung One UI and other OEM-specific Telecom behavior.

## Security and Device Restrictions

- Secure Mode / Device Owner kiosk locking is not included.
- Immersive mode alone cannot guarantee permanent system navigation-bar or status-bar suppression.
- Android's default launcher and default phone application should not be replaced until the APK has been built, tested, and a recovery path has been confirmed.
- Keep Samsung One UI Home available during testing.

## Version 0.3.0 Changelog

### Added
- Per-page row layout configuration.
- Independent card counts for individual rows (1–4).
- Independent final-row alignment (left, center, right).
- Home screen rendering based on persisted row layout settings.
- Layout preview reflecting row counts and final-row alignment.

### Build and Testing
- Updated application version to `0.3.0`.
- Updated version code to `14`.
- Android Gradle build and physical-device verification remain pending.
